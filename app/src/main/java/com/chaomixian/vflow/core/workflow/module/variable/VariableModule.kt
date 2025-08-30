package com.chaomixian.vflow.modules.variable

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// --- 1. 定义模块化的、可传递的变量类型 ---
@Parcelize data class TextVariable(val value: String) : Parcelable
@Parcelize data class NumberVariable(val value: Double) : Parcelable
@Parcelize data class BooleanVariable(val value: Boolean) : Parcelable
@Parcelize data class ListVariable(val value: @RawValue List<Any?>) : Parcelable
@Parcelize data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable


/**
 * 设置变量模块（已重构）
 * 职责：根据用户选择的类型，创建并输出一个强类型的变量。
 */
class SetVariableModule : ActionModule {
    override val id = "vflow.variable.set"
    override val metadata = ActionMetadata("设置变量", "创建文本、数字、布尔值等变量", R.drawable.ic_variable, "变量")

    // 这个模块没有输入，它的值是在编辑器内定义的
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(): List<OutputDefinition> = listOf(
        // 输出一个通用的变量，具体类型在执行时确定
        OutputDefinition("variable", "变量", Parcelable::class.java)
    )

    // --- 2. 使用静态参数让用户在UI上选择类型和赋值 ---
    override fun getParameters(): List<ParameterDefinition> = listOf(
        ParameterDefinition(
            id = "type",
            name = "变量类型",
            type = ParameterType.ENUM,
            defaultValue = "文本",
            options = listOf("文本", "数字", "布尔", "列表", "字典")
        ),
        // "value" 参数将由 ActionEditorSheet 根据所选类型动态处理
        ParameterDefinition("value", "值", ParameterType.STRING, defaultValue = "")
    )

    override suspend fun execute(context: ExecutionContext): ActionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        // --- 3. 根据类型创建对应的变量对象 ---
        val variable: Parcelable = when (type) {
            "数字" -> NumberVariable((value as? String)?.toDoubleOrNull() ?: 0.0)
            "布尔" -> BooleanVariable((value as? String)?.toBooleanStrictOrNull() ?: false)
            // 列表和字典的解析会更复杂，暂时简化处理
            // "列表" -> ListVariable(...)
            // "字典" -> DictionaryVariable(...)
            else -> TextVariable(value?.toString() ?: "") // 默认为文本
        }

        return ActionResult(true, mapOf("variable" to variable))
    }
}