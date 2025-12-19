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
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShizukuManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred
import java.util.regex.Pattern

/**
 * "屏幕操作" 模块。
 */
class ScreenOperationModule : BaseModule() {

    override val id = "vflow.interaction.screen_operation"
    override val metadata = ActionMetadata(
        name = "屏幕操作",
        description = "在屏幕上执行点击、长按或滑动操作。",
        iconRes = R.drawable.rounded_ads_click_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = ScreenOperationModuleUIProvider()

    val executionModeOptions = listOf("自动", "无障碍", "Shell")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        if (step == null) return listOf(PermissionManager.ACCESSIBILITY)
        val mode = step.parameters["execution_mode"] as? String ?: "自动"

        return when (mode) {
            "无障碍" -> listOf(PermissionManager.ACCESSIBILITY)
            "Shell" -> {
                val context = LogManager.applicationContext
                val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                val shellType = prefs.getString("default_shell_mode", "shizuku")
                if (shellType == "root") listOf(PermissionManager.ROOT) else listOf(PermissionManager.SHIZUKU)
            }
            else -> listOf(PermissionManager.ACCESSIBILITY)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("operation_type", "类型", ParameterType.ENUM, "点击", options = listOf("点击", "长按", "滑动"), acceptsMagicVariable = false),
        InputDefinition(
            "target", "目标/起点", ParameterType.STRING, "",
            acceptsMagicVariable = true,
            supportsRichText = false,
            acceptedMagicVariableTypes = setOf(ScreenElement.TYPE_NAME, Coordinate.TYPE_NAME, TextVariable.TYPE_NAME)
        ),
        // [修复] isHidden = false，让 AI 能看到这个参数
        InputDefinition(
            "target_end", "滑动终点", ParameterType.STRING, "",
            acceptsMagicVariable = true,
            supportsRichText = false,
            acceptedMagicVariableTypes = setOf(ScreenElement.TYPE_NAME, Coordinate.TYPE_NAME, TextVariable.TYPE_NAME),
            isHidden = false
        ),
        // [修复] isHidden = false，让 AI 能控制时间
        InputDefinition("duration", "持续时间(ms)", ParameterType.NUMBER, 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME), isHidden = false),
        // [修复] isHidden = false，允许 AI (极少数情况下) 选择执行模式，或者仅仅是为了 Schema 完整性
        InputDefinition("execution_mode", "执行方式", ParameterType.ENUM, "自动", options = executionModeOptions, acceptsMagicVariable = false, isHidden = false),

        // show_advanced 仅用于 UI 状态保存，保持隐藏
        InputDefinition("show_advanced", "显示高级选项", ParameterType.BOOLEAN, false, acceptsMagicVariable = false, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val type = step.parameters["operation_type"] as? String ?: "点击"
        val targetPill = PillUtil.createPillFromParam(step.parameters["target"], inputs.find { it.id == "target" })

        return when (type) {
            "滑动" -> {
                val endPill = PillUtil.createPillFromParam(step.parameters["target_end"], inputs.find { it.id == "target_end" })
                PillUtil.buildSpannable(context, "从 ", targetPill, " 滑动到 ", endPill)
            }
            "长按" -> {
                val duration = step.parameters["duration"]
                PillUtil.buildSpannable(context, "长按 ", targetPill, " ($duration ms)")
            }
            else -> { // 点击
                PillUtil.buildSpannable(context, "点击 ", targetPill)
            }
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val opType = context.variables["operation_type"] as? String ?: "点击"
        val mode = context.variables["execution_mode"] as? String ?: "自动"

        val durationVal = context.magicVariables["duration"] ?: context.variables["duration"]
        val duration = (durationVal as? Number)?.toLong() ?:
        (durationVal as? String)?.toLongOrNull() ?:
        (if (opType == "长按") 1000L else if (opType == "滑动") 500L else 50L)

        // 1. 解析坐标
        val targetObj = context.magicVariables["target"] ?: context.variables["target"]
        val startPoint = resolveTargetToPoint(context, targetObj)
            ?: return ExecutionResult.Failure("无效目标", "无法解析起点位置: $targetObj")

        var endPoint: Point? = null
        if (opType == "滑动") {
            val endObj = context.magicVariables["target_end"] ?: context.variables["target_end"]
            endPoint = resolveTargetToPoint(context, endObj)
                ?: return ExecutionResult.Failure("无效目标", "无法解析终点位置: $endObj")
        }

        onProgress(ProgressUpdate("执行 $opType ($mode)..."))

        val accService = ServiceStateBus.getAccessibilityService()
        val shellMode = context.applicationContext.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
            .getString("default_shell_mode", "shizuku")
        val useRoot = shellMode == "root"

        var success = false

        // 2. 根据模式执行
        if (mode == "无障碍" || (mode == "自动" && accService != null)) {
            if (accService != null) {
                success = when (opType) {
                    "点击" -> performGesture(accService, createClickPath(startPoint), 50L)
                    "长按" -> performGesture(accService, createClickPath(startPoint), duration.coerceAtLeast(500L))
                    "滑动" -> performGesture(accService, createSwipePath(startPoint, endPoint!!), duration)
                    else -> false
                }
                if (success) DebugLogger.d("ScreenOp", "无障碍执行成功")
            } else if (mode == "无障碍") {
                return ExecutionResult.Failure("服务未连接", "指定使用无障碍服务，但服务未运行。")
            }
        }

        if (!success && (mode == "Shell" || mode == "自动")) {
            if (ShizukuManager.isShizukuActive(context.applicationContext) || useRoot) {
                val cmd = when (opType) {
                    "点击" -> "input tap ${startPoint.x} ${startPoint.y}"
                    "长按" -> "input swipe ${startPoint.x} ${startPoint.y} ${startPoint.x} ${startPoint.y} $duration"
                    "滑动" -> "input swipe ${startPoint.x} ${startPoint.y} ${endPoint!!.x} ${endPoint.y} $duration"
                    else -> ""
                }
                if (cmd.isNotEmpty()) {
                    val result = if (useRoot) {
                        try {
                            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
                            "Success"
                        } catch (e: Exception) { "Error: ${e.message}" }
                    } else {
                        ShizukuManager.execShellCommand(context.applicationContext, cmd)
                    }
                    success = !result.startsWith("Error")
                    if (success) DebugLogger.d("ScreenOp", "Shell 执行成功")
                }
            } else if (mode == "Shell") {
                return ExecutionResult.Failure("权限不足", "指定使用 Shell，但无 Root 或 Shizuku 权限。")
            }
        }

        if (success) {
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        } else {
            return ExecutionResult.Failure("执行失败", "操作无法执行，请检查相关权限或服务状态。")
        }
    }

    private fun resolveTargetToPoint(context: ExecutionContext, target: Any?): Point? {
        return when (target) {
            is ScreenElement -> Point(target.bounds.centerX(), target.bounds.centerY())
            is Coordinate -> Point(target.x, target.y)
            is String -> {
                if (target.contains(",") && !target.contains("[")) {
                    val parts = target.split(",")
                    val x = parts[0].trim().toIntOrNull()
                    val y = parts[1].trim().toIntOrNull()
                    if (x != null && y != null) return Point(x, y)
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
                null
            }
            else -> null
        }
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