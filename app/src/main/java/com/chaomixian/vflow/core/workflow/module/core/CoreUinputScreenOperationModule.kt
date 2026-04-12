package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.normalizeEnumValue
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoreUinputScreenOperationModule : BaseModule() {

    companion object {
        private const val TAG = "CoreUinputScreenOperation"
        const val OP_CLICK = "click"
        const val OP_LONG_PRESS = "long_press"
        const val OP_SWIPE = "swipe"
    }

    override val id = "vflow.core.uinput_screen_operation"
    override val metadata = ActionMetadata(
        name = "uinput屏幕操作",
        nameStringRes = R.string.module_vflow_core_uinput_screen_operation_name,
        description = "通过 vFlow Core 创建 uinput 触摸设备执行点击、长按或滑动，需要 Root 权限。",
        descriptionStringRes = R.string.module_vflow_core_uinput_screen_operation_desc,
        iconRes = R.drawable.rounded_ads_click_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE_ROOT)
    }

    override val uiProvider: ModuleUIProvider = CoreScreenOperationModuleUIProvider()

    private val operationTypeOptions = listOf(OP_CLICK, OP_LONG_PRESS, OP_SWIPE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operation_type",
            name = "操作类型",
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
            name = "目标位置/起点",
            staticType = ParameterType.STRING,
            defaultValue = "500,1000",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_core_target_position
        ),
        InputDefinition(
            id = "target_end",
            name = "滑动终点",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_core_swipe_end
        ),
        InputDefinition(
            id = "duration",
            name = "持续时间-毫秒",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_core_duration
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_core_screen_operation_success_name
        )
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
                    context.getString(R.string.summary_vflow_core_uinput_screen_operation_swipe),
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
                    context.getString(R.string.summary_vflow_core_uinput_screen_operation_long_press),
                    targetPill,
                    " ",
                    durPill,
                    "ms"
                )
            }
            else -> PillUtil.buildSpannable(
                context,
                context.getString(R.string.summary_vflow_core_uinput_screen_operation_click),
                targetPill
            )
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        if (VFlowCoreBridge.privilegeMode != VFlowCoreBridge.PrivilegeMode.ROOT) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_uinput_screen_operation_permission_denied),
                appContext.getString(R.string.error_vflow_core_uinput_screen_operation_root_required)
            )
        }

        val operationTypeInput = getInputs().first { it.id == "operation_type" }
        val rawOpType = context.getVariableAsString("operation_type", OP_CLICK)
        val opType = operationTypeInput.normalizeEnumValue(rawOpType) ?: rawOpType

        return when (opType) {
            OP_CLICK -> executeClick(context, onProgress)
            OP_LONG_PRESS -> executeLongPress(context, onProgress)
            OP_SWIPE -> executeSwipe(context, onProgress)
            else -> ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                appContext.getString(R.string.error_vflow_core_screen_operation_invalid_type, opType)
            )
        }
    }

    private suspend fun executeClick(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
            ?: return invalidCoordinateFailure(context, "target")

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_clicking, target.x, target.y)))
        val result = VFlowCoreBridge.performUinputTap(target.x, target.y)

        return if (result.success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_click_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(
                    R.string.error_vflow_core_uinput_screen_operation_click_failed,
                    result.error ?: ""
                ).trim()
            )
        }
    }

    private suspend fun executeLongPress(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val target = parseCoordinate(context, "target")
            ?: return invalidCoordinateFailure(context, "target")
        val duration = getDuration(context, 1000L)

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_long_pressing, target.x, target.y, duration)))
        val result = VFlowCoreBridge.performUinputLongPress(target.x, target.y, duration)

        return if (result.success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_long_press_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(
                    R.string.error_vflow_core_uinput_screen_operation_long_press_failed,
                    result.error ?: ""
                ).trim()
            )
        }
    }

    private suspend fun executeSwipe(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val start = parseCoordinate(context, "target")
            ?: return invalidCoordinateFailure(context, "target")
        val end = parseCoordinate(context, "target_end")
            ?: return invalidCoordinateFailure(context, "target_end")
        val duration = getDuration(context, 500L)

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_swiping, start.x, start.y, end.x, end.y, duration)))
        val result = VFlowCoreBridge.performUinputSwipe(start.x, start.y, end.x, end.y, duration)

        return if (result.success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_uinput_screen_operation_swipe_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(
                    R.string.error_vflow_core_uinput_screen_operation_swipe_failed,
                    result.error ?: ""
                ).trim()
            )
        }
    }

    private fun invalidCoordinateFailure(
        context: ExecutionContext,
        paramKey: String
    ): ExecutionResult.Failure {
        val value = context.getVariable(paramKey)
        return if (value is VNull) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                appContext.getString(R.string.error_vflow_core_screen_operation_target_empty)
            )
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                appContext.getString(R.string.error_vflow_core_screen_operation_target_invalid)
            )
        }
    }

    private data class Point(val x: Int, val y: Int)

    private fun parseCoordinate(context: ExecutionContext, paramKey: String): Point? {
        val value = context.getVariable(paramKey)

        if (value is VNull) {
            DebugLogger.w(TAG, "坐标参数 '$paramKey' 为空")
            return null
        }
        if (value is VCoordinate) {
            return Point(value.x, value.y)
        }
        if (value is VString) {
            return parseStringToCoordinate(VariableResolver.resolve(value.asString(), context))
        }
        return parseStringToCoordinate(value.asString())
    }

    private fun parseStringToCoordinate(coordStr: String): Point? {
        val parts = coordStr.split(",")
        if (parts.size != 2) {
            return null
        }
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        return Point(x, y)
    }

    private fun getDuration(context: ExecutionContext, default: Long): Long {
        val duration = context.getVariableAsLong("duration") ?: return default.coerceAtLeast(100)
        return duration.coerceAtLeast(100)
    }
}
