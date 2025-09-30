// main/java/com/chaomixian/vflow/core/workflow/module/system/ToastModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Matcher
import java.util.regex.Pattern

class ToastModule : BaseModule() {

    // 模块的唯一ID
    override val id = "vflow.device.toast"
    // 模块的元数据，用于在UI中展示
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

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 简化摘要，只返回模块名称。预览将由UIProvider处理。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val messagePill = PillUtil.createPillFromParam(
            step.parameters["message"],
            getInputs().find { it.id == "message" }
        )
        return PillUtil.buildSpannable(context, "显示Toast: ", messagePill)
    }

    /**
     * 验证模块参数的有效性。
     * 确保消息内容在不使用魔法变量时不能为空。
     */
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
        val finalMessage = resolveRichText(richTextMessage, context)
        onProgress(ProgressUpdate("准备显示Toast: $finalMessage"))
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, finalMessage, Toast.LENGTH_LONG).show()
        }
        return ExecutionResult.Success(outputs = mapOf("success" to BooleanVariable(true)))
    }

    private fun resolveRichText(richText: String, context: ExecutionContext): String {
        val pattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")
        val matcher = pattern.matcher(richText)
        val result = StringBuffer()
        while (matcher.find()) {
            val variableRef = matcher.group(1)
            var replacement = ""
            if (variableRef != null) {
                if (variableRef.isMagicVariable()) {
                    val parts = variableRef.removeSurrounding("{{", "}}").split('.')
                    val sourceStepId = parts.getOrNull(0)
                    val sourceOutputId = parts.getOrNull(1)
                    if (sourceStepId != null && sourceOutputId != null) {
                        val value = context.stepOutputs[sourceStepId]?.get(sourceOutputId)
                        replacement = value?.let {
                            when(it) {
                                is TextVariable -> it.value
                                is NumberVariable -> it.value.toString()
                                is BooleanVariable -> it.value.toString()
                                is ListVariable -> it.value.joinToString()
                                is DictionaryVariable -> it.value.toString()
                                else -> it.toString()
                            }
                        } ?: ""
                    }
                } else if (variableRef.isNamedVariable()) {
                    val varName = variableRef.removeSurrounding("[[", "]]")
                    val value = context.namedVariables[varName]
                    replacement = value?.let {
                        when(it) {
                            is TextVariable -> it.value
                            is NumberVariable -> it.value.toString()
                            is BooleanVariable -> it.value.toString()
                            is ListVariable -> it.value.joinToString()
                            is DictionaryVariable -> it.value.toString()
                            else -> it.toString()
                        }
                    } ?: ""
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)
        return result.toString()
    }
}