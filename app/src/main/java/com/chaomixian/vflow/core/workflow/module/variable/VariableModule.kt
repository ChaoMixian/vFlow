// main/java/com/chaomixian/vflow/core/workflow/module/variable/VariableModule.kt

package com.chaomixian.vflow.modules.variable

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 1. 定义模块化的、可传递的变量类型
@Parcelize data class TextVariable(val value: String) : Parcelable
@Parcelize data class NumberVariable(val value: Double) : Parcelable
@Parcelize data class BooleanVariable(val value: Boolean) : Parcelable
@Parcelize data class ListVariable(val value: @RawValue List<Any?>) : Parcelable
@Parcelize data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable

/**
 * 设置变量模块
 */
class SetVariableModule : ActionModule {
    override val id = "vflow.variable.set"
    override val metadata = ActionMetadata("设置变量", "创建文本、数字、布尔值等变量", R.drawable.ic_variable, "变量")

    private val typeOptions = listOf("文本", "数字", "布尔", "字典")

    // 委托所有UI逻辑
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
            acceptsMagicVariable = false // 值在这个模块中是静态设置的
        )
    )

    override fun getOutputs(): List<OutputDefinition> = emptyList()

    override fun getDynamicOutputs(step: ActionStep): List<OutputDefinition> {
        val selectedType = step.parameters["type"] as? String
        return when (selectedType) {
            "文本" -> listOf(OutputDefinition("variable", "变量 (文本)", TextVariable::class.java))
            "数字" -> listOf(OutputDefinition("variable", "变量 (数字)", NumberVariable::class.java))
            "布尔" -> listOf(OutputDefinition("variable", "变量 (布尔)", BooleanVariable::class.java))
            "字典" -> listOf(OutputDefinition("variable", "变量 (字典)", DictionaryVariable::class.java))
            else -> emptyList()
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ActionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        val variable: Parcelable = when (type) {
            "数字" -> NumberVariable((value as? String)?.toDoubleOrNull() ?: (value as? Number ?: 0.0).toDouble())
            "布尔" -> BooleanVariable(value as? Boolean ?: false)
            "字典" -> DictionaryVariable(value as? Map<String, Any?> ?: emptyMap())
            else -> TextVariable(value?.toString() ?: "")
        }
        return ActionResult(true, mapOf("variable" to variable))
    }
}