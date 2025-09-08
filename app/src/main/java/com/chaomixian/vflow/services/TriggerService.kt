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
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TriggerService : Service() {

    private lateinit var workflowManager: WorkflowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keyEventJob: Job? = null

    // [核心修复] 为每个按键独立维护单击/双击状态
    private val singleClickJobs = ConcurrentHashMap<String, Job>()
    private val lastPressTimestampMap = ConcurrentHashMap<String, Long>()
    private val doubleClickThreshold = 400L // 双击时间窗口 (毫秒)
    private val longPressThreshold = 500L  // 长按阈值 (毫秒)


    companion object {
        private const val TAG = "TriggerService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "trigger_service_channel"
        const val ACTION_RELOAD_TRIGGERS = "com.chaomixian.vflow.RELOAD_TRIGGERS"
        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
        const val EXTRA_EVENT_TYPE = "event_type" // "UP" or "DOWN"
        const val EXTRA_ACTION_TYPE = "action_type" // 旧版脚本兼容字段
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

        when (intent?.action) {
            ACTION_KEY_EVENT_RECEIVED -> {
                handleKeyEvent(intent)
            }
            else -> {
                if (intent?.action == ACTION_RELOAD_TRIGGERS) {
                    Log.d(TAG, "通过 Intent 请求重新加载触发器。")
                }
                reloadKeyEventTriggers()
            }
        }

        return START_STICKY
    }

    /**
     * [核心修复] 使用 per-key 的时间戳处理按键事件，解决冲突
     */
    private fun handleKeyEvent(intent: Intent) {
        val device = intent.getStringExtra("device")
        val keyCode = intent.getStringExtra("key_code")
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)
        val pressDuration = intent.getLongExtra("duration", 0)

        if (device.isNullOrBlank() || keyCode.isNullOrBlank() || eventType.isNullOrBlank()) {
            return
        }

        val uniqueKey = "$device-$keyCode"

        if (eventType == "UP") {
            // 1. 长按事件处理 (优先级最高，无冲突)
            if (pressDuration >= longPressThreshold) {
                Log.d(TAG, "长按事件检测成功 for $uniqueKey")
                // 长按会取消任何待处理的单击/双击逻辑
                singleClickJobs[uniqueKey]?.cancel()
                lastPressTimestampMap.remove(uniqueKey)
                executeMatchingWorkflows(device, keyCode, "长按")
                return
            }

            // 2. 短按 (立即触发) 事件处理
            executeMatchingWorkflows(device, keyCode, "短按 (立即触发)")

            // 3. 单击 与 双击 的互斥逻辑处理
            val now = System.currentTimeMillis()
            val lastPressTimestamp = lastPressTimestampMap[uniqueKey] ?: 0L

            // 取消上一次单击可能安排的延迟任务
            singleClickJobs[uniqueKey]?.cancel()

            if (now - lastPressTimestamp <= doubleClickThreshold) {
                // 如果两次点击间隔很短，确认为“双击”
                Log.d(TAG, "双击事件检测成功 for $uniqueKey")
                lastPressTimestampMap.remove(uniqueKey) // 重置时间戳，防止三连击
                executeMatchingWorkflows(device, keyCode, "双击")
            } else {
                // 否则，这可能是一次“单击”
                lastPressTimestampMap[uniqueKey] = now
                // 安排一个延迟任务。如果这个任务在延迟时间内没有被下一次点击取消，
                // 那么它就是一次确定的“单击”。
                singleClickJobs[uniqueKey] = serviceScope.launch {
                    delay(doubleClickThreshold)
                    if (isActive) { // 检查任务是否在延迟期间被取消
                        Log.d(TAG, "单击事件触发 for $uniqueKey")
                        executeMatchingWorkflows(device, keyCode, "单击")
                    }
                }
            }
        }
    }

    /**
     * 辅助函数，用于查找并执行匹配的工作流
     */
    private fun executeMatchingWorkflows(device: String, keyCode: String, actionType: String) {
        serviceScope.launch {
            val workflowsToExecute = workflowManager.findKeyEventTriggerWorkflows()
                .filter {
                    val config = it.triggerConfig
                    config?.get("device") == device &&
                            config?.get("key_code") == keyCode &&
                            config?.get("action_type") == actionType
                }

            if (workflowsToExecute.isNotEmpty()) {
                Log.i(TAG, "找到 ${workflowsToExecute.size} 个匹配 '$actionType' 的工作流，准备执行。")
                workflowsToExecute.forEach {
                    WorkflowExecutor.execute(it, applicationContext)
                }
            }
        }
    }


    private fun reloadKeyEventTriggers() {
        keyEventJob?.cancel()
        Log.d(TAG, "正在停止旧的按键监听任务...")

        keyEventJob = serviceScope.launch {
            ShizukuManager.execShellCommand(applicationContext, "killall getevent")
            delay(500)

            val keyEventWorkflows = workflowManager.findKeyEventTriggerWorkflows()
            if (keyEventWorkflows.isEmpty()) {
                Log.d(TAG, "没有已启用的按键触发器，停止监听。")
                return@launch
            }

            val triggersByDevice = keyEventWorkflows.groupBy {
                it.triggerConfig?.get("device") as? String
            }

            Log.d(TAG, "找到 ${triggersByDevice.size} 个需要监听的输入设备。")

            triggersByDevice.forEach { (device, workflows) ->
                if (device.isNullOrBlank()) return@forEach

                val script = createShellScriptForDevice(device, workflows)

                if (script.isNotBlank()) {
                    launch {
                        try {
                            val scriptFile = File(cacheDir, "key_listener_${device.replace('/', '_')}.sh")
                            scriptFile.writeText(script)
                            scriptFile.setExecutable(true)
                            Log.d(TAG, "为设备 $device 创建脚本: ${scriptFile.absolutePath}")

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
     * [v11] 脚本现在只上报 UP/DOWN 事件和持续时间
     */
    private fun createShellScriptForDevice(device: String, workflows: List<Workflow>): String {
        if (workflows.isEmpty()) return ""

        val grepPattern = workflows.mapNotNull { it.triggerConfig?.get("key_code") as? String }.distinct().joinToString("|")

        val logFile = "${applicationContext.cacheDir.absolutePath}/key_listener.log"
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"

        return """
            #!/system/bin/sh
            LOG_FILE="$logFile"
            DEVICE="$device"
            GREP_PATTERN="$grepPattern"
            SERVICE_COMPONENT="$serviceComponent"

            echo "--- SCRIPT START (v11-simplified-reporter) ---" >> "${'$'}LOG_FILE"
            echo "PID: $$" >> "${'$'}LOG_FILE"
            echo "Listening on device: ${'$'}DEVICE with pattern: ${'$'}GREP_PATTERN" >> "${'$'}LOG_FILE"

            while true; do
              echo "Starting getevent process..." >> "${'$'}LOG_FILE"
              getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
                if echo "${'$'}line" | grep -E -q "(${'$'}GREP_PATTERN)"; then
                    TIMESTAMP=`date +%s%3N`
                    EVENT_TYPE=`echo ${'$'}line | awk '{print ${'$'}NF}'`
                    KEY_CODE=`echo ${'$'}line | awk '{print ${'$'}2}'`

                    if [ "${'$'}EVENT_TYPE" = "DOWN" ]; then
                        DOWN_TIMESTAMP=${'$'}TIMESTAMP
                        am start-service -n "${'$'}SERVICE_COMPONENT" \
                            -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} \
                            --es device "${'$'}DEVICE" \
                            --es key_code "${'$'}{KEY_CODE}" \
                            --es event_type "DOWN"
                    elif [ "${'$'}EVENT_TYPE" = "UP" ]; then
                        if [ -z "${'$'}DOWN_TIMESTAMP" ]; then
                            continue
                        fi
                        
                        PRESS_DURATION=$((TIMESTAMP - DOWN_TIMESTAMP))
                        DOWN_TIMESTAMP=""

                        am start-service -n "${'$'}SERVICE_COMPONENT" \
                            -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} \
                            --es device "${'$'}DEVICE" \
                            --es key_code "${'$'}{KEY_CODE}" \
                            --es event_type "UP" \
                            --el duration ${'$'}PRESS_DURATION
                    fi
                fi
              done
              echo "getevent process for ${'$'}DEVICE exited. Restarting in 1s..." >> "${'$'}LOG_FILE"
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