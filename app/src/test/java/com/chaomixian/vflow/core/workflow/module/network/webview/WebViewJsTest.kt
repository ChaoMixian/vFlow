// 文件: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewJsTest.kt
// 描述: 自定义 JS 两阶段脚本构造/解释、返回变量求值解释与纯字符串解码
//      的单元测试。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewJsTest.kt
// Description: Unit tests for the two-stage custom-JS script building and
//      interpretation, the return-variable result interpretation and the
//      plain-string result decoding.

package com.chaomixian.vflow.core.workflow.module.network.webview

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsScriptsTest {

    private val gson = Gson()

    @Test
    fun `attempt script runs user code verbatim at top level`() {
        // 用户代码必须原样出现在脚本顶层（不经函数包装），
        // var/let/const 声明才能成为全局绑定。
        // The user code must appear verbatim at top level (not wrapped in
        // a function) so var/let/const declarations become global bindings.
        val script = JsScripts.buildCustomJsAttemptScript("var someVariable = { foo: 'bar' };")
        assertTrue(script.contains("/* ---- user script (verbatim, top level) ---- */\nvar someVariable = { foo: 'bar' };\n/* ---- end of user script ---- */"))
        assertFalse(script.contains("(function(){\nvar someVariable"))
    }

    @Test
    fun `attempt script installs and restores a self recovering onerror`() {
        val script = JsScripts.buildCustomJsAttemptScript("x = 1;")
        // 安装 onerror，并在收尾 IIFE 与错误句柄里自恢复。
        // Installs onerror and restores it both in the trailing IIFE and
        // from inside the handler itself.
        assertTrue(script.contains("var __vflowWebViewPrevOnError = window.onerror;"))
        assertTrue(script.contains("window.onerror = function (msg) {"))
        assertTrue(script.contains("window.onerror = __vflowWebViewPrevOnError;"))
        // 哨兵：脚本跑完时以对象作为完成值回传。
        // The sentinel: a delivered object completion value means the
        // script ran to the end.
        assertTrue(script.contains("return { v: 'ran', error: window.__vflowWebViewLastError };"))
        // 桥变量清零，避免跨次执行残留。
        // The bridge variable is reset so no stale value survives runs.
        assertTrue(script.contains("window.__vflowWebViewReturnValue = undefined;"))
    }

    @Test
    fun `attempt parsing recognizes the sentinel and dropped callbacks`() {
        // 哨兵回传 -> 跑完（无错误）。
        // Sentinel delivered -> ran (no error).
        val ran = JsScripts.parseCustomJsAttempt("""{"v":"ran","error":null}""")
        assertTrue(ran is JsScripts.CustomJsAttempt.Ran)
        assertNull((ran as JsScripts.CustomJsAttempt.Ran).error)

        // 回调丢失 / 非哨兵载荷 -> 需要恢复脚本。
        // Dropped callback / non-sentinel payload -> recovery needed.
        assertTrue(JsScripts.parseCustomJsAttempt(null) is JsScripts.CustomJsAttempt.NeedsRecovery)
        assertTrue(JsScripts.parseCustomJsAttempt("null") is JsScripts.CustomJsAttempt.NeedsRecovery)
        assertTrue(JsScripts.parseCustomJsAttempt("") is JsScripts.CustomJsAttempt.NeedsRecovery)
        assertTrue(JsScripts.parseCustomJsAttempt("42") is JsScripts.CustomJsAttempt.NeedsRecovery)
        assertTrue(JsScripts.parseCustomJsAttempt("""{"ok":true,"error":null}""") is JsScripts.CustomJsAttempt.NeedsRecovery)
    }

    @Test
    fun `recovery script probes leftovers and wraps the user code as fallback`() {
        val script = JsScripts.buildCustomJsRecoveryScript("return document.title;")
        // 探测残留错误 + 恢复未释放的 onerror 句柄。
        // Probes leftover errors and restores an unreleased onerror handler.
        assertTrue(script.contains("var leftover = (typeof window.__vflowWebViewLastError === 'string')"))
        assertTrue(script.contains("window.__vflowWebViewAttemptActive === true"))
        // 运行时错误优先上报。
        // A runtime error is reported first.
        assertTrue(script.contains("if (leftover !== null) return { v: 'runtime', error: leftover };"))
        // 解析失败 -> 函数包装重跑（顶层 return 合法），返回值进入桥变量。
        // Parse failure -> function-wrapped re-run (top-level return legal),
        // the return value goes into the bridge variable.
        assertTrue(script.contains("window.__vflowWebViewReturnValue = (function () {"))
        assertTrue(script.contains("return document.title;"))
    }

    @Test
    fun `recovery parsing distinguishes runtime wrapped and failed`() {
        // 运行时错误 -> Error。
        // Runtime error -> Error.
        val runtime = JsScripts.parseCustomJsRecovery("""{"v":"runtime","error":"TypeError: x is null"}""")
        assertTrue(runtime is JsScripts.CustomJsRecovery.Error)
        assertEquals("TypeError: x is null", (runtime as JsScripts.CustomJsRecovery.Error).message)

        // 包装执行无错误 -> RanClean。
        // Wrapped execution without error -> RanClean.
        assertTrue(JsScripts.parseCustomJsRecovery("""{"v":"wrapped","error":null}""") is JsScripts.CustomJsRecovery.RanClean)

        // 包装执行捕获到错误 -> Error。
        // Wrapped execution captured an error -> Error.
        val wrappedError = JsScripts.parseCustomJsRecovery("""{"v":"wrapped","error":"ReferenceError: y is not defined"}""")
        assertTrue(wrappedError is JsScripts.CustomJsRecovery.Error)

        // 恢复脚本也没跑（真正的语法错误）-> FailedToRun。
        // The recovery script did not run either (genuine syntax error) ->
        // FailedToRun.
        assertTrue(JsScripts.parseCustomJsRecovery(null) is JsScripts.CustomJsRecovery.FailedToRun)
        assertTrue(JsScripts.parseCustomJsRecovery("null") is JsScripts.CustomJsRecovery.FailedToRun)
        assertTrue(JsScripts.parseCustomJsRecovery("""{"v":"bogus"}""") is JsScripts.CustomJsRecovery.FailedToRun)
    }

    @Test
    fun `return variable script embeds the variable name and the return bridge`() {
        val script = JsScripts.buildReturnVariableScript("someVariable")
        assertTrue(script.contains("typeof someVariable === 'undefined'"))
        assertTrue(script.contains("value: someVariable"))
        // 变量缺失时回退到顶层 return 桥。
        // Falls back to the top-level return bridge when the variable is
        // missing.
        assertTrue(script.contains("var __vflowBridgeValue = window.__vflowWebViewReturnValue;"))
    }

    @Test
    fun `document html script serializes doctype and document element`() {
        val script = JsScripts.buildDocumentHtmlScript()
        assertTrue(script.contains("document.doctype"))
        assertTrue(script.contains("documentElement.outerHTML"))
    }

    @Test
    fun `missing variable is distinguished from null`() {
        val missing = JsScripts.parseReturnVariableResult("""{"v":"missing"}""")
        assertTrue(missing is JsScripts.ReturnVariableResult.Missing)
        assertEquals("missing", JsScripts.describeType(missing))

        val nullValue = JsScripts.parseReturnVariableResult("""{"v":"ok","value":null}""")
        assertTrue(nullValue is JsScripts.ReturnVariableResult.Value)
        assertEquals(null, (nullValue as JsScripts.ReturnVariableResult.Value).value)
        assertEquals("null", JsScripts.describeType(nullValue))
    }

    @Test
    fun `primitive values keep their types`() {
        assertEquals("string", typeOf("""{"v":"ok","value":"hello"}"""))
        assertEquals("boolean", typeOf("""{"v":"ok","value":true}"""))
        assertEquals("number", typeOf("""{"v":"ok","value":3.5}"""))
        assertEquals("number", typeOf("""{"v":"ok","value":7}"""))
    }

    @Test
    fun `object and array values keep their structure`() {
        val result = JsScripts.parseReturnVariableResult("""{"v":"ok","value":{"foo":"bar"}}""") as JsScripts.ReturnVariableResult.Value
        assertEquals(mapOf("foo" to "bar"), result.value)
        assertEquals("object", JsScripts.describeType(result))

        val array = JsScripts.parseReturnVariableResult("""{"v":"ok","value":[1,2,3]}""") as JsScripts.ReturnVariableResult.Value
        assertEquals(listOf(1L, 2L, 3L), array.value)
        assertEquals("array", JsScripts.describeType(array))
    }

    @Test
    fun `js numbers normalize to long when integral`() {
        assertEquals(42L, JsScripts.normalizeJsonTree(42.0))
        assertEquals(3.5, JsScripts.normalizeJsonTree(3.5))
        assertEquals(0L, JsScripts.normalizeJsonTree(0.0))
        val big = JsScripts.normalizeJsonTree(9.007199254740991E15)
        assertEquals(9007199254740991L, big)
    }

    @Test
    fun `nested structures are normalized recursively`() {
        val tree = JsScripts.normalizeJsonTree(mapOf("a" to listOf(1.0, 2.0), "b" to mapOf("c" to 3.0)))
        assertEquals(mapOf("a" to listOf(1L, 2L), "b" to mapOf("c" to 3L)), tree)
    }

    @Test
    fun `error results are reported`() {
        val error = JsScripts.parseReturnVariableResult("""{"v":"error","error":"ReferenceError: x is not defined"}""")
        assertTrue(error is JsScripts.ReturnVariableResult.Error)
        assertEquals("error", JsScripts.describeType(error))
    }

    @Test
    fun `null callback means unavailable`() {
        assertTrue(JsScripts.parseReturnVariableResult(null) is JsScripts.ReturnVariableResult.Unavailable)
        assertTrue(JsScripts.parseReturnVariableResult("null") is JsScripts.ReturnVariableResult.Unavailable)
        assertTrue(JsScripts.parseReturnVariableResult("") is JsScripts.ReturnVariableResult.Unavailable)
    }

    @Test
    fun `variable path validation accepts identifiers and dotted paths only`() {
        assertTrue(JsScripts.isValidVariablePath("a"))
        assertTrue(JsScripts.isValidVariablePath("someVariable"))
        assertTrue(JsScripts.isValidVariablePath("window.someVar"))
        assertTrue(JsScripts.isValidVariablePath("\$x"))
        assertTrue(JsScripts.isValidVariablePath("_private"))
        assertFalse(JsScripts.isValidVariablePath("a b"))
        assertFalse(JsScripts.isValidVariablePath("a[0]"))
        assertFalse(JsScripts.isValidVariablePath("javascript:alert(1)"))
        assertFalse(JsScripts.isValidVariablePath(""))
        assertFalse(JsScripts.isValidVariablePath("alert('x')"))
    }

    @Test
    fun `wrapper output survives a gson round trip`() {
        // 模拟 evaluateJavascript 回传给宿主的内容。
        // Simulates what evaluateJavascript hands back to the host.
        val simulatedJson = gson.toJson(mapOf("v" to "ok", "value" to mapOf("foo" to "bar")))
        val result = JsScripts.parseReturnVariableResult(simulatedJson) as JsScripts.ReturnVariableResult.Value
        assertEquals(mapOf("foo" to "bar"), result.value)
    }

    @Test
    fun `document html callback json is decoded to the raw html document`() {
        // evaluateJavascript 以 JSON 字符串字面量回传文档 HTML；
        // 解码后必须得到原始 HTML（<、引号、换行全部保留）。
        // evaluateJavascript delivers the document HTML as a JSON string
        // literal; decoding must yield the original HTML (<, quotes and
        // newlines all preserved).
        val raw = "<!DOCTYPE html>\n<html lang=\"en\"><head><title>t</title></head><body>hi</body></html>"
        val callbackJson = gson.toJson(raw)
        assertEquals(raw, JsScripts.decodeStringResult(callbackJson))
    }

    @Test
    fun `unicode escaped html characters are restored`() {
        // WebView 风格的 \u003C 转义序列被还原为原始字符。
        // WebView-style \u003C escape sequences are restored to the raw
        // characters.
        assertEquals("<html lang=\"en\">", JsScripts.decodeStringResult("\"\\u003Chtml lang=\\\"en\\\"\\u003E\""))
    }

    @Test
    fun `null or empty string callback yields null`() {
        // 回调丢失 / JSON null 字面量 / 空白载荷都返回 null，
        // 调用方回退到中继响应的精确字节。
        // A dropped callback / the JSON null literal / a blank payload all
        // return null; the caller falls back to the relay response's exact
        // bytes.
        assertNull(JsScripts.decodeStringResult(null))
        assertNull(JsScripts.decodeStringResult("null"))
        assertNull(JsScripts.decodeStringResult(""))
        assertNull(JsScripts.decodeStringResult("   "))
    }

    private fun typeOf(json: String): String =
        JsScripts.describeType(JsScripts.parseReturnVariableResult(json))
}
