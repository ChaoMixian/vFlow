// 文件: main/java/com/chaomixian/vflow/core/pill/PillFormatter.kt
// 描述: Pill文本格式化器，纯逻辑，无UI依赖
package com.chaomixian.vflow.core.pill

import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable

/**
 * Pill文本格式化器（无UI依赖）
 *
 * 负责将参数值转换为Pill数据结构，包含格式化逻辑但无Android依赖。
 * 这个类是纯Kotlin代码，可以在任何平台上使用。
 */
object PillFormatter {

    /**
     * 从参数值创建Pill
     *
     * 这是核心模块创建Pill的标准方法。它会：
     * 1. 检测值是否为变量引用（魔法变量或命名变量）
     * 2. 如果是变量，直接使用引用字符串作为文本
     * 3. 如果是静态值，格式化显示（如数字格式化）
     *
     * @param paramValue 步骤中存储的原始参数值
     * @param inputDef 该参数的输入定义，用于获取默认值和ID
     * @param type Pill类型，默认为PARAMETER
     * @return 配置好的Pill对象
     */
    fun createPillFromParam(
        paramValue: Any?,
        inputDef: InputDefinition?,
        type: PillType = PillType.PARAMETER
    ): Pill {
        val paramStr = paramValue?.toString()
        val text: String = if (isVariableReference(paramStr)) {
            // 如果是变量，直接使用引用字符串作为文本
            paramStr!!
        } else {
            // 否则，格式化静态值
            formatStaticValue(paramValue, inputDef?.defaultValue)
        }
        return Pill(text, inputDef?.id ?: "", type)
    }

    /**
     * 格式化静态值
     *
     * 处理非变量类型的值，特别是数字格式化。
     *
     * @param value 要格式化的值
     * @param defaultValue 默认值（当value为null时使用）
     * @return 格式化后的字符串
     */
    private fun formatStaticValue(value: Any?, defaultValue: Any?): String {
        val valueToFormat = value ?: defaultValue
        return when (valueToFormat) {
            is Number -> formatNumber(valueToFormat)
            else -> valueToFormat?.toString() ?: "..."
        }
    }

    /**
     * 格式化数字
     *
     * 整数不显示小数点，浮点数保留2位小数。
     *
     * @param number 要格式化的数字
     * @return 格式化后的字符串
     */
    private fun formatNumber(number: Number): String {
        val doubleValue = number.toDouble()
        val longValue = number.toLong()
        return if (doubleValue == longValue.toDouble()) {
            // 整数：不显示小数点
            longValue.toString()
        } else {
            // 浮点数：保留2位小数
            String.format("%.2f", doubleValue)
        }
    }

    /**
     * 检测是否为变量引用
     *
     * 变量引用包括：
     * - 魔法变量：{{stepId.outputName}}
     * - 命名变量：[[variableName]]
     *
     * @param text 要检测的文本
     * @return 是否为变量引用
     */
    private fun isVariableReference(text: String?): Boolean {
        return text.isMagicVariable() || text.isNamedVariable()
    }
}
