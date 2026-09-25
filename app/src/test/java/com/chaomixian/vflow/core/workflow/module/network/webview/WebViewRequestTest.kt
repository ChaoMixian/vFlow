// 文件: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRequestTest.kt
// 描述: 请求计划与模块参数校验的单元测试。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRequestTest.kt
// Description: Unit tests for the request plan and module parameter
//      validation.

package com.chaomixian.vflow.core.workflow.module.network.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestPlanTest {

    private val plan = RequestPlan(
        url = "https://example.com/login",
        method = "POST",
        userHeaders = mapOf("Authorization" to "Bearer tok", "X-Custom" to "yes"),
        bodyText = "user=a&pass=b"
    )

    @Test
    fun `user headers override defaults and cookies merge with the store`() {
        val headers = plan.firstHopHeaders(
            userAgent = "TestUA/1.0",
            cookieHeader = "sid=store; theme=dark"
        ).toHeaderMap()

        assertEquals("TestUA/1.0", headers["User-Agent"])
        assertEquals("Bearer tok", headers["Authorization"])
        assertEquals("yes", headers["X-Custom"])
        // 用户 Cookie（此处缺省）+ 存储中的 Cookie 都存在 / Both the user cookie (absent here) and the store's cookie exist
        assertEquals("sid=store; theme=dark", headers["Cookie"])
        // 带请求体的 POST 携带 content type / A POST with a body carries the content type
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", headers["Content-Type"])
    }

    @Test
    fun `user supplied cookie header wins on name collisions`() {
        val headers = RequestPlan(
            url = "https://example.com",
            method = "GET",
            userHeaders = mapOf("Cookie" to "sid=user"),
            bodyText = ""
        ).firstHopHeaders("UA", "sid=store; extra=1").toHeaderMap()
        assertEquals("sid=user; extra=1", headers["Cookie"])
    }

    @Test
    fun `user agent and content type can be overridden by user headers`() {
        val headers = RequestPlan(
            url = "https://example.com",
            method = "POST",
            userHeaders = mapOf(
                "user-agent" to "MyBot/2.0",
                "content-type" to "application/json"
            ),
            bodyText = "{\"a\": 1}"
        ).firstHopHeaders("DefaultUA", null).toHeaderMap()
        assertEquals("MyBot/2.0", headers["user-agent"])
        assertEquals("application/json", headers["content-type"])
    }

    @Test
    fun `json bodies default to application json`() {
        assertEquals(
            "application/json; charset=utf-8",
            RequestPlan("https://x.com", "POST", emptyMap(), "{ \"a\": 1 }").bodyContentType
        )
        assertEquals(
            "application/x-www-form-urlencoded; charset=utf-8",
            RequestPlan("https://x.com", "POST", emptyMap(), "a=1").bodyContentType
        )
    }

    @Test
    fun `redirect methods follow http semantics`() {
        assertEquals("GET", RequestPlan.methodForRedirect(301, "POST"))
        assertEquals("GET", RequestPlan.methodForRedirect(302, "POST"))
        assertEquals("GET", RequestPlan.methodForRedirect(303, "POST"))
        assertEquals("POST", RequestPlan.methodForRedirect(307, "POST"))
        assertEquals("POST", RequestPlan.methodForRedirect(308, "POST"))
    }

    @Test
    fun `get requests do not carry a content type`() {
        val headers = RequestPlan("https://x.com", "GET", emptyMap(), "")
            .firstHopHeaders("UA", null).toHeaderMap()
        assertFalse(headers.containsKey("Content-Type"))
    }

    private fun List<Pair<String, String>>.toHeaderMap(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for ((k, v) in this) map[k] = v
        return map
    }
}

class WebViewRequestSpecTest {

    @Test
    fun `bare domains default to https`() {
        assertEquals("https://example.com", WebViewRequestSpec.normalizeUrl("example.com"))
        assertEquals("https://example.com", WebViewRequestSpec.normalizeUrl("https://example.com"))
        assertEquals("http://example.com", WebViewRequestSpec.normalizeUrl("http://example.com"))
    }

    @Test
    fun `only http and https urls with a host are accepted`() {
        assertTrue(WebViewRequestSpec.isHttpUrl("https://example.com/path"))
        assertTrue(WebViewRequestSpec.isHttpUrl("http://example.com"))
        assertFalse(WebViewRequestSpec.isHttpUrl("ftp://example.com"))
        assertFalse(WebViewRequestSpec.isHttpUrl("file:///data/local/tmp"))
        assertFalse(WebViewRequestSpec.isHttpUrl("javascript:alert(1)"))
        assertFalse(WebViewRequestSpec.isHttpUrl("https://"))
    }

    @Test
    fun `parse produces validated spec with normalized defaults`() {
        val result = WebViewRequestSpec.parse(
            url = "example.com",
            method = " get ",
            headers = emptyMap(),
            body = "",
            customJs = "",
            returnVariable = null,
            headless = true,
            timeoutSeconds = 60
        ) as SpecResult.Valid
        assertEquals("https://example.com", result.spec.url)
        assertEquals("GET", result.spec.method)
        assertEquals(true, result.spec.headless)
        assertEquals(60_000L, result.spec.executionDeadlineMs)
        assertNull(result.spec.returnVariable)
    }

    @Test
    fun `non-headless executions have no deadline`() {
        val result = WebViewRequestSpec.parse(
            "https://example.com", "GET", emptyMap(), "", "", null, headless = false, timeoutSeconds = 60
        ) as SpecResult.Valid
        assertNull(result.spec.executionDeadlineMs)
    }

    @Test
    fun `timeout is clamped to a sane range`() {
        val zero = WebViewRequestSpec.parse(
            "https://example.com", "GET", emptyMap(), "", "", null, true, timeoutSeconds = 0
        ) as SpecResult.Valid
        assertEquals(5_000L, zero.spec.executionDeadlineMs)
        val huge = WebViewRequestSpec.parse(
            "https://example.com", "GET", emptyMap(), "", "", null, true, timeoutSeconds = 100_000
        ) as SpecResult.Valid
        assertEquals(600_000L, huge.spec.executionDeadlineMs)
    }

    @Test
    fun `validation failures map to typed reasons`() {
        val empty = WebViewRequestSpec.parse(
            "", "GET", emptyMap(), "", "", null, true, 60
        ) as SpecResult.Invalid
        assertEquals(InvalidReason.EMPTY_URL, empty.reason)

        val badUrl = WebViewRequestSpec.parse(
            "javascript:alert(1)", "GET", emptyMap(), "", "", null, true, 60
        ) as SpecResult.Invalid
        assertEquals(InvalidReason.INVALID_URL, badUrl.reason)

        val badMethod = WebViewRequestSpec.parse(
            "https://example.com", "BREW", emptyMap(), "", "", null, true, 60
        ) as SpecResult.Invalid
        assertEquals(InvalidReason.INVALID_METHOD, badMethod.reason)
        assertEquals("BREW", badMethod.detail)

        val badVariable = WebViewRequestSpec.parse(
            "https://example.com", "GET", emptyMap(), "", "", "alert('x')", true, 60
        ) as SpecResult.Invalid
        assertEquals(InvalidReason.INVALID_RETURN_VARIABLE, badVariable.reason)
    }

    @Test
    fun `headers accept json strings and decoded maps`() {
        val fromJson = WebViewRequestSpec.parseHeaders(
            """{"Authorization": "Bearer x", "X-Key": "abc"}"""
        ) as HeaderParseResult.Valid
        assertEquals("Bearer x", fromJson.headers["Authorization"])
        assertEquals("abc", fromJson.headers["X-Key"])

        val fromMap = WebViewRequestSpec.parseHeaders(
            mapOf("Authorization" to "Bearer y")
        ) as HeaderParseResult.Valid
        assertEquals("Bearer y", fromMap.headers["Authorization"])

        assertTrue(WebViewRequestSpec.parseHeaders(null) is HeaderParseResult.Valid)
        assertTrue(WebViewRequestSpec.parseHeaders("") is HeaderParseResult.Valid)
        assertTrue(WebViewRequestSpec.parseHeaders("[1,2]") is HeaderParseResult.Invalid)
        assertTrue(WebViewRequestSpec.parseHeaders("{invalid json") is HeaderParseResult.Invalid)
    }

    @Test
    fun `header validation rejects unsafe names and values`() {
        val badName = WebViewRequestSpec.parseHeaders(mapOf("Bad:Name" to "x"))
        assertTrue(badName is HeaderParseResult.Invalid)
        val badValue = WebViewRequestSpec.parseHeaders(mapOf("X-Key" to "line\nbreak"))
        assertTrue(badValue is HeaderParseResult.Invalid)
        val badJson = WebViewRequestSpec.parseHeaders("""{"X-Key": "line\nbreak"}""")
        assertTrue(badJson is HeaderParseResult.Invalid)
    }
}
