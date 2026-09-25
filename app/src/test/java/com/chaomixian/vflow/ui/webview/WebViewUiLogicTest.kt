// 文件: test/java/com/chaomixian/vflow/ui/webview/WebViewUiLogicTest.kt
// 描述: 确认界面标题规则的纯逻辑单元测试。
//
// File: test/java/com/chaomixian/vflow/ui/webview/WebViewUiLogicTest.kt
// Description: Pure-logic unit tests for the confirmation screen's title
//      rules.

package com.chaomixian.vflow.ui.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewUiLogicTest {

    @Test
    fun `page title wins when present`() {
        assertEquals("My Page", WebViewUiLogic.resolveToolbarTitle("My Page", "https://example.com"))
        assertEquals("spaced", WebViewUiLogic.resolveToolbarTitle("  spaced  ", null))
    }

    @Test
    fun `blank title falls back to the url domain`() {
        assertEquals("example.com", WebViewUiLogic.resolveToolbarTitle("", "https://example.com/a?b=1"))
        assertEquals("example.com", WebViewUiLogic.resolveToolbarTitle(null, "http://example.com/"))
        assertEquals("example.com", WebViewUiLogic.resolveToolbarTitle("   ", "https://example.com"))
    }

    @Test
    fun `userinfo and ports are excluded from the domain`() {
        assertEquals("example.com", WebViewUiLogic.extractHost("https://user:pass@example.com:8443/x"))
        assertEquals("sub.example.com", WebViewUiLogic.extractHost("http://sub.example.com/a"))
        assertNull(WebViewUiLogic.extractHost("about:blank"))
        assertNull(WebViewUiLogic.extractHost("not a url"))
    }

    @Test
    fun `urls without a host fall back to the raw url`() {
        assertEquals("about:blank", WebViewUiLogic.resolveToolbarTitle("", "about:blank"))
    }

    @Test
    fun `nothing available yields an empty title`() {
        assertEquals("", WebViewUiLogic.resolveToolbarTitle(null, null))
        assertEquals("", WebViewUiLogic.resolveToolbarTitle("", ""))
    }
}
