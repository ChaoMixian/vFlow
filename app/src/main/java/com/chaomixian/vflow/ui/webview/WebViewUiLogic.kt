// 文件: main/java/com/chaomixian/vflow/ui/webview/WebViewUiLogic.kt
// 描述: 确认界面的纯展示逻辑（不依赖 Android 类，可在 JVM 中单测）。
//
// File: main/java/com/chaomixian/vflow/ui/webview/WebViewUiLogic.kt
// Description: Pure presentation logic of the confirmation screen (no
//      Android dependencies, unit-testable on the JVM).

package com.chaomixian.vflow.ui.webview

/**
 * 确认界面（非后台模式）的纯展示逻辑。
 *
 * Pure presentation logic of the confirmation screen (non-headless mode).
 */
object WebViewUiLogic {

    /** 权威模式: scheme://[userinfo@]host[:port]/...
     *  Authority pattern: scheme://[userinfo@]host[:port]/... */
    private val HOST_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/@]*@)?([^/:?#]+)")

    /**
     * 确认界面应用栏的标题规则：使用页面的文档标题；页面没有标题时
     * 回退到 URL 的域名（无法解析主机时使用 URL 本身）。
     *
     * The title rule for the confirmation screen's app bar: use the page's
     * document title; when the page has no title, fall back to the URL's
     * domain (or the URL itself when no host can be parsed).
     */
    fun resolveToolbarTitle(pageTitle: String?, url: String?): String {
        val title = pageTitle?.trim().orEmpty()
        if (title.isNotEmpty()) return title
        val currentUrl = url?.trim().orEmpty()
        if (currentUrl.isEmpty()) return ""
        val host = extractHost(currentUrl)
        return if (!host.isNullOrEmpty()) host else currentUrl
    }

    /** 提取绝对 URL 的主机部分（纯函数，不使用 android.net.Uri）。
     *  Extracts the host of an absolute URL (a pure function, not using
     *  android.net.Uri). */
    internal fun extractHost(url: String): String? =
        HOST_PATTERN.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
}
