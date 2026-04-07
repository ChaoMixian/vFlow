package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.content.Intent
import android.os.Build
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class KeyEventTriggerHandler : BaseTriggerHandler() {

    private data class ResolvedKeyEventTrigger(
        val trigger: TriggerSpec,
        val devicePath: String,
        val keyCode: Int,
        val actionType: String
    )

    private data class NativeProcessSpec(
        val devicePath: String,
        val keyCode: Int,
        val flags: Set<String>
    )

    companion object {
        private const val TAG = "KeyEventTriggerHandler"
        private const val NATIVE_BIN_DIR = "/data/local/tmp/vflow"
        private const val NATIVE_BIN_NAME = "key_event_trigger_handler"

        const val ACTION_KEY_EVENT_RECEIVED = "com.chaomixian.vflow.KEY_EVENT_RECEIVED"
        const val EXTRA_GESTURE_TYPE = "gesture_type"
        const val EXTRA_KEY_CODE = "key_code"
        const val EXTRA_DEVICE = "device"
    }

    private val registeredTriggers = CopyOnWriteArrayList<TriggerSpec>()
    private val activeTriggers = CopyOnWriteArrayList<ResolvedKeyEventTrigger>()
    private var nativeProcessJob: Job? = null

    override fun start(context: Context) {
        super.start(context)
        DebugLogger.d(TAG, "KeyEventTriggerHandler 已启动。")
    }

    override fun stop(context: Context) {
        nativeProcessJob?.cancel()
        triggerScope.launch {
            cleanupNativeProcesses(context)
        }
        DebugLogger.d(TAG, "KeyEventTriggerHandler 已停止并请求清理原生进程。")
        super.stop(context)
    }

    override fun addTrigger(context: Context, trigger: TriggerSpec) {
        registeredTriggers.removeAll { it.triggerId == trigger.triggerId }
        registeredTriggers.add(trigger)
        DebugLogger.d(TAG, "已添加/更新触发器 '${trigger.triggerId}'。准备重载原生监听进程。")
        reloadKeyEventTriggers(context)
    }

    override fun removeTrigger(context: Context, triggerId: String) {
        val removed = registeredTriggers.removeAll { it.triggerId == triggerId }
        if (removed) {
            DebugLogger.d(TAG, "已移除触发器 '$triggerId'。准备重载原生监听进程。")
            reloadKeyEventTriggers(context)
        }
    }

    fun handleKeyEventIntent(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY_EVENT_RECEIVED) return

        val gestureType = intent.getStringExtra(EXTRA_GESTURE_TYPE) ?: return
        val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
        val device = intent.getStringExtra(EXTRA_DEVICE) ?: return
        if (keyCode == -1) return

        val actionType = gestureTypeToActionType(gestureType) ?: return
        DebugLogger.d(TAG, "收到原生按键事件: device=$device key=$keyCode gesture=$gestureType")
        executeMatchingWorkflows(context, device, keyCode, actionType)
    }

    private fun resolveTrigger(trigger: TriggerSpec): ResolvedKeyEventTrigger? {
        val params = trigger.parameters
        val devicePath = params["device"] as? String ?: return null
        val keyCodeValue = params["key_code"] as? String ?: return null
        val rawActionType = params["action_type"] as? String ?: return null
        val actionType = normalizeActionType(rawActionType) ?: return null
        val keyCode = parseKeyCode(keyCodeValue) ?: return null

        return ResolvedKeyEventTrigger(
            trigger = trigger,
            devicePath = devicePath,
            keyCode = keyCode,
            actionType = actionType
        )
    }

    private fun reloadKeyEventTriggers(context: Context) {
        nativeProcessJob?.cancel()
        DebugLogger.d(TAG, "请求重新加载触发器，正在停止旧的 C++ 进程...")

        nativeProcessJob = triggerScope.launch {
            try {
                cleanupNativeProcesses(context)

                activeTriggers.clear()
                activeTriggers.addAll(registeredTriggers.mapNotNull { resolveTrigger(it) })

                if (activeTriggers.isEmpty()) {
                    DebugLogger.d(TAG, "没有已启用的按键触发器，停止监听。")
                    return@launch
                }

                val binaryFile = deployNativeBinary(context)
                if (binaryFile == null) {
                    DebugLogger.e(TAG, "无法部署原生按键监听器。")
                    return@launch
                }

                val specs = activeTriggers
                    .groupBy { it.devicePath to it.keyCode }
                    .map { (groupKey, triggers) ->
                        NativeProcessSpec(
                            devicePath = groupKey.first,
                            keyCode = groupKey.second,
                            flags = triggers.mapNotNull { actionTypeToNativeFlag(it.actionType) }.toSet()
                        )
                    }

                specs.forEach { spec ->
                    launch {
                        try {
                            startNativeProcess(context, binaryFile.absolutePath, spec)
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                DebugLogger.d(TAG, "设备 ${spec.devicePath} 按键 ${spec.keyCode} 的监听进程被取消。")
                            } else {
                                DebugLogger.e(TAG, "启动设备 ${spec.devicePath} 按键 ${spec.keyCode} 的监听进程时出错。", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    DebugLogger.d(TAG, "原生监听器重载已取消。")
                } else {
                    DebugLogger.e(TAG, "重载原生监听器失败。", e)
                }
            }
        }
    }

    private suspend fun cleanupNativeProcesses(context: Context) {
        runShellCommand(context, "killall $NATIVE_BIN_NAME")
        delay(300)
    }

    private suspend fun deployNativeBinary(context: Context): File? {
        val deviceAbi = detectDeviceAbi()
        val assetPath = "key_event_trigger_handler/$deviceAbi/$NATIVE_BIN_NAME"
        val stagedFile = File(StorageManager.tempDir, "${NATIVE_BIN_NAME}_$deviceAbi")
        val targetDir = File(NATIVE_BIN_DIR)
        val targetFile = File(targetDir, NATIVE_BIN_NAME)

        return try {
            context.assets.open(assetPath).use { input ->
                stagedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            stagedFile.setReadable(true, false)

            val deployResult = ShellManager.deployExecutableViaShell(
                context = context,
                stagedFile = stagedFile,
                targetPath = targetFile.absolutePath
            )

            if (!deployResult.success) {
                DebugLogger.e(TAG, "部署原生监听器失败: ${deployResult.output}")
                null
            } else {
                DebugLogger.d(TAG, "原生监听器已部署: ${deployResult.targetFile.absolutePath}")
                deployResult.targetFile
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "部署原生监听器失败。", e)
            null
        }
    }

    private suspend fun startNativeProcess(
        context: Context,
        binaryPath: String,
        spec: NativeProcessSpec
    ) {
        val args = buildList {
            add("--device")
            add(spec.devicePath)
            add("--key")
            add(spec.keyCode.toString())
            add("--package")
            add(context.packageName)
            spec.flags.sorted().forEach { add(it) }
        }

        val command = buildString {
            append(shellQuote(binaryPath))
            args.forEach { arg ->
                append(' ')
                append(shellQuote(arg))
            }
        }

        DebugLogger.d(TAG, "启动 C++ 监听进程: $command")
        val execResult = runShellCommand(context, "$command </dev/null >/dev/null 2>&1 &")
        DebugLogger.d(TAG, "启动结果: $execResult")
    }

    private fun executeMatchingWorkflows(context: Context, devicePath: String, keyCode: Int, actionType: String) {
        val matchingTriggers = activeTriggers.filter {
            it.devicePath == devicePath && it.keyCode == keyCode && it.actionType == actionType
        }.distinctBy { it.trigger.workflowId }

        if (matchingTriggers.isEmpty()) return

        triggerScope.launch {
            when {
                matchingTriggers.size == 1 -> executeTrigger(context, matchingTriggers.first().trigger)
                else -> handleMultipleMatches(context, matchingTriggers.map { it.trigger })
            }
        }
    }

    private suspend fun handleMultipleMatches(context: Context, triggers: List<TriggerSpec>) {
        val uiService = ExecutionUIService(context)
        val workflows = triggers.map { it.workflow }
        val selectedWorkflowId = uiService.showWorkflowChooser(workflows) ?: return
        triggers.firstOrNull { it.workflowId == selectedWorkflowId }?.let { executeTrigger(context, it) }
    }

    private suspend fun runShellCommand(context: Context, command: String): String {
        return try {
            ShellManager.execShellCommand(context, command, ShellManager.ShellMode.AUTO)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "执行 Shell 命令失败: $command", e)
            "Error: ${e.message}"
        }
    }

    private fun actionTypeToNativeFlag(actionType: String): String? {
        return when (actionType) {
            KeyEventTriggerModule.ACTION_SINGLE_CLICK -> "--click"
            KeyEventTriggerModule.ACTION_DOUBLE_CLICK -> "--double"
            KeyEventTriggerModule.ACTION_LONG_PRESS -> "--hold"
            KeyEventTriggerModule.ACTION_SHORT_PRESS -> "--press"
            KeyEventTriggerModule.ACTION_SWIPE_DOWN,
            KeyEventTriggerModule.ACTION_SWIPE_UP -> "--slider"
            else -> null
        }
    }

    private fun normalizeActionType(actionType: String): String? {
        return when (actionType) {
            KeyEventTriggerModule.ACTION_SINGLE_CLICK,
            "单击",
            "Single Click" -> KeyEventTriggerModule.ACTION_SINGLE_CLICK

            KeyEventTriggerModule.ACTION_DOUBLE_CLICK,
            "双击",
            "Double Click" -> KeyEventTriggerModule.ACTION_DOUBLE_CLICK

            KeyEventTriggerModule.ACTION_LONG_PRESS,
            "长按",
            "Long Press" -> KeyEventTriggerModule.ACTION_LONG_PRESS

            KeyEventTriggerModule.ACTION_SHORT_PRESS,
            "短按 (立即触发)",
            "Short Press (Immediate)" -> KeyEventTriggerModule.ACTION_SHORT_PRESS

            KeyEventTriggerModule.ACTION_SWIPE_DOWN,
            "向下滑动",
            "Swipe Down" -> KeyEventTriggerModule.ACTION_SWIPE_DOWN

            KeyEventTriggerModule.ACTION_SWIPE_UP,
            "向上滑动",
            "Swipe Up" -> KeyEventTriggerModule.ACTION_SWIPE_UP

            else -> {
                DebugLogger.w(TAG, "不支持的按键动作类型: $actionType")
                null
            }
        }
    }

    private fun gestureTypeToActionType(gestureType: String): String? {
        return when (gestureType) {
            "click" -> KeyEventTriggerModule.ACTION_SINGLE_CLICK
            "double_click" -> KeyEventTriggerModule.ACTION_DOUBLE_CLICK
            "long_press" -> KeyEventTriggerModule.ACTION_LONG_PRESS
            "short_press" -> KeyEventTriggerModule.ACTION_SHORT_PRESS
            "swipe_down" -> KeyEventTriggerModule.ACTION_SWIPE_DOWN
            "swipe_up" -> KeyEventTriggerModule.ACTION_SWIPE_UP
            else -> null
        }
    }

    private fun parseKeyCode(value: String?): Int? {
        val normalized = value?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null
        return try {
            when {
                normalized.startsWith("0x") -> normalized.substring(2).toInt(16)
                else -> normalized.toInt()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun detectDeviceAbi(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            primaryAbi.startsWith("arm64") -> "arm64-v8a"
            primaryAbi.startsWith("armeabi-v7a") -> "armeabi-v7a"
            primaryAbi.startsWith("x86_64") -> "x86_64"
            primaryAbi.startsWith("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\\''")}'"
    }
}
