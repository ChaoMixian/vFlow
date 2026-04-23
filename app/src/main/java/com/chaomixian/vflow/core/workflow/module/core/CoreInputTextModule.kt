package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 输入文本模块（Beta）。
 * 使用 vFlow Core 输入文本，比无障碍服务更快速稳定。
 */
class CoreInputTextModule : BaseModule() {

    override val id = "vflow.core.input_text"
    override val metadata = ActionMetadata(
        name = "输入文本",  // Fallback
        nameStringRes = R.string.module_vflow_core_input_text_name,
        description = "使用 vFlow Core 输入文本，比无障碍服务更快速稳定。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_input_text_desc,
        iconRes = R.drawable.rounded_keyboard_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Type text through vFlow Core. Focus the target field first if needed.",
        workflowStepDescription = "Type text through vFlow Core.",
        inputHints = mapOf(
            "text" to "Plain text to type into the currently focused field.",
        ),
        requiredInputIds = setOf("text"),
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            name = "文本内容",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_interaction_input_text_text_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_input_text_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            getInputs().find { it.id == "text" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_input_text), textPill)
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
        val text = context.getVariableAsString("text")

        if (text == null) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_input_text_param_error),
                appContext.getString(R.string.error_vflow_core_input_text_empty)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_input_text_inputting)))

        // 3. 执行操作
        val success = VFlowCoreBridge.inputText(text)

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_input_text_success)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(R.string.error_vflow_core_input_text_failed)
            )
        }
    }
}
