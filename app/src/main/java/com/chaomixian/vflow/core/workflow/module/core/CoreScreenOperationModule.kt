package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 屏幕操作模块（Beta）。
 * 使用 vFlow Core 执行点击、长按或滑动操作，比无障碍服务更快速稳定。
 */
class CoreScreenOperationModule : BaseModule() {

    companion object {
        private const val TAG = "CoreScreenOperationModule"
    }

    override val id = "vflow.core.screen_operation"
    override val metadata = ActionMetadata(
        name = "屏幕操作",
        description = "使用 vFlow Core 执行点击、长按或滑动操作，比无障碍服务更快速稳定。",
        iconRes = R.drawable.rounded_ads_click_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override val uiProvider: ModuleUIProvider = CoreScreenOperationModuleUIProvider()

    private val operationTypeOptions = listOf("点击", "长按", "滑动")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operation_type",
            name = "操作类型",
            staticType = ParameterType.ENUM,
            defaultValue = "点击",
            options = operationTypeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "target",
            name = "目标位置/起点",
            staticType = ParameterType.STRING,
            defaultValue = "500,1000",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id)
        ),
        InputDefinition(
            id = "target_end",
            name = "滑动终点",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id)
        ),
        InputDefinition(
            id = "duration",
            name = "持续时间-毫秒",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val type = step.parameters["operation_type"] as? String ?: "点击"

        val targetPill = PillUtil.createPillFromParam(
            step.parameters["target"],
            inputs.find { it.id == "target" }
        )

        return when (type) {
            "滑动" -> {
                val endPill = PillUtil.createPillFromParam(
                    step.parameters["target_end"],
                    inputs.find { it.id == "target_end" }
                )
                val durPill = PillUtil.createPillFromParam(
                    step.parameters["duration"],
                    inputs.find { it.id == "duration" }
                )
                PillUtil.buildSpannable(
                    context, "Core 滑动: ", targetPill, " → ", endPill, " ", durPill, "ms"
                )
            }
            "长按" -> {
                val durPill = PillUtil.createPillFromParam(
                    step.parameters["duration"],
                    inputs.find { it.id == "duration" }
                )
                PillUtil.buildSpannable(
                    context, "Core 长按: ", targetPill, " ", durPill, "ms"
                )
            }
            else -> {
                PillUtil.buildSpannable(context, "Core 点击: ", targetPill)
            }
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 确保 Core 连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已授予 Shizuku 或 Root 权限。"
            )
        }

        val step = context.allSteps[context.currentStepIndex]
        val opType = step.parameters["operation_type"]?.toString() ?: "点击"

        return when (opType) {
            "点击" -> executeClick(context, onProgress)
            "长按" -> executeLongPress(context, onProgress)
            "滑动" -> executeSwipe(context, onProgress)
            else -> ExecutionResult.Failure("参数错误", "未知的操作类型: $opType")
        }
    }

    private suspend fun executeClick(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
        if (target == null) {
            val value = context.magicVariables["target"] ?: context.variables["target"]
            return if (value == null) {
                ExecutionResult.Failure("参数错误", "目标位置为空。请确保已设置坐标或连接了上游模块的坐标输出。")
            } else {
                ExecutionResult.Failure("参数错误", "目标位置格式错误。需要 Coordinate 类型的坐标或 \"x,y\" 格式的字符串。实际类型: ${value.javaClass.simpleName}")
            }
        }

        onProgress(ProgressUpdate("正在使用 vFlow Core 点击坐标 (${target.x}, ${target.y})..."))

        val success = VFlowCoreBridge.performClick(target.x, target.y)

        return if (success) {
            onProgress(ProgressUpdate("点击成功"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 点击操作失败")
        }
    }

    private suspend fun executeLongPress(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
        if (target == null) {
            val value = context.magicVariables["target"] ?: context.variables["target"]
            return if (value == null) {
                ExecutionResult.Failure("参数错误", "目标位置为空。请确保已设置坐标或连接了上游模块的坐标输出。")
            } else {
                ExecutionResult.Failure("参数错误", "目标位置格式错误。需要 Coordinate 类型的坐标或 \"x,y\" 格式的字符串。实际类型: ${value.javaClass.simpleName}")
            }
        }

        val duration = getDuration(context, 1000L)

        onProgress(ProgressUpdate("正在使用 vFlow Core 长按坐标 (${target.x}, ${target.y}) ${duration}ms..."))

        // 使用 swipe 实现长按：起点=终点
        val success = VFlowCoreBridge.performSwipe(
            target.x, target.y,
            target.x, target.y,
            duration
        )

        return if (success) {
            onProgress(ProgressUpdate("长按成功"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 长按操作失败")
        }
    }

    private suspend fun executeSwipe(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val start = parseCoordinate(context, "target")
        if (start == null) {
            val value = context.magicVariables["target"] ?: context.variables["target"]
            return if (value == null) {
                ExecutionResult.Failure("参数错误", "起点坐标为空。请确保已设置坐标或连接了上游模块的坐标输出。")
            } else {
                ExecutionResult.Failure("参数错误", "起点坐标格式错误。需要 Coordinate 类型的坐标或 \"x,y\" 格式的字符串。实际类型: ${value.javaClass.simpleName}")
            }
        }

        val end = parseCoordinate(context, "target_end")
        if (end == null) {
            val value = context.magicVariables["target_end"] ?: context.variables["target_end"]
            return if (value == null) {
                ExecutionResult.Failure("参数错误", "终点坐标为空。请确保已设置坐标或连接了上游模块的坐标输出。")
            } else {
                ExecutionResult.Failure("参数错误", "终点坐标格式错误。需要 Coordinate 类型的坐标或 \"x,y\" 格式的字符串。实际类型: ${value.javaClass.simpleName}")
            }
        }

        val duration = getDuration(context, 500L)

        onProgress(ProgressUpdate("正在使用 vFlow Core 执行滑动: (${start.x}, ${start.y}) → (${end.x}, ${end.y}) ${duration}ms..."))

        val success = VFlowCoreBridge.performSwipe(
            start.x, start.y,
            end.x, end.y,
            duration
        )

        return if (success) {
            onProgress(ProgressUpdate("滑动成功"))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 滑动操作失败")
        }
    }

    /**
     * 坐标点数据类
     */
    private data class Point(val x: Int, val y: Int)

    /**
     * 解析坐标参数
     * 支持格式：
     * 1. Coordinate 类型变量
     * 2. 字符串 "x,y"
     */
    private fun parseCoordinate(
        context: ExecutionContext,
        paramKey: String
    ): Point? {
        val value = context.magicVariables[paramKey] ?: context.variables[paramKey]

        // 检查值是否存在
        if (value == null) {
            DebugLogger.w(TAG, "坐标参数 '$paramKey' 为空")
            return null
        }

        // 处理 VCoordinate 类型
        if (value is VCoordinate) {
            DebugLogger.d(TAG, "解析坐标 $paramKey: VCoordinate(${value.x}, ${value.y})")
            return Point(value.x, value.y)
        }

        // 处理 VString 类型（支持字符串 "x,y"）
        if (value is VString) {
            val coordStr = value.asString()
            DebugLogger.d(TAG, "解析坐标 $paramKey: VString('$coordStr')")
            return parseStringToCoordinate(coordStr)
        }

        val coordStr = value.toString()
        DebugLogger.w(TAG, "坐标参数 '$paramKey' 不是已知类型，而是: ${value.javaClass.simpleName}, 值: $coordStr")

        // 解析字符串 "x,y"
        return parseStringToCoordinate(coordStr)
    }

    /**
     * 解析字符串格式的坐标 "x,y"
     */
    private fun parseStringToCoordinate(coordStr: String): Point? {
        val parts = coordStr.split(",")
        if (parts.size != 2) {
            DebugLogger.w(TAG, "坐标格式错误: 部分数量不是2 (coordStr='$coordStr', parts=${parts.size})")
            return null
        }

        val x = parts[0].trim().toIntOrNull()
        if (x == null) {
            DebugLogger.w(TAG, "坐标格式错误: x坐标无效 (coordStr='$coordStr', x='${parts[0].trim()}')")
            return null
        }

        val y = parts[1].trim().toIntOrNull()
        if (y == null) {
            DebugLogger.w(TAG, "坐标格式错误: y坐标无效 (coordStr='$coordStr', y='${parts[1].trim()}')")
            return null
        }

        return Point(x, y)
    }

    /**
     * 获取持续时间参数
     * @param default 默认值（毫秒）
     * @return 持续时间，最少 100ms
     */
    private fun getDuration(
        context: ExecutionContext,
        default: Long
    ): Long {
        val duration = (context.magicVariables["duration"]
            ?: context.variables["duration"]) as? Number
        return duration?.toLong()?.coerceAtLeast(100) ?: default
    }
}
