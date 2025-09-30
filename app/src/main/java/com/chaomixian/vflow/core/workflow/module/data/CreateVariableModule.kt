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

class CreateVariableModule : BaseModule() {
    override val id = "vflow.variable.create"
    override val metadata = ActionMetadata(
        name = "创建变量",
        description = "创建一个新的变量，可选择为其命名以便后续修改或读取。",
        iconRes = R.drawable.rounded_add_24,
        category = "数据"
    )
    private val typeOptions = listOf("文本", "数字", "布尔", "字典", "列表", "图像")

    /**
     * [核心修复]
     * 将 uiProvider 指回 VariableModuleUIProvider。
     * 这样 ActionEditorSheet 就会使用我们自定义的编辑器界面，
     * 从而恢复字典和列表的 RecyclerView 编辑功能。
     */
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
     * 摘要逻辑保持不变，它与 Adapter 中的预览逻辑协同工作。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val name = step.parameters["variableName"] as? String
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]

        // 1. 如果值是变量引用，总是显示完整的摘要
        if (value is String && (value.isMagicVariable() || value.isNamedVariable())) {
            val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
            return if (name.isNullOrBlank()) {
                PillUtil.buildSpannable(context, "创建匿名 ", type, " 为 ", valuePill)
            } else {
                val namePill = PillUtil.Pill("[[${name}]]", "variableName")
                PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
            }
        }

        // 2. 特别处理“文本”类型
        if (type == "文本") {
            val rawText = value?.toString() ?: ""
            // 如果文本内容不复杂，摘要必须自己显示内容
            if (!isComplex(rawText)) {
                val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
                return if (name.isNullOrBlank()) {
                    PillUtil.buildSpannable(context, "创建匿名 ", type, " 为 ", valuePill)
                } else {
                    val namePill = PillUtil.Pill("[[${name}]]", "variableName")
                    PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
                }
            } else {
                // 如果文本内容复杂，摘要只显示简洁的标题
                return if (name.isNullOrBlank()) {
                    "创建 匿名变量 ($type)"
                } else {
                    val namePill = PillUtil.Pill("[[${name}]]", "variableName")
                    PillUtil.buildSpannable(context, "创建变量 ", namePill, " ($type)")
                }
            }
        }

        // 3. 处理其他类型（字典、列表等）
        // 对于静态的字典和列表，它们的预览UI会显示，所以这里返回简洁摘要
        if ((type == "字典" || type == "列表") && value !is String) {
            return if (name.isNullOrBlank()) {
                "创建 匿名变量 ($type)"
            } else {
                val namePill = PillUtil.Pill("[[${name}]]", "variableName")
                PillUtil.buildSpannable(context, "创建变量 ", namePill, " ($type)")
            }
        }

        // 对于数字、布尔等其他简单类型
        val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, "创建匿名 ", type, " 为 ", valuePill)
        } else {
            val namePill = PillUtil.Pill("[[${name}]]", "variableName")
            PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
        }
    }

    private fun isComplex(rawText: String): Boolean {
        val variablePattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")
        val matcher = variablePattern.matcher(rawText)

        var variableCount = 0
        while (matcher.find()) {
            variableCount++
        }

        if (variableCount == 0) {
            return false
        }

        if (variableCount > 1) {
            return true
        }

        val textWithoutVariable = matcher.replaceAll("").trim()
        return textWithoutVariable.isNotEmpty()
    }

    // ... validate 和 execute 方法保持不变 ...
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val variableName = step.parameters["variableName"] as? String
        if (!variableName.isNullOrBlank()) {
            val count = allSteps.count {
                it.id != step.id &&
                        it.moduleId == this.id &&
                        (it.parameters["variableName"] as? String) == variableName
            }
            if (count > 0) {
                return ValidationResult(false, "变量名 '$variableName' 已存在，请使用其他名称。")
            }
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val rawValue = context.magicVariables["value"] ?: context.variables["value"]
        val variableName = context.variables["variableName"] as? String

        val variable: Parcelable = when (type) {
            "文本" -> {
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
                    is String -> rawValue.lines().filter { it.isNotEmpty() }
                    else -> emptyList()
                }
                ListVariable(list)
            }
            "图像" -> ImageVariable(rawValue?.toString() ?: "")
            else -> TextVariable(rawValue?.toString() ?: "")
        }

        if (!variableName.isNullOrBlank()) {
            if (context.namedVariables.containsKey(variableName)) {
                return ExecutionResult.Failure("命名冲突", "变量 '$variableName' 已存在。")
            }
            context.namedVariables[variableName] = variable
            onProgress(ProgressUpdate("已创建命名变量 '$variableName'"))
        }

        return ExecutionResult.Success(mapOf("variable" to variable))
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