package com.chaomixian.vflow.ui.workflow_editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterTreeEditorTest {

    @Test
    fun `parse top level path`() {
        val path = ParamPath.parse("content")

        assertEquals("content", path.rootId)
        assertTrue(path.segments.isEmpty())
    }

    @Test
    fun `parse nested path with index segment`() {
        val path = ParamPath.parse("items.0.title")

        assertEquals("items", path.rootId)
        assertEquals(
            listOf(
                ParamPath.Segment.Index(0),
                ParamPath.Segment.Key("title")
            ),
            path.segments
        )
    }

    @Test
    fun `parse encoded dictionary key keeps literal dots and digits`() {
        val path = ParamPath.parse("value.${ParamPath.encodeSegment("user.name")}.${ParamPath.encodeSegment("0")}")

        assertEquals("value", path.rootId)
        assertEquals(
            listOf(
                ParamPath.Segment.Key("user.name"),
                ParamPath.Segment.Key("0")
            ),
            path.segments
        )
    }

    @Test
    fun `set top level value`() {
        val parameters = mutableMapOf<String, Any?>("content" to "old")

        ParameterTreeEditor(parameters).set(ParamPath.parse("content"), "new")

        assertEquals("new", parameters["content"])
    }

    @Test
    fun `set nested map value creates map when missing`() {
        val parameters = mutableMapOf<String, Any?>()

        ParameterTreeEditor(parameters).set(ParamPath.parse("extras.title"), "hello")

        assertEquals(mapOf("title" to "hello"), parameters["extras"])
    }

    @Test
    fun `set nested list value updates existing index`() {
        val parameters = mutableMapOf<String, Any?>(
            "items" to mutableListOf("a", "b", "c")
        )

        ParameterTreeEditor(parameters).set(ParamPath.parse("items.1"), "x")

        assertEquals(listOf("a", "x", "c"), parameters["items"])
    }

    @Test
    fun `set nested list value keeps state on invalid index`() {
        val original = listOf("a", "b")
        val parameters = mutableMapOf<String, Any?>("items" to original)

        ParameterTreeEditor(parameters).set(ParamPath.parse("items.3"), "x")

        assertEquals(original, parameters["items"])
    }

    @Test
    fun `clear top level uses default value`() {
        val parameters = mutableMapOf<String, Any?>("content" to "hello")

        ParameterTreeEditor(parameters).clear(ParamPath.parse("content"), "default")

        assertEquals("default", parameters["content"])
    }

    @Test
    fun `clear nested map value writes empty string`() {
        val parameters = mutableMapOf<String, Any?>(
            "extras" to mapOf("title" to "hello", "other" to "keep")
        )

        ParameterTreeEditor(parameters).clear(ParamPath.parse("extras.title"), "unused")

        assertEquals(
            mapOf("title" to "", "other" to "keep"),
            parameters["extras"]
        )
    }

    @Test
    fun `clear nested list value writes empty string`() {
        val parameters = mutableMapOf<String, Any?>(
            "items" to mutableListOf("a", "b")
        )

        ParameterTreeEditor(parameters).clear(ParamPath.parse("items.0"), "unused")

        assertEquals(listOf("", "b"), parameters["items"])
    }
}
