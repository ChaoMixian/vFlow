// 文件: main/java/com/chaomixian/vflow/services/TriggerService.kt
// 描述: 一个专用于处理后台触发器（如按键事件）的前台服务。
package com.chaomixian.vflow.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import kotlinx.coroutines.*
import java.io.File

class TriggerService : Service() {

    private lateinit var workflowManager: WorkflowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keyEventJob: Job? = null

    companion object {
        private const val TAG = "TriggerService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "trigger_service_channel"
        const val ACTION_RELOAD_TRIGGERS = "com.chaomixian.vflow.RELOAD_TRIGGERS"
        // [新增] 定义接收按键事件的 Action
        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
    }

    // 监听工作流更新的广播接收器
    private val workflowUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WorkflowManager.ACTION_WORKFLOWS_UPDATED) {
                Log.d(TAG, "接收到工作流更新通知，重新加载按键触发器。")
                reloadKeyEventTriggers()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        workflowManager = WorkflowManager(applicationContext)
        val filter = IntentFilter(WorkflowManager.ACTION_WORKFLOWS_UPDATED)
        LocalBroadcastManager.getInstance(this).registerReceiver(workflowUpdateReceiver, filter)
        Log.d(TAG, "TriggerService 已创建。")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "TriggerService 已启动。")

        // [修改] 扩展 onStartCommand 的功能
        when (intent?.action) {
            ACTION_KEY_EVENT_RECEIVED -> {
                // 如果是按键事件，则处理它
                handleKeyEvent(intent)
            }
            else -> {
                // 否则，执行原有的启动/重载逻辑
                if (intent?.action == ACTION_RELOAD_TRIGGERS) {
                    Log.d(TAG, "通过 Intent 请求重新加载触发器。")
                }
                reloadKeyEventTriggers()
            }
        }

        return START_STICKY
    }

    /**
     * [新增] 处理接收到的按键事件
     */
    private fun handleKeyEvent(intent: Intent) {
        val device = intent.getStringExtra("device")
        val keyCode = intent.getStringExtra("key_code")

        if (device.isNullOrBlank() || keyCode.isNullOrBlank()) {
            Log.w(TAG, "接收到无效的按键事件: device=$device, keyCode=$keyCode")
            return
        }

        Log.d(TAG, "服务已唤醒并接收到按键事件: device=$device, keyCode=$keyCode")

        // 在一个新的协程中查找并执行工作流，避免阻塞服务主线程
        serviceScope.launch {
            val workflowsToExecute = workflowManager.findKeyEventTriggerWorkflows()
                .filter {
                    val config = it.triggerConfig
                    config?.get("device") == device && config?.get("key_code") == keyCode
                }

            if (workflowsToExecute.isNotEmpty()) {
                Log.i(TAG, "找到 ${workflowsToExecute.size} 个匹配的工作流，准备执行。")
                workflowsToExecute.forEach {
                    WorkflowExecutor.execute(it, applicationContext)
                }
            } else {
                Log.w(TAG, "未找到匹配此按键事件的工作流。")
            }
        }
    }


    private fun reloadKeyEventTriggers() {
        // 先停止当前正在运行的监听任务
        keyEventJob?.cancel()
        Log.d(TAG, "正在停止旧的按键监听任务...")

        keyEventJob = serviceScope.launch {
            // 确保旧进程已终止
            ShizukuManager.execShellCommand(applicationContext, "killall getevent")
            delay(500) // 等待进程被完全终止

            val keyEventWorkflows = workflowManager.findKeyEventTriggerWorkflows()
            if (keyEventWorkflows.isEmpty()) {
                Log.d(TAG, "没有已启用的按键触发器，停止监听。")
                return@launch
            }

            // 按输入设备对触发器进行分组
            val triggersByDevice = keyEventWorkflows.groupBy {
                it.triggerConfig?.get("device") as? String
            }

            Log.d(TAG, "找到 ${triggersByDevice.size} 个需要监听的输入设备。")

            triggersByDevice.forEach { (device, workflows) ->
                if (device.isNullOrBlank()) return@forEach

                // 为每个设备创建一个监听脚本
                val script = createShellScriptForDevice(device, workflows.mapNotNull {
                    it.triggerConfig?.get("key_code") as? String
                })

                if (script.isNotBlank()) {
                    launch {
                        try {
                            // 将脚本写入临时文件
                            val scriptFile = File(cacheDir, "key_listener_${device.replace('/', '_')}.sh")
                            scriptFile.writeText(script)
                            scriptFile.setExecutable(true)
                            Log.d(TAG, "为设备 $device 创建脚本: ${scriptFile.absolutePath}")

                            // 通过 Shizuku 执行脚本，这是一个长期运行的命令
                            val result = ShizukuManager.execShellCommand(applicationContext, "sh ${scriptFile.absolutePath}")
                            Log.d(TAG, "设备 $device 的监听脚本已结束，输出: $result")
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                Log.d(TAG, "设备 $device 的监听任务已被取消。")
                            } else {
                                Log.e(TAG, "执行设备 $device 的监听脚本时出错。", e)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * [最终修复] 修正了按键码的提取逻辑。
     * - 使用 `awk '{print $2}'` 来精确提取事件行中的第二列（即按键码），替换了之前错误的 `sed` 命令。
     * - [修复] 移除了 `KEY_CODE=` 和 `$(...` 之间的空格来修复 'unexpected `|`' 语法错误。
     * - [新增] 将 getevent 命令放入 `while true` 循环中，以在进程被杀后自动重启。
     * - [修改] 将 `am broadcast` 替换为 `am start-service` 来直接唤醒服务。
     */
    private fun createShellScriptForDevice(device: String, keyCodes: List<String>): String {
        if (keyCodes.isEmpty()) return ""

        val grepPattern = keyCodes.joinToString("|") { "($it.*DOWN|$it.*UP)" }

        val logFile = "${applicationContext.cacheDir.absolutePath}/key_listener.log"
        // [修改] 定义服务的组件名
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"

        return """
            #!/system/bin/sh
            LOG_FILE="$logFile"
            echo "--- SCRIPT START (v6-service-wake) ---" >> "${'$'}LOG_FILE"
            echo "PID: $$" >> "${'$'}LOG_FILE"
            echo "Listening on device: $device" >> "${'$'}LOG_FILE"
            
            while true; do
              echo "Starting getevent process..." >> "${'$'}LOG_FILE"
              getevent -l "$device" | while IFS= read -r line; do
                if echo "${'$'}{line}" | grep -E -q "$grepPattern"; then
                    echo "[MATCH]: Condition met for line: ${'$'}{line}" >> "${'$'}LOG_FILE"
                    KEY_CODE=$(echo "${'$'}{line}" | awk '{print ${'$'}2}')
                    echo "[ACTION]: Extracted KEY_CODE: ${'$'}{KEY_CODE}" >> "${'$'}LOG_FILE"
                    
                    if [ -n "${'$'}{KEY_CODE}" ]; then
                       # [修改] 使用 am start-service 来直接唤醒应用
                       am start-service -n "$serviceComponent" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "$device" --es key_code "${'$'}{KEY_CODE}"
                       echo "[WAKE_UP]: Sent start-service for KEY_CODE: ${'$'}{KEY_CODE}" >> "${'$'}LOG_FILE"
                    fi
                fi
              done
              echo "getevent process for $device exited. Restarting in 1s..." >> "${'$'}LOG_FILE"
              sleep 1
            done
        """.trimIndent()
    }


    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台触发器服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("vFlow 后台服务")
            .setContentText("正在监听自动化触发器...")
            .setSmallIcon(R.drawable.ic_workflows)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(workflowUpdateReceiver)
        Log.d(TAG, "TriggerService 已销毁。")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}