// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/ScreenOperationModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred
import java.util.regex.Pattern

/**
 * "屏幕操作" 模块。
 */
class ScreenOperationModule : BaseModule() {

    companion object {
        private const val TAG = "ScreenOperationModule"
        private const val OP_TAP = "tap"
        private const val OP_LONG_PRESS = "long_press"
        private const val OP_SWIPE = "swipe"
        private const val MODE_AUTO = "auto"
        private const val MODE_ACCESSIBILITY = "accessibility"
        private const val MODE_SHELL = "shell"
    }

    override val id = "vflow.interaction.screen_operation"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_interaction_screen_operation_name,
        descriptionStringRes = R.string.module_vflow_interaction_screen_operation_desc,
        name = "屏幕操作",
        description = "在屏幕上执行点击、长按或滑动操作。",
        iconRes = R.drawable.rounded_ads_click_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = ScreenOperationModuleUIProvider()

    val executionModeOptions = listOf(MODE_AUTO, MODE_ACCESSIBILITY, MODE_SHELL)

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("execution_mode") as? String ?: MODE_AUTO
        return when (mode) {
            MODE_ACCESSIBILITY -> listOf(PermissionManager.ACCESSIBILITY)
            MODE_SHELL -> ShellManager.getRequiredPermissions(LogManager.applicationContext)
            // 自动模式下，如果无障碍不可用，可能会用到 Shell，所以都带上
            else -> listOf(PermissionManager.ACCESSIBILITY) + ShellManager.getRequiredPermissions(LogManager.applicationContext)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "operation_type",
            "类型",
            ParameterType.ENUM,
            OP_TAP,
            options = listOf(OP_TAP, OP_LONG_PRESS, OP_SWIPE),
            optionsStringRes = listOf(R.string.screen_op_tap, R.string.screen_op_long_press, R.string.screen_op_swipe),
            legacyValueMap = mapOf(
                "点击" to OP_TAP,
                "Tap" to OP_TAP,
                "长按" to OP_LONG_PRESS,
                "Long Press" to OP_LONG_PRESS,
                "滑动" to OP_SWIPE,
                "Swipe" to OP_SWIPE
            ),
            nameStringRes = R.string.screen_op_type_label,
            acceptsMagicVariable = false
        ),
        InputDefinition("target", "目标/起点", ParameterType.STRING, "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.SCREEN_ELEMENT.id, VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id)),
        InputDefinition("target_end", "滑动终点", ParameterType.STRING, "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.SCREEN_ELEMENT.id, VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id), isHidden = false),
        InputDefinition("duration", "持续时间(ms)", ParameterType.NUMBER, 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), isHidden = false, nameStringRes = R.string.screen_op_duration_label),
        InputDefinition(
            "execution_mode",
            "执行方式",
            ParameterType.ENUM,
            MODE_AUTO,
            options = executionModeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_interaction_screen_operation_mode_auto,
                R.string.option_vflow_interaction_screen_operation_mode_accessibility,
                R.string.option_vflow_interaction_screen_operation_mode_shell
            ),
            legacyValueMap = mapOf(
                "自动" to MODE_AUTO,
                "Auto" to MODE_AUTO,
                "无障碍" to MODE_ACCESSIBILITY,
                "Accessibility" to MODE_ACCESSIBILITY,
                "Shell" to MODE_SHELL
            ),
            nameStringRes = R.string.screen_op_execution_mode,
            acceptsMagicVariable = false,
            isHidden = false
        ),
        InputDefinition("show_advanced", "显示高级选项", ParameterType.BOOLEAN, false, acceptsMagicVariable = false, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val type = step.parameters["operation_type"] as? String ?: OP_TAP
        val targetPill = PillUtil.createPillFromParam(step.parameters["target"], inputs.find { it.id == "target" })

        return when (type) {
            OP_SWIPE -> {
                val endPill = PillUtil.createPillFromParam(step.parameters["target_end"], inputs.find { it.id == "target_end" })
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_swipe_from), targetPill, context.getString(R.string.summary_vflow_device_swipe_to), endPill)
            }
            OP_LONG_PRESS -> {
                val duration = step.parameters["duration"]
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_long_press), targetPill, " ($duration ms)")
            }
            else -> { // 点击
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_click_prefix), targetPill)
            }
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val opType = context.getVariableAsString("operation_type", OP_TAP)
        val mode = context.getVariableAsString("execution_mode", MODE_AUTO)

        val durationVal = context.getVariable("duration")
        val duration = (durationVal as? Number)?.toLong() ?:
        (durationVal as? String)?.toLongOrNull() ?:
        (if (opType == OP_LONG_PRESS) 1000L else if (opType == OP_SWIPE) 500L else 50L)

        // 1. 解析坐标
        val targetObj = context.getVariable("target")
        val startPoint = resolveTargetToPoint(context, targetObj)
            ?: return ExecutionResult.Failure("无效目标", "无法解析起点位置: $targetObj")

        var endPoint: Point? = null
        if (opType == OP_SWIPE) {
            val endObj = context.getVariable("target_end")
            endPoint = resolveTargetToPoint(context, endObj)
                ?: return ExecutionResult.Failure("无效目标", "无法解析终点位置: $endObj")
        }

        onProgress(ProgressUpdate("执行 $opType ($mode)..."))

        val accService = ServiceStateBus.getAccessibilityService()
        var success = false

        // 1. 无障碍模式
        if (mode == MODE_ACCESSIBILITY || (mode == MODE_AUTO && accService != null)) {
            if (accService != null) {
                success = when (opType) {
                    OP_TAP -> performGesture(accService, createClickPath(startPoint), 50L)
                    OP_LONG_PRESS -> performGesture(accService, createClickPath(startPoint), duration.coerceAtLeast(500L))
                    OP_SWIPE -> performGesture(accService, createSwipePath(startPoint, endPoint!!), duration)
                    else -> false
                }
                if (success) DebugLogger.d("ScreenOp", "无障碍执行成功")
            } else if (mode == MODE_ACCESSIBILITY) {
                return ExecutionResult.Failure("服务未连接", "指定使用无障碍服务，但服务未运行。")
            }
        }

        // 2. Shell 模式 (自动回落或强制指定)
        if (!success && (mode == MODE_SHELL || mode == MODE_AUTO)) {
            val cmd = when (opType) {
                OP_TAP -> "input tap ${startPoint.x} ${startPoint.y}"
                OP_LONG_PRESS -> "input swipe ${startPoint.x} ${startPoint.y} ${startPoint.x} ${startPoint.y} $duration"
                OP_SWIPE -> "input swipe ${startPoint.x} ${startPoint.y} ${endPoint!!.x} ${endPoint.y} $duration"
                else -> ""
            }
            if (cmd.isNotEmpty()) {
                val result = ShellManager.execShellCommand(context.applicationContext, cmd, ShellManager.ShellMode.AUTO)
                success = !result.startsWith("Error")
                if (success) DebugLogger.d("ScreenOp", "Shell 执行成功")
            } else if (mode == MODE_SHELL) {
                // 如果命令生成失败但又指定了 Shell
                return ExecutionResult.Failure("内部错误", "无法生成 Shell 命令")
            }
        }

        return if (success) {
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "操作无法执行，请检查相关权限或服务状态。")
        }
    }

    private fun resolveTargetToPoint(context: ExecutionContext, target: Any?): Point? {
        return when (target) {
            is VCoordinate -> {
                Point(target.x, target.y)
            }
            is VString -> {
                val str = target.asString()
                // 先解析字符串中的变量引用
                val resolvedStr = com.chaomixian.vflow.core.execution.VariableResolver.resolve(str, context)
                parseStringToPoint(resolvedStr)
            }
            is String -> {
                // 先解析字符串中的变量引用
                val resolvedStr = com.chaomixian.vflow.core.execution.VariableResolver.resolve(target, context)
                parseStringToPoint(resolvedStr)
            }
            else -> null
        }
    }

    private fun parseStringToPoint(target: String): Point? {
        // 检查是否为坐标格式 "x,y"（包含逗号但不包含方括号，避免与矩形格式混淆）
        if (target.contains(",") && !target.contains("[")) {
            val parts = target.split(",")
            // 如果是坐标格式但解析失败，直接返回 null（不再尝试其他方式）
            if (parts.size == 2) {
                val x = parts[0].trim().toIntOrNull()
                val y = parts[1].trim().toIntOrNull()
                if (x != null && y != null) {
                    return Point(x, y)
                } else {
                    // 坐标格式错误（如 "200," 或 ",300" 或 "abc,def"）
                    DebugLogger.w(TAG, "坐标格式错误: '$target'")
                    return null
                }
            } else if (parts.size > 2) {
                // 有多个逗号，可能不是坐标格式
                DebugLogger.w(TAG, "疑似坐标格式但部分过多: '$target'")
                // 不立即返回 null，继续尝试其他解析方式
            }
        }
        val rectMatcher = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").matcher(target)
        if (rectMatcher.find()) {
            val left = rectMatcher.group(1)?.toIntOrNull() ?: 0
            val top = rectMatcher.group(2)?.toIntOrNull() ?: 0
            val right = rectMatcher.group(3)?.toIntOrNull() ?: 0
            val bottom = rectMatcher.group(4)?.toIntOrNull() ?: 0
            return Point((left + right) / 2, (top + bottom) / 2)
        }

        val accService = ServiceStateBus.getAccessibilityService()
        if (accService != null) {
            val root = accService.rootInActiveWindow
            if (root != null) {
                val nodes = root.findAccessibilityNodeInfosByViewId(target)
                val node = nodes?.firstOrNull()
                if (node != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    return Point(rect.centerX(), rect.centerY())
                }
            }
        }
        return null
    }

    private fun createClickPath(p: Point): Path {
        return Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }
    }

    private fun createSwipePath(start: Point, end: Point): Path {
        return Path().apply {
            moveTo(start.x.toFloat(), start.y.toFloat())
            lineTo(end.x.toFloat(), end.y.toFloat())
        }
    }

    private suspend fun performGesture(service: AccessibilityService, path: Path, duration: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
        }, null)
        return deferred.await()
    }
}
