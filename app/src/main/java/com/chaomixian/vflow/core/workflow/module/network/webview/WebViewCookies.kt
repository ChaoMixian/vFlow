// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewCookies.kt
// 描述: WebView 模块的 Cookie 收集与解析。
//      遵循 RFC 6265 的宽松解析语义。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewCookies.kt
// Description: Cookie collection and parsing for the WebView module.
//      Follows the lenient parsing semantics of RFC 6265.

package com.chaomixian.vflow.core.workflow.module.network.webview

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 一条带有工作流所需属性的 Cookie。
 *
 * 属性按“所见即所得”原则记录：响应中的 `Set-Cookie` 头携带完整属性，
 * 而 WebView CookieManager 与 `document.cookie` 只暴露键值对（浏览器不允许
 * JavaScript 读取 HttpOnly Cookie 及 Cookie 属性）。仅通过后两种渠道观察到的
 * Cookie 会回退到由观察 URL 推导出的合理默认值；若同名 Cookie 曾在响应头中
 * 出现过，则保留已记录的属性。
 *
 * A cookie carrying the attributes the workflow needs.
 *
 * Attributes are recorded on a what-you-see-is-what-you-get basis: a
 * `Set-Cookie` header in a response carries full attributes, while the
 * WebView CookieManager and `document.cookie` only expose name/value pairs
 * (browsers do not let JavaScript read HttpOnly cookies or cookie
 * attributes). Cookies observed only through the latter two channels fall
 * back to reasonable defaults derived from the observing URL; if a
 * same-named cookie previously appeared in a response header, the recorded
 * attributes are kept.
 */
data class CookieData(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    /** Epoch 毫秒；会话 Cookie 或未知过期时间时为 null。
     *  Epoch millis; null for session cookies or unknown expiry. */
    val expiresAtMillis: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null,
    /** Cookie 的观察来源: response / cookie_manager / js。
     *  How the cookie was observed: response / cookie_manager / js. */
    val source: String = "response"
) {
    val key: String get() = "$name@$domain$path"

    fun toOutputMap(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "value" to value,
        "domain" to domain,
        "path" to path,
        "expiry" to (expiresAtMillis?.let { CookieTime.formatInstant(it) }),
        "secure" to secure,
        "http_only" to httpOnly,
        "same_site" to sameSite,
        "source" to source
    )
}

/** Cookie 输出共用的 ISO-8601 时间格式化。
 *  ISO-8601 time formatting shared by the cookie outputs. */
object CookieTime {
    private val formatter = java.time.format.DateTimeFormatter.ISO_INSTANT
        .withZone(java.time.ZoneOffset.UTC)

    fun formatInstant(epochMillis: Long): String =
        formatter.format(java.time.Instant.ofEpochMilli(epochMillis))
}

/**
 * RFC 6265 §5.2 风格的 `Set-Cookie` 解析器（宽松、浏览器式）。
 *
 * An RFC 6265 §5.2-style `Set-Cookie` parser (lenient, browser-like).
 */
object SetCookieParser {

    fun parse(setCookieValue: String, requestUrl: String): CookieData? {
        val pairEnd = setCookieValue.indexOf(';')
        val nameValue = if (pairEnd >= 0) setCookieValue.substring(0, pairEnd) else setCookieValue
        val attrs = if (pairEnd >= 0) setCookieValue.substring(pairEnd + 1) else ""

        val eq = nameValue.indexOf('=')
        if (eq < 0) return null
        val name = nameValue.substring(0, eq).trim()
        var value = nameValue.substring(eq + 1).trim()
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        if (name.isEmpty()) return null

        var domain = hostOf(requestUrl) ?: ""
        var path = defaultPathOf(requestUrl)
        var expires: Long? = null
        var maxAge: Long? = null
        var secure = false
        var httpOnly = false
        var sameSite: String? = null

        for (token in splitAttributes(attrs)) {
            val lower = token.lowercase(Locale.US)
            when {
                lower.startsWith("domain=") -> {
                    var d = token.substring(7).trim().trimStart('.')
                    if (d.isNotEmpty()) domain = d.lowercase(Locale.US)
                }
                lower.startsWith("path=") -> {
                    val p = token.substring(5).trim()
                    if (p.startsWith("/")) path = p
                }
                lower.startsWith("expires=") -> {
                    expires = parseHttpDate(token.substring(8).trim()) ?: expires
                }
                lower.startsWith("max-age=") -> {
                    maxAge = token.substring(8).trim().toLongOrNull() ?: maxAge
                }
                lower == "secure" -> secure = true
                lower == "httponly" -> httpOnly = true
                lower.startsWith("samesite=") -> sameSite = token.substring(9).trim()
                else -> Unit
            }
        }

        // RFC 6265 §5.2.2: Max-Age 优先于 Expires。
        // RFC 6265 §5.2.2: Max-Age takes precedence over Expires.
        if (maxAge != null) {
            expires = System.currentTimeMillis() + maxAge * 1000
        }

        return CookieData(
            name = name,
            value = value,
            domain = domain,
            path = path,
            expiresAtMillis = expires,
            secure = secure,
            httpOnly = httpOnly,
            sameSite = sameSite,
            source = "response"
        )
    }

    /** RFC 6265 Cookie 日期格式: RFC 1123, RFC 1036 与 asctime。
     *  RFC 6265 cookie date formats: RFC 1123, RFC 1036 and asctime. */
    internal fun parseHttpDate(value: String): Long? {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "EEE, dd MMM yyyy HH:mm:ss z"
        )
        for (pattern in formats) {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.isLenient = true
            try {
                val date: Date = sdf.parse(value) ?: continue
                return date.time
            } catch (_: ParseException) {
                // 尝试下一种格式
                // Try the next format.
            }
        }
        return null
    }

    /**
     * 把 `Cookie:` 风格头（"a=1; b=2"）或 `document.cookie` 拆成键值对。
     * 无效的键值对会被跳过（与浏览器行为一致）。
     *
     * Splits a `Cookie:`-style header ("a=1; b=2") or `document.cookie`
     * into name/value pairs. Invalid pairs are skipped (matching browser
     * behavior).
     */
    fun splitCookiePairs(headerValue: String): List<Pair<String, String>> {
        return headerValue.split(';')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = part.substring(0, eq).trim()
                if (name.isEmpty()) return@mapNotNull null
                name to part.substring(eq + 1).trim()
            }
    }

    /**
     * 将多份 Cookie 键值对合并成一个 `Cookie:` 头。
     * 靠前的头优先：调用方先传入用户提供的 Cookie 头，
     * 同名冲突时用户意图优先于 Cookie 存储。
     *
     * Merges several cookie name/value sources into one `Cookie:` header.
     * Earlier headers win: callers pass the user-supplied Cookie header
     * first so that, on name conflicts, user intent beats the cookie
     * store.
     */
    fun mergeCookieHeaders(vararg headers: String?): String {
        val merged = LinkedHashMap<String, String>()
        for (header in headers) {
            if (header.isNullOrBlank()) continue
            for ((name, value) in splitCookiePairs(header)) {
                if (!merged.containsKey(name)) merged[name] = value
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    internal fun hostOf(url: String): String? = try {
        java.net.URI(url).host?.lowercase(Locale.US)
    } catch (_: Exception) {
        null
    }

    /** RFC 6265 §5.1.4 默认路径。
     *  RFC 6265 §5.1.4 default path. */
    internal fun defaultPathOf(url: String): String {
        val uriPath = try {
            java.net.URI(url).path.orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (uriPath.isEmpty() || !uriPath.startsWith("/")) return "/"
        val lastSlash = uriPath.lastIndexOf('/')
        return if (lastSlash == 0) "/" else uriPath.substring(0, lastSlash)
    }

    private fun splitAttributes(attrs: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in attrs) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(c)
                }
                c == ';' && !inQuotes -> {
                    out.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out.filter { it.isNotEmpty() }
    }
}

/**
 * 汇总一次 WebView 执行期间观察到的所有 Cookie，并产出最终的 `cookies` 输出列表。
 *
 * 合并顺序（后观察到的值会更新，但不会丢失已记录的属性）：
 * 1. 响应中的 `Set-Cookie` 头（完整属性）—— 由中继引擎记录；
 * 2. 初始 / 中继 / 最终 URL 的 CookieManager 快照（捕获原生导航和
 *    WebView 自身处理的 Set-Cookie）；
 * 3. 最终页面的 `document.cookie`（捕获 JavaScript 创建或修改的 Cookie）。
 *
 * Aggregates every cookie observed during one WebView execution and
 * produces the final `cookies` output list.
 *
 * Merge order (later-observed values update in place, but recorded
 * attributes are never lost):
 * 1. `Set-Cookie` headers from responses (full attributes) — recorded by
 *    the relay engine;
 * 2. CookieManager snapshots of the initial / relayed / final URLs
 *    (captures native navigation and Set-Cookie handled by the WebView
 *    itself);
 * 3. The final page's `document.cookie` (captures cookies created or
 *    modified by JavaScript).
 */
class CookieCollector(private val maxEntries: Int = 300) {

    private val cookies = LinkedHashMap<String, CookieData>()
    private val observedUrls = LinkedHashSet<String>()

    fun observeUrl(url: String) {
        observedUrls.add(url)
    }

    fun recordSetCookie(requestUrl: String, setCookieValue: String) {
        val cookie = SetCookieParser.parse(setCookieValue, requestUrl) ?: return
        cookies[cookie.key] = cookie
    }

    /**
     * 合并为 [sourceUrl] 观察到的 `Cookie:` 风格头
     * （CookieManager.getCookie / document.cookie）。
     * 若该 Cookie 已在响应头中记录且域名匹配观察 URL，则原位更新
     * （刷新值、保留属性）；否则以 URL 推导的默认值新建条目。
     *
     * Merges a `Cookie:`-style header observed for [sourceUrl]
     * (CookieManager.getCookie / document.cookie). If the cookie was
     * already recorded from a response header and its domain matches the
     * observing URL, it is updated in place (value refreshed, attributes
     * kept); otherwise a new entry is created with URL-derived defaults.
     */
    fun mergeCookieHeader(sourceUrl: String, headerValue: String?, source: String) {
        if (headerValue.isNullOrBlank()) return
        val host = SetCookieParser.hostOf(sourceUrl) ?: return
        for ((name, value) in SetCookieParser.splitCookiePairs(headerValue)) {
            val existing = cookies.values.firstOrNull { cookie ->
                cookie.name == name && domainMatches(host, cookie.domain)
            }
            if (existing != null) {
                cookies[existing.key] = existing.copy(value = value, source = source)
            } else {
                cookies["$name@$host/"] = CookieData(
                    name = name,
                    value = value,
                    domain = host,
                    path = "/",
                    secure = false,
                    httpOnly = false,
                    source = source
                )
            }
        }
    }

    /** RFC 6265 主机匹配: Cookie 域名作用于该主机及其子域名。
     *  RFC 6265 host matching: a cookie domain applies to the host and its
     *  subdomains. */
    internal fun domainMatches(host: String, cookieDomain: String): Boolean {
        val h = host.lowercase()
        val d = cookieDomain.lowercase().trimStart('.')
        if (d.isEmpty()) return false
        return h == d || h.endsWith(".$d")
    }

    fun allCookies(): List<CookieData> =
        cookies.values.sortedWith(compareBy({ it.domain }, { it.name })).take(maxEntries)

    fun toOutputList(): List<Map<String, Any?>> = allCookies().map { it.toOutputMap() }
}
