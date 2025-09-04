package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 文件：VariableModule.kt
// 描述：定义了核心的变量数据类型及其伴生的类型名称常量，
//      以及用于在工作流中设置这些变量的 SetVariableModule。

/**
 * 表示文本类型的变量。
 * @param value 变量的字符串值。
 */
@Parcelize
data class TextVariable(val value: String) : Parcelable {
    companion object {
        /** 文本变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.text"
    }
}

/**
 * 表示数字类型的变量。
 * @param value 变量的 Double 值。
 */
@Parcelize
data class NumberVariable(val value: Double) : Parcelable {
    companion object {
        /** 数字变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.number"
    }
}

/**
 * 表示布尔类型的变量。
 * @param value 变量的布尔值。
 */
@Parcelize
data class BooleanVariable(val value: Boolean) : Parcelable {
    companion object {
        /** 布尔变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.boolean"
    }
}

/**
 * 表示列表类型的变量。
 * @param value 变量的 List 值，列表元素可以是任意受支持的类型。
 */
@Parcelize
data class ListVariable(val value: @RawValue List<Any?>) : Parcelable {
    companion object {
        /** 列表变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.list" 
    }
}

/**
 * 表示字典（Map）类型的变量。
 * @param value 变量的 Map 值，键为 String，值可以是任意受支持的类型。
 */
@Parcelize
data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable {
    companion object {
        /** 字典变量的唯一类型标识符。 */
        const val TYPE_NAME = "vflow.type.dictionary"
    }
}

/**
 * “设置变量”模块。
 * 允许用户在工作流中定义一个变量，并指定其类型和初始值。
 * 该变量随后可以被其他模块作为魔法变量使用。
 */
class SetVariableModule : BaseModule() {
    // 模块的唯一ID
    override val id = "vflow.variable.set"
    // 模块的元数据，用于在UI中展示
    override val metadata = ActionMetadata("设置变量", "创建文本、数字、布尔值等变量", R.drawable.rounded_data_object_24, "数据")

    // 定义变量类型下拉框的选项
    private val typeOptions = listOf("文本", "数字", "布尔", "字典")
    // 提供自定义编辑器UI的实现
    override val uiProvider: ModuleUIProvider? = VariableModuleUIProvider(typeOptions)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "type",
            name = "变量类型",
            staticType = ParameterType.ENUM, // 参数类型：枚举，通过下拉框选择
            defaultValue = "文本",
            options = typeOptions, // 下拉框的选项列表
            acceptsMagicVariable = false // 类型选择本身不接受魔法变量
        ),
        InputDefinition(
            id = "value",
            name = "值",
            staticType = ParameterType.ANY, // 参数类型：任意，具体输入控件由 uiProvider 根据 "type" 动态生成
            defaultValue = "", // 默认值，通常为空字符串或对应类型的默认
            acceptsMagicVariable = false // 此模块的值设置不支持从魔法变量读取，因为其目的是定义新变量
        )
    )

    /**
     * 根据当前步骤中选择的变量类型，动态定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        if (step == null) return emptyList()

        val selectedType = step.parameters["type"] as? String // 获取用户选择的变量类型
        // 根据选择的类型，确定输出变量的类型名称
        return when (selectedType) {
            "文本" -> listOf(OutputDefinition("variable", "变量 (文本)", TextVariable.TYPE_NAME))
            "数字" -> listOf(OutputDefinition("variable", "变量 (数字)", NumberVariable.TYPE_NAME))
            "布尔" -> listOf(OutputDefinition("variable", "变量 (布尔)", BooleanVariable.TYPE_NAME))
            "字典" -> listOf(OutputDefinition("variable", "变量 (字典)", DictionaryVariable.TYPE_NAME))
            else -> emptyList() // 如果类型未知或未选择，则无输出
        }
    }

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如：“设置变量 [文本] 为 [‘示例值’]”
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]

        // 根据变量类型格式化值的显示文本
        val valuePillText = when (type) {
            "布尔" -> value?.toString() ?: "false"
            "字典" -> "{...}" // 字典通常显示为占位符，避免摘要过长
            "数字" -> when (value) {
                is Number -> { // 格式化数字，整数不显示小数点
                    if (value.toDouble() == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString() // 或者 String.format("%.2f", value.toDouble()) 保留特定小数位
                    }
                }
                else -> value?.toString() ?: "..." // 如果不是数字，显示原始文本或占位符
            }
            // "文本" 和其他未知类型
            else -> when {
                value is String && value.isNotEmpty() -> "'$value'" // 非空字符串加上引号
                value != null && value.toString().isNotEmpty() -> value.toString()
                else -> "..." // 空值或无法识别的值显示占位符
            }
        }

        // 使用 PillUtil 构建带样式的摘要
        return PillUtil.buildSpannable(
            context,
            "设置变量 ",
            PillUtil.Pill(type, false, parameterId = "type", isModuleOption = true), // 类型药丸
            " 为 ",
            PillUtil.Pill(valuePillText, false, parameterId = "value") // 值药丸
        )
    }

    /**
     * 执行模块逻辑：根据用户输入的类型和值，创建一个相应的变量实例。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 从上下文的静态变量中获取用户设定的类型和值
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        // 根据选定的类型创建相应的变量 Parcelable 对象
        val variable: Parcelable = when (type) {
            "数字" -> {
                // 对数字进行类型转换和空值处理
                val numValue = (value as? String)?.toDoubleOrNull() ?: (value as? Number)?.toDouble() ?: 0.0
                NumberVariable(numValue)
            }
            "布尔" -> BooleanVariable(value as? Boolean ?: false)
            "字典" -> {
                // 对字典的键进行转换，确保为 String 类型
                val dictValue = (value as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
                DictionaryVariable(dictValue)
            }
            else -> TextVariable(value?.toString() ?: "") // 默认为文本变量
        }
        onProgress(ProgressUpdate("设置变量 ($type) 完成"))
        // 成功执行，输出创建的变量
        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}