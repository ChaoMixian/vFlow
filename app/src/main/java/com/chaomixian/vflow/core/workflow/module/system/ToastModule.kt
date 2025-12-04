// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/ToastModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToastModule : BaseModule() {

    override val id = "vflow.device.toast"
    override val metadata = ActionMetadata(
        name = "显示Toast",
        description = "在屏幕底部弹出一个简短的提示消息。",
        iconRes = R.drawable.rounded_call_to_action_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("message")
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message",
            name = "消息内容",
            staticType = ParameterType.STRING,
            defaultValue = "Hello, vFlow!",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME),
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["message"]?.toString() ?: ""

        // [修改] 直接使用 VariableResolver 的通用判断逻辑，删除本地冗余代码
        if (VariableResolver.isComplex(rawText)) {
            return metadata.name
        } else {
            // 内容简单，显示完整的摘要
            val messagePill = PillUtil.createPillFromParam(
                step.parameters["message"],
                getInputs().find { it.id == "message" }
            )
            return PillUtil.buildSpannable(context, "显示Toast: ", messagePill)
        }
    }

    // [修改] 删除了 private fun isComplex(rawText: String): Boolean

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val message = step.parameters["message"]?.toString()
        if (message.isNullOrBlank()) {
            return ValidationResult(isValid = false, errorMessage = "消息内容不能为空")
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
            return ExecutionResult.Failure("消息为空", "需要显示的消息内容为空，无法执行。")
        }

        val finalMessage = VariableResolver.resolve(richTextMessage, context)

        onProgress(ProgressUpdate("准备显示Toast: $finalMessage"))
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, finalMessage, Toast.LENGTH_LONG).show()
        }
        return ExecutionResult.Success(outputs = mapOf("success" to BooleanVariable(true)))
    }
}