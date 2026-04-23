package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.view.KeyEvent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 按键模块（Beta）。
 * 使用 vFlow Core 发送按键事件，比无障碍服务更快速稳定。
 */
class CorePressKeyModule : BaseModule() {

    override val id = "vflow.core.press_key"
    override val metadata = ActionMetadata(
        name = "按键",  // Fallback
        nameStringRes = R.string.module_vflow_core_press_key_name,
        description = "使用 vFlow Core 发送按键事件，比无障碍服务更快速稳定。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_press_key_desc,
        iconRes = R.drawable.rounded_keyboard_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Send an Android key code through vFlow Core.",
        workflowStepDescription = "Send an Android key code through vFlow Core.",
        inputHints = mapOf(
            "key_code" to "Numeric Android key code such as 4 for BACK or 66 for ENTER.",
        ),
        requiredInputIds = setOf("key_code"),
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "key_code",
            name = "按键代码",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 4.0, // KEYCODE_BACK
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_core_press_key_code_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_press_key_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val keyPill = PillUtil.createPillFromParam(
            step.parameters["key_code"],
            getInputs().find { it.id == "key_code" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_press_key), keyPill)
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

        // 2. 获取参数
        val keyCodeInt = context.getVariableAsInt("key_code")

        if (keyCodeInt == null) {
            val rawValue = context.getVariable("key_code")
            val rawValueStr = when (rawValue) {
                is VString -> rawValue.raw
                is VNull -> "空值"
                is VNumber -> rawValue.raw.toString()
                else -> rawValue?.toString() ?: "未知"
            }
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                appContext.getString(R.string.error_vflow_core_press_key_invalid_code_detail, rawValueStr)
            )
        }

        val keyName = KeyEvent.keyCodeToString(keyCodeInt)

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_press_key_sending, keyName)))

        // 3. 执行操作
        val success = VFlowCoreBridge.pressKey(keyCodeInt)

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_press_key_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(R.string.error_vflow_core_press_key_failed)
            )
        }
    }
}
