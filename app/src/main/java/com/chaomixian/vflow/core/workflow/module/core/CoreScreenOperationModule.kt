package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
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
        private const val OP_CLICK = "click"
        private const val OP_LONG_PRESS = "long_press"
        private const val OP_SWIPE = "swipe"
    }

    override val id = "vflow.core.screen_operation"
    override val metadata = ActionMetadata(
        name = "屏幕操作",  // Fallback
        nameStringRes = R.string.module_vflow_core_screen_operation_name,
        description = "使用 vFlow Core 执行点击、长按或滑动操作，比无障碍服务更快速稳定。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_screen_operation_desc,
        iconRes = R.drawable.rounded_ads_click_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override val uiProvider: ModuleUIProvider = CoreScreenOperationModuleUIProvider()

    private val operationTypeOptions = listOf(OP_CLICK, OP_LONG_PRESS, OP_SWIPE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operation_type",
            name = "操作类型",  // Fallback
            staticType = ParameterType.ENUM,
            defaultValue = OP_CLICK,
            options = operationTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_core_screen_operation_click,
                R.string.option_vflow_core_screen_operation_long_press,
                R.string.option_vflow_core_screen_operation_swipe
            ),
            legacyValueMap = mapOf(
                "点击" to OP_CLICK,
                "Tap" to OP_CLICK,
                "长按" to OP_LONG_PRESS,
                "Long Press" to OP_LONG_PRESS,
                "滑动" to OP_SWIPE,
                "Swipe" to OP_SWIPE
            ),
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_core_operation_type
        ),
        InputDefinition(
            id = "target",
            name = "目标位置/起点",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "500,1000",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_core_target_position
        ),
        InputDefinition(
            id = "target_end",
            name = "滑动终点",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_core_swipe_end
        ),
        InputDefinition(
            id = "duration",
            name = "持续时间-毫秒",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_core_duration
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_screen_operation_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val type = step.parameters["operation_type"] as? String ?: OP_CLICK

        val targetPill = PillUtil.createPillFromParam(
            step.parameters["target"],
            inputs.find { it.id == "target" }
        )

        return when (type) {
            OP_SWIPE -> {
                val endPill = PillUtil.createPillFromParam(
                    step.parameters["target_end"],
                    inputs.find { it.id == "target_end" }
                )
                val durPill = PillUtil.createPillFromParam(
                    step.parameters["duration"],
                    inputs.find { it.id == "duration" }
                )
                PillUtil.buildSpannable(
                    context,
                    context.getString(R.string.summary_vflow_core_screen_operation_swipe),
                    targetPill,
                    " → ",
                    endPill,
                    " ",
                    durPill,
                    "ms"
                )
            }
            OP_LONG_PRESS -> {
                val durPill = PillUtil.createPillFromParam(
                    step.parameters["duration"],
                    inputs.find { it.id == "duration" }
                )
                PillUtil.buildSpannable(
                    context,
                    context.getString(R.string.summary_vflow_core_screen_operation_long_press),
                    targetPill,
                    " ",
                    durPill,
                    "ms"
                )
            }
            else -> {
                PillUtil.buildSpannable(
                    context,
                    context.getString(R.string.summary_vflow_core_screen_operation_click),
                    targetPill
                )
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
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        val opType = context.getVariableAsString("operation_type", OP_CLICK)

        return when (opType) {
            OP_CLICK -> executeClick(context, onProgress)
            OP_LONG_PRESS -> executeLongPress(context, onProgress)
            OP_SWIPE -> executeSwipe(context, onProgress)
            else -> ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_invalid_type, opType))
        }
    }

    private suspend fun executeClick(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
        if (target == null) {
            val value = context.getVariable("target")
            return if (value is VNull) {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_empty))
            } else {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_invalid))
            }
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_clicking, target.x, target.y)))

        val success = VFlowCoreBridge.performClick(target.x, target.y)

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_click_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(appContext.getString(R.string.error_vflow_shizuku_shell_command_failed), appContext.getString(R.string.error_vflow_core_screen_operation_click_failed))
        }
    }

    private suspend fun executeLongPress(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
        if (target == null) {
            val value = context.getVariable("target")
            return if (value is VNull) {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_empty))
            } else {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_invalid))
            }
        }

        val duration = getDuration(context, 1000L)

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_long_pressing, target.x, target.y, duration)))

        // 使用 swipe 实现长按：起点=终点
        val success = VFlowCoreBridge.performSwipe(
            target.x, target.y,
            target.x, target.y,
            duration
        )

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_long_press_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(appContext.getString(R.string.error_vflow_shizuku_shell_command_failed), appContext.getString(R.string.error_vflow_core_screen_operation_long_press_failed))
        }
    }

    private suspend fun executeSwipe(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val start = parseCoordinate(context, "target")
        if (start == null) {
            val value = context.getVariable("target")
            return if (value is VNull) {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_empty))
            } else {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_invalid))
            }
        }

        val end = parseCoordinate(context, "target_end")
        if (end == null) {
            val value = context.getVariable("target_end")
            return if (value is VNull) {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_empty))
            } else {
                ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_core_screen_operation_target_invalid))
            }
        }

        val duration = getDuration(context, 500L)

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_swiping, start.x, start.y, end.x, end.y, duration)))

        val success = VFlowCoreBridge.performSwipe(
            start.x, start.y,
            end.x, end.y,
            duration
        )

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_operation_swipe_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(appContext.getString(R.string.error_vflow_shizuku_shell_command_failed), appContext.getString(R.string.error_vflow_core_screen_operation_swipe_failed))
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
        val value = context.getVariable(paramKey)

        // 检查值是否存在（VNull 表示不存在）
        if (value is VNull) {
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
            // 先解析字符串中的变量引用
            val resolvedStr = com.chaomixian.vflow.core.execution.VariableResolver.resolve(coordStr, context)
            return parseStringToCoordinate(resolvedStr)
        }

        val coordStr = value.asString()
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
        val duration = context.getVariableAsLong("duration") ?: return default.coerceAtLeast(100)
        return duration.coerceAtLeast(100)
    }
}
