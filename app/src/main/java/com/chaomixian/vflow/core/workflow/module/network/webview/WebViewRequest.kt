// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRequest.kt
// 描述: WebView 模块的请求参数解析、校验与请求计划。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRequest.kt
// Description: Request-parameter parsing, validation and the request plan
//      of the WebView module.

package com.chaomixian.vflow.core.workflow.module.network.webview

import com.google.gson.Gson

/** WebView 模块支持的 HTTP 方法。
 *  HTTP methods supported by the WebView module. */
val HTTP_METHODS = listOf("GET", "POST")

/**
 * 一次 WebView 模块执行的已解析、已校验参数。
 *
 * The parsed and validated parameters of one WebView module execution.
 */
data class WebViewRequestSpec(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String,
    val customJs: String,
    val returnVariable: String?,
    val headless: Boolean,
    /** 后台模式下的整体执行截止时间（毫秒）；非后台（确认）模式为 null（不设限）。
     *  The overall execution deadline in headless mode (millis); null
     *  (no limit) in non-headless (confirmation) mode. */
    val executionDeadlineMs: Long?
) {
    val hasCustomJs: Boolean get() = customJs.isNotBlank()
    val hasReturnVariable: Boolean get() = returnVariable != null

    companion object {

        /**
         * 解析并校验模块输入。
         *
         * @param timeoutSeconds 用户配置的超时秒数（仅作用于后台模式）。
         *  The user-configured timeout in seconds (headless mode only).
         * @return 校验结果：成功携带 [WebViewRequestSpec]，
         *         失败携带可直接交给 [String.format] 的格式串与可选参数。
         *         The validation result: success carries a
         *         [WebViewRequestSpec]; failure carries a format string
         *         ready for [String.format] plus an optional argument.
         *
         * Parses and validates the module inputs.
         */
        fun parse(
            url: String,
            method: String,
            headers: Map<String, String>,
            body: String,
            customJs: String,
            returnVariable: String?,
            headless: Boolean,
            timeoutSeconds: Long
        ): SpecResult {
            val rawUrl = url.trim()
            if (rawUrl.isEmpty()) {
                return SpecResult.Invalid(InvalidReason.EMPTY_URL)
            }
            val normalizedUrl = normalizeUrl(rawUrl)
            if (!isHttpUrl(normalizedUrl)) {
                return SpecResult.Invalid(InvalidReason.INVALID_URL)
            }

            val normalizedMethod = method.trim().uppercase().ifEmpty { "GET" }
            if (normalizedMethod !in HTTP_METHODS) {
                return SpecResult.Invalid(InvalidReason.INVALID_METHOD, normalizedMethod)
            }

            val normalizedReturnVariable = returnVariable?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedReturnVariable != null && !JsScripts.isValidVariablePath(normalizedReturnVariable)) {
                return SpecResult.Invalid(InvalidReason.INVALID_RETURN_VARIABLE, normalizedReturnVariable)
            }

            val timeoutMs = timeoutSeconds.coerceIn(5, 600) * 1000
            return SpecResult.Valid(
                WebViewRequestSpec(
                    url = normalizedUrl,
                    method = normalizedMethod,
                    headers = headers,
                    body = body,
                    customJs = customJs,
                    returnVariable = normalizedReturnVariable,
                    headless = headless,
                    executionDeadlineMs = if (headless) timeoutMs else null
                )
            )
        }

        /**
         * 裸域名默认补 https://（与 vFlow HTTP 模块一致）；
         * 已携带 scheme 的 URL 原样使用，使非 http scheme 在校验中失败
         * 而不是被静默包装。
         *
         * Bare domains are prefixed with https:// by default (matching the
         * vFlow HTTP module); URLs that already carry a scheme are used
         * verbatim so that non-http schemes fail validation instead of
         * being silently wrapped.
         */
        fun normalizeUrl(raw: String): String {
            val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(raw)
            return if (hasScheme) raw else "https://$raw"
        }

        fun isHttpUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
            return try {
                java.net.URI(url).let { uri ->
                    uri.host != null && !uri.host.isBlank()
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * 请求头输入契约：JSON 对象字符串，例如
         * `{"Authorization": "Bearer token", "X-Api-Key": "abc"}`。
         * 宽容地接受已解码的 Map（例如以字典魔法变量作为输入）。
         *
         * The header input contract: a JSON object string such as
         * `{"Authorization": "Bearer token", "X-Api-Key": "abc"}`.
         * An already-decoded Map is accepted leniently (e.g. a dictionary
         * magic variable used as the input).
         */
        fun parseHeaders(raw: Any?): HeaderParseResult {
            val direct = flattenToStringMap(raw)
            if (direct != null) {
                return validateHeaderMap(direct)?.let { HeaderParseResult.Invalid(it) }
                    ?: HeaderParseResult.Valid(direct)
            }
            if (raw == null) return HeaderParseResult.Valid(emptyMap())
            val text = raw.toString().trim()
            if (text.isEmpty()) return HeaderParseResult.Valid(emptyMap())
            return try {
                val parsed: Any? = Gson().fromJson(text, Any::class.java)
                val flattened = flattenToStringMap(parsed)
                    ?: return HeaderParseResult.Invalid("not a JSON object")
                validateHeaderMap(flattened)?.let { HeaderParseResult.Invalid(it) }
                    ?: HeaderParseResult.Valid(flattened)
            } catch (e: Exception) {
                HeaderParseResult.Invalid(e.message ?: "invalid JSON")
            }
        }

        /**
         * 当值在结构上是一个请求头 Map 时，把它转换成 `Map<String, String>`。
         *
         * Converts a value to `Map<String, String>` when it is structurally
         * a header map.
         */
        @Suppress("UNCHECKED_CAST")
        private fun flattenToStringMap(raw: Any?): Map<String, String>? {
            return when (raw) {
                null -> null
                is Map<*, *> -> {
                    val out = LinkedHashMap<String, String>()
                    for ((key, value) in raw) {
                        if (value == null) continue
                        out[key.toString()] = unwrapValue(value)
                    }
                    out
                }
                else -> null
            }
        }

        /** 字典魔法变量直接以 Map 形态到达；值可能是嵌套结构，统一转字符串。
         *  Dictionary magic variables arrive as Maps; values may be nested
         *  structures and are uniformly stringified. */
        private fun unwrapValue(value: Any): String = when (value) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> value.toString()
        }

        private fun validateHeaderMap(headers: Map<String, String>): String? {
            for ((name, value) in headers) {
                if (name.isBlank()) return "empty header name"
                if (name.contains('\n') || name.contains('\r') || name.contains(':')) {
                    return "illegal character in header name \"$name\""
                }
                if (value.contains('\n') || value.contains('\r')) {
                    return "illegal line break in header value \"$name\""
                }
            }
            return null
        }
    }
}

/** 请求头解析结果。
 *  The result of parsing the headers input. */
sealed class HeaderParseResult {
    data class Valid(val headers: Map<String, String>) : HeaderParseResult()
    data class Invalid(val detail: String) : HeaderParseResult()
}

/** 规格解析结果。
 *  The result of parsing the module spec. */
sealed class SpecResult {
    data class Valid(val spec: WebViewRequestSpec) : SpecResult()

    /** 格式串可含一个 %s 占位符（详情值）。
     *  The format string may carry one %s placeholder (the detail value). */
    data class Invalid(val reason: InvalidReason, val detail: String? = null) : SpecResult()
}

/** 校验失败的原因，逐条对应模块的错误字符串资源。
 *  Validation failure reasons, each mapped to one of the module's error
 *  string resources. */
enum class InvalidReason {
    EMPTY_URL,
    INVALID_URL,
    INVALID_METHOD,
    INVALID_RETURN_VARIABLE,
}

/**
 * WebView 模块必须执行的初始主框架 HTTP 请求计划：
 * 方法 + 用户请求头 + 请求体被应用到代表 WebView 发出的真实网络请求，
 * 而 Cookie 处理仍然留在 WebView 的 CookieManager，以保留正常的浏览器行为。
 *
 * The initial main-frame HTTP request plan the WebView module must perform:
 * the method + user headers + body are applied to the real network request
 * made on behalf of the WebView, while cookie handling stays in WebView's
 * CookieManager to preserve normal browser behavior.
 */
data class RequestPlan(
    val url: String,
    val method: String,
    val userHeaders: Map<String, String>,
    val bodyText: String
) {
    val hasBody: Boolean get() = bodyText.isNotEmpty()

    val bodyBytes: ByteArray get() = bodyText.toByteArray(Charsets.UTF_8)

    /**
     * 默认 Content-Type：表单编码（与 WebView.postUrl 语义一致）；
     * JSON 形态的请求体被检测为 application/json。
     *
     * Default Content-Type: form encoding (matching WebView.postUrl
     * semantics); JSON-shaped bodies are detected as application/json.
     */
    val bodyContentType: String
        get() {
            userHeaders.keys.firstOrNull { it.equals("Content-Type", ignoreCase = true) }?.let { return userHeaders[it]!! }
            val trimmed = bodyText.trimStart()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "application/json; charset=utf-8"
            return "application/x-www-form-urlencoded; charset=utf-8"
        }

    /**
     * 第一跳的请求头：用户头覆盖默认值；`Cookie` 头是用户提供的
     * Cookie 头与 WebView Cookie 存储中该 URL 的头合并的结果
     * （同名冲突时用户条目优先，保证用户意图总是被应用）。
     *
     * Headers for the first hop: user headers override the defaults; the
     * `Cookie` header merges the user-supplied cookie header with the one
     * the WebView cookie store holds for the URL (on name conflicts the
     * user entry wins, so user intent is always applied).
     */
    fun firstHopHeaders(userAgent: String, cookieHeader: String?): List<Pair<String, String>> {
        val headers = LinkedHashMap<String, String>()
        fun put(name: String, value: String) {
            val existing = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) headers.remove(existing)
            headers[name] = value
        }
        put("User-Agent", userAgent)
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        if (method != "GET" && method != "HEAD" && hasBody) {
            put("Content-Type", bodyContentType)
        }
        for ((name, value) in userHeaders) put(name, value)
        val userCookie = userHeaders.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value
        val mergedCookie = SetCookieParser.mergeCookieHeaders(userCookie, cookieHeader)
        if (mergedCookie.isNotEmpty()) put("Cookie", mergedCookie)
        // Content-Length 由 HTTP 客户端自行计算。
        // Content-Length is computed by the HTTP client itself.
        return headers.map { it.key to it.value }
    }

    /** 重定向后续跳跃的请求头：用户头仍然生效。
     *  Headers for hops after a redirect: user headers still apply. */
    fun redirectHopHeaders(userAgent: String, cookieHeader: String?): List<Pair<String, String>> {
        val headers = LinkedHashMap<String, String>()
        fun put(name: String, value: String) {
            val existing = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) headers.remove(existing)
            headers[name] = value
        }
        put("User-Agent", userAgent)
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        for ((name, value) in userHeaders) put(name, value)
        val userCookie = userHeaders.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value
        val mergedCookie = SetCookieParser.mergeCookieHeaders(userCookie, cookieHeader)
        if (mergedCookie.isNotEmpty()) put("Cookie", mergedCookie)
        return headers.map { it.key to it.value }
    }

    companion object {
        /**
         * 后续请求的 HTTP 重定向语义：
         * 301/302/303 把方法切换为 GET 并丢弃请求体，
         * 307/308 保留方法与请求体。
         *
         * HTTP redirect semantics for follow-up requests:
         * 301/302/303 switch the method to GET and drop the body;
         * 307/308 preserve the method and body.
         */
        fun methodForRedirect(statusCode: Int, originalMethod: String): String =
            if (statusCode == 307 || statusCode == 308) originalMethod else "GET"
    }
}
