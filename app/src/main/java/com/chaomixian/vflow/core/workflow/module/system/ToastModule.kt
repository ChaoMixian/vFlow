// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/ToastModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToastModule : BaseModule() {

    override val id = "vflow.device.toast"

    // 模块的元数据
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_device_toast_name,
        descriptionStringRes = R.string.module_vflow_device_toast_desc,
        name = "显示Toast",                              // Fallback
        description = "在屏幕底部弹出一个简短的提示消息", // Fallback
        iconRes = R.drawable.rounded_call_to_action_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("message")
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message",
            name = "消息内容",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "Hello, vFlow!",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_device_toast_message_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_toast_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["message"]?.toString() ?: ""

        // [修改] 直接使用 VariableResolver 的通用判断逻辑，删除本地冗余代码
        if (VariableResolver.isComplex(rawText)) {
            return metadata.getLocalizedName(context)
        } else {
            // 内容简单，显示完整的摘要
            val messagePill = PillUtil.createPillFromParam(
                step.parameters["message"],
                getInputs().find { it.id == "message" }
            )
            return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", messagePill)
        }
    }

    // [修改] 删除了 private fun isComplex(rawText: String): Boolean

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val message = step.parameters["message"]?.toString()
        if (message.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_device_toast_empty)
            )
        }
        return ValidationResult(isValid = true) // 默认有效
    }

    /**
     * 执行显示Toast的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val richTextMessage = context.variables["message"] as? String
        if (richTextMessage.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_toast_message_empty),
                appContext.getString(R.string.error_vflow_device_toast_message_missing)
            )
        }

        val finalMessage = VariableResolver.resolve(richTextMessage, context)

        onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_device_toast_preparing), finalMessage)))

        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, finalMessage, Toast.LENGTH_LONG).show()
        }
        return ExecutionResult.Success(outputs = mapOf("success" to VBoolean(true)))
    }
}