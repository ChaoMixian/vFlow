// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CreateVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * "创建变量" 模块。
 * 允许用户在工作流中定义一个新变量，并可以选择性地为其命名，
 * 使其成为可在整个工作流中通过名称访问的“命名变量”。
 */
class CreateVariableModule : BaseModule() {
    override val id = "vflow.variable.create"
    override val metadata = ActionMetadata(
        name = "创建变量",
        description = "创建一个新的变量，可选择为其命名以便后续修改或读取。",
        iconRes = R.drawable.rounded_add_24,
        category = "数据"
    )
    private val typeOptions = listOf("文本", "数字", "布尔", "字典", "列表", "图像")
    override val uiProvider: ModuleUIProvider? = VariableModuleUIProvider(typeOptions)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "variableName",
            name = "变量名称 (可选)",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "type",
            name = "变量类型",
            staticType = ParameterType.ENUM,
            defaultValue = "文本",
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "value",
            name = "值",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            // 当类型为文本时，支持富文本
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        if (step == null) return emptyList()
        val selectedType = step.parameters["type"] as? String
        val outputTypeName = when (selectedType) {
            "文本" -> TextVariable.TYPE_NAME
            "数字" -> NumberVariable.TYPE_NAME
            "布尔" -> BooleanVariable.TYPE_NAME
            "字典" -> DictionaryVariable.TYPE_NAME
            "列表" -> ListVariable.TYPE_NAME
            "图像" -> ImageVariable.TYPE_NAME
            else -> TextVariable.TYPE_NAME
        }
        return listOf(OutputDefinition("variable", "变量值", outputTypeName))
    }

    /**
     * 优化摘要逻辑。当类型为“文本”时，返回一个不含“值”的简洁摘要，
     * 因为值会由富文本预览(RichTextUIProvider)来显示。
     * 这可以避免在UI上同时显示两种摘要内容，解决了内容重复的问题。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val name = step.parameters["variableName"] as? String
        val type = step.parameters["type"]?.toString() ?: "文本"

        // 如果是文本类型，提供一个简洁的回退摘要，不包含值
        if (type == "文本") {
            return if (name.isNullOrBlank()) {
                "创建 匿名变量 (文本)"
            } else {
                val namePill = PillUtil.Pill("[[${name}]]", "variableName")
                PillUtil.buildSpannable(context, "创建变量 ", namePill, " (文本)")
            }
        }

        // 对于其他非文本类型，显示包含值的完整摘要
        val value = step.parameters["value"]
        val inputs = getInputs()
        val valuePill = when {
            type == "列表" -> {
                val listSize = (value as? List<*>)?.size ?: 0
                PillUtil.Pill("[$listSize 项]", "value")
            }
            type == "字典" -> {
                val dictSize = (value as? Map<*, *>)?.size ?: 0
                PillUtil.Pill("{$dictSize 项}", "value")
            }
            else -> PillUtil.createPillFromParam(value, inputs.find { it.id == "value" })
        }

        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, "创建匿名 ", type, " 为 ", valuePill)
        } else {
            val namePill = PillUtil.Pill("[[${name}]]", "variableName")
            PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
        }
    }


    /**
     * 重写验证逻辑以检查重复的变量名。
     * @param step 要验证的步骤。
     * @param allSteps 工作流中的所有步骤，用于上下文检查。
     * @return 验证结果。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val variableName = step.parameters["variableName"] as? String
        if (!variableName.isNullOrBlank()) {
            // 查找所有具有相同非空变量名的“创建变量”模块实例
            val count = allSteps.count {
                it.id != step.id && // 排除当前正在验证的步骤本身
                        it.moduleId == this.id &&
                        (it.parameters["variableName"] as? String) == variableName
            }
            if (count > 0) {
                return ValidationResult(false, "变量名 '$variableName' 已存在，请使用其他名称。")
            }
        }
        return ValidationResult(true) // 默认有效
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val rawValue = context.magicVariables["value"] ?: context.variables["value"]
        val variableName = context.variables["variableName"] as? String

        // 增加列表变量的创建逻辑
        val variable: Parcelable = when (type) {
            "文本" -> {
                // [核心修复] 如果是文本类型，则解析富文本内容
                val resolvedText = resolveRichText(rawValue?.toString() ?: "", context)
                TextVariable(resolvedText)
            }
            "数字" -> {
                val numValue = when (rawValue) {
                    is NumberVariable -> rawValue.value
                    is Number -> rawValue.toDouble()
                    is String -> rawValue.toDoubleOrNull()
                    else -> 0.0
                } ?: 0.0
                NumberVariable(numValue)
            }
            "布尔" -> BooleanVariable(
                when (rawValue) {
                    is BooleanVariable -> rawValue.value
                    is Boolean -> rawValue
                    else -> rawValue?.toString().toBoolean()
                }
            )
            "字典" -> DictionaryVariable((rawValue as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap())
            "列表" -> {
                val list = when (rawValue) {
                    is List<*> -> rawValue
                    is String -> rawValue.lines().filter { it.isNotEmpty() } // 从多行文本解析
                    else -> emptyList()
                }
                ListVariable(list)
            }
            "图像" -> ImageVariable(rawValue?.toString() ?: "")
            else -> TextVariable(rawValue?.toString() ?: "")
        }

        // 如果用户提供了变量名，则将其存入命名变量上下文中
        if (!variableName.isNullOrBlank()) {
            // 在执行时再次检查重复，以防万一
            if (context.namedVariables.containsKey(variableName)) {
                return ExecutionResult.Failure("命名冲突", "变量 '$variableName' 已存在。")
            }
            context.namedVariables[variableName] = variable
            onProgress(ProgressUpdate("已创建命名变量 '$variableName'"))
        }

        // 将创建的变量作为匿名输出返回，以便紧随其后的模块能立即通过魔法变量使用
        return ExecutionResult.Success(mapOf("variable" to variable))
    }

    /**
     * 解析富文本字符串中的变量引用，并替换为实际值。
     */
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