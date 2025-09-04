// 文件: main/java/com/chaomixian/vflow/core/workflow/module/variable/VariableModule.kt

package com.chaomixian.vflow.modules.variable

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class TextVariable(val value: String) : Parcelable {
    companion object { const val TYPE_NAME = "vflow.type.text" }
}
@Parcelize
data class NumberVariable(val value: Double) : Parcelable {
    companion object { const val TYPE_NAME = "vflow.type.number" }
}
@Parcelize
data class BooleanVariable(val value: Boolean) : Parcelable {
    companion object { const val TYPE_NAME = "vflow.type.boolean" }
}
@Parcelize
data class ListVariable(val value: @RawValue List<Any?>) : Parcelable {
    companion object { const val TYPE_NAME = "vflow.type.list" }
}
@Parcelize
data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable {
    companion object { const val TYPE_NAME = "vflow.type.dictionary" }
}

class SetVariableModule : BaseModule() {
    override val id = "vflow.variable.set"
    override val metadata = ActionMetadata("设置变量", "创建文本、数字、布尔值等变量", R.drawable.rounded_data_object_24, "变量")

    private val typeOptions = listOf("文本", "数字", "布尔", "字典")
    override val uiProvider = VariableModuleUIProvider(typeOptions)

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
            acceptsMagicVariable = false // 注意：此模块的值不支持魔法变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        if (step == null) return emptyList()

        val selectedType = step.parameters["type"] as? String
        return when (selectedType) {
            "文本" -> listOf(OutputDefinition("variable", "变量 (文本)", TextVariable.TYPE_NAME))
            "数字" -> listOf(OutputDefinition("variable", "变量 (数字)", NumberVariable.TYPE_NAME))
            "布尔" -> listOf(OutputDefinition("variable", "变量 (布爾)", BooleanVariable.TYPE_NAME))
            "字典" -> listOf(OutputDefinition("variable", "变量 (字典)", DictionaryVariable.TYPE_NAME))
            else -> emptyList()
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]

        // 根据变量类型格式化要显示的文本
        val valuePillText = when (type) {
            "布尔" -> value?.toString() ?: "false"
            "字典" -> "{...}"
            "数字" -> when (value) {
                is Number -> {
                    if (value.toDouble() == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    }
                }
                else -> value?.toString() ?: "..."
            }
            // "文本" 和其他类型
            else -> when {
                value is String && value.isNotEmpty() -> "'$value'"
                value != null && value.toString().isNotEmpty() -> value.toString()
                else -> "..."
            }
        }

        return PillUtil.buildSpannable(
            context,
            "设置变量 ",
            PillUtil.Pill(type, false, parameterId = "type", isModuleOption = true),
            " 为 ",
            PillUtil.Pill(valuePillText, false, parameterId = "value")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        val variable: Parcelable = when (type) {
            "数字" -> NumberVariable((value as? String)?.toDoubleOrNull() ?: (value as? Number ?: 0.0).toDouble())
            "布尔" -> BooleanVariable(value as? Boolean ?: false)
            "字典" -> DictionaryVariable((value as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap())
            else -> TextVariable(value?.toString() ?: "")
        }
        onProgress(ProgressUpdate("设置变量 ($type) 完成"))
        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}