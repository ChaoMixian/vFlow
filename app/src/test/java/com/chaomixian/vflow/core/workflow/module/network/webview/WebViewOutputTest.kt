// 文件: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewOutputTest.kt
// 描述: 输出汇总与大小保护的单元测试。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewOutputTest.kt
// Description: Unit tests for output assembly and size protection.

package com.chaomixian.vflow.core.workflow.module.network.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewOutputCollectorTest {

    private fun snapshot(
        finalUrl: String = "https://example.com/home",
        title: String = "Example Home",
        html: String? = "<!DOCTYPE html>\n<html><body>hi</body></html>",
        status: Int? = 200,
        headers: List<Pair<String, String>>? = listOf(
            "Content-Type" to "text/html; charset=utf-8",
            "X-Dup" to "a",
            "x-dup" to "b"
        ),
        requestHeaders: List<Pair<String, String>>? = listOf(
            "Host" to "example.com",
            "X-Dup" to "a",
            "x-dup" to "b"
        ),
        cookies: List<Map<String, Any?>> = listOf(
            mapOf("name" to "sid", "value" to "1", "domain" to "example.com", "path" to "/",
                "expiry" to null, "secure" to true, "http_only" to false, "same_site" to null, "source" to "response")
        ),
        returnValue: JsScripts.ReturnVariableResult = JsScripts.ReturnVariableResult.Missing,
        jsError: String = "",
        matches: Boolean = true
    ) = WebViewOutputCollector.WebViewSnapshot(
        finalUrl = finalUrl,
        pageTitle = title,
        documentHtml = html,
        statusCode = status,
        responseHeaders = headers,
        requestHeaders = requestHeaders,
        cookies = cookies,
        returnValue = returnValue,
        jsError = jsError,
        responseInfoMatchesDisplayedPage = matches
    )

    @Test
    fun `outputs contain every documented key`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot())
        val expectedKeys = setOf(
            "final_url", "status_code", "request_headers", "response_headers", "response_body",
            "page_title", "cookies", "return_value", "return_value_type", "js_error"
        )
        assertEquals(expectedKeys, outputs.keys)
    }

    @Test
    fun `response headers join duplicates with comma`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot())
        val headers = outputs["response_headers"] as Map<*, *>
        assertEquals("text/html; charset=utf-8", headers["Content-Type"])
        assertEquals("a, b", headers["X-Dup"])
    }

    @Test
    fun `request headers join duplicates with comma`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot())
        val requestHeaders = outputs["request_headers"] as Map<*, *>
        assertEquals("example.com", requestHeaders["Host"])
        assertEquals("a, b", requestHeaders["X-Dup"])
    }

    @Test
    fun `request headers default to an empty map when unavailable`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot(requestHeaders = null))
        assertEquals(emptyMap<String, Any?>(), outputs["request_headers"])
    }

    @Test
    fun `response info is cleared when the user navigated away before confirming`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot(matches = false))
        assertNull(outputs["status_code"])
        assertEquals(emptyMap<String, Any?>(), outputs["response_headers"])
        assertEquals(emptyMap<String, Any?>(), outputs["request_headers"])
    }

    @Test
    fun `missing return variable maps to null and type missing`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot(returnValue = JsScripts.ReturnVariableResult.Missing))
        assertNull(outputs["return_value"])
        assertEquals("missing", outputs["return_value_type"])
    }

    @Test
    fun `explicit null return value maps to null and type null`() {
        val outputs = WebViewOutputCollector.buildOutputs(
            snapshot(returnValue = JsScripts.ReturnVariableResult.Value(null))
        )
        assertNull(outputs["return_value"])
        assertEquals("null", outputs["return_value_type"])
    }

    @Test
    fun `object return value flows through structured`() {
        val outputs = WebViewOutputCollector.buildOutputs(
            snapshot(returnValue = JsScripts.ReturnVariableResult.Value(mapOf("foo" to "bar")))
        )
        assertEquals(mapOf("foo" to "bar"), outputs["return_value"])
        assertEquals("object", outputs["return_value_type"])
    }

    @Test
    fun `oversized return value is dropped and flagged`() {
        val huge = (1..60_000).joinToString(",") { "\"chunk$it\"" }
        val value = JsScripts.normalizeJsonTree(
            com.google.gson.Gson().fromJson("""{"data":[$huge]}""", Any::class.java)
        )
        val outputs = WebViewOutputCollector.buildOutputs(
            snapshot(returnValue = JsScripts.ReturnVariableResult.Value(value))
        )
        assertNull(outputs["return_value"])
        assertEquals("oversized", outputs["return_value_type"])
    }

    @Test
    fun `body truncation respects utf-8 character boundaries`() {
        val cjk = "你好世界".repeat(40_000) // 640_000 UTF-8 字节 / 640_000 UTF-8 bytes
        val outputs = WebViewOutputCollector.buildOutputs(snapshot(html = cjk))
        val body = outputs["response_body"] as String
        assertTrue(body.toByteArray(Charsets.UTF_8).size <= WebViewOutputCollector.MAX_STRING_OUTPUT_BYTES)
        // 结尾没有半个字符。 / No half character at the end.
        val lastChar = body.last()
        assertTrue(lastChar.isSurrogate() || lastChar.code > 0)
    }

    @Test
    fun `null document html yields empty body`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot(html = null))
        assertEquals("", outputs["response_body"])
    }

    @Test
    fun `outputs serialize to valid json`() {
        val outputs = WebViewOutputCollector.buildOutputs(snapshot())
        val json = WebViewOutputCollector.serializeOutputs(outputs)
        val parsed: Map<String, Any?> =
            com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any?>
        assertEquals("https://example.com/home", parsed["final_url"])
        assertEquals("Example Home", parsed["page_title"])
        @Suppress("UNCHECKED_CAST")
        val cookies = parsed["cookies"] as List<Map<String, Any?>>
        assertEquals("sid", cookies[0]["name"])
    }
}
