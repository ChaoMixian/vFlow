// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewJs.kt
// 描述: WebView 模块在页面上下文中构造与解释 JavaScript 的工具。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewJs.kt
// Description: Utilities for building and interpreting the JavaScript that
//      runs inside the WebView module's page context.

package com.chaomixian.vflow.core.workflow.module.network.webview

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException

/**
 * 构造与解释 WebView 页面上下文中执行的 JavaScript。
 *
 * 支持两个相互独立的 JS 输入：
 *
 *  * `custom_js`      —— 每次主框架页面加载完成后（页面就绪）执行。
 *                        面向副作用：可以创建/修改变量、操纵 DOM 或导航。
 *                        顶层 var / let / const / 隐式全局声明都成为全局
 *                        绑定，能被 `return_variable` 求值看到（见
 *                        [buildCustomJsAttemptScript]）。错误会被捕获进
 *                        `js_error` 输出，但永远不会导致模块失败。
 *  * `return_variable` —— 仅在完成时（后台模式稳定后，或用户按下确认按钮时）
 *                        于同一个存活的页面上下文中求值一次。若变量未定义
 *                        而自定义 JS 使用了顶层 `return`，则回退到该返回值。
 *
 * Builds and interprets the JavaScript executed in the WebView's page
 * context.
 *
 * Two independent JS inputs are supported:
 *
 *  * `custom_js`      — executed after each main-frame page load (page
 *                      ready). Side-effect oriented: it may create/modify
 *                      variables, manipulate the DOM or navigate. Top-level
 *                      var / let / const / implicit-global declarations all
 *                      become global bindings that `return_variable` can
 *                      see (see [buildCustomJsAttemptScript]). Errors are
 *                      captured into the `js_error` output but never fail
 *                      the module.
 *  * `return_variable` — evaluated exactly once, at completion time (after
 *                      headless mode settles, or when the user presses the
 *                      confirm button), in the same live page context. If
 *                      the variable is undefined and the custom JS used a
 *                      top-level `return`, the evaluation falls back to
 *                      that return value.
 */
object JsScripts {

    /** 点分 JavaScript 标识符路径，例如 `someVariable` 或 `a.b.c`。
     *  A dotted JavaScript identifier path, e.g. `someVariable` or
     *  `a.b.c`. */
    fun isValidVariablePath(name: String): Boolean =
        Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*){0,8}$").matches(name)

    /**
     * 自定义 JS 第一阶段：把用户脚本原样拼接到脚本的顶层执行（不经函数
     * 包装）。这保证返回变量始终可见：若把用户代码包在 `(function(){ … })()`
     * 里执行，`var` / `let` / `const` 声明会随函数作用域一起消失，随后的
     * 返回变量求值将永远得到 `typeof x === 'undefined'`。
     *
     * 顶层执行使全部常见声明形式都成为全局绑定：
     *  - `var x` / 隐式 `x =` / `window.x =` 挂到全局对象；
     *  - 顶层 `let x` / `const x` 进入全局词法环境，后续脚本（包括返回变量
     *    求值脚本）同样能看到。
     *
     * 错误捕获：安装一个自恢复的 `window.onerror`（未捕获的运行时错误会
     * 中止脚本、使 evaluateJavascript 回调收到 null，但 onerror 先把错误
     * 文本记录到 `window.__vflowWebViewLastError`）。脚本正常跑完时，收尾
     * IIFE 恢复先前的 onerror 并返回哨兵对象 `{v:'ran', error}` 作为脚本
     * 完成值 —— 哨兵把“脚本跑完了”与“回调因错误/解析失败而丢失”区分开。
     *
     * Custom JS stage one: the user script is concatenated verbatim at the
     * TOP LEVEL of the script (no function wrapper). This keeps the return
     * variable visible: if the user code were run inside `(function(){ … })()`,
     * `var` / `let` / `const` declarations would vanish with the function
     * scope and the later return-variable evaluation would always see
     * `typeof x === 'undefined'`.
     *
     * Top-level execution makes every common declaration form a global
     * binding:
     *  - `var x` / implicit `x =` / `window.x =` land on the global object;
     *  - top-level `let x` / `const x` enter the global lexical environment,
     *    equally visible to later scripts (including the return-variable
     *    evaluation script).
     *
     * Error capture: a self-restoring `window.onerror` is installed (an
     * uncaught runtime error aborts the script and makes the
     * evaluateJavascript callback receive null, but onerror first records
     * the error text into `window.__vflowWebViewLastError`). When the script
     * runs to completion, a trailing IIFE restores the previous onerror and
     * returns the sentinel object `{v:'ran', error}` as the script's
     * completion value — the sentinel distinguishes "the script ran" from
     * "the callback was dropped because of an error / parse failure".
     */
    fun buildCustomJsAttemptScript(userJs: String): String =
        "window.__vflowWebViewLastError = null;\n" +
            "window.__vflowWebViewReturnValue = undefined;\n" +
            "var __vflowWebViewPrevOnError = window.onerror;\n" +
            "window.__vflowWebViewAttemptActive = true;\n" +
            "window.onerror = function (msg) {\n" +
            "  window.__vflowWebViewLastError = String(msg);\n" +
            "  window.__vflowWebViewAttemptActive = false;\n" +
            "  window.onerror = __vflowWebViewPrevOnError;\n" +
            "  return true;\n" +
            "};\n" +
            "/* ---- user script (verbatim, top level) ---- */\n" +
            userJs + "\n" +
            "/* ---- end of user script ---- */\n" +
            ";\n" +
            "(function () {\n" +
            "  window.onerror = __vflowWebViewPrevOnError;\n" +
            "  try { delete window.__vflowWebViewAttemptActive; } catch (e) {}\n" +
            "  return { v: 'ran', error: window.__vflowWebViewLastError };\n" +
            "})()\n"

    /**
     * 自定义 JS 第二阶段（恢复脚本）：第一阶段回调丢失（运行时错误或解析
     * 失败）时执行。一次脚本同时完成两件事：
     *
     *  1. 探测并清理第一阶段残留 —— 读取并清除
     *     `window.__vflowWebViewLastError`；若 onerror 句柄因脚本中止而
     *     未被收尾 IIFE 恢复（`__vflowWebViewAttemptActive` 仍为 true），
     *     恢复先前的句柄。若残留的是运行时错误文本，直接把它作为
     *     `js_error` 上报（错误点之前赋值的全局变量仍然存活可用）。
     *  2. 解析失败路径（例如用户使用了顶层 `return`）：退回函数包装执行
     *     —— 顶层 `return` 合法，其返回值存入
     *     `window.__vflowWebViewReturnValue` 桥（返回变量求值在变量缺失时
     *     会读取它）。包装内声明的变量是函数作用域的（无法全局可见），
     *     这是顶层 `return` 语法的固有代价；需要全局可见请用顶层声明。
     *
     * 恢复脚本自身也无法解析（真正的语法错误）时回调为 null，调用方据此
     * 报告“自定义 JS 未执行（语法错误?）”。
     *
     * Custom JS stage two (the recovery script), executed when stage one's
     * callback was dropped (a runtime error or a parse failure). One script
     * does both jobs:
     *
     *  1. Probe and clean stage-one leftovers — read and clear
     *     `window.__vflowWebViewLastError`; if the onerror handler was never
     *     restored because the script aborted (`__vflowWebViewAttemptActive`
     *     still true), restore the previous handler. When the leftover is a
     *     runtime error text it is reported as `js_error` directly (globals
     *     assigned before the failing statement still survive and work).
     *  2. Parse-failure path (e.g. the user used a top-level `return`): fall
     *     back to function-wrapped execution — a top-level `return` is legal
     *     and its value is stored into the `window.__vflowWebViewReturnValue`
     *     bridge (the return-variable evaluation reads it when the variable
     *     is missing). Variables declared inside the wrapper are
     *     function-scoped (not globally visible) — the inherent cost of
     *     top-level `return` syntax; use top-level declarations when global
     *     visibility is needed.
     *
     * When the recovery script itself cannot parse (a genuine syntax error)
     * its callback is null, from which the caller reports "custom JS did not
     * execute (syntax error?)".
     */
    fun buildCustomJsRecoveryScript(userJs: String): String =
        "(function () {\n" +
            "  var leftover = (typeof window.__vflowWebViewLastError === 'string') ? window.__vflowWebViewLastError : null;\n" +
            "  if (window.__vflowWebViewLastError !== undefined) { try { delete window.__vflowWebViewLastError; } catch (e) {} }\n" +
            "  if (window.__vflowWebViewAttemptActive === true) {\n" +
            "    try { window.onerror = window.__vflowWebViewPrevOnError; } catch (e) {}\n" +
            "    try { delete window.__vflowWebViewAttemptActive; } catch (e) {}\n" +
            "  }\n" +
            "  if (leftover !== null) return { v: 'runtime', error: leftover };\n" +
            "  try {\n" +
            "    window.__vflowWebViewReturnValue = (function () {\n" +
            userJs + "\n" +
            "    })();\n" +
            "    return { v: 'wrapped', error: null };\n" +
            "  } catch (e) {\n" +
            "    return { v: 'wrapped', error: String(e) };\n" +
            "  }\n" +
            "})()\n"

    /**
     * 直接在页面上下文中求值返回变量：
     * `typeof` 区分“变量不存在”与“显式的 null 值”，
     * 引用错误（例如安全限制）会被报告为 error 而不是 missing。
     * 变量未定义而自定义 JS 的恢复路径记录了顶层 `return` 的返回值时
     * （`window.__vflowWebViewReturnValue` 桥），回退到该返回值。
     *
     * Evaluates the return variable directly in the page context:
     * `typeof` distinguishes "the variable does not exist" from "an
     * explicit null value"; reference errors (e.g. security restrictions)
     * are reported as error rather than missing. When the variable is
     * undefined but the custom JS recovery path recorded a top-level
     * `return` value (the `window.__vflowWebViewReturnValue` bridge), the
     * evaluation falls back to that return value.
     */
    fun buildReturnVariableScript(variableName: String): String =
        "(function(){\n" +
            "  try {\n" +
            "    if (typeof $variableName === 'undefined') {\n" +
            "      var __vflowBridgeValue = window.__vflowWebViewReturnValue;\n" +
            "      if (typeof __vflowBridgeValue !== 'undefined') {\n" +
            "        return { v: 'ok', value: __vflowBridgeValue, via: 'return' };\n" +
            "      }\n" +
            "      return { v: 'missing' };\n" +
            "    }\n" +
            "    return { v: 'ok', value: $variableName };\n" +
            "  } catch (e) {\n" +
            "    return { v: 'error', error: String(e) };\n" +
            "  }\n" +
            "})()"

    /**
     * 返回当前文档序列化结果（doctype + outer HTML）的脚本。
     * `response_body` 反映最终显示的页面，包括自定义 JS 或页面脚本
     * 做出的 DOM 修改。
     *
     * Returns a script that serializes the current document (doctype +
     * outer HTML). `response_body` reflects the finally displayed page,
     * including DOM modifications made by custom JS or page scripts.
     */
    fun buildDocumentHtmlScript(): String =
        "(function(){\n" +
            "  var d = document.doctype ? '<!DOCTYPE ' + document.doctype.name + '>\\n' : '';\n" +
            "  return d + document.documentElement.outerHTML;\n" +
            "})()"

    fun buildDocumentCookieScript(): String =
        "(function(){ try { return { v: 'ok', value: document.cookie }; } catch (e) { return { v: 'error', error: String(e) } } })()"

    private val gson = Gson()

    /**
     * 第一阶段（顶层尝试）的执行结果。
     *
     * The outcome of stage one (the top-level attempt).
     */
    sealed class CustomJsAttempt {
        /** 顶层脚本跑到收尾 IIFE（哨兵已回传）；error 为 onerror 捕获到的
         *  运行时错误文本（无错为 null —— 实际上未捕获错误会中止脚本，
         *  使回调变 null，因此此处几乎总为 null）。
         *  The top-level script reached its trailing IIFE (the sentinel was
         *  delivered); error is the runtime error text captured by onerror
         *  (null when none — in practice an uncaught error aborts the script
         *  and turns the callback null, so this is almost always null). */
        data class Ran(val error: String?) : CustomJsAttempt()

        /** 回调丢失（null / 空 / 非哨兵载荷）：运行时错误或解析失败，
         *  需要恢复脚本分辨。
         *  The callback was dropped (null / empty / non-sentinel payload):
         *  a runtime error or a parse failure; the recovery script tells
         *  them apart. */
        object NeedsRecovery : CustomJsAttempt()
    }

    /**
     * 解释第一阶段（顶层尝试）的回调 JSON。
     *
     * Interprets the stage-one (top-level attempt) callback JSON.
     */
    fun parseCustomJsAttempt(callbackJson: String?): CustomJsAttempt {
        if (callbackJson == null) return CustomJsAttempt.NeedsRecovery
        val trimmed = callbackJson.trim()
        if (trimmed.isEmpty() || trimmed == "null") return CustomJsAttempt.NeedsRecovery
        return try {
            val parsed: Any? = gson.fromJson(trimmed, Any::class.java)
            if (parsed is Map<*, *> && parsed["v"] == "ran") {
                CustomJsAttempt.Ran((parsed["error"] as? String)?.takeIf { it.isNotEmpty() })
            } else {
                CustomJsAttempt.NeedsRecovery
            }
        } catch (_: Exception) {
            CustomJsAttempt.NeedsRecovery
        }
    }

    /**
     * 第二阶段（恢复脚本）的执行结果。
     *
     * The outcome of stage two (the recovery script).
     */
    sealed class CustomJsRecovery {
        /** 恢复脚本捕获到错误文本（第一阶段的运行时错误，或包装执行中的
         *  错误）。
         *  The recovery script captured an error text (stage one's runtime
         *  error, or an error inside the wrapped execution). */
        data class Error(val message: String) : CustomJsRecovery()

        /** 恢复脚本干净跑完（解析失败路径的包装执行无错误）。
         *  The recovery script ran clean (the wrapped execution on the
         *  parse-failure path had no error). */
        object RanClean : CustomJsRecovery()

        /** 恢复脚本自身也未运行（两阶段都无法解析 —— 真正的语法错误）。
         *  The recovery script itself did not run either (neither stage
         *  could parse — a genuine syntax error). */
        object FailedToRun : CustomJsRecovery()
    }

    /**
     * 解释第二阶段（恢复脚本）的回调 JSON。
     *
     * Interprets the stage-two (recovery script) callback JSON.
     */
    fun parseCustomJsRecovery(callbackJson: String?): CustomJsRecovery {
        if (callbackJson == null) return CustomJsRecovery.FailedToRun
        val trimmed = callbackJson.trim()
        if (trimmed.isEmpty() || trimmed == "null") return CustomJsRecovery.FailedToRun
        return try {
            val parsed: Any? = gson.fromJson(trimmed, Any::class.java)
            if (parsed !is Map<*, *>) return CustomJsRecovery.FailedToRun
            val error = (parsed["error"] as? String)?.takeIf { it.isNotEmpty() }
            when (parsed["v"]) {
                "runtime" -> CustomJsRecovery.Error(error ?: "unknown runtime error")
                "wrapped" -> error?.let { CustomJsRecovery.Error(it) } ?: CustomJsRecovery.RanClean
                else -> CustomJsRecovery.FailedToRun
            }
        } catch (_: Exception) {
            CustomJsRecovery.FailedToRun
        }
    }

    /**
     * 返回变量包装器的求值结果。
     *
     * The evaluation result of the return-variable wrapper.
     */
    sealed class ReturnVariableResult {
        /** 变量存在且持有该 JSON 值（可能为 null）。
         *  The variable exists and holds this JSON value (possibly null). */
        data class Value(val value: Any?) : ReturnVariableResult()

        /** 变量从未在页面上下文中定义。
         *  The variable was never defined in the page context. */
        object Missing : ReturnVariableResult()

        /** 求值失败（语法 / 安全错误）。
         *  Evaluation failed (syntax / security error). */
        data class Error(val message: String) : ReturnVariableResult()

        /** WebView 丢弃了回调（导航）或超时。
         *  The WebView dropped the callback (navigation) or timed out. */
        object Unavailable : ReturnVariableResult()
    }

    /**
     * 解释 evaluateJavascript 回调交付的 JSON 字符串（返回变量包装器）。
     *
     * Interprets the JSON string delivered by an evaluateJavascript
     * callback (the return-variable wrapper).
     */
    fun parseReturnVariableResult(callbackJson: String?): ReturnVariableResult {
        if (callbackJson == null) return ReturnVariableResult.Unavailable
        val trimmed = callbackJson.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ReturnVariableResult.Unavailable
        return try {
            val parsed: Any? = gson.fromJson(trimmed, Any::class.java)
            if (parsed !is Map<*, *>) return ReturnVariableResult.Unavailable
            when (parsed["v"]) {
                "missing" -> ReturnVariableResult.Missing
                "error" -> ReturnVariableResult.Error(parsed["error"]?.toString() ?: "unknown error")
                "ok" -> ReturnVariableResult.Value(normalizeJsonTree(parsed["value"]))
                else -> ReturnVariableResult.Unavailable
            }
        } catch (e: JsonSyntaxException) {
            ReturnVariableResult.Error("unexpected result: ${e.message}")
        } catch (e: JsonParseException) {
            ReturnVariableResult.Error("unexpected result: ${e.message}")
        }
    }

    /**
     * 解释 evaluateJavascript 回调交付的纯字符串结果（如文档 HTML 序列化）。
     * 回调交付的是 JSON 编码的字符串字面量：带引号，HTML 字符被转义为
     * `\u003C` 之类的 Unicode 序列。这里把它解码为原始字符串 ——
     * `response_body` 必须是原始 HTML 文档而不是它的 JSON 表示。
     *
     * Interprets a plain-string result delivered by an evaluateJavascript
     * callback (e.g. the document HTML serialization). The callback delivers
     * a JSON-encoded string literal: quoted, with HTML characters escaped
     * as `\u003C`-style unicode sequences. This decodes it back to the
     * original string — `response_body` must be the original HTML document,
     * not its JSON representation.
     *
     * @return 解码后的字符串；回调丢失、脚本返回 null、或载荷不是 JSON
     *         字符串时返回 null（调用方回退到中继响应的精确字节）。
     *         The decoded string; null when the callback was dropped, the
     *         script returned null, or the payload is not a JSON string
     *         (the caller falls back to the relay response's exact bytes).
     */
    fun decodeStringResult(callbackJson: String?): String? {
        if (callbackJson == null) return null
        val trimmed = callbackJson.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        return try {
            gson.fromJson(trimmed, String::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gson 会把 JSON 数字解析为 Double。能精确往返的整数会被转换回 Long，
     * 使得 JS 整数值以整数形式进入工作流（VNumber 保留 Int/Long），
     * 非整数数值保持浮点。
     *
     * Gson parses JSON numbers as Double. Integers that round-trip exactly
     * are converted back to Long so JS integer values enter the workflow
     * as integers (VNumber preserves Int/Long); non-integer values stay
     * floating-point.
     */
    fun normalizeJsonTree(value: Any?): Any? = when (value) {
        is Double -> {
            if (value.isFinite() && value == Math.floor(value) && Math.abs(value) <= 9.007199254740992E15) {
                value.toLong()
            } else {
                value
            }
        }
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normalizeJsonTree(v) }
        is List<*> -> value.map { normalizeJsonTree(it) }
        else -> value
    }

    /**
     * 描述返回值的 JS 类型，用于 `return_value_type` 输出：
     * string / number / boolean / object / array / null / missing / error / oversized。
     *
     * Describes the JS type of the return value, used by the
     * `return_value_type` output: string / number / boolean / object /
     * array / null / missing / error / oversized.
     */
    fun describeType(result: ReturnVariableResult): String = when (result) {
        is ReturnVariableResult.Missing -> "missing"
        is ReturnVariableResult.Unavailable -> "missing"
        is ReturnVariableResult.Error -> "error"
        is ReturnVariableResult.Value -> when (val v = result.value) {
            null -> "null"
            is String -> "string"
            is Boolean -> "boolean"
            is Number -> "number"
            is Map<*, *> -> "object"
            is List<*> -> "array"
            else -> "string"
        }
    }
}
