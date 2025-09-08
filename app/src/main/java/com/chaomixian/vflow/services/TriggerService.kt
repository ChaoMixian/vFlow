// 文件: main/java/com/chaomixian/vflow/services/TriggerService.kt
// 描述: 已通过重构核心逻辑，最终修复所有已知的单击/双击冲突问题。
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
import java.util.concurrent.CopyOnWriteArrayList

class TriggerService : Service() {

    private data class ResolvedKeyEventTrigger(
        val workflowId: String,
        val devicePath: String,
        val keyCode: String,
        val actionType: String,
        val isSlider: Boolean = false
    )

    private lateinit var workflowManager: WorkflowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keyEventJob: Job? = null

    private val resolvedTriggers = CopyOnWriteArrayList<ResolvedKeyEventTrigger>()

    // --- 单按键状态管理 ---
    private val singleClickJobs = ConcurrentHashMap<String, Job>()
    private val lastPressTimestampMap = ConcurrentHashMap<String, Long>()
    private val doubleClickThreshold = 400L // 毫秒
    private val longPressThreshold = 500L  // 毫秒

    // --- 三段式滑块状态管理 ---
    private val lastSliderModeMap = ConcurrentHashMap<String, String>()
    private val sliderDebounceJobs = ConcurrentHashMap<String, Job>()
    private val sliderDebounceTime = 200L

    companion object {
        private const val TAG = "TriggerService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "trigger_service_channel"
        const val ACTION_RELOAD_TRIGGERS = "com.chaomixian.vflow.RELOAD_TRIGGERS"
        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_SLIDER_MODE = "slider_mode"
    }

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
                if (intent.hasExtra(EXTRA_SLIDER_MODE)) {
                    handleSliderEvent(intent)
                } else {
                    handleKeyEvent(intent)
                }
            }
            else -> {
                if (intent?.action == ACTION_RELOAD_TRIGGERS) Log.d(TAG, "通过 Intent 请求重新加载触发器。")
                reloadKeyEventTriggers()
            }
        }
        return START_STICKY
    }

    /**
     * [最终修复] 采用全新的状态机逻辑，彻底根除单击/双击的连锁误判问题。
     */
    private fun handleKeyEvent(intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val keyCode = intent.getStringExtra("key_code") ?: return
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return
        val pressDuration = intent.getLongExtra("duration", 0)

        val uniqueKey = "$device-$keyCode"

        if (eventType == "UP") {
            // 1. 长按事件处理 (最高优先级)
            if (pressDuration >= longPressThreshold) {
                Log.d(TAG, "长按事件 for $uniqueKey")
                singleClickJobs[uniqueKey]?.cancel()
                lastPressTimestampMap.remove(uniqueKey)
                executeMatchingWorkflows(device, keyCode, "长按")
                return
            }

            // 2. 短按(立即触发)事件处理
            executeMatchingWorkflows(device, keyCode, "短按 (立即触发)")

            // 3. 决定性的单击/双击互斥逻辑
            val now = System.currentTimeMillis()
            val lastPressTime = lastPressTimestampMap[uniqueKey]

            // 检查这是否是一次双击的第二次点击
            if (lastPressTime != null && (now - lastPressTime <= doubleClickThreshold)) {
                // --- 确认是双击 ---
                Log.d(TAG, "双击事件 for $uniqueKey")
                // 1. 取消之前为第一次点击安排的单击任务
                singleClickJobs[uniqueKey]?.cancel()
                // 2. 立即清除时间戳，将状态机彻底重置为“初始状态”，这是防止连锁反应的关键！
                lastPressTimestampMap.remove(uniqueKey)
                // 3. 执行双击工作流
                executeMatchingWorkflows(device, keyCode, "双击")
                // 4. 任务已完成，必须立即返回，不再将此次点击视为任何其他操作的开始
                return
            }

            // --- 如果不是双击，则这是一次潜在的单击 ---
            // 1. 记录下这次点击的时间，为下一次可能的双击做准备
            lastPressTimestampMap[uniqueKey] = now
            // 2. 安排一个延迟任务。如果这个任务在指定时间内没有被下一次点击（即双击）取消，
            //    那么它就是一个确定的、独立的单击事件。
            singleClickJobs[uniqueKey] = serviceScope.launch {
                delay(doubleClickThreshold)
                if (isActive) {
                    Log.d(TAG, "单击事件 for $uniqueKey")
                    executeMatchingWorkflows(device, keyCode, "单击")
                    // 单击任务执行完成后，同样清除时间戳，保持状态干净
                    lastPressTimestampMap.remove(uniqueKey)
                }
            }
        }
    }

    private fun handleSliderEvent(intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val newMode = intent.getStringExtra(EXTRA_SLIDER_MODE) ?: return

        sliderDebounceJobs[device]?.cancel()
        sliderDebounceJobs[device] = serviceScope.launch {
            delay(sliderDebounceTime)
            val lastMode = lastSliderModeMap[device]
            if (lastMode != null && lastMode != newMode) {
                Log.d(TAG, "三段式滑块事件: $lastMode -> $newMode")
                val lastLevel = getModeLevel(lastMode)
                val newLevel = getModeLevel(newMode)
                if (newLevel > lastLevel) {
                    executeMatchingWorkflows(device, "KEY_F3", "向上滑动")
                } else if (newLevel < lastLevel) {
                    executeMatchingWorkflows(device, "KEY_F3", "向下滑动")
                }
            }
            lastSliderModeMap[device] = newMode
        }
    }

    private fun getModeLevel(mode: String): Int {
        return when (mode) {
            "响铃" -> 2
            "震动" -> 1
            "静音", "勿扰" -> 0
            else -> -1
        }
    }

    private fun executeMatchingWorkflows(devicePath: String, keyCode: String, actionType: String) {
        val matchingWorkflowIds = resolvedTriggers.filter {
            it.devicePath == devicePath && it.keyCode == keyCode && it.actionType == actionType
        }.map { it.workflowId }.toSet()

        if (matchingWorkflowIds.isNotEmpty()) {
            serviceScope.launch {
                Log.i(TAG, "找到 ${matchingWorkflowIds.size} 个匹配 '$actionType' on '$devicePath' 的工作流")
                val workflowsToExecute = workflowManager.findKeyEventTriggerWorkflows().filter {
                    it.id in matchingWorkflowIds
                }

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
            resolvedTriggers.clear()

            for (workflow in keyEventWorkflows) {
                val config = workflow.triggerConfig ?: continue
                val preset = config["device_preset"] as? String
                val actionType = config["action_type"] as? String ?: continue

                var devicePath: String? = null
                var keyCode: String? = null
                var isSlider = false

                when (preset) {
                    "一加 13T (侧键)" -> {
                        devicePath = "/dev/input/event0"
                        keyCode = (config["_internal_key_code"] as? String) ?: "BTN_TRIGGER_HAPPY32"
                        Log.d(TAG, "解析到 '一加 13T' 预设, 使用设备路径: $devicePath")
                    }
                    "一加 13 (三段式)" -> {
                        val deviceName = (config["_internal_device_name"] as? String) ?: "oplus,hall_tri_state_key"
                        devicePath = findDeviceByName(deviceName)
                        keyCode = (config["_internal_key_code"] as? String) ?: "KEY_F3"
                        isSlider = true
                        Log.d(TAG, "解析到 '一加 13' 预设, 查找到设备路径: $devicePath")
                    }
                    else -> { // 手动/自定义
                        devicePath = config["device"] as? String
                        keyCode = config["key_code"] as? String
                    }
                }

                if (!devicePath.isNullOrBlank() && !keyCode.isNullOrBlank()) {
                    resolvedTriggers.add(
                        ResolvedKeyEventTrigger(workflow.id, devicePath, keyCode, actionType, isSlider)
                    )
                } else {
                    Log.w(TAG, "工作流 ${workflow.id} ($preset) 无法解析出有效的设备或按键码，已跳过。")
                }
            }

            if (resolvedTriggers.isEmpty()) {
                Log.d(TAG, "没有已启用的按键触发器，停止监听。")
                return@launch
            }

            val triggersByDevice = resolvedTriggers.groupBy { it.devicePath }
            Log.d(TAG, "找到 ${triggersByDevice.size} 个需要监听的输入设备。")

            triggersByDevice.forEach { (path, triggers) ->
                val isSlider = triggers.any { it.isSlider }
                val script = if (isSlider) {
                    createSliderScriptForDevice(path)
                } else {
                    val keyCodes = triggers.map { it.keyCode }.distinct()
                    createShellScriptForDevice(path, keyCodes)
                }

                if (script.isNotBlank()) {
                    launch {
                        try {
                            val scriptFile = File(cacheDir, "key_listener_${path.replace('/', '_')}.sh")
                            scriptFile.writeText(script)
                            scriptFile.setExecutable(true)
                            Log.d(TAG, "为设备 $path 创建脚本: ${scriptFile.absolutePath}")
                            ShizukuManager.execShellCommand(applicationContext, "sh ${scriptFile.absolutePath}")
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                Log.e(TAG, "执行设备 $path 的监听脚本时出错。", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun findDeviceByName(name: String): String? {
        val command = "getevent -il | grep -B 1 -i '$name' | head -n 1 | awk '{print \$4}'"
        val result = ShizukuManager.execShellCommand(applicationContext, command)
        return if (result.startsWith("/dev/input/event")) result.trim() else null
    }

    private fun createSliderScriptForDevice(device: String): String {
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"
        val getModeCommand = "case \$(settings get global three_keys_mode) in 1) echo 静音;; 2) echo 振动;; 3) echo 响铃;; *) echo 未知;; esac"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            SERVICE_COMPONENT="$serviceComponent"
            
            CURRENT_MODE=`$getModeCommand`
            am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es slider_mode "${'$'}CURRENT_MODE"

            getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
              if echo "${'$'}line" | grep -q "KEY_F3.*UP"; then
                  sleep 0.1
                  NEW_MODE=`$getModeCommand`
                  am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es slider_mode "${'$'}NEW_MODE"
              fi
            done
        """.trimIndent()
    }

    private fun createShellScriptForDevice(device: String, keyCodes: List<String>): String {
        if (keyCodes.isEmpty()) return ""
        val grepPattern = keyCodes.joinToString("|")
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            GREP_PATTERN="$grepPattern"
            SERVICE_COMPONENT="$serviceComponent"

            while true; do
              getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
                if echo "${'$'}line" | grep -E -q "(${'$'}GREP_PATTERN)"; then
                    TIMESTAMP=`date +%s%3N`
                    EVENT_TYPE=`echo ${'$'}line | awk '{print ${'$'}NF}'`
                    KEY_CODE=`echo ${'$'}line | awk '{print ${'$'}2}'`

                    if [ "${'$'}EVENT_TYPE" = "DOWN" ]; then
                        DOWN_TIMESTAMP=${'$'}TIMESTAMP
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}{KEY_CODE}" --es event_type "DOWN"
                    elif [ "${'$'}EVENT_TYPE" = "UP" ]; then
                        if [ -z "${'$'}DOWN_TIMESTAMP" ]; then continue; fi
                        PRESS_DURATION=$((TIMESTAMP - DOWN_TIMESTAMP))
                        DOWN_TIMESTAMP=""
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}{KEY_CODE}" --es event_type "UP" --el duration ${'$'}PRESS_DURATION
                    fi
                fi
              done
              sleep 1
            done
        """.trimIndent()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台触发器服务", NotificationManager.IMPORTANCE_LOW)
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

    override fun onBind(intent: Intent?): IBinder? = null
}