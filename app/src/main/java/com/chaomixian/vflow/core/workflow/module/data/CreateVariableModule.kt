// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CreateVariableModule.kt
// (完整代码)
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

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
    private val typeOptions = listOf("文本", "数字", "布尔", "字典", "图像")
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
            acceptsMagicVariable = true
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
            "图像" -> ImageVariable.TYPE_NAME
            else -> TextVariable.TYPE_NAME
        }
        return listOf(OutputDefinition("variable", "变量值", outputTypeName))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val name = step.parameters["variableName"] as? String
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]
        val inputs = getInputs()

        val valuePill = PillUtil.createPillFromParam(value, inputs.find { it.id == "value" })

        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, "创建匿名变量 (", type, ") 为 ", valuePill)
        } else {
            val namePill = PillUtil.Pill(name, false, "variableName")
            PillUtil.buildSpannable(context, "创建变量 ", namePill, " (", type, ") 为 ", valuePill)
        }
    }


    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val rawValue = context.magicVariables["value"] ?: context.variables["value"]
        val variableName = context.variables["variableName"] as? String

        // 根据类型和输入值创建对应的变量对象
        val variable: Parcelable = when (type) {
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
            "图像" -> ImageVariable(rawValue?.toString() ?: "")
            else -> TextVariable(rawValue?.toString() ?: "")
        }

        // 如果用户提供了变量名，则将其存入命名变量上下文中
        if (!variableName.isNullOrBlank()) {
            context.namedVariables[variableName] = variable
            onProgress(ProgressUpdate("已创建命名变量 '$variableName'"))
        }

        // 将创建的变量作为匿名输出返回，以便紧随其后的模块能立即通过魔法变量使用
        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}