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
 * 设置剪贴板模块（Beta）。
 * 使用 vFlow Core 设置剪贴板内容。
 */
class CoreSetClipboardModule : BaseModule() {

    override val id = "vflow.core.set_clipboard"
    override val metadata = ActionMetadata(
        name = "设置剪贴板",  // Fallback
        nameStringRes = R.string.module_vflow_core_set_clipboard_name,
        description = "使用 vFlow Core 设置剪贴板内容。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_set_clipboard_desc,
        iconRes = R.drawable.rounded_content_copy_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            name = "文本内容",  // Fallback
            nameStringRes = R.string.param_vflow_core_set_clipboard_text_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_set_clipboard_success_name),
        OutputDefinition("text", "设置的文本内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_set_clipboard_text_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            inputs.find { it.id == "text" }
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_core_set_clipboard),
            textPill
        )
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

        // 3. 执行操作
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_set_clipboard_setting)))
        val success = VFlowCoreBridge.setClipboard(text)

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_set_clipboard_success)))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "text" to VString(text)
            ))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(R.string.error_vflow_core_set_clipboard_failed)
            )
        }
    }
}
