// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewOutput.kt
// 描述: WebView 模块完成时输出的汇总与大小保护。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewOutput.kt
// Description: Output assembly and size protection for the WebView module
//      at completion time.

package com.chaomixian.vflow.core.workflow.module.network.webview

import com.google.gson.Gson
import java.nio.charset.StandardCharsets

/**
 * 汇总 WebView 模块的最终输出，并对体积施加保护（超大的输出会拖垮
 * 工作流变量的保存/恢复与界面渲染）。
 *
 * Assembles the WebView module's final outputs and protects against
 * oversized values (huge outputs would bog down workflow variable
 * save/restore and UI rendering).
 */
object WebViewOutputCollector {

    /** 字符串输出的最大 UTF-8 字节数。
     *  Maximum UTF-8 byte size of string outputs. */
    const val MAX_STRING_OUTPUT_BYTES = 300_000

    /** 返回值序列化后的最大字节数。
     *  Maximum serialized byte size of the return value. */
    const val MAX_RETURN_VALUE_BYTES = 300_000

    private val gson = Gson()

    /**
     * 会话在完成时刻收集的输入。
     *
     * The inputs collected by the session at completion time.
     */
    data class WebViewSnapshot(
        val finalUrl: String,
        val pageTitle: String,
        /** 存活 DOM 的序列化（doctype + documentElement.outerHTML，已从
         *  evaluateJavascript 的 JSON 载荷解码为原始 HTML 文本）。
         *  The live DOM serialization (doctype +
         *  documentElement.outerHTML, already decoded from the
         *  evaluateJavascript JSON payload into plain HTML text). */
        val documentHtml: String?,
        /** 链路最终响应的状态码，不可用时为 null。
         *  Status code of the chain's final response, null when
         *  unavailable. */
        val statusCode: Int?,
        /** 链路最终响应的响应头，不可用时为 null。
         *  Response headers of the chain's final response, null when
         *  unavailable. */
        val responseHeaders: List<Pair<String, String>>?,
        /** 链路最终请求实际发出的请求头，不可用时为 null。
         *  Request headers actually sent on the chain's final request, null
         *  when unavailable. */
        val requestHeaders: List<Pair<String, String>>?,
        /** CookieCollector 组装出的 Cookie 输出列表。
         *  The cookie output list assembled by the CookieCollector. */
        val cookies: List<Map<String, Any?>>,
        /** 返回变量求值的结果。
         *  The evaluation result of the return variable. */
        val returnValue: JsScripts.ReturnVariableResult,
        /** 捕获到的自定义 JS 错误文本（无错误时为空）。
         *  The captured custom-JS error text (empty when no error). */
        val jsError: String,
        /** 响应信息仍与显示中的页面对应时为 true。
         *  True while the response info still corresponds to the displayed
         *  page. */
        val responseInfoMatchesDisplayedPage: Boolean
    )

    /**
     * 组装输出 Map，键与模块声明的输出 id 一一对应。
     *
     * Assembles the output map whose keys match the module's declared
     * output ids one by one.
     */
    fun buildOutputs(snapshot: WebViewSnapshot): Map<String, Any?> {
        val headersMap = LinkedHashMap<String, Any?>()
        if (snapshot.responseHeaders != null && snapshot.responseInfoMatchesDisplayedPage) {
            for ((name, value) in snapshot.responseHeaders) {
                val existing = headersMap.keys.firstOrNull { it.equals(name, ignoreCase = true) }
                if (existing != null) {
                    headersMap[existing] = "${headersMap[existing]}, $value"
                } else {
                    headersMap[name] = value
                }
            }
        }

        // 请求头与响应头同门控：只有中继链的响应仍对应显示中的页面时才
        // 上报（用户确认前导航离开则两者一起清空）。
        // Request headers are gated like response headers: reported only
        // while the relay chain's response still corresponds to the
        // displayed page (both are cleared together when the user navigates
        // away before confirming).
        val requestHeadersMap = LinkedHashMap<String, Any?>()
        if (snapshot.requestHeaders != null && snapshot.responseInfoMatchesDisplayedPage) {
            for ((name, value) in snapshot.requestHeaders) {
                val existing = requestHeadersMap.keys.firstOrNull { it.equals(name, ignoreCase = true) }
                if (existing != null) {
                    requestHeadersMap[existing] = "${requestHeadersMap[existing]}, $value"
                } else {
                    requestHeadersMap[name] = value
                }
            }
        }

        val returnValuePayload: Any?
        val returnType: String
        when (val rv = snapshot.returnValue) {
            is JsScripts.ReturnVariableResult.Value -> {
                val capped = capReturnValue(rv.value)
                // 区分“超大而被丢弃”与值本身就是 null。
                // Distinguish "dropped because oversized" from a value that
                // is null by itself.
                returnType = if (capped == null && rv.value != null) "oversized" else JsScripts.describeType(rv)
                returnValuePayload = if (capped == null && rv.value != null) null else capped
            }
            is JsScripts.ReturnVariableResult.Missing -> {
                returnType = "missing"
                returnValuePayload = null
            }
            is JsScripts.ReturnVariableResult.Unavailable -> {
                returnType = "missing"
                returnValuePayload = null
            }
            is JsScripts.ReturnVariableResult.Error -> {
                returnType = "error"
                returnValuePayload = null
            }
        }

        val body = snapshot.documentHtml ?: ""
        val truncatedBody = truncateUtf8(body, MAX_STRING_OUTPUT_BYTES)

        return linkedMapOf<String, Any?>(
            "final_url" to snapshot.finalUrl,
            "status_code" to (if (snapshot.responseInfoMatchesDisplayedPage) snapshot.statusCode else null),
            "request_headers" to requestHeadersMap,
            "response_headers" to headersMap,
            "response_body" to truncatedBody,
            "page_title" to snapshot.pageTitle,
            "cookies" to snapshot.cookies,
            "return_value" to returnValuePayload,
            "return_value_type" to returnType,
            "js_error" to snapshot.jsError
        )
    }

    fun serializeOutputs(outputs: Map<String, Any?>): String = gson.toJson(outputs)

    /**
     * 超出大小预算的返回值被丢弃，并把 return_value_type 标记为
     * "oversized"，让工作流可以据此做出反应。
     *
     * Return values beyond the size budget are dropped and
     * return_value_type is marked "oversized" so the workflow can react
     * accordingly.
     */
    internal fun capReturnValue(value: Any?): Any? {
        val serialized = gson.toJson(value)
        return if (utf8Length(serialized) <= MAX_RETURN_VALUE_BYTES) value else null
    }

    /** 在不切断字符的前提下把字符串截断到 UTF-8 字节预算内。
     *  Truncates a string to the UTF-8 byte budget without cutting a
     *  character in half. */
    internal fun truncateUtf8(value: String, maxBytes: Int): String {
        if (utf8Length(value) <= maxBytes) return value
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        var end = maxBytes.coerceAtMost(bytes.size)
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, StandardCharsets.UTF_8)
    }

    internal fun utf8Length(value: String): Int {
        // 纯 ASCII 字符串的快速路径。
        // Fast path for pure-ASCII strings.
        var ascii = true
        for (c in value) {
            if (c.code > 0x7F) {
                ascii = false
                break
            }
        }
        if (ascii) return value.length
        return value.toByteArray(StandardCharsets.UTF_8).size
    }
}
