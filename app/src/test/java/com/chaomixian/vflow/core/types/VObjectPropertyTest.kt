// 文件: test/java/com/chaomixian/vflow/core/types/VObjectPropertyTest.kt
package com.chaomixian.vflow.core.types

import com.chaomixian.vflow.core.types.basic.*
import org.junit.Assert.*
import org.junit.Test

/**
 * VObject 属性系统测试
 * 验证新架构的魔法变量功能
 */
class VObjectPropertyTest {

    @Test
    fun `test VString length property`() {
        val str = VString("Hello")
        val length = str.getProperty("length")
        assertNotNull(length)
        assertTrue(length is VNumber)
        assertEquals(5.0, (length as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VString length with Chinese alias`() {
        val str = VString("Hello")
        val length = str.getProperty("长度")
        assertNotNull(length)
        assertEquals(5.0, (length as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VString length with abbreviated alias`() {
        val str = VString("Hello")
        val length = str.getProperty("len")
        assertNotNull(length)
        assertEquals(5.0, (length as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VString uppercase property`() {
        val str = VString("hello")
        val upper = str.getProperty("uppercase")
        assertNotNull(upper)
        assertTrue(upper is VString)
        assertEquals("HELLO", (upper as VString).raw)
    }

    @Test
    fun `test VString uppercase with Chinese alias`() {
        val str = VString("hello")
        val upper = str.getProperty("大写")
        assertNotNull(upper)
        assertEquals("HELLO", (upper as VString).raw)
    }

    @Test
    fun `test VString case sensitive property access`() {
        val str = VString("Hello")
        // 大小写敏感：小写可以匹配
        assertEquals(5.0, str.getProperty("length")?.asNumber() ?: 0.0, 0.01)
        // 大写无法匹配（大小写敏感）
        assertNull(str.getProperty("LENGTH"))
        assertNull(str.getProperty("Length"))
        assertNull(str.getProperty("LeNgTh"))
    }

    @Test
    fun `test VNumber int property`() {
        val num = VNumber(3.14)
        val intVal = num.getProperty("int")
        assertNotNull(intVal)
        assertTrue(intVal is VNumber)
        assertEquals(3.0, (intVal as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VNumber int with Chinese alias`() {
        val num = VNumber(3.14)
        val intVal = num.getProperty("整数")
        assertNotNull(intVal)
        assertEquals(3.0, (intVal as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VNumber round property`() {
        val num = VNumber(3.7)
        val rounded = num.getProperty("round")
        assertNotNull(rounded)
        assertEquals(4.0, (rounded as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VNumber abs property`() {
        val num = VNumber(-5.5)
        val absVal = num.getProperty("abs")
        assertNotNull(absVal)
        assertEquals(5.5, (absVal as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VBoolean not property`() {
        val bool = VBoolean(true)
        val notVal = bool.getProperty("not")
        assertNotNull(notVal)
        assertTrue(notVal is VBoolean)
        assertFalse((notVal as VBoolean).raw)
    }

    @Test
    fun `test VBoolean not with Chinese alias`() {
        val bool = VBoolean(true)
        val notVal = bool.getProperty("非")
        assertNotNull(notVal)
        assertFalse((notVal as VBoolean).raw)
    }

    @Test
    fun `test VNull safe navigation`() {
        val nullObj = VNull
        val prop1 = nullObj.getProperty("anyProperty")
        val prop2 = prop1?.getProperty("anotherProperty")

        // VNull 的任何属性都返回 VNull 自身
        assertSame(VNull, prop1)
        assertSame(VNull, prop2)
    }

    @Test
    fun `test property doesn't exist returns null`() {
        val str = VString("Hello")
        val nonExistent = str.getProperty("nonExistentProperty")
        assertNull(nonExistent)
    }

    @Test
    fun `test VString chained property access`() {
        val str = VString("hello")
        // hello -> uppercase -> "HELLO" -> lowercase -> "hello"
        val upper = str.getProperty("uppercase")
        assertNotNull(upper)
        val lower = upper?.getProperty("lowercase")
        assertNotNull(lower)
        assertEquals("hello", (lower as VString).raw)
    }

    @Test
    fun `test VString isempty property`() {
        val emptyStr = VString("")
        assertTrue((emptyStr.getProperty("isempty") as VBoolean).raw)

        val nonEmptyStr = VString("hello")
        assertFalse((nonEmptyStr.getProperty("isempty") as VBoolean).raw)
    }

    @Test
    fun `test VString trim property`() {
        // 测试 trim 去除首尾空格
        val str1 = VString("  hello  ")
        val trimmed1 = str1.getProperty("trim")
        assertNotNull(trimmed1)
        assertEquals("hello", (trimmed1 as VString).raw)

        // 测试 trim 去除全角空格
        val str2 = VString("　hello　")
        val trimmed2 = str2.getProperty("trim")
        assertNotNull(trimmed2)
        assertEquals("hello", (trimmed2 as VString).raw)

        // 测试 trim 中文别名
        val str3 = VString("  world  ")
        val trimmed3 = str3.getProperty("去除首尾空格")
        assertNotNull(trimmed3)
        assertEquals("world", (trimmed3 as VString).raw)
    }

    @Test
    fun `test VString trim property with spaces only`() {
        // 测试只有空格的字符串
        val str = VString("     ")
        val trimmed = str.getProperty("trim")
        assertNotNull(trimmed)
        assertEquals("", (trimmed as VString).raw)
    }

    @Test
    fun `test VString trim property preserves middle spaces`() {
        // 测试 trim 只去除首尾，不去除中间的空格
        val str = VString("  hello world  ")
        val trimmed = str.getProperty("trim")
        assertNotNull(trimmed)
        assertEquals("hello world", (trimmed as VString).raw)
    }

    @Test
    fun `test TemplateParser parses trim correctly`() {
        // 测试模板解析器正确解析 {{xxx.trim}} 形式
        val parser = com.chaomixian.vflow.core.types.parser.TemplateParser("{{result.trim}}")
        val segments = parser.parse()

        assertEquals(1, segments.size)
        val variable = segments[0] as com.chaomixian.vflow.core.types.parser.TemplateSegment.Variable
        assertEquals(listOf("result", "trim"), variable.path)
    }

    @Test
    fun `test TemplateParser with Chinese alias`() {
        // 测试中文别名 {{result.去除首尾空格}}
        val parser = com.chaomixian.vflow.core.types.parser.TemplateParser("{{result.去除首尾空格}}")
        val segments = parser.parse()

        assertEquals(1, segments.size)
        val variable = segments[0] as com.chaomixian.vflow.core.types.parser.TemplateSegment.Variable
        assertEquals(listOf("result", "去除首尾空格"), variable.path)
    }

    @Test
    fun `test VString removeSpaces property`() {
        // 测试去除所有空格
        val str = VString("  hello world  ")
        val result = str.getProperty("removeSpaces")
        assertNotNull(result)
        assertEquals("helloworld", (result as VString).raw)

        // 测试保留中间的多个空格
        val str2 = VString("a   b")
        val result2 = str2.getProperty("removeSpaces")
        assertNotNull(result2)
        assertEquals("ab", (result2 as VString).raw)
    }

    @Test
    fun `test VString removeSpaces English alias`() {
        // 测试英文别名
        val str = VString("hello world")
        val result = str.getProperty("remove_space")
        assertNotNull(result)
        assertEquals("helloworld", (result as VString).raw)
    }

    @Test
    fun `test VNumber abs with negative value`() {
        val num = VNumber(-10.0)
        val absVal = num.getProperty("abs")
        assertEquals(10.0, (absVal as VNumber).raw.toDouble(), 0.01)
    }

    @Test
    fun `test VBoolean double negation`() {
        val bool = VBoolean(true)
        val not1 = bool.getProperty("not")
        val not2 = not1?.getProperty("not")
        assertTrue((not2 as VBoolean).raw)
    }

    @Test
    fun `test VNumber length property`() {
        val num1 = VNumber(3.14)
        val length1 = num1.getProperty("length")
        assertNotNull(length1)
        assertTrue(length1 is VNumber)
        assertEquals(1.0, (length1 as VNumber).raw.toDouble(), 0.01) // 3.14.toLong() = 3, "3".length = 1

        val num2 = VNumber(100.0)
        val length2 = num2.getProperty("length")
        assertEquals(3.0, (length2 as VNumber).raw.toDouble(), 0.01) // 100.toLong() = 100, "100".length = 3

        val num3 = VNumber(-5.5)
        val length3 = num3.getProperty("length")
        assertEquals(2.0, (length3 as VNumber).raw.toDouble(), 0.01) // -5.5.toLong() = -5, "-5".length = 2
    }

    @Test
    fun `test VNumber length with Chinese alias`() {
        val num = VNumber(123.45)
        val length = num.getProperty("长度")
        assertNotNull(length)
        assertEquals(3.0, (length as VNumber).raw.toDouble(), 0.01) // 123.45.toLong() = 123, "123".length = 3
    }

    @Test
    fun `test VNumber length with abbreviated alias`() {
        val num = VNumber(42.0)
        val length = num.getProperty("len")
        assertNotNull(length)
        assertEquals(2.0, (length as VNumber).raw.toDouble(), 0.01) // "42"的长度是2
    }

    @Test
    fun `test VNumber chained length access`() {
        val text = VString("Hello")
        // text.length -> 5.0 -> 5.length -> 1
        val lengthOfText = text.getProperty("length")
        assertNotNull(lengthOfText)
        val lengthOfLength = lengthOfText?.getProperty("length")
        assertNotNull(lengthOfLength)
        assertEquals(1.0, (lengthOfLength as VNumber).raw.toDouble(), 0.01) // "5"的长度是1
    }
}
