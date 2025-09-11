package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
// 更新导入路径
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.module.NumberVariable
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.DictionaryVariable
import com.chaomixian.vflow.core.module.ImageVariable

// 文件：SetVariableModule.kt
// 描述：用于在工作流中设置各种类型变量的 SetVariableModule。

/**
 * “设置变量”模块。
 * 允许用户在工作流中定义一个变量，并指定其类型和初始值。
 * 该变量随后可以被其他模块作为魔法变量使用。
 */
class SetVariableModule : BaseModule() {
    // 模块的唯一ID
    override val id = "vflow.variable.set"
    // 模块的元数据，用于在UI中展示
    override val metadata = ActionMetadata("设置变量\n(废弃)", "创建文本、数字、布尔、图像等变量", R.drawable.rounded_data_object_24, "数据")

    // 定义变量类型下拉框的选项
    private val typeOptions = listOf("文本", "数字", "布尔", "字典", "图像")
    // 提供自定义编辑器UI的实现
    override val uiProvider: ModuleUIProvider? = VariableModuleUIProvider(typeOptions)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
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
            acceptsMagicVariable = false
        )
    )

    /**
     * 根据当前步骤中选择的变量类型，动态定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        if (step == null) return emptyList()

        val selectedType = step.parameters["type"] as? String
        return when (selectedType) {
            "文本" -> listOf(OutputDefinition("variable", "变量 (文本)", TextVariable.TYPE_NAME))
            "数字" -> listOf(OutputDefinition("variable", "变量 (数字)", NumberVariable.TYPE_NAME))
            "布尔" -> listOf(OutputDefinition("variable", "变量 (布尔)", BooleanVariable.TYPE_NAME))
            "字典" -> listOf(OutputDefinition("variable", "变量 (字典)", DictionaryVariable.TYPE_NAME))
//            "图像" -> listOf(OutputDefinition("variable", "变量 (图像)", ImageVariable.TYPE_NAME))
            else -> emptyList()
        }
    }

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]

        val typePill = PillUtil.createPillFromParam(
            type,
            inputs.find { it.id == "type" },
            isModuleOption = true
        )

        val valuePillText = when (type) {
            "布尔" -> value?.toString() ?: "false"
            "字典" -> "{...}"
            "图像" -> "[图像]"
            else -> null // 其他类型让 createPillFromParam 自己处理
        }

        val valuePill = if (valuePillText != null) {
            PillUtil.Pill(valuePillText, false, "value")
        } else {
            PillUtil.createPillFromParam(value, inputs.find { it.id == "value" })
        }

        return PillUtil.buildSpannable(
            context,
            "设置变量 ",
            typePill,
            " 为 ",
            valuePill
        )
    }

    /**
     * 执行模块逻辑：根据用户输入的类型和值，创建一个相应的变量实例。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        val variable: Parcelable = when (type) {
            "数字" -> {
                val numValue = (value as? String)?.toDoubleOrNull() ?: (value as? Number)?.toDouble() ?: 0.0
                NumberVariable(numValue)
            }
            "布尔" -> BooleanVariable(value as? Boolean ?: false)
            "字典" -> {
                val dictValue = (value as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
                DictionaryVariable(dictValue)
            }
            "图像" -> ImageVariable(value?.toString() ?: "")
            else -> TextVariable(value?.toString() ?: "")
        }
        onProgress(ProgressUpdate("设置变量 ($type) 完成"))
        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}
