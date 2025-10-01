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
     * 当文本复杂时，只显示标题，预览由UIProvider处理。
     * 当文本简单时，显示完整的带Pill的摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["message"]?.toString() ?: ""

        if (isComplex(rawText)) {
            // 内容复杂，只显示模块名，具体内容由 RichTextUIProvider 预览
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

    /**
     * 判断一个字符串是否为“复杂”内容。
     * 复杂定义为：
     * 1. 包含至少一个变量，并且还包含非空格的纯文本。
     * 2. 包含两个或更多个变量。
     * @param rawText 待检查的原始文本。
     * @return 如果内容复杂则返回 true，否则返回 false。
     */
    private fun isComplex(rawText: String): Boolean {
        val variablePattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")
        val matcher = variablePattern.matcher(rawText)

        var variableCount = 0
        while (matcher.find()) {
            variableCount++
        }

        if (variableCount == 0) {
            // 没有变量，不复杂
            return false
        }

        if (variableCount > 1) {
            // 超过一个变量，就算复杂
            return true
        }

        // 只有一个变量，检查是否还有其他非空格文本
        val textWithoutVariable = matcher.replaceAll("").trim()
        return textWithoutVariable.isNotEmpty()
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