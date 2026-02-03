// 文件: main/java/com/chaomixian/vflow/core/workflow/module/logic/ConditionEvaluator.kt
package com.chaomixian.vflow.core.workflow.module.logic

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.*
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * 条件评估工具对象。
 * 封装了在 'If' 和 'While' 模块中用于判断条件的通用逻辑。
 *
 * 设计原则（统一包装 - Python 风格）：
 * - 所有输入值都应该是 VObject
 * - 不再直接处理 String/Number/Boolean 等 Kotlin 原生类型
 * - 等于/不等于：弱类型比较，自动类型转换
 * - 严格等于：强类型比较，类型和值都必须相同
 * - 大于/小于/介于：数字比较
 * - 为空/不为空：集合或文本通用
 * - 包含/开头是/结尾是/匹配正则：文本比较
 * - 为真/为假：布尔比较
 */
object ConditionEvaluator {

    /**
     * 根据输入、操作符和比较值评估条件。
     * @param input1 主输入值（必须是 VObject 或会被自动包装）
     * @param operator 操作符字符串
     * @param value1 第一个比较值（必须是 VObject 或会被自动包装）
     * @param value2 第二个比较值 (仅用于 '介于' 操作)
     * @return 条件评估的布尔结果
     */
    fun evaluateCondition(input1: Any?, operator: String, value1: Any?, value2: Any?): Boolean {
        // 特殊处理：Kotlin null 视为"不存在"
        // 注意：这必须在包装之前检查，因为包装后 null 会变成 VNull
        if (input1 == null) {
            return when (operator) {
                OP_EXISTS -> false
                OP_NOT_EXISTS -> true
                else -> false
            }
        }

        // 统一包装为 VObject
        val vInput1 = VObjectFactory.from(input1)
        val vValue1 = VObjectFactory.from(value1)
        val vValue2 = VObjectFactory.from(value2)

        return evaluateConditionVObject(vInput1, operator, vValue1, vValue2)
    }

    /**
     * 核心评估逻辑 - 只处理 VObject
     */
    private fun evaluateConditionVObject(input1: VObject, operator: String, value1: VObject, value2: VObject): Boolean {
        // OP_EXISTS 和 OP_NOT_EXISTS 只检查对象本身是否存在
        // VNull 是对象实例，视为"存在"
        when (operator) {
            OP_EXISTS -> return true  // 只有 Kotlin null 视为不存在，而 Kotlin null 已经在 evaluateCondition 中处理
            OP_NOT_EXISTS -> return false  // VObject 永远"存在"
        }

        // 处理等于/不等于操作符（弱类型，PHP == 风格）
        if (operator == OP_EQUALS || operator == OP_NOT_EQUALS) {
            val result = looseEquals(input1, value1)
            return if (operator == OP_EQUALS) result else !result
        }

        // 处理严格等于操作符（强类型，PHP === 风格）
        if (operator == OP_STRICT_EQUALS) {
            return strictEquals(input1, value1)
        }

        // 主输入为 VNull 时，其他操作符条件不成立
        if (input1 is VNull) return false
        if (isNumberOperator(operator)) {
            val num1 = input1.asNumber()
            if (num1 != null) {
                return evaluateNumberCondition(num1, operator, value1.asNumber(), value2.asNumber())
            }
            // 无法转为数字时，条件不成立
            return false
        }

        // 处理布尔操作符
        if (operator == OP_IS_TRUE || operator == OP_IS_FALSE) {
            // 对于字符串，使用 normalizeToBoolean 进行更精确的检查
            if (input1 is VString) {
                val normalized = normalizeToBoolean(input1.raw)
                if (normalized != null) {
                    return when (operator) {
                        OP_IS_TRUE -> normalized
                        OP_IS_FALSE -> !normalized
                        else -> false
                    }
                }
                // 非布尔字符串返回 false
                return when (operator) {
                    OP_IS_TRUE -> false
                    OP_IS_FALSE -> true  // 非布尔字符串被视为"不为真"，所以"为假"返回 true
                    else -> false
                }
            }
            val bool1 = input1.asBoolean()
            return when (operator) {
                OP_IS_TRUE -> bool1
                OP_IS_FALSE -> !bool1
                else -> false
            }
        }

        // 处理为空/不为空操作符（适用于文本和集合）
        if (operator == OP_IS_EMPTY || operator == OP_IS_NOT_EMPTY) {
            val isEmpty = when (input1) {
                is VList -> input1.raw.isEmpty()
                is VDictionary -> input1.raw.isEmpty()
                else -> input1.asString().isEmpty() // 文本或其他类型
            }
            return when (operator) {
                OP_IS_EMPTY -> isEmpty
                OP_IS_NOT_EMPTY -> !isEmpty
                else -> false
            }
        }

        // 处理文本操作符
        if (isTextOperator(operator)) {
            val text1 = input1.asString()
            val text2 = value1.asString()
            return evaluateTextCondition(text1, operator, text2)
        }

        // 默认返回 false
        return false
    }

    // ==================== 核心比较逻辑 ====================

    /**
     * 弱类型等于比较（PHP == 风格）。
     * 自动进行类型转换后比较。
     *
     * 规则：
     * 1. VNull == VNull → true
     * 2. 双方都能转成数字 → 数字比较
     * 3. 否则 → 文本比较（忽略大小写）
     */
    private fun looseEquals(input1: VObject, input2: VObject): Boolean {
        // 处理 VNull - VNull 只与 VNull 或空集合/空字符串相等，不与数字类型相等
        val isNull1 = input1 is VNull
        val isNull2 = input2 is VNull
        if (isNull1 && isNull2) return true

        // VNull 与非 VNull 比较：VNull 只与空集合/空字符串相等，不与数字类型相等
        if (isNull1 || isNull2) {
            // 如果非空值是数字类型，则不相等（VNull 不等于任何数字）
            // 注意：不能用 asNumber() != null 来判断，因为 VList.asNumber() 返回列表长度
            if (isNull1 && input2 is VNumber) return false
            if (isNull2 && input1 is VNumber) return false
            // 检查非空值是否为空集合/空字符串
            val nonNullInput = if (isNull1) input2 else input1
            if (!isEmptyValue(nonNullInput)) return false
            return true
        }

        // 特殊处理：空字符串 == 0
        val str1 = input1.asString()
        val str2 = input2.asString()
        if (str1.isEmpty() || str2.isEmpty()) {
            // 尝试转为数字比较
            val num1 = if (str1.isEmpty()) 0.0 else input1.asNumber()
            val num2 = if (str2.isEmpty()) 0.0 else input2.asNumber()
            if (num1 != null && num2 != null) {
                return num1 == num2
            }
            // 如果有任一个是空字符串且无法转数字，回退到空值比较规则
            if (str1.isEmpty() || str2.isEmpty()) {
                val empty1 = str1.isEmpty() || input1 is VList || input1 is VDictionary
                val empty2 = str2.isEmpty() || input2 is VList || input2 is VDictionary
                return empty1 || empty2
            }
        }

        // 尝试转为数字比较
        val num1 = input1.asNumber()
        val num2 = input2.asNumber()
        if (num1 != null && num2 != null) {
            return num1 == num2
        }

        // 尝试布尔比较（如果任意一个是布尔）
        val bool1 = input1.asBoolean()
        val bool2 = input2.asBoolean()
        // 如果两个值都是布尔类型（true 或 false），进行布尔比较
        // 注意：asBoolean() 总是返回布尔值，但我们需要知道原始类型
        if (isExplicitBoolean(input1) && isExplicitBoolean(input2)) {
            return bool1 == bool2
        }

        // 处理布尔字符串 ("yes", "no", "on", "off") 与布尔值的比较
        val normalized1 = normalizeToBoolean(str1)
        val normalized2 = normalizeToBoolean(str2)
        if (normalized1 != null && normalized2 != null) {
            return normalized1 == normalized2
        }
        if (normalized1 != null && isExplicitBoolean(input2)) {
            return normalized1 == bool2
        }
        if (normalized2 != null && isExplicitBoolean(input1)) {
            return normalized2 == bool1
        }

        // 文本比较（忽略大小写）
        return str1.equals(str2, ignoreCase = true)
    }

    /**
     * 判断是否为显式的布尔值类型
     */
    private fun isExplicitBoolean(vobj: VObject): Boolean {
        return vobj is VBoolean
    }

    /**
     * 将常见布尔字符串转换为布尔值
     */
    private fun normalizeToBoolean(value: String): Boolean? {
        return when (value.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
    }

    /**
     * 判断值是否为"空"（用于 null 比较）
     * 空集合、空字符串视为"空"
     */
    private fun isEmptyValue(value: VObject): Boolean {
        return when (value) {
            is VList -> value.raw.isEmpty()
            is VDictionary -> value.raw.isEmpty()
            is VString -> value.raw.isEmpty()
            else -> false
        }
    }

    /**
     * 严格等于比较（PHP === 风格）。
     * 类型和值都必须相同。
     * 对于 VNumber，比较原始值的同时还要考虑原始类型（Int vs Double）。
     */
    private fun strictEquals(input1: VObject, input2: VObject): Boolean {
        // 处理 VNull
        val isNull1 = input1 is VNull
        val isNull2 = input2 is VNull
        if (isNull1 && isNull2) return true
        if (isNull1 || isNull2) return false

        // 同类型检查
        return when {
            // VString vs VString
            input1 is VString && input2 is VString -> input1.raw == input2.raw

            // VNumber vs VNumber - 需要同时检查类型和值
            input1 is VNumber && input2 is VNumber -> {
                val type1 = input1.getNumberType()
                val type2 = input2.getNumberType()
                val valueEqual = input1.raw.toDouble() == input2.raw.toDouble()
                type1 == type2 && valueEqual
            }

            // VBoolean vs VBoolean
            input1 is VBoolean && input2 is VBoolean -> input1.raw == input2.raw

            // VList vs VList
            input1 is VList && input2 is VList -> input1.raw == input2.raw

            // VDictionary vs VDictionary
            input1 is VDictionary && input2 is VDictionary -> input1.raw == input2.raw

            // 不同 VObject 子类型直接返回 false
            else -> false
        }
    }

    // ==================== 辅助函数 ====================

    /** 判断是否为文本相关操作符 */
    private fun isTextOperator(operator: String): Boolean {
        return operator in listOf(
            OP_CONTAINS, OP_NOT_CONTAINS, OP_STARTS_WITH, OP_ENDS_WITH, OP_MATCHES_REGEX
        )
    }

    /** 判断是否为数字相关操作符 */
    private fun isNumberOperator(operator: String): Boolean {
        return operator in listOf(
            OP_NUM_GT, OP_NUM_GTE, OP_NUM_LT, OP_NUM_LTE, OP_NUM_BETWEEN
        )
    }

    /** 评估数字相关条件。 */
    private fun evaluateNumberCondition(num1: Double, operator: String, num2: Double?, num3: Double?): Boolean {
        if (operator == OP_NUM_BETWEEN) {
            if (num2 == null || num3 == null) return false
            val minVal = min(num2, num3)
            val maxVal = max(num2, num3)
            return num1 >= minVal && num1 <= maxVal
        }
        if (num2 == null) return false
        return when (operator) {
            OP_NUM_GT -> num1 > num2
            OP_NUM_GTE -> num1 >= num2
            OP_NUM_LT -> num1 < num2
            OP_NUM_LTE -> num1 <= num2
            else -> false
        }
    }

    /** 评估文本相关条件。 */
    private fun evaluateTextCondition(text1: String, operator: String, text2: String): Boolean {
        return when (operator) {
            OP_CONTAINS -> text1.contains(text2, ignoreCase = true)
            OP_NOT_CONTAINS -> !text1.contains(text2, ignoreCase = true)
            OP_STARTS_WITH -> text1.startsWith(text2, ignoreCase = true)
            OP_ENDS_WITH -> text1.endsWith(text2, ignoreCase = true)
            OP_MATCHES_REGEX -> try { Pattern.compile(text2).matcher(text1).find() } catch (e: Exception) { false }
            else -> false
        }
    }
}
