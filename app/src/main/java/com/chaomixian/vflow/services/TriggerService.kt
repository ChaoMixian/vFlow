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

    // 按键状态枚举
    private enum class KeyState {
        IDLE,           // 空闲状态
        WAIT_FOR_DOUBLE, // 等待双击状态
        PROCESSING      // 处理中状态
    }

    private lateinit var workflowManager: WorkflowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keyEventJob: Job? = null

    private val resolvedTriggers = CopyOnWriteArrayList<ResolvedKeyEventTrigger>()

    // --- 重新设计的按键状态管理 ---
    private val keyStates = ConcurrentHashMap<String, KeyState>()
    private val pendingClickJobs = ConcurrentHashMap<String, Job>()
    private val firstClickTimestamps = ConcurrentHashMap<String, Long>()
    private val doubleClickThreshold = 400L // 毫秒
    private val longPressThreshold = 500L  // 毫秒

    // --- 三段式滑块状态管理 ---
    private val lastSliderModeMap = ConcurrentHashMap<String, Int>() // 改为存储Int类型的ringer_mode值
    private val sliderDebounceJobs = ConcurrentHashMap<String, Job>()
    private val sliderDebounceTime = 200L // 防抖时间

    companion object {
        private const val TAG = "TriggerService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "trigger_service_channel"
        const val ACTION_RELOAD_TRIGGERS = "com.chaomixian.vflow.RELOAD_TRIGGERS"
        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_RINGER_MODE = "ringer_mode" // 改为ringer_mode
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
                if (intent.hasExtra(EXTRA_RINGER_MODE)) {
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
     * 重新设计的按键事件处理逻辑，使用清晰的状态机模式
     */
    private fun handleKeyEvent(intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val keyCode = intent.getStringExtra("key_code") ?: return
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return
        val pressDuration = intent.getLongExtra("duration", 0)

        val uniqueKey = "$device-$keyCode"

        if (eventType == "UP") {
            // 1. 首先检查是否为长按事件
            if (pressDuration >= longPressThreshold) {
                Log.d(TAG, "长按事件 for $uniqueKey (${pressDuration}ms)")
                resetKeyState(uniqueKey)
                executeMatchingWorkflows(device, keyCode, "长按")
                return
            }

            // 2. 处理短按事件的状态机逻辑
            val currentState = keyStates[uniqueKey] ?: KeyState.IDLE
            val now = System.currentTimeMillis()

            when (currentState) {
                KeyState.IDLE -> {
                    // 第一次点击
                    Log.d(TAG, "第一次点击 for $uniqueKey")
                    keyStates[uniqueKey] = KeyState.WAIT_FOR_DOUBLE
                    firstClickTimestamps[uniqueKey] = now

                    // 检查是否需要立即触发短按
                    if (hasActionType(device, keyCode, "短按 (立即触发)")) {
                        executeMatchingWorkflows(device, keyCode, "短按 (立即触发)")
                    }

                    // 启动延迟任务，等待可能的第二次点击
                    pendingClickJobs[uniqueKey] = serviceScope.launch {
                        delay(doubleClickThreshold)
                        if (isActive && keyStates[uniqueKey] == KeyState.WAIT_FOR_DOUBLE) {
                            Log.d(TAG, "确认单击事件 for $uniqueKey")
                            executeMatchingWorkflows(device, keyCode, "单击")
                            resetKeyState(uniqueKey)
                        }
                    }
                }

                KeyState.WAIT_FOR_DOUBLE -> {
                    // 第二次点击，检查是否在双击时间窗口内
                    val firstClickTime = firstClickTimestamps[uniqueKey] ?: 0
                    if (now - firstClickTime <= doubleClickThreshold) {
                        Log.d(TAG, "确认双击事件 for $uniqueKey")
                        keyStates[uniqueKey] = KeyState.PROCESSING

                        // 取消单击任务
                        pendingClickJobs[uniqueKey]?.cancel()

                        // 执行双击工作流
                        executeMatchingWorkflows(device, keyCode, "双击")

                        // 重置状态
                        resetKeyState(uniqueKey)
                    } else {
                        // 超出双击时间窗口，当作新的第一次点击处理
                        Log.d(TAG, "双击超时，当作新的第一次点击 for $uniqueKey")
                        pendingClickJobs[uniqueKey]?.cancel()
                        keyStates[uniqueKey] = KeyState.WAIT_FOR_DOUBLE
                        firstClickTimestamps[uniqueKey] = now

                        if (hasActionType(device, keyCode, "短按 (立即触发)")) {
                            executeMatchingWorkflows(device, keyCode, "短按 (立即触发)")
                        }

                        pendingClickJobs[uniqueKey] = serviceScope.launch {
                            delay(doubleClickThreshold)
                            if (isActive && keyStates[uniqueKey] == KeyState.WAIT_FOR_DOUBLE) {
                                Log.d(TAG, "确认单击事件 for $uniqueKey")
                                executeMatchingWorkflows(device, keyCode, "单击")
                                resetKeyState(uniqueKey)
                            }
                        }
                    }
                }

                KeyState.PROCESSING -> {
                    // 正在处理中，忽略额外的点击
                    Log.d(TAG, "正在处理中，忽略点击 for $uniqueKey")
                }
            }
        }
    }

    /**
     * 检查是否有指定动作类型的触发器
     */
    private fun hasActionType(devicePath: String, keyCode: String, actionType: String): Boolean {
        return resolvedTriggers.any {
            it.devicePath == devicePath && it.keyCode == keyCode && it.actionType == actionType
        }
    }

    /**
     * 重置按键状态
     */
    private fun resetKeyState(uniqueKey: String) {
        keyStates.remove(uniqueKey)
        firstClickTimestamps.remove(uniqueKey)
        pendingClickJobs[uniqueKey]?.cancel()
        pendingClickJobs.remove(uniqueKey)
    }

    /**
     * 处理三段式滑块事件
     * 使用ringer_mode值：0=静音，1=震动，2=响铃
     */
    private fun handleSliderEvent(intent: Intent) {
        val device = intent.getStringExtra("device") ?: return
        val ringerModeStr = intent.getStringExtra(EXTRA_RINGER_MODE) ?: return

        // 将ringer_mode字符串转换为整数
        val newRingerMode = ringerModeStr.toIntOrNull() ?: return

        // 使用防抖机制，避免快速切换时的重复触发
        sliderDebounceJobs[device]?.cancel()
        sliderDebounceJobs[device] = serviceScope.launch {
            delay(sliderDebounceTime)

            val lastRingerMode = lastSliderModeMap[device]

            if (lastRingerMode != null && lastRingerMode != newRingerMode) {
                Log.d(TAG, "三段式滑块事件: ${getRingerModeDescription(lastRingerMode)} -> ${getRingerModeDescription(newRingerMode)}")

                // 判断滑动方向：根据ringer_mode的数值大小来判断
                when {
                    newRingerMode > lastRingerMode -> {
                        // 数值增大，表示向上滑动（静音 -> 震动 -> 响铃）
                        Log.d(TAG, "检测到向上滑动: $lastRingerMode -> $newRingerMode")
                        executeMatchingWorkflows(device, "KEY_F3", "向上滑动")
                    }
                    newRingerMode < lastRingerMode -> {
                        // 数值减小，表示向下滑动（响铃 -> 震动 -> 静音）
                        Log.d(TAG, "检测到向下滑动: $lastRingerMode -> $newRingerMode")
                        executeMatchingWorkflows(device, "KEY_F3", "向下滑动")
                    }
                    else -> {
                        Log.d(TAG, "ringer_mode未发生变化，忽略此次事件")
                    }
                }
            } else if (lastRingerMode == null) {
                // 首次获取到ringer_mode值，只是记录下来，不触发任何动作
                Log.d(TAG, "首次记录三段式滑块状态: ${getRingerModeDescription(newRingerMode)}")
            }

            // 更新最后记录的ringer_mode值
            lastSliderModeMap[device] = newRingerMode
        }
    }

    /**
     * 获取ringer_mode的描述文字
     * @param ringerMode ringer_mode值（0=静音，1=震动，2=响铃）
     * @return 对应的中文描述
     */
    private fun getRingerModeDescription(ringerMode: Int): String {
        return when (ringerMode) {
            0 -> "静音"
            1 -> "震动"
            2 -> "响铃"
            else -> "未知($ringerMode)"
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
        Log.d(TAG, "请求重新加载触发器，正在停止所有旧的监听脚本...")

        keyEventJob = serviceScope.launch {
            // --- 新增的、更可靠的停止和清理逻辑 ---
            try {
                // 1. 扫描所有可能的 PID 文件
                val pidFiles = cacheDir.listFiles { _, name -> name.startsWith("vflow_listener_") && name.endsWith(".pid") }

                if (pidFiles != null && pidFiles.isNotEmpty()) {
                    Log.d(TAG, "找到 ${pidFiles.size} 个旧的 PID 文件，正在停止相关脚本进程...")
                    pidFiles.forEach { pidFile ->
                        try {
                            val pid = pidFile.readText().trim()
                            if (pid.isNotEmpty()) {
                                Log.d(TAG, "正在停止 PID: $pid (来自 ${pidFile.name})")
                                // 2. 使用 kill 命令终止脚本进程，这将触发脚本内的 trap 来删除 pid 文件
                                ShizukuManager.execShellCommand(applicationContext, "kill $pid")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "处理 PID 文件 ${pidFile.name} 时出错: ", e)
                        }
                    }
                    // 3. 等待一小段时间让 trap 命令执行清理
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "扫描或停止旧监听进程时出错: ", e)
            }

            // 4. 作为最终保险措施，强制清理所有残留的 PID 文件
            try {
                cacheDir.listFiles { _, name -> name.startsWith("vflow_listener_") && name.endsWith(".pid") }?.forEach { it.delete() }
                Log.d(TAG, "已强制清理所有残留的 PID 文件。")
            } catch (e: Exception) {
                Log.e(TAG, "强制清理 PID 文件时出错: ", e)
            }
            // 同样，保留 killall 作为备用方案
            ShizukuManager.execShellCommand(applicationContext, "killall getevent")
            // --- 清理逻辑结束 ---

            // 在启动新脚本前稍作等待
            delay(200)

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
                        devicePath = "/dev/input/event6"
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

            val cacheDirPath = cacheDir.absolutePath

            triggersByDevice.forEach { (path, triggers) ->
                val isSlider = triggers.any { it.isSlider }
                val script = if (isSlider) {
                    createSliderScriptForDevice(path, cacheDirPath)
                } else {
                    val keyCodes = triggers.map { it.keyCode }.distinct()
                    createShellScriptForDevice(path, keyCodes, cacheDirPath)
                }

                if (script.isNotBlank()) {
                    launch {
                        try {
                            val scriptFile = File(cacheDir, "key_listener_${path.replace('/', '_')}.sh")
                            scriptFile.writeText(script)
                            scriptFile.setExecutable(true)
                            Log.d(TAG, "为设备 $path 创建并启动新脚本: ${scriptFile.absolutePath}")
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

    /**
     * 为三段式滑块设备创建监听脚本
     * 使用 settings get system ringer_mode 命令获取当前状态
     */
    private fun createSliderScriptForDevice(device: String, cacheDir: String): String {
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"
        val pidFileName = "vflow_listener_${device.replace('/', '_')}.pid"
        val pidFilePath = "$cacheDir/$pidFileName"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            SERVICE_COMPONENT="$serviceComponent"
            PID_FILE="$pidFilePath"

            # --- PID 机制开始 ---
            # 检查是否已有监听器在运行，避免重复启动
            if [ -f "${'$'}PID_FILE" ]; then
                EXISTING_PID=`cat "${'$'}PID_FILE"`
                if [ -n "${'$'}EXISTING_PID" ] && ps -p "${'$'}EXISTING_PID" > /dev/null; then
                    echo "监听器 (${'$'}DEVICE) 已在运行 (PID: ${'$'}EXISTING_PID)，新脚本退出。"
                    exit 0
                else
                    echo "发现无效的 PID 文件，已删除。"
                    rm -f "${'$'}PID_FILE"
                fi
            fi

            # 记录当前进程PID并设置退出时的清理动作
            echo "$$" > "${'$'}PID_FILE"
            trap 'rm -f "${'$'}PID_FILE"; exit' INT TERM EXIT
            # --- PID 机制结束 ---
            
            # 获取当前ringer_mode状态并发送初始状态给Service
            CURRENT_RINGER_MODE=`settings get system ringer_mode`
            echo "初始ringer_mode状态: ${'$'}CURRENT_RINGER_MODE"
            am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es ringer_mode "${'$'}CURRENT_RINGER_MODE"

            # 监听设备事件，当检测到KEY_F3的UP事件时检查ringer_mode变化
            getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
              if echo "${'$'}line" | grep -q "KEY_F3.*UP"; then
                  # 稍等片刻让系统更新ringer_mode设置
                  sleep 0.1
                  # 获取新的ringer_mode状态
                  NEW_RINGER_MODE=`settings get system ringer_mode`
                  echo "检测到KEY_F3 UP事件，新ringer_mode状态: ${'$'}NEW_RINGER_MODE"
                  # 将新状态发送给Service进行处理
                  am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es ringer_mode "${'$'}NEW_RINGER_MODE"
              fi
            done
        """.trimIndent()
    }

    /**
     * 为普通按键设备创建监听脚本
     */
    private fun createShellScriptForDevice(device: String, keyCodes: List<String>, cacheDir: String): String {
        if (keyCodes.isEmpty()) return ""
        val grepPattern = keyCodes.joinToString("|")
        val serviceComponent = "${applicationContext.packageName}/.services.TriggerService"
        val pidFileName = "vflow_listener_${device.replace('/', '_')}.pid"
        val pidFilePath = "$cacheDir/$pidFileName"

        return """
            #!/system/bin/sh
            DEVICE="$device"
            GREP_PATTERN="$grepPattern"
            SERVICE_COMPONENT="$serviceComponent"
            PID_FILE="$pidFilePath"

            # --- PID 机制开始 ---
            # 检查是否已有监听器在运行，避免重复启动
            if [ -f "${'$'}PID_FILE" ]; then
                EXISTING_PID=`cat "${'$'}PID_FILE"`
                if [ -n "${'$'}EXISTING_PID" ] && ps -p "${'$'}EXISTING_PID" > /dev/null; then
                    echo "监听器 (${'$'}DEVICE) 已在运行 (PID: ${'$'}EXISTING_PID)，新脚本退出。"
                    exit 0
                else
                    echo "发现无效的 PID 文件，已删除。"
                    rm -f "${'$'}PID_FILE"
                fi
            fi

            # 记录当前进程PID并设置退出时的清理动作
            echo "$$" > "${'$'}PID_FILE"
            trap 'rm -f "${'$'}PID_FILE"; exit' INT TERM EXIT
            # --- PID 机制结束 ---

            # 持续监听按键事件
            while true; do
              getevent -l "${'$'}DEVICE" | while IFS= read -r line; do
                # 检查是否匹配目标按键码
                if echo "${'$'}line" | grep -E -q "(${'$'}GREP_PATTERN)"; then
                    TIMESTAMP=`date +%s%3N`  # 获取毫秒级时间戳
                    EVENT_TYPE=`echo ${'$'}line | awk '{print ${'$'}NF}'`  # 获取事件类型(DOWN/UP)
                    KEY_CODE=`echo ${'$'}line | awk '{print ${'$'}2}'`     # 获取按键码

                    if [ "${'$'}EVENT_TYPE" = "DOWN" ]; then
                        # 记录按下时的时间戳
                        DOWN_TIMESTAMP=${'$'}TIMESTAMP
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}KEY_CODE" --es event_type "DOWN"
                    elif [ "${'$'}EVENT_TYPE" = "UP" ]; then
                        # 计算按压持续时间
                        if [ -z "${'$'}DOWN_TIMESTAMP" ]; then continue; fi
                        PRESS_DURATION=$((TIMESTAMP - DOWN_TIMESTAMP))
                        DOWN_TIMESTAMP=""
                        am start-service -n "${'$'}SERVICE_COMPONENT" -a ${TriggerService.ACTION_KEY_EVENT_RECEIVED} --es device "${'$'}DEVICE" --es key_code "${'$'}KEY_CODE" --es event_type "UP" --el duration ${'$'}PRESS_DURATION
                    fi
                fi
              done
              # 如果getevent意外退出，等待1秒后重新启动
              sleep 1
            done
        """.trimIndent()
    }

    /**
     * 创建前台服务通知
     */
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

        // 清理所有状态
        keyStates.clear()
        firstClickTimestamps.clear()
        pendingClickJobs.values.forEach { it.cancel() }
        pendingClickJobs.clear()
        sliderDebounceJobs.values.forEach { it.cancel() }
        sliderDebounceJobs.clear()

        Log.d(TAG, "TriggerService 已销毁。")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}