// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/KeyEventTriggerHandler.kt
// 描述: 实现了精细化的工作流管理，解决了编译错误和稳定性问题。

package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ShizukuManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException

class KeyEventTriggerHandler : BaseTriggerHandler() {

    // 定义一个数据类来存储解析后的、规范化的触发器信息
    private data class ResolvedKeyEventTrigger(
        val workflowId: String,
        val devicePath: String,
        val keyCode: String,
        val actionType: String,
        val isSlider: Boolean = false
    )

    private enum class KeyState { IDLE, WAIT_FOR_DOUBLE, PROCESSING }

    // 线程安全的列表，用于存储当前正在监听的工作流
    private val listeningWorkflows = CopyOnWriteArrayList<Workflow>()
    // 存储当前所有已解析并激活的触发器配置
    private val activeTriggers = CopyOnWriteArrayList<ResolvedKeyEventTrigger>()
    private var keyEventJob: Job? = null
    private val keyStates = ConcurrentHashMap<String, KeyState>()
    private val pendingClickJobs = ConcurrentHashMap<String, Job>()
    private val firstClickTimestamps = ConcurrentHashMap<String, Long>()
    private val doubleClickThreshold = 400L
    private val longPressThreshold = 500L
    private val lastSliderModeMap = ConcurrentHashMap<String, Int>()
    private val sliderDebounceJobs = ConcurrentHashMap<String, Job>()
    private val sliderDebounceTime = 200L

    companion object {
        private const val TAG = "KeyEventTriggerHandler"
        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_RINGER_MODE = "ringer_mode"
    }

    override fun start(context: Context) {
        super.start(context)
        DebugLogger.d(TAG, "KeyEventTriggerHandler 已启动。")
    }

    override fun stop(context: Context) {
        super.stop(context)
        keyEventJob?.cancel()
        triggerScope.launch {
            DebugLogger.d(TAG, "KeyEventTriggerHandler 正在清理资源。")
            ShizukuManager.execShellCommand(context, "killall getevent")
            context.cacheDir.listFiles { _, name -> name.startsWith("vflow_listener_") && name.endsWith(".pid") }?.forEach { it.delete() }
        }
        DebugLogger.d(TAG, "KeyEventTriggerHandler 已停止并清理了资源。")
    }

    fun handleKeyEventIntent(context: Context, intent: Intent) {
        if (intent.action == ACTION_KEY_EVENT_RECEIVED) {
            if (intent.hasExtra(EXTRA_RINGER_MODE)) {
                handleSliderEvent(context, intent)
            } else {
                handleKeyEvent(context, intent)
            }
        }
    }

    override fun addWorkflow(context: Context, workflow: Workflow) {
        listeningWorkflows.removeAll { it.id == workflow.id }
        listeningWorkflows.add(workflow)
        DebugLogger.d(TAG, "已添加/更新 '${workflow.name}'。准备重新加载按键监听。")
        reloadKeyEventTriggers(context)
    }

    override fun removeWorkflow(context: Context, workflowId: String) {
        val removed = listeningWorkflows.removeAll { it.id == workflowId }
        if (removed) {
            DebugLogger.d(TAG, "已从监听列表移除 workflowId: $workflowId。准备重新加载按键监听。")
            reloadKeyEventTriggers(context)
        }
    }

    /**
     * 新增一个私有辅助函数，用于将工作流的 triggerConfig 解析为标准化的 ResolvedKeyEventTrigger。
     * 这个函数集中处理了预设和手动配置的逻辑，确保了数据的一致性。
     */
    private fun resolveTriggerFromWorkflow(workflow: Workflow): ResolvedKeyEventTrigger? {
        val config = workflow.triggerConfig ?: return null
        val preset = config["device_preset"] as? String
        val actionType = config["action_type"] as? String ?: return null
        var devicePath: String?
        var keyCode: String?
        var isSlider = false

        when (preset) {
            "一加 13T (侧键)" -> {
                devicePath = "/dev/input/event0"
                keyCode = (config["_internal_key_code"] as? String) ?: "BTN_TRIGGER_HAPPY32"
            }
            "一加 13 (三段式)" -> {
                devicePath = "/dev/input/event6"
                keyCode = (config["_internal_key_code"] as? String) ?: "KEY_F3"
                isSlider = true
            }
            else -> { // "手动/自定义"
                devicePath = config["device"] as? String
                keyCode = config["key_code"] as? String
            }
        }

        if (devicePath.isNullOrBlank() || keyCode.isNullOrBlank()) {
            return null
        }

        return ResolvedKeyEventTrigger(workflow.id, devicePath, keyCode, actionType, isSlider)
    }

    private fun reloadKeyEventTriggers(context: Context) {
        keyEventJob?.cancel()
        DebugLogger.d(TAG, "请求重新加载触发器，正在停止所有旧的监听脚本...")

        keyEventJob = triggerScope.launch {
            // 1. 清理旧的监听进程
            try {
                DebugLogger.d(TAG, "KeyEventTriggerHandler 结束进程并清理残留中...")
                ShizukuManager.execShellCommand(context, "killall getevent")
                val pidFiles = context.cacheDir.listFiles { _, name -> name.startsWith("vflow_listener_") && name.endsWith(".pid") }
                pidFiles?.forEach { pidFile ->
                    try {
                        val pid = pidFile.readText().trim()
                        DebugLogger.d(TAG, "KeyEventTriggerHandler 正在结束进程 $pid...")
                        if (pid.isNotEmpty()) ShizukuManager.execShellCommand(context, "kill $pid")
                        pidFile.delete()
                    } catch (e: Exception) {
                        DebugLogger.w(TAG, "清理 PID 文件 ${pidFile.name} 时出错: ", e)
                    }
                }
                delay(500)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "停止旧监听进程时出错: ", e)
            }

            // 2. 使用新的辅助函数来构建 activeTriggers 列表
            activeTriggers.clear()
            val resolvedTriggers = listeningWorkflows.mapNotNull { resolveTriggerFromWorkflow(it) }
            activeTriggers.addAll(resolvedTriggers)

            if (activeTriggers.isEmpty()) {
                DebugLogger.d(TAG, "没有已启用的按键触发器，停止监听。")
                return@launch
            }

            // 3. 根据设备路径对触发器分组，并启动新的监听脚本
            val triggersByDevice = activeTriggers.groupBy { it.devicePath }
            val cacheDirPath = context.cacheDir.absolutePath
            DebugLogger.d(TAG, "KeyEventTriggerHandler 设备路径：$triggersByDevice 缓存文件夹：$cacheDirPath")

            triggersByDevice.forEach { (path, triggers) ->
                val isSlider = triggers.any { it.isSlider }
                val script = if (isSlider) {
                    createSliderScriptForDevice(path, cacheDirPath, context.packageName)
                } else {
                    createShellScriptForDevice(path, triggers.map { it.keyCode }.distinct(), cacheDirPath, context.packageName)
                }

                if (script.isNotBlank()) {
                    launch {
                        try {
                            val scriptFile = File(context.cacheDir, "key_listener_${path.replace('/', '_')}.sh")
                            DebugLogger.d(TAG, "KeyEventTriggerHandler 准备创建监听脚本：${scriptFile.absolutePath} ...")
                            scriptFile.writeText(script)
                            scriptFile.setExecutable(true)
                            DebugLogger.d(TAG, "正在为设备 $path 启动新的监听脚本...")
                            ShizukuManager.execShellCommand(context, "sh ${scriptFile.absolutePath}")
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                DebugLogger.e(TAG, "设备 $path 监听脚本的执行被取消。", e)
                            } else {
                                DebugLogger.e(TAG, "执行设备 $path 的监听脚本时出错。", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleKeyEvent(context: Context, intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val keyCode = intent.getStringExtra("key_code") ?: return
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return
        val pressDuration = intent.getLongExtra("duration", 0)
        val uniqueKey = "$device-$keyCode"

        if (eventType == "UP") {
            if (pressDuration >= longPressThreshold) {
                resetKeyState(uniqueKey)
                executeMatchingWorkflows(context, device, keyCode, "长按")
                return
            }

            val currentState = keyStates[uniqueKey] ?: KeyState.IDLE
            val now = System.currentTimeMillis()

            when (currentState) {
                KeyState.IDLE -> {
                    keyStates[uniqueKey] = KeyState.WAIT_FOR_DOUBLE
                    firstClickTimestamps[uniqueKey] = now
                    if (hasActionType(device, keyCode, "短按 (立即触发)")) {
                        executeMatchingWorkflows(context, device, keyCode, "短按 (立即触发)")
                    }
                    pendingClickJobs[uniqueKey] = triggerScope.launch {
                        delay(doubleClickThreshold)
                        if (isActive && keyStates[uniqueKey] == KeyState.WAIT_FOR_DOUBLE) {
                            executeMatchingWorkflows(context, device, keyCode, "单击")
                            resetKeyState(uniqueKey)
                        }
                    }
                }
                KeyState.WAIT_FOR_DOUBLE -> {
                    val firstClickTime = firstClickTimestamps[uniqueKey] ?: 0
                    if (now - firstClickTime <= doubleClickThreshold) {
                        keyStates[uniqueKey] = KeyState.PROCESSING
                        pendingClickJobs[uniqueKey]?.cancel()
                        executeMatchingWorkflows(context, device, keyCode, "双击")
                        resetKeyState(uniqueKey)
                    } else {
                        pendingClickJobs[uniqueKey]?.cancel()
                        keyStates[uniqueKey] = KeyState.WAIT_FOR_DOUBLE
                        firstClickTimestamps[uniqueKey] = now
                        if (hasActionType(device, keyCode, "短按 (立即触发)")) {
                            executeMatchingWorkflows(context, device, keyCode, "短按 (立即触发)")
                        }
                        pendingClickJobs[uniqueKey] = triggerScope.launch {
                            delay(doubleClickThreshold)
                            if (isActive && keyStates[uniqueKey] == KeyState.WAIT_FOR_DOUBLE) {
                                executeMatchingWorkflows(context, device, keyCode, "单击")
                                resetKeyState(uniqueKey)
                            }
                        }
                    }
                }
                KeyState.PROCESSING -> {}
            }
        }
    }

    /**
     * hasActionType 现在直接查询已解析的 activeTriggers 列表，避免了空指针异常。
     */
    private fun hasActionType(devicePath: String, keyCode: String, actionType: String): Boolean {
        return activeTriggers.any {
            it.devicePath == devicePath && it.keyCode == keyCode && it.actionType == actionType
        }
    }

    private fun resetKeyState(uniqueKey: String) {
        keyStates.remove(uniqueKey)
        firstClickTimestamps.remove(uniqueKey)
        pendingClickJobs[uniqueKey]?.cancel()
        pendingClickJobs.remove(uniqueKey)
    }

    private fun handleSliderEvent(context: Context, intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val ringerModeStr = intent.getStringExtra(EXTRA_RINGER_MODE) ?: return
        val newRingerMode = ringerModeStr.toIntOrNull() ?: return

        sliderDebounceJobs[device]?.cancel()
        sliderDebounceJobs[device] = triggerScope.launch {
            delay(sliderDebounceTime)
            val lastRingerMode = lastSliderModeMap[device]
            if (lastRingerMode != null && lastRingerMode != newRingerMode) {
                when {
                    newRingerMode > lastRingerMode -> executeMatchingWorkflows(context, device, "KEY_F3", "向上滑动")
                    newRingerMode < lastRingerMode -> executeMatchingWorkflows(context, device, "KEY_F3", "向下滑动")
                }
            }
            lastSliderModeMap[device] = newRingerMode
        }
    }

    /**
     * executeMatchingWorkflows 现在直接查询已解析的 activeTriggers 列表。
     */
    private fun executeMatchingWorkflows(context: Context, devicePath: String, keyCode: String, actionType: String) {
        val matchingWorkflowIds = activeTriggers.filter {
            it.devicePath == devicePath && it.keyCode == keyCode && it.actionType == actionType
        }.map { it.workflowId }

        if (matchingWorkflowIds.isNotEmpty()) {
            val matchingWorkflows = listeningWorkflows.filter { it.id in matchingWorkflowIds }
            if (matchingWorkflows.isNotEmpty()) {
                triggerScope.launch {
                    when {
                        matchingWorkflows.size == 1 -> WorkflowExecutor.execute(matchingWorkflows.first(), context)
                        else -> handleMultipleMatches(context, matchingWorkflows)
                    }
                }
            }
        }
    }

    private suspend fun handleMultipleMatches(context: Context, workflows: List<Workflow>) {
        val uiService = ExecutionUIService(context)
        val selectedWorkflowId = uiService.showWorkflowChooser(workflows)
        if (selectedWorkflowId != null) {
            workflowManager.getWorkflow(selectedWorkflowId)?.let {
                WorkflowExecutor.execute(it, context)
            }
        }
    }

    private fun createSliderScriptForDevice(device: String, cacheDir: String, packageName: String): String {
        val serviceComponent = "$packageName/com.chaomixian.vflow.services.TriggerService"
        val pidFileName = "vflow_listener_${device.replace('/', '_')}.pid"
        val pidFilePath = "$cacheDir/$pidFileName"

        // 增加日志文件定义
        val logFile = "$cacheDir/vflow_shell.log"
        val markerFile = "$cacheDir/vflow_logging_enabled.marker"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            SERVICE_COMPONENT="$serviceComponent"
            PID_FILE="$pidFilePath"
            LOG_FILE="$logFile"
            MARKER_FILE="$markerFile"

            # 日志函数
            log_msg() {
              if [ -f "${'$'}MARKER_FILE" ]; then
                echo "${'$'}(date +"%Y-%m-%d %H:%M:%S") [Slider/${'$'}DEVICE] ${'$'}1" >> "${'$'}LOG_FILE"
              fi
            }

            if [ -f "${'$'}PID_FILE" ]; then
                EXISTING_PID=`cat "${'$'}PID_FILE"`
                if [ -n "${'$'}EXISTING_PID" ] && ps -p "${'$'}EXISTING_PID" > /dev/null; then
                    log_msg "Listener already running with PID ${'$'}EXISTING_PID"
                    exit 0
                else
                    rm -f "${'$'}PID_FILE"
                fi
            fi

            echo "$$" > "${'$'}PID_FILE"
            log_msg "Listener started with PID $$"
            trap 'rm -f "${'$'}PID_FILE"; log_msg "Listener stopped"; exit' INT TERM EXIT
            
            CURRENT_RINGER_MODE=`settings get system ringer_mode`
            log_msg "Initial ringer mode: ${'$'}CURRENT_RINGER_MODE"
            am start-service -n "${'$'}SERVICE_COMPONENT" -a ${KeyEventTriggerHandler.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es ringer_mode "${'$'}CURRENT_RINGER_MODE"

            getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
              if echo "${'$'}line" | grep -q "KEY_F3.*UP"; then
                  log_msg "Detected slider movement event"
                  sleep 0.1
                  NEW_RINGER_MODE=`settings get system ringer_mode`
                  log_msg "New ringer mode: ${'$'}NEW_RINGER_MODE"
                  am start-service -n "${'$'}SERVICE_COMPONENT" -a ${KeyEventTriggerHandler.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es ringer_mode "${'$'}NEW_RINGER_MODE"
              fi
            done
        """.trimIndent()
    }

    private fun createShellScriptForDevice(device: String, keyCodes: List<String>, cacheDir: String, packageName: String): String {
        if (keyCodes.isEmpty()) return ""
        val grepPattern = keyCodes.joinToString("|")
        val serviceComponent = "$packageName/com.chaomixian.vflow.services.TriggerService"
        val pidFileName = "vflow_listener_${device.replace('/', '_')}.pid"
        val pidFilePath = "$cacheDir/$pidFileName"

        // 增加日志文件定义
        val logFile = "$cacheDir/vflow_shell.log"
        val markerFile = "$cacheDir/vflow_logging_enabled.marker"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            GREP_PATTERN="$grepPattern"
            SERVICE_COMPONENT="$serviceComponent"
            PID_FILE="$pidFilePath"
            LOG_FILE="$logFile"
            MARKER_FILE="$markerFile"
            
            # 日志函数
            log_msg() {
              if [ -f "${'$'}MARKER_FILE" ]; then
                echo "${'$'}(date +"%Y-%m-%d %H:%M:%S") [Key/${'$'}DEVICE] ${'$'}1" >> "${'$'}LOG_FILE"
              fi
            }

            if [ -f "${'$'}PID_FILE" ]; then
                EXISTING_PID=`cat "${'$'}PID_FILE"`
                if [ -n "${'$'}EXISTING_PID" ] && ps -p "${'$'}EXISTING_PID" > /dev/null; then
                    log_msg "Listener already running with PID ${'$'}EXISTING_PID"
                    exit 0
                else
                    rm -f "${'$'}PID_FILE"
                fi
            fi

            echo "$$" > "${'$'}PID_FILE"
            log_msg "Listener started with PID $$ monitoring: ${'$'}GREP_PATTERN"
            trap 'rm -f "${'$'}PID_FILE"; log_msg "Listener stopped"; exit' INT TERM EXIT

            while true; do
              getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
                if echo "${'$'}line" | grep -E -q "(${'$'}GREP_PATTERN)"; then
                    log_msg "Event captured: ${'$'}line"
                    
                    TIMESTAMP=`date +%s%3N`
                    EVENT_TYPE=`echo ${'$'}line | awk '{print ${'$'}NF}'`
                    KEY_CODE=`echo ${'$'}line | awk '{print ${'$'}2}'`

                    if [ "${'$'}EVENT_TYPE" = "DOWN" ]; then
                        DOWN_TIMESTAMP=${'$'}TIMESTAMP
                        log_msg "Processing DOWN for ${'$'}KEY_CODE"
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${KeyEventTriggerHandler.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}KEY_CODE" --es event_type "DOWN"
                    elif [ "${'$'}EVENT_TYPE" = "UP" ]; then
                        if [ -z "${'$'}DOWN_TIMESTAMP" ]; then continue; fi
                        PRESS_DURATION=$((TIMESTAMP - DOWN_TIMESTAMP))
                        DOWN_TIMESTAMP=""
                        log_msg "Processing UP for ${'$'}KEY_CODE (duration: ${'$'}PRESS_DURATION ms)"
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${KeyEventTriggerHandler.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}KEY_CODE" --es event_type "UP" --el duration ${'$'}PRESS_DURATION
                    fi
                fi
              done
              sleep 1
            done
        """.trimIndent()
    }
}