// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/ConditionEvaluator.kt
package com.chaomixian.vflow.core.workflow.module.logic

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.module.ScreenElement
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * 条件评估工具对象。
 * 封装了在 'If' 和 'While' 模块中用于判断条件的通用逻辑。
 */
object ConditionEvaluator {

    /**
     * 根据输入、操作符和比较值评估条件。
     * @param input1 主输入值。
     * @param operator 操作符字符串。
     * @param value1 第一个比较值。
     * @param value2 第二个比较值 (仅用于 '介于' 操作)。
     * @return 条件评估的布尔结果。
     */
    fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        when (operator) {
            OP_EXISTS -> return input1 != null
            OP_NOT_EXISTS -> return input1 == null
        }
        if (input1 == null) return false // 对于其他操作符，主输入为null则条件不成立

        // 根据主输入的数据类型，调用相应的评估函数
        return when (input1) {
            is TextVariable, is String -> evaluateTextCondition(input1.toStringValue(), operator, value1)
            is BooleanVariable, is Boolean -> evaluateBooleanCondition(input1.toBooleanValue(), operator)
            is ListVariable, is Collection<*> -> evaluateCollectionCondition(input1, operator)
            is DictionaryVariable, is Map<*, *> -> evaluateMapCondition(input1, operator)
            is NumberVariable, is Number -> {
                val value = input1.toDoubleValue() ?: return false // 数字转换失败则条件不成立
                evaluateNumberCondition(value, operator, value1, value2)
            }
            is ScreenElement -> {
                val text = input1.text ?: return false // 屏幕元素无文本则条件不成立 (除非是EXISTS/NOT_EXISTS)
                evaluateTextCondition(text, operator, value1)
            }
            else -> false
        }
    }

    /** 评估文本相关条件。 */
    private fun evaluateTextCondition(text1: String, operator: String, value1: Any?): Boolean {
        val text2 = value1.toStringValue() // 获取比较值文本
        return when (operator) {
            OP_IS_EMPTY -> text1.isEmpty()
            OP_IS_NOT_EMPTY -> text1.isNotEmpty()
            OP_TEXT_EQUALS -> text1.equals(text2, ignoreCase = true)
            OP_TEXT_NOT_EQUALS -> !text1.equals(text2, ignoreCase = true)
            OP_CONTAINS -> text1.contains(text2, ignoreCase = true)
            OP_NOT_CONTAINS -> !text1.contains(text2, ignoreCase = true)
            OP_STARTS_WITH -> text1.startsWith(text2, ignoreCase = true)
            OP_ENDS_WITH -> text1.endsWith(text2, ignoreCase = true)
            OP_MATCHES_REGEX -> try { Pattern.compile(text2).matcher(text1).find() } catch (e: Exception) { false }
            else -> false
        }
    }

    /** 评估数字相关条件。 */
    private fun evaluateNumberCondition(num1: Double, operator: String, value1: Any?, value2: Any?): Boolean {
        val num2 = value1.toDoubleValue() // 获取第一个比较值
        if (operator == OP_NUM_BETWEEN) { // "介于" 操作
            val num3 = value2.toDoubleValue() // 获取第二个比较值
            if (num2 == null || num3 == null) return false // 比较值无效则条件不成立
            val minVal = min(num2, num3)
            val maxVal = max(num2, num3)
            return num1 >= minVal && num1 <= maxVal
        }
        if (num2 == null) return false // 其他数字操作，第一个比较值无效则不成立
        return when (operator) {
            OP_NUM_EQ -> num1 == num2
            OP_NUM_NEQ -> num1 != num2
            OP_NUM_GT -> num1 > num2
            OP_NUM_GTE -> num1 >= num2
            OP_NUM_LT -> num1 < num2
            OP_NUM_LTE -> num1 <= num2
            else -> false
        }
    }

    /** 评估布尔相关条件。 */
    private fun evaluateBooleanCondition(bool1: Boolean, operator: String): Boolean {
        return when (operator) {
            OP_IS_TRUE -> bool1
            OP_IS_FALSE -> !bool1
            else -> false
        }
    }

    /** 评估集合（列表）相关条件。 */
    private fun evaluateCollectionCondition(col1: Any, operator: String): Boolean {
        val size = when(col1) {
            is ListVariable -> col1.value.size
            is Collection<*> -> col1.size
            else -> -1 //无法获取大小
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    /** 评估字典（Map）相关条件。 */
    private fun evaluateMapCondition(map1: Any, operator: String): Boolean {
        val size = when(map1) {
            is DictionaryVariable -> map1.value.size
            is Map<*,*> -> map1.size
            else -> -1 //无法获取大小
        }
        return when (operator) {
            OP_IS_EMPTY -> size == 0
            OP_IS_NOT_EMPTY -> size > 0
            else -> false
        }
    }

    // --- 类型转换辅助函数 ---
    /** 将任意类型安全转换为字符串。 */
    private fun Any?.toStringValue(): String {
        return when(this) {
            is TextVariable -> this.value
            else -> this?.toString() ?: ""
        }
    }
    /** 将任意类型安全转换为 Double?。 */
    private fun Any?.toDoubleValue(): Double? {
        return when(this) {
            is NumberVariable -> this.value
            is Number -> this.toDouble()
            is String -> this.toDoubleOrNull()
            else -> null
        }
    }
    /** 将任意类型安全转换为布尔值。 */
    private fun Any?.toBooleanValue(): Boolean {
        return when(this) {
            is BooleanVariable -> this.value
            is Boolean -> this
            else -> false // 默认转换为 false
        }
    }
}