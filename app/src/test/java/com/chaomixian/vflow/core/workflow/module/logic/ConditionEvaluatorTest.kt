// 文件: test/java/com/chaomixian/vflow/core/workflow/module/logic/ConditionEvaluatorTest.kt
package com.chaomixian.vflow.core.workflow.module.logic

import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.*
import org.junit.Assert.*
import org.junit.Test

/**
 * ConditionEvaluator 单元测试
 *
 * 测试策略：
 * 1. 等于/不等于（弱类型）- PHP == 风格
 * 2. 严格等于/严格不等于 - PHP === 风格
 * 3. 数字比较（大于、小于、介于）
 * 4. 为空/不为空
 * 5. 文本操作符（包含、开头是、结尾是、匹配正则）
 * 6. 布尔操作符（为真、为假）
 * 7. 存在/不存在
 */
class ConditionEvaluatorTest {

    // ==================== 等于操作符（弱类型）测试 ====================

    @Test
    fun `equals - number vs string number`() {
        // "42" == 42 -> true (PHP == style)
        assertTrue(ConditionEvaluator.evaluateCondition(42.0, OP_EQUALS, "42", null))
        assertTrue(ConditionEvaluator.evaluateCondition(VNumber(42.0), OP_EQUALS, "42", null))
        assertTrue(ConditionEvaluator.evaluateCondition(42.0, OP_EQUALS, "42", null))
    }

    @Test
    fun `equals - string vs number`() {
        assertTrue(ConditionEvaluator.evaluateCondition("123", OP_EQUALS, 123.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VString("123"), OP_EQUALS, 123.0, null))
    }

    @Test
    fun `equals - float vs string`() {
        assertTrue(ConditionEvaluator.evaluateCondition(3.14, OP_EQUALS, "3.14", null))
        assertTrue(ConditionEvaluator.evaluateCondition("3.14", OP_EQUALS, 3.14, null))
    }

    @Test
    fun `equals - boolean vs string`() {
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_EQUALS, "true", null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_EQUALS, "false", null))
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_EQUALS, "1", null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_EQUALS, "0", null))
    }

    @Test
    fun `equals - boolean vs number`() {
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_EQUALS, 1.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_EQUALS, 0.0, null))
    }

    @Test
    fun `equals - null values`() {
        assertTrue(ConditionEvaluator.evaluateCondition(VNull, OP_EQUALS, VNull, null))
        assertFalse(ConditionEvaluator.evaluateCondition(VNull, OP_EQUALS, 42.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_EQUALS, VNull, null))
    }

    @Test
    fun `equals - text comparison case insensitive`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_EQUALS, "HELLO", null))
        assertTrue(ConditionEvaluator.evaluateCondition("Hello World", OP_EQUALS, "hello world", null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_EQUALS, "world", null))
    }

    @Test
    fun `equals - list comparison`() {
        val list1 = listOf(1.0, 2.0, 3.0)
        val list2 = listOf(1.0, 2.0, 3.0)
        val list3 = listOf(1.0, 2.0)
        assertTrue(ConditionEvaluator.evaluateCondition(list1, OP_EQUALS, list2, null))
        assertFalse(ConditionEvaluator.evaluateCondition(list3, OP_EQUALS, list1, null))
    }

    @Test
    fun `equals - dictionary comparison`() {
        val map1 = mapOf("a" to 1.0, "b" to 2.0)
        val map2 = mapOf("a" to 1.0, "b" to 2.0)
        assertTrue(ConditionEvaluator.evaluateCondition(map1, OP_EQUALS, map2, null))
    }

    @Test
    fun `not equals - loose type`() {
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_EQUALS, 43.0, null))
        // 42.0 != 43.0，所以返回 false（字符串 "43" 转为数字 43）
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_EQUALS, "43", null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_EQUALS, "world", null))
    }

    // ==================== 严格等于测试 ====================

    @Test
    fun `strict equals - same type number`() {
        assertTrue(ConditionEvaluator.evaluateCondition(42.0, OP_STRICT_EQUALS, 42.0, null))
        // int 和 double 类型不同
        assertFalse(ConditionEvaluator.evaluateCondition(42, OP_STRICT_EQUALS, 42.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_STRICT_EQUALS, 42, null))
    }

    @Test
    fun `strict equals - VNumber vs Number`() {
        // VNumber(42.0) === 42.0 -> true (都是 Double)
        assertTrue(ConditionEvaluator.evaluateCondition(VNumber(42.0), OP_STRICT_EQUALS, 42.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(42.0, OP_STRICT_EQUALS, VNumber(42.0), null))
        // VNumber(42.0) !== 42 (Double vs Int)
        assertFalse(ConditionEvaluator.evaluateCondition(VNumber(42.0), OP_STRICT_EQUALS, 42, null))
    }

    @Test
    fun `strict equals - string type comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_STRICT_EQUALS, "hello", null))
        assertTrue(ConditionEvaluator.evaluateCondition(VString("hello"), OP_STRICT_EQUALS, "hello", null))
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_STRICT_EQUALS, VString("hello"), null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_STRICT_EQUALS, "HELLO", null))
    }

    @Test
    fun `strict equals - number vs string different types`() {
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_STRICT_EQUALS, "42", null))
        assertFalse(ConditionEvaluator.evaluateCondition("42", OP_STRICT_EQUALS, 42.0, null))
    }

    @Test
    fun `strict equals - boolean comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_STRICT_EQUALS, true, null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_STRICT_EQUALS, false, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VBoolean(true), OP_STRICT_EQUALS, true, null))
        assertFalse(ConditionEvaluator.evaluateCondition(true, OP_STRICT_EQUALS, 1.0, null))
    }

    @Test
    fun `strict equals - list comparison`() {
        val list1 = VList(listOf(VNumber(1.0), VNumber(2.0), VNumber(3.0)))
        val list2 = VList(listOf(VNumber(1.0), VNumber(2.0), VNumber(3.0)))
        val list3 = VList(listOf(VNumber(1.0), VNumber(2.0)))
        assertTrue(ConditionEvaluator.evaluateCondition(list1, OP_STRICT_EQUALS, list2, null))
        assertTrue(ConditionEvaluator.evaluateCondition(list1, OP_STRICT_EQUALS, list1.raw, null))
        assertFalse(ConditionEvaluator.evaluateCondition(list1, OP_STRICT_EQUALS, list3, null))
    }

    @Test
    fun `strict equals - null comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition(VNull, OP_STRICT_EQUALS, VNull, null))
        assertFalse(ConditionEvaluator.evaluateCondition(VNull, OP_STRICT_EQUALS, 42.0, null))
    }

    @Test
    fun `strict equals - VString vs VNumber different types`() {
        assertFalse(ConditionEvaluator.evaluateCondition(VString("42"), OP_STRICT_EQUALS, VNumber(42.0), null))
    }

    // ==================== 数字比较测试 ====================

    @Test
    fun `greater than - normal comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_GT, 5.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(10.5, OP_NUM_GT, 10.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_GT, 10.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_GT, 10.0, null))
    }

    @Test
    fun `greater than - cannot convert to number returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_NUM_GT, 10.0, null))
    }

    @Test
    fun `greater than or equal`() {
        assertTrue(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_GTE, 10.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_GTE, 5.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_GTE, 10.0, null))
    }

    @Test
    fun `less than`() {
        assertTrue(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_LT, 10.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_LT, 5.0, null))
    }

    @Test
    fun `less than or equal`() {
        assertTrue(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_LTE, 10.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_LTE, 10.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_LTE, 5.0, null))
    }

    @Test
    fun `between - normal case`() {
        assertTrue(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_BETWEEN, 1.0, 10.0))
        assertTrue(ConditionEvaluator.evaluateCondition(1.0, OP_NUM_BETWEEN, 1.0, 10.0)) // boundary inclusive
        assertTrue(ConditionEvaluator.evaluateCondition(10.0, OP_NUM_BETWEEN, 1.0, 10.0)) // boundary inclusive
        assertFalse(ConditionEvaluator.evaluateCondition(0.0, OP_NUM_BETWEEN, 1.0, 10.0))
        assertFalse(ConditionEvaluator.evaluateCondition(11.0, OP_NUM_BETWEEN, 1.0, 10.0))
    }

    @Test
    fun `between - reverse range still works`() {
        assertTrue(ConditionEvaluator.evaluateCondition(5.0, OP_NUM_BETWEEN, 10.0, 1.0))
    }

    @Test
    fun `between - float`() {
        assertTrue(ConditionEvaluator.evaluateCondition(3.14, OP_NUM_BETWEEN, 3.0, 4.0))
    }

    @Test
    fun `between - cannot convert to number returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_NUM_BETWEEN, 1.0, 10.0))
    }

    // ==================== 为空/不为空测试 ====================

    @Test
    fun `is empty - empty string`() {
        assertTrue(ConditionEvaluator.evaluateCondition("", OP_IS_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VString(""), OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(" ", OP_IS_EMPTY, null, null)) // space is not empty
    }

    @Test
    fun `is empty - non-empty string`() {
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(VString("world"), OP_IS_EMPTY, null, null))
    }

    @Test
    fun `is empty - empty list`() {
        val emptyVList = VList(emptyList<VObject>())
        assertTrue(ConditionEvaluator.evaluateCondition(emptyList<Double>(), OP_IS_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(emptyVList, OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(VList(listOf(VNumber(1.0), VNumber(2.0))), OP_IS_EMPTY, null, null))
    }

    @Test
    fun `is empty - empty dictionary`() {
        assertTrue(ConditionEvaluator.evaluateCondition(emptyMap<String, Any>(), OP_IS_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VDictionary(emptyMap()), OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(mapOf("a" to 1), OP_IS_EMPTY, null, null))
    }

    @Test
    fun `is not empty - reverse test`() {
        assertFalse(ConditionEvaluator.evaluateCondition("", OP_IS_NOT_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_IS_NOT_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(listOf(1.0), OP_IS_NOT_EMPTY, null, null))
    }

    @Test
    fun `is empty - number 0 is not empty`() {
        assertFalse(ConditionEvaluator.evaluateCondition(0.0, OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(0.0, OP_IS_EMPTY, null, null))
    }

    // ==================== 文本操作符测试 ====================

    @Test
    fun `contains - normal case`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello world", OP_CONTAINS, "world", null))
        assertTrue(ConditionEvaluator.evaluateCondition("Hello World", OP_CONTAINS, "world", null)) // case insensitive
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_CONTAINS, "xyz", null))
    }

    @Test
    fun `not contains`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_NOT_CONTAINS, "xyz", null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello world", OP_NOT_CONTAINS, "world", null))
    }

    @Test
    fun `starts with`() {
        assertTrue(ConditionEvaluator.evaluateCondition("Hello World", OP_STARTS_WITH, "hello", null))
        assertTrue(ConditionEvaluator.evaluateCondition("Hello", OP_STARTS_WITH, "He", null))
        assertFalse(ConditionEvaluator.evaluateCondition("World", OP_STARTS_WITH, "He", null))
    }

    @Test
    fun `ends with`() {
        assertTrue(ConditionEvaluator.evaluateCondition("Hello World", OP_ENDS_WITH, "World", null))
        assertTrue(ConditionEvaluator.evaluateCondition("test.txt", OP_ENDS_WITH, ".txt", null))
        assertFalse(ConditionEvaluator.evaluateCondition("test.txt", OP_ENDS_WITH, ".png", null))
    }

    @Test
    fun `matches regex - normal case`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello123", OP_MATCHES_REGEX, "\\d+", null))
        assertTrue(ConditionEvaluator.evaluateCondition("abc@email.com", OP_MATCHES_REGEX, "[a-z]+@[a-z]+\\.[a-z]+", null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_MATCHES_REGEX, "\\d+", null))
    }

    @Test
    fun `matches regex - invalid regex returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition("test", OP_MATCHES_REGEX, "[invalid(", null))
    }

    @Test
    fun `matches regex - empty input returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition("", OP_MATCHES_REGEX, ".+", null))
    }

    // ==================== 布尔操作符测试 ====================

    @Test
    fun `is true - boolean true`() {
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_IS_TRUE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VBoolean(true), OP_IS_TRUE, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(false, OP_IS_TRUE, null, null))
    }

    @Test
    fun `is true - string true false`() {
        assertTrue(ConditionEvaluator.evaluateCondition("true", OP_IS_TRUE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition("TRUE", OP_IS_TRUE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition("1", OP_IS_TRUE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition("yes", OP_IS_TRUE, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition("false", OP_IS_TRUE, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_IS_TRUE, null, null))
    }

    @Test
    fun `is false - normal case`() {
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_IS_FALSE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VBoolean(false), OP_IS_FALSE, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(true, OP_IS_FALSE, null, null))
    }

    @Test
    fun `is false - number 0`() {
        assertTrue(ConditionEvaluator.evaluateCondition(0.0, OP_IS_FALSE, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(0.0, OP_IS_FALSE, null, null))
    }

    @Test
    fun `is false - non-zero number returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition(1.0, OP_IS_FALSE, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition(-1.0, OP_IS_FALSE, null, null))
    }

    // ==================== 存在/不存在测试 ====================

    @Test
    fun `exists - non null value`() {
        assertTrue(ConditionEvaluator.evaluateCondition(42.0, OP_EXISTS, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition("hello", OP_EXISTS, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VString("test"), OP_EXISTS, null, null))
    }

    @Test
    fun `exists - null value returns false`() {
        // Kotlin null 不存在
        assertFalse(ConditionEvaluator.evaluateCondition(null, OP_EXISTS, null, null))
        // VNull 是对象实例，视为"存在"
        assertTrue(ConditionEvaluator.evaluateCondition(VNull, OP_EXISTS, null, null))
    }

    @Test
    fun `not exists - null value`() {
        // Kotlin null 不存在
        assertTrue(ConditionEvaluator.evaluateCondition(null, OP_NOT_EXISTS, null, null))
        // VNull 是对象实例，视为"存在"
        assertFalse(ConditionEvaluator.evaluateCondition(VNull, OP_NOT_EXISTS, null, null))
    }

    @Test
    fun `not exists - non null value returns false`() {
        assertFalse(ConditionEvaluator.evaluateCondition(42.0, OP_NOT_EXISTS, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition("", OP_NOT_EXISTS, null, null))
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun `empty string vs 0`() {
        // "" == 0 (PHP rule)
        assertTrue(ConditionEvaluator.evaluateCondition("", OP_EQUALS, 0.0, null))
    }

    @Test
    fun `number vs unparseable string`() {
        // "hello" == 0 在 PHP 8.0+ 返回 false（无法解析为数字的字符串转为 0 的规则已改变）
        // 但在弱类型模式下，我们遵循更简单的规则：无法转数字时回退到文本比较
        // "" 转数字失败，回退到文本比较 "" == "hello" -> false
        assertFalse(ConditionEvaluator.evaluateCondition("hello", OP_EQUALS, 0.0, null))
    }

    @Test
    fun `negative number vs string`() {
        assertTrue(ConditionEvaluator.evaluateCondition(-5.0, OP_EQUALS, "-5", null))
        assertTrue(ConditionEvaluator.evaluateCondition("-5", OP_EQUALS, -5.0, null))
    }

    @Test
    fun `scientific notation comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition(1e10, OP_EQUALS, "10000000000", null))
    }

    @Test
    fun `decimal leading zeros`() {
        assertTrue(ConditionEvaluator.evaluateCondition(3.14, OP_EQUALS, "03.14", null))
    }

    @Test
    fun `boolean vs string yes no on off`() {
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_EQUALS, "yes", null))
        assertTrue(ConditionEvaluator.evaluateCondition(true, OP_EQUALS, "on", null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_EQUALS, "no", null))
        assertTrue(ConditionEvaluator.evaluateCondition(false, OP_EQUALS, "off", null))
    }

    @Test
    fun `empty list vs null`() {
        // [] == null (PHP rule)
        assertTrue(ConditionEvaluator.evaluateCondition(emptyList<Any>(), OP_EQUALS, null, null))
    }

    @Test
    fun `space string is not empty`() {
        assertFalse(ConditionEvaluator.evaluateCondition(" ", OP_IS_EMPTY, null, null))
        assertTrue(ConditionEvaluator.evaluateCondition(" ", OP_IS_NOT_EMPTY, null, null))
    }

    @Test
    fun `tab newline is not empty`() {
        assertFalse(ConditionEvaluator.evaluateCondition("\t", OP_IS_EMPTY, null, null))
        assertFalse(ConditionEvaluator.evaluateCondition("\n", OP_IS_EMPTY, null, null))
    }

    @Test
    fun `special regex character escape`() {
        assertTrue(ConditionEvaluator.evaluateCondition("hello.world", OP_MATCHES_REGEX, "\\.", null))
    }

    @Test
    fun `unicode comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition("你好", OP_EQUALS, "你好", null))
        assertTrue(ConditionEvaluator.evaluateCondition("HELLO", OP_EQUALS, "hello", null))
    }

    @Test
    fun `VNull as input1 operator handling`() {
        // VNull is not null, so OP_EXISTS returns true
        assertTrue(ConditionEvaluator.evaluateCondition(VNull, OP_EXISTS, null, null))
        // But other operators should return false for VNull
        assertFalse(ConditionEvaluator.evaluateCondition(VNull, OP_EQUALS, "test", null))
    }

    @Test
    fun `VNull asNumber is null`() {
        // VNull.toDoubleValue() returns null, so number comparison returns false
        assertFalse(ConditionEvaluator.evaluateCondition(VNull, OP_NUM_GT, 0.0, null))
    }

    @Test
    fun `mixed VObject and native type comparison`() {
        assertTrue(ConditionEvaluator.evaluateCondition(VNumber(42.0), OP_EQUALS, 42.0, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VBoolean(true), OP_EQUALS, true, null))
        assertTrue(ConditionEvaluator.evaluateCondition(VString("test"), OP_EQUALS, "test", null))
    }

    @Test
    fun `VNumber 0 and boolean false comparison`() {
        // 0 == false (PHP rule)
        assertTrue(ConditionEvaluator.evaluateCondition(VNumber(0.0), OP_EQUALS, false, null))
        assertTrue(ConditionEvaluator.evaluateCondition(0.0, OP_EQUALS, VBoolean(false), null))
    }

    @Test
    fun `float precision comparison`() {
        // 0.1 + 0.2 != 0.3 (float precision issue)
        val sum = 0.1 + 0.2
        assertFalse(sum == 0.3)
        // 字符串比较也是精确的，不会忽略精度差异
        assertFalse(ConditionEvaluator.evaluateCondition(sum.toString(), OP_EQUALS, "0.3", null))
        // 正确的字符串应该是 "0.30000000000000004"
        assertTrue(ConditionEvaluator.evaluateCondition(sum.toString(), OP_EQUALS, "0.30000000000000004", null))
    }

    @Test
    fun `NaN handling`() {
        val nan = Double.NaN
        // NaN comparison with any value should return false (including with itself)
        assertFalse(ConditionEvaluator.evaluateCondition(nan, OP_EQUALS, nan, null))
        assertFalse(ConditionEvaluator.evaluateCondition(nan, OP_EQUALS, 0.0, null))
        assertFalse(ConditionEvaluator.evaluateCondition(0.0, OP_EQUALS, nan, null))
    }

    @Test
    fun `infinity comparison`() {
        val infinity = Double.POSITIVE_INFINITY
        val negInfinity = Double.NEGATIVE_INFINITY

        assertTrue(ConditionEvaluator.evaluateCondition(infinity, OP_EQUALS, "Infinity", null))
        assertTrue(ConditionEvaluator.evaluateCondition(infinity, OP_NUM_GT, 1e308, null))
        assertTrue(ConditionEvaluator.evaluateCondition(negInfinity, OP_NUM_LT, -1e308, null))
    }
}
