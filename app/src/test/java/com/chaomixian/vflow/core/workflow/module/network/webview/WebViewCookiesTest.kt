// 文件: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewCookiesTest.kt
// 描述: Set-Cookie 解析与 Cookie 收集器的单元测试。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewCookiesTest.kt
// Description: Unit tests for Set-Cookie parsing and the cookie collector.

package com.chaomixian.vflow.core.workflow.module.network.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetCookieParserTest {

    private val url = "https://www.example.com/dir/page.html"

    @Test
    fun `parses basic name value pair with defaults from request url`() {
        val cookie = SetCookieParser.parse("session=abc123", url)!!
        assertEquals("session", cookie.name)
        assertEquals("abc123", cookie.value)
        assertEquals("www.example.com", cookie.domain)
        assertEquals("/dir", cookie.path)
        assertNull(cookie.expiresAtMillis)
        assertEquals(false, cookie.secure)
        assertEquals(false, cookie.httpOnly)
    }

    @Test
    fun `parses full attribute set`() {
        val cookie = SetCookieParser.parse(
            "token=xyz; Domain=.example.com; Path=/app; Expires=Wed, 21 Oct 2026 07:28:00 GMT; " +
                "Secure; HttpOnly; SameSite=Lax",
            url
        )!!
        assertEquals("token", cookie.name)
        assertEquals("xyz", cookie.value)
        assertEquals("example.com", cookie.domain)
        assertEquals("/app", cookie.path)
        assertNotNull(cookie.expiresAtMillis)
        assertEquals(true, cookie.secure)
        assertEquals(true, cookie.httpOnly)
        assertEquals("Lax", cookie.sameSite)
    }

    @Test
    fun `parses rfc1123 rfc1036 and asctime dates`() {
        assertNotNull(SetCookieParser.parseHttpDate("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNotNull(SetCookieParser.parseHttpDate("Wednesday, 21-Oct-26 07:28:00 GMT"))
        assertNotNull(SetCookieParser.parseHttpDate("Wed Oct 21 07:28:00 2026"))
    }

    @Test
    fun `max-age takes precedence over expires`() {
        val cookie = SetCookieParser.parse(
            "a=b; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Max-Age=3600", url
        )!!
        assertNotNull(cookie.expiresAtMillis)
        val expected = System.currentTimeMillis() + 3_600_000L
        assertTrue(Math.abs(cookie.expiresAtMillis!! - expected) < 2_000)
    }

    @Test
    fun `quoted values are unquoted`() {
        val cookie = SetCookieParser.parse("quoted=\"hello world\"", url)!!
        assertEquals("hello world", cookie.value)
    }

    @Test
    fun `value may contain equals and semicolon-free junk`() {
        val cookie = SetCookieParser.parse("data=a=b=c", url)!!
        assertEquals("a=b=c", cookie.value)
    }

    @Test
    fun `invalid inputs return null`() {
        assertNull(SetCookieParser.parse("novalue", url))
        assertNull(SetCookieParser.parse("=value", url))
        assertNull(SetCookieParser.parse("", url))
    }

    @Test
    fun `splitCookiePairs parses cookie header`() {
        val pairs = SetCookieParser.splitCookiePairs("a=1; b=2; broken; c=3")
        assertEquals(listOf("a" to "1", "b" to "2", "c" to "3"), pairs)
    }

    @Test
    fun `mergeCookieHeaders lets earlier headers win`() {
        val merged = SetCookieParser.mergeCookieHeaders("a=1; b=2", "b=9; c=3")
        assertEquals("a=1; b=2; c=3", merged)
    }

    @Test
    fun `mergeCookieHeaders handles null and blank`() {
        assertEquals("a=1", SetCookieParser.mergeCookieHeaders(null, "a=1"))
        assertEquals("a=1", SetCookieParser.mergeCookieHeaders(" ", "a=1"))
    }

    @Test
    fun `default path is root for root-level uri`() {
        assertEquals("/", SetCookieParser.defaultPathOf("https://example.com"))
        assertEquals("/", SetCookieParser.defaultPathOf("https://example.com/page.html"))
        assertEquals("/dir", SetCookieParser.defaultPathOf("https://example.com/dir/page.html"))
    }
}

class CookieCollectorTest {

    private val url = "https://www.example.com/dir/page.html"

    @Test
    fun `response set-cookies carry full attributes`() {
        val collector = CookieCollector()
        collector.recordSetCookie(url, "sid=1; Domain=.example.com; Path=/; Secure; HttpOnly")
        val cookies = collector.allCookies()
        assertEquals(1, cookies.size)
        val cookie = cookies[0]
        assertEquals("sid", cookie.name)
        assertEquals("1", cookie.value)
        assertEquals("example.com", cookie.domain)
        assertEquals(true, cookie.secure)
        assertEquals(true, cookie.httpOnly)
        assertEquals("response", cookie.source)
    }

    @Test
    fun `cookie manager snapshot refreshes value but keeps attributes`() {
        val collector = CookieCollector()
        collector.recordSetCookie(url, "sid=1; Domain=.example.com; Path=/; Secure; HttpOnly")
        collector.mergeCookieHeader("https://other.example.com/x", "sid=2", "cookie_manager")
        val cookie = collector.allCookies().single()
        assertEquals("2", cookie.value)
        assertEquals(true, cookie.secure) // 属性保留自响应
        assertEquals(true, cookie.httpOnly)
        assertEquals("cookie_manager", cookie.source)
    }

    @Test
    fun `js-created cookies get defaults derived from url`() {
        val collector = CookieCollector()
        collector.mergeCookieHeader("https://app.example.com/x", "theme=dark; jsFlag=1", "js")
        val cookies = collector.allCookies()
        assertEquals(2, cookies.size)
        val theme = cookies.first { it.name == "theme" }
        assertEquals("app.example.com", theme.domain)
        assertEquals("/", theme.path)
        // JS 观察到的 Cookie 的 secure / httpOnly 未知，默认为 false / secure / httpOnly are unknown for JS-observed cookies and default to false
        assertEquals(false, theme.secure)
        assertEquals(false, theme.httpOnly)
        assertEquals("js", theme.source)
    }

    @Test
    fun `distinct domains and paths are separate cookies`() {
        val collector = CookieCollector()
        collector.recordSetCookie("https://a.com/", "x=1")
        collector.recordSetCookie("https://b.com/", "x=2")
        assertEquals(2, collector.allCookies().size)
    }

    @Test
    fun `output map exposes documented cookie properties`() {
        val collector = CookieCollector()
        collector.recordSetCookie(
            url,
            "sid=1; Domain=.example.com; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Secure; HttpOnly"
        )
        collector.mergeCookieHeader(url, "jsVar=7", "js")
        val output = collector.toOutputList()

        val sid = output.first { it["name"] == "sid" }
        assertEquals("1", sid["value"])
        assertEquals("example.com", sid["domain"])
        assertEquals("/", sid["path"])
        assertNotNull(sid["expiry"])
        assertEquals(true, sid["secure"])
        assertEquals(true, sid["http_only"])

        val jsVar = output.first { it["name"] == "jsVar" }
        assertEquals("7", jsVar["value"])
        assertNull(jsVar["expiry"])
        assertEquals(false, jsVar["secure"])
        assertEquals(false, jsVar["http_only"])
    }

    @Test
    fun `entries are sorted deterministically and capped`() {
        val collector = CookieCollector(maxEntries = 2)
        collector.recordSetCookie(url, "b=1")
        collector.recordSetCookie(url, "a=1")
        collector.recordSetCookie(url, "c=1")
        val names = collector.toOutputList().map { it["name"] }
        assertEquals(listOf("a", "b"), names) // 排序并截断为 2 条
    }

    @Test
    fun `expiry is iso8601 utc`() {
        val millis = SetCookieParser.parseHttpDate("Wed, 21 Oct 2026 07:28:00 GMT")!!
        assertEquals("2026-10-21T07:28:00Z", CookieTime.formatInstant(millis))
    }
}
