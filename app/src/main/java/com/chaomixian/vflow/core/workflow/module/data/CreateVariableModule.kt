// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CreateVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

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
        InputDefinition("variableName", "变量名称 (可选)", ParameterType.STRING, defaultValue = "", acceptsMagicVariable = false),
        InputDefinition("type", "变量类型", ParameterType.ENUM, defaultValue = "文本", options = typeOptions, acceptsMagicVariable = false),
        InputDefinition("value", "值", ParameterType.ANY, defaultValue = "", acceptsMagicVariable = true, supportsRichText = true)
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

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val name = step.parameters["variableName"] as? String
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]
        val rawText = value?.toString() ?: ""

        // 优先检查是否为“复杂内容”。
        // 如果是复杂的（包含多个变量或混合文本），则只返回简单标题。
        // RichTextUIProvider 会负责渲染预览视图。
        if (type == "文本" && VariableResolver.isComplex(rawText)) {
            return if (name.isNullOrBlank()) {
                "创建 匿名变量 ($type)"
            } else {
                val namePill = PillUtil.Pill("[[$name]]", "variableName")
                PillUtil.buildSpannable(context, "创建变量 ", namePill, " ($type)")
            }
        }

        // 如果是字典或列表，且不是单纯的变量引用
        // 此时 VariableValueUIProvider 会显示详细预览列表，摘要中隐藏 value pill 以防重复
        if ((type == "字典" || type == "列表") && !rawText.isMagicVariable() && !rawText.isNamedVariable()) {
            return buildSimpleSummary(context, name, type)
        }

        // 其他情况（简单文本、数字、布尔、或直接引用变量的字典/列表），摘要中显示完整值
        val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, "创建匿名 ", type, " 为 ", valuePill)
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
        }
    }

    private fun buildSimpleSummary(context: Context, name: String?, type: String): CharSequence {
        return if (name.isNullOrBlank()) {
            "创建 匿名变量 ($type)"
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, "创建变量 ", namePill, " ($type)")
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val variableName = step.parameters["variableName"] as? String
        if (!variableName.isNullOrBlank()) {
            val count = allSteps.count {
                it.id != step.id && it.moduleId == this.id && (it.parameters["variableName"] as? String) == variableName
            }
            if (count > 0) return ValidationResult(false, "变量名 '$variableName' 已存在，请使用其他名称。")
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
                val resolvedText = VariableResolver.resolve(rawValue?.toString() ?: "", context)
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
}