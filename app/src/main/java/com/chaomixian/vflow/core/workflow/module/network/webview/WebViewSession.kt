// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewSession.kt
// 描述: WebView 模块的一次执行会话：WebView 生命周期、主框架中继、
//      自定义 JS、返回变量求值与确认界面（非后台模式）。
//      工作流协程通过 CompletableDeferred 直接挂起等待会话完成。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewSession.kt
// Description: One execution session of the WebView module: WebView
//      lifecycle, main-frame relay, custom JS, return-variable evaluation
//      and the confirmation screen (non-headless mode).
//      The workflow coroutine suspends on a CompletableDeferred until
//      the session completes.

package com.chaomixian.vflow.core.workflow.module.network.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaomixian.vflow.ui.webview.WebViewConfirmationActivity
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一次 WebView 模块执行。
 *
 * 所有权与线程模型
 * -----------------
 *  - 模块的 execute() 协程创建会话并挂起等待 [deferred]；所有 WebView /
 *    Handler 工作都被调度到主线程（[WebViewSessionManager.mainHandler]）。
 *  - 初始主框架请求链被拦截（shouldInterceptRequest），由 [MainFrameRelay]
 *    以用户配置的方法/请求头/请求体执行，并在每一跳把 Cookie 喂给
 *    CookieManager。重定向被中继回 WebView，使 document.location 与相对
 *    URL 保持正确；看门狗在重定向停滞时通过 loadUrl 重新驱动。
 *  - 后台模式：每次主框架页面加载后执行自定义 JS；导航稳定
 *    （[SETTLE_MS] 内没有新的主框架导航）后自动完成。
 *  - 非后台模式：会话保持暂停，直到用户在 [WebViewConfirmationActivity]
 *    中按下确认按钮（显式的确认闸门）；然后在同一个存活的页面上下文中
 *    求值返回变量并产出输出。
 *
 * 所有临时状态（WebView、中继、收集器、定时器）都不会被序列化 ——
 * 可序列化的模块配置由宿主持有（步骤参数），工作流保存/恢复正常工作。
 *
 * One WebView module execution.
 *
 * Ownership and threading model
 * -----------------------------
 *  - The module's execute() coroutine creates the session and suspends on
 *    [deferred]; all WebView / Handler work is scheduled on the main thread
 *    ([WebViewSessionManager.mainHandler]).
 *  - The initial main-frame request chain is intercepted
 *    (shouldInterceptRequest) and executed by [MainFrameRelay] with the
 *    user-configured method/headers/body, feeding cookies into the
 *    CookieManager at every hop. Redirects are relayed back into the
 *    WebView so document.location and relative URLs stay correct; a
 *    watchdog re-drives stalled redirects via loadUrl.
 *  - Headless mode: custom JS runs after each main-frame page load; the
 *    session completes automatically once navigation settles (no new
 *    main-frame navigation for [SETTLE_MS]).
 *  - Non-headless mode: the session stays paused until the user presses the
 *    confirm button in [WebViewConfirmationActivity] (an explicit
 *    confirmation gate); the return variable is then evaluated and outputs
 *    are produced in the same live page context.
 *
 * No transient state (WebView, relay, collectors, timers) is serialized —
 * the serializable module configuration is owned by the host (step
 * parameters), so workflow save/restore keeps working.
 */
class WebViewSession(
    val sessionId: String,
    val spec: WebViewRequestSpec,
    private val appContext: Context
) {
    /** 由会话在主线程完成；由模块的 execute() 协程挂起等待。
     *  Completed by the session on the main thread; awaited by the module's
     *  execute() coroutine. */
    private val completion = CompletableDeferred<SessionOutcome>()
    val deferred: CompletableDeferred<SessionOutcome> get() = completion

    /** 挂起等待会话结束（支持协程取消 —— 取消时调用方负责 [cancel]）。
     *  Suspends until the session ends (cancellation-aware — the caller is
     *  responsible for calling [cancel] when cancelled). */
    suspend fun await(): SessionOutcome = completion.await()

    private val startedAt = SystemClock.elapsedRealtime()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private val userAgent: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(appContext) }.getOrDefault("Android")
    }

    // --- 主线程状态 / Main-thread state -------------------------------------
    @Volatile private var webView: WebView? = null
    @Volatile private var relay: MainFrameRelay? = null
    private var attached = false
    private var loadTriggered = false
    private var jsRuns = 0
    private var jsErrorText = ""
    private var collecting = false
    private var activity: WebViewConfirmationActivity? = null
    private var activityReady = false
    private val cleaned = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)

    // --- 公开入口（线程安全） / Public entry points (thread-safe) ------------

    /** 启动会话（主线程）。
     *  Starts the session (main thread). */
    fun start() {
        mainHandler.post {
            if (finished.get()) return@post
            try {
                startOnMain()
            } catch (e: Exception) {
                fail(SessionFailure.INTERNAL, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** 从任意线程使会话失败（幂等）。工作流取消时调用 [cancel]。
     *  Fails the session from any thread (idempotent). [cancel] is invoked
     *  when the workflow is cancelled. */
    fun cancel() {
        postFailure(SessionFailure.CANCELLED)
    }

    /** 从任意线程投递一个失败（幂等）。
     *  Posts a failure from any thread (idempotent). */
    fun postFailure(failure: SessionFailure, detail: String? = null) {
        mainHandler.post { fail(failure, detail) }
    }

    /** 确认界面中按下了确认按钮（主线程）。
     *  The confirm button was pressed on the confirmation screen (main
     *  thread). */
    fun confirmFromUi() {
        if (finished.get() || collecting) return
        if (webView == null) {
            fail(SessionFailure.INTERNAL, "WebView is not attached")
            return
        }
        startCompletion()
    }

    /** 确认界面被销毁（主线程）。
     *  The confirmation screen was destroyed (main thread). */
    fun onActivityDestroyed(confirmed: Boolean) {
        activity = null
        if (!confirmed && !finished.get()) {
            fail(SessionFailure.CANCELLED)
        }
    }

    /** 附着由后台驱动或确认界面创建的 WebView。
     *  Attaches the WebView created by the background driver or the
     *  confirmation screen. */
    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(view: WebView, fromActivity: WebViewConfirmationActivity?) {
        mainHandler.post {
            if (attached || finished.get()) {
                // 会话已结束：调用方持有该视图的所有权。
                // The session already ended: the caller owns the view.
                fromActivity?.let { activity = it }
                return@post
            }
            activity = fromActivity
            if (fromActivity != null) activityReady = true
            attached = true
            webView = view
            configureWebView(view)
            triggerInitialLoad(view)
        }
    }

    // --- 内部 / Internal ------------------------------------------------------

    private fun startOnMain() {
        val cookieManager = CookieManager.getInstance()
        runCatching { cookieManager.setAcceptCookie(true) }

        val plan = RequestPlan(
            url = spec.url,
            method = spec.method,
            userHeaders = spec.headers,
            bodyText = spec.body
        )
        val collector = CookieCollector()
        collector.observeUrl(spec.url)
        val r = MainFrameRelay(
            plan = plan,
            client = OkHttpHopClient(),
            cookieStore = WebViewCookieStore(),
            cookieCollector = collector,
            userAgent = userAgent
        )
        relay = r

        if (spec.headless) {
            val view = WebView(appContext.applicationContext)
            attachWebView(view, null)
        } else {
            val intent = Intent(appContext, WebViewConfirmationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(WebViewConfirmationActivity.EXTRA_SESSION_ID, sessionId)
            }
            runCatching { appContext.startActivity(intent) }
                .onFailure { fail(SessionFailure.ACTIVITY_LAUNCH_FAILED) }
        }

        mainHandler.post(watchdogRunnable)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            supportZoom()
            setSupportMultipleWindows(false)
        }
        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(view, true) }
        view.webViewClient = RelayWebViewClient()
        view.webChromeClient = TitleChromeClient()
    }

    /**
     * 触发会被中继拦截的主框架导航：
     * GET -> loadUrl；其他方法 -> postUrl（带配置方法/请求头/请求体的真实
     * 请求由中继执行）。
     *
     * Triggers the main-frame navigation that the relay intercepts:
     * GET -> loadUrl; other methods -> postUrl (the real request with the
     * configured method/headers/body is performed by the relay).
     */
    private fun triggerInitialLoad(view: WebView) {
        if (loadTriggered) return
        loadTriggered = true
        if (spec.method == "GET") {
            view.loadUrl(spec.url)
        } else {
            val body = if (spec.body.isNotEmpty()) spec.body.toByteArray(Charsets.UTF_8) else ByteArray(0)
            view.postUrl(spec.url, body)
        }
    }

    /**
     * 标题更新驱动确认界面的应用栏（onReceivedTitle 位于 WebChromeClient）：
     * 页面的文档标题，页面没有标题时使用 URL 的域名。
     *
     * Title updates drive the confirmation screen's app bar
     * (onReceivedTitle lives on WebChromeClient): the page's document
     * title, falling back to the URL's domain when the page has no title.
     */
    private inner class TitleChromeClient : android.webkit.WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) {
            activity?.onPageTitleReceived(title, view.url)
        }
    }

    private inner class RelayWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            // 只有初始主框架链被中继；其余一切（子资源、XHR、链完成后的
            // 用户导航）保持 100% 原生 WebView 行为 —— 包括 POST 请求体。
            // Only the initial main-frame chain is relayed; everything else
            // (subresources, XHR, user navigation after the chain finished)
            // keeps 100% native WebView behavior — including POST bodies.
            if (!request.isForMainFrame) return null
            val currentRelay = relay ?: return null
            if (!currentRelay.isChainActive()) return null
            return try {
                when (val decision = currentRelay.intercept(request.url.toString())) {
                    is MainFrameRelay.RelayDecision.PassThrough -> null
                    is MainFrameRelay.RelayDecision.Respond -> decision.toWebResourceResponse()
                }
            } catch (e: RelayHopException) {
                postFailure(SessionFailure.NETWORK, e.message)
                errorResponse()
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url == "about:blank") return
            cancelSettleTimer()
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url == "about:blank") return
            relay?.notifyPageFinished()
            runCustomJsAndSettle(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError
        ) {
            // 链完成后失败的原生主框架加载（用户导航）留给用户处理；
            // 其余情况由后台模式的截止时间兑底。
            // Native main-frame loads (user navigation) that fail after the
            // chain completed are left to the user; everything else is
            // covered by the headless-mode deadline.
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // 自动化流程显式选择了目标站点；如同用户同意后的浏览器一样继续。
            // The automated flow explicitly selected the target site;
            // proceed like a browser after explicit user consent.
            handler.proceed()
        }
    }

    private fun MainFrameRelay.RelayDecision.Respond.toWebResourceResponse(): WebResourceResponse {
        val contentType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
        val (mime, encoding) = parseContentType(contentType)
        val headerMap = LinkedHashMap<String, String>()
        for ((name, value) in headers) {
            val existing = headerMap.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) {
                headerMap[existing] = "${headerMap[existing]}, $value"
            } else {
                headerMap[name] = value
            }
        }
        return WebResourceResponse(mime, encoding, statusCode, reasonPhrase, headerMap, java.io.ByteArrayInputStream(body))
    }

    private fun parseContentType(contentType: String?): Pair<String, String> {
        if (contentType.isNullOrBlank()) return "text/html" to "utf-8"
        val parts = contentType.split(';')
        val mime = parts[0].trim().ifEmpty { "text/html" }
        val charset = parts.drop(1)
            .firstOrNull { it.trim().lowercase().startsWith("charset=") }
            ?.substringAfter('=')?.trim()
        return mime to (charset ?: "utf-8")
    }

    private fun errorResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 502, "vFlow WebView Relay Error", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))

    private fun runCustomJsAndSettle(view: WebView, url: String) {
        if (finished.get() || collecting) return
        if (spec.hasCustomJs && jsRuns < MAX_JS_RUNS) {
            jsRuns++
            runCustomJs(view) { errorText ->
                if (!finished.get() && !collecting) {
                    if (errorText.isNotEmpty()) jsErrorText = errorText
                    if (spec.headless) armSettleTimer()
                }
            }
        } else if (spec.headless) {
            armSettleTimer()
        }
    }

    /**
     * 两阶段执行自定义 JS：
     *  1. 顶层尝试 —— 用户脚本原样拼接在脚本顶层执行，var / let /
     *     const / 隐式全局声明都成为全局绑定，随后的返回变量求值可以
     *     看到它们（函数包装会把声明限制在局部作用域，返回值将总是
     *     missing）；
     *  2. 回调丢失（运行时错误或解析失败，如顶层 return）时执行恢复
     *     脚本 —— 上报运行时错误，或以函数包装重跑（顶层 return 合法，
     *     返回值进入桥变量供返回变量求值回退）。
     *
     * Runs the custom JS in two stages:
     *  1. The top-level attempt — the user script is concatenated verbatim
     *     at script top level, so var / let / const / implicit-global
     *     declarations all become global bindings the later
     *     return-variable evaluation can see (a function wrapper would
     *     confine them to its local scope, leaving the return value
     *     always missing);
     *  2. When the callback is dropped (a runtime error or a parse failure
     *     such as a top-level return), the recovery script runs — it
     *     reports the runtime error, or re-runs the code function-wrapped
     *     (a top-level return is legal; its value goes into the bridge
     *     variable the return-variable evaluation falls back to).
     */
    private fun runCustomJs(view: WebView, onDone: (String) -> Unit) {
        evaluateAsync(view, JsScripts.buildCustomJsAttemptScript(spec.customJs), JS_EVAL_TIMEOUT_MS) { attemptResult ->
            when (val attempt = JsScripts.parseCustomJsAttempt(attemptResult)) {
                is JsScripts.CustomJsAttempt.Ran -> onDone(attempt.error ?: "")
                is JsScripts.CustomJsAttempt.NeedsRecovery ->
                    evaluateAsync(
                        view,
                        JsScripts.buildCustomJsRecoveryScript(spec.customJs),
                        JS_EVAL_TIMEOUT_MS
                    ) { recoveryResult ->
                        val errorText = when (val recovery = JsScripts.parseCustomJsRecovery(recoveryResult)) {
                            is JsScripts.CustomJsRecovery.Error -> recovery.message
                            is JsScripts.CustomJsRecovery.RanClean -> ""
                            is JsScripts.CustomJsRecovery.FailedToRun -> CUSTOM_JS_SYNTAX_ERROR_MESSAGE
                        }
                        onDone(errorText)
                    }
            }
        }
    }

    private fun armSettleTimer() {
        cancelSettleTimer()
        mainHandler.postDelayed(settleRunnable, SETTLE_MS)
    }

    private fun cancelSettleTimer() {
        mainHandler.removeCallbacks(settleRunnable)
    }

    private val settleRunnable = Runnable {
        if (!finished.get() && !collecting && spec.headless) {
            startCompletion()
        }
    }

    private val watchdogRunnable: Runnable = object : Runnable {
        override fun run() {
            if (finished.get()) return
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - startedAt
            val deadline = spec.executionDeadlineMs

            val currentRelay = relay
            if (currentRelay != null) {
                if (currentRelay.redirectStalled || currentRelay.tooManyRedirects) {
                    fail(SessionFailure.REDIRECT_LOOP)
                    return
                }
                val takeOver = currentRelay.takeOverIfStalled()
                if (takeOver != null) {
                    val view = webView
                    if (view != null && !collecting) {
                        runCatching { view.loadUrl(takeOver) }
                    }
                }
            }

            if (!spec.headless && !activityReady && elapsed > ACTIVITY_READY_TIMEOUT_MS) {
                fail(SessionFailure.ACTIVITY_LAUNCH_FAILED)
                return
            }

            if (deadline != null && elapsed >= deadline) {
                if (collecting) {
                    // 给进行中的 JS 收集链多留一秒，然后硬性中止，
                    // 保证调用方总能拿到结果。
                    // Give the in-flight JS collection chain one extra
                    // second, then abort hard so the caller always gets a
                    // result.
                    if (elapsed >= deadline + JS_EVAL_TIMEOUT_MS) {
                        fail(SessionFailure.TIMEOUT)
                    }
                } else {
                    fail(SessionFailure.TIMEOUT)
                }
            }

            if (!finished.get()) {
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    /**
     * 完成链：返回变量 -> 文档 HTML -> document.cookie，
     * 全部在当前（同一个）页面上下文中求值，然后组装输出并结束会话。
     *
     * Completion chain: return variable -> document HTML -> document.cookie,
     * all evaluated in the current (same) page context, then the outputs
     * are assembled and the session completes.
     */
    private fun startCompletion() {
        if (finished.get() || collecting) return
        collecting = true
        cancelSettleTimer()
        val view = webView ?: run {
            fail(SessionFailure.INTERNAL, "WebView is not attached")
            return
        }
        stepReturnVariable(view) { returnResult ->
            stepDocumentHtml(view) { html ->
                stepDocumentCookie(view) { documentCookie ->
                    finishWith(returnResult, html, documentCookie)
                }
            }
        }
    }

    private fun stepReturnVariable(view: WebView, onDone: (JsScripts.ReturnVariableResult) -> Unit) {
        val variable = spec.returnVariable
        if (variable == null) {
            onDone(JsScripts.ReturnVariableResult.Missing)
            return
        }
        evaluateAsync(view, JsScripts.buildReturnVariableScript(variable), JS_EVAL_TIMEOUT_MS) { result ->
            val parsed = JsScripts.parseReturnVariableResult(result)
            if (parsed is JsScripts.ReturnVariableResult.Unavailable) {
                onDone(
                    JsScripts.ReturnVariableResult.Error(
                        "timeout or navigation during evaluation"
                    )
                )
            } else {
                onDone(parsed)
            }
        }
    }

    private fun stepDocumentHtml(view: WebView, onDone: (String?) -> Unit) {
        // evaluateJavascript 的回调交付的是 JSON 编码的字符串（带引号，
        // HTML 字符被转义为 \u003C 之类的 Unicode 序列）。必须先解码，
        // response_body 才是原始 HTML 文档而不是它的 JSON 表示。
        // The evaluateJavascript callback delivers a JSON-encoded string
        // (quoted, with HTML characters escaped as \u003C-style unicode
        // sequences). It must be decoded first so that response_body is the
        // original HTML document rather than its JSON representation.
        evaluateAsync(view, JsScripts.buildDocumentHtmlScript(), JS_EVAL_TIMEOUT_MS) { result ->
            onDone(JsScripts.decodeStringResult(result))
        }
    }

    private fun stepDocumentCookie(view: WebView, onDone: (String?) -> Unit) {
        evaluateAsync(view, JsScripts.buildDocumentCookieScript(), JS_EVAL_TIMEOUT_MS) { result ->
            // {"v":"ok","value":"a=1; b=2"} 或失败时为 null。
            // {"v":"ok","value":"a=1; b=2"}, or null on failure.
            val cookie = JsScripts.parseReturnVariableResult(result)
            onDone((cookie as? JsScripts.ReturnVariableResult.Value)?.value as? String)
        }
    }

    /**
     * 运行脚本并保证回调（页面导航可能静默丢弃 evaluateJavascript 的回调
     * —— 超时把它转换成 null）。
     *
     * Runs a script and guarantees the callback (page navigation can
     * silently drop evaluateJavascript's callback — the timeout converts
     * that to null).
     */
    private fun evaluateAsync(
        view: WebView,
        script: String,
        timeoutMs: Long,
        onResult: (String?) -> Unit
    ) {
        val done = AtomicBoolean(false)
        runCatching {
            view.evaluateJavascript(script) { result ->
                if (done.compareAndSet(false, true)) {
                    mainHandler.post { onResult(result) }
                }
            }
        }.onFailure {
            if (done.compareAndSet(false, true)) mainHandler.post { onResult(null) }
        }
        mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) onResult(null)
        }, timeoutMs)
    }

    private fun finishWith(
        returnResult: JsScripts.ReturnVariableResult,
        documentHtml: String?,
        documentCookie: String?
    ) {
        val view = webView
        val currentRelay = relay
        val finalUrl = view?.url ?: spec.url
        val title = view?.title.orEmpty()

        val finalResponse = currentRelay?.finalResponseSnapshot()
        val responseMatches = finalResponse != null && urlsMatch(finalUrl, finalResponse.requestedUrl)

        val collector = currentRelay?.cookieCollector
        val cookieManager = CookieManager.getInstance()
        val snapshotUrls = (listOf(spec.url) + (currentRelay?.hopUrlsSnapshot() ?: emptyList()) + finalUrl).distinct()
        for (url in snapshotUrls) {
            runCatching {
                collector?.mergeCookieHeader(url, cookieManager.getCookie(url), "cookie_manager")
            }
        }
        if (documentCookie != null) {
            runCatching { collector?.mergeCookieHeader(finalUrl, documentCookie, "js") }
        }

        val snapshot = WebViewOutputCollector.WebViewSnapshot(
            finalUrl = finalUrl,
            pageTitle = title,
            // 优先存活 DOM（反映自定义 JS / 页面脚本的修改）；页面上下文
            // 已丢失时（例如求值期间发生导航）回退到中继响应的精确字节。
            // Prefer the live DOM (reflecting custom JS / page-script
            // mutations); fall back to the relay response's exact bytes when
            // the page context is already gone (e.g. navigation happened
            // during evaluation).
            documentHtml = documentHtml
                ?: finalResponse?.takeIf { responseMatches }?.body?.toString(Charsets.UTF_8),
            statusCode = finalResponse?.statusCode,
            responseHeaders = finalResponse?.headers,
            requestHeaders = finalResponse?.requestHeaders,
            cookies = collector?.toOutputList() ?: emptyList(),
            returnValue = returnResult,
            jsError = jsErrorText,
            responseInfoMatchesDisplayedPage = responseMatches
        )
        val outputs = WebViewOutputCollector.buildOutputs(snapshot)
        complete(SessionOutcome.Completed(outputs))
    }

    private fun urlsMatch(a: String, b: String): Boolean {
        if (a == b) return true
        val aTrimmed = a.trimEnd('/')
        val bTrimmed = b.trimEnd('/')
        return aTrimmed.isNotEmpty() && aTrimmed == bTrimmed
    }

    private fun complete(outcome: SessionOutcome) {
        if (!finished.compareAndSet(false, true)) return
        completion.complete(outcome)
        cleanup()
    }

    private fun fail(failure: SessionFailure, detail: String? = null) {
        complete(SessionOutcome.Failed(failure, detail))
    }

    /** 恰好一次地释放所有 Android 资源（主线程）。
     *  Releases every Android resource exactly once (main thread). */
    private fun cleanup() {
        mainHandler.post {
            if (!cleaned.compareAndSet(false, true)) return@post
            mainHandler.removeCallbacks(watchdogRunnable)
            cancelSettleTimer()

            val view = webView
            webView = null
            relay = null
            if (view != null) {
                runCatching { view.stopLoading() }
                runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                runCatching { view.removeAllViews() }
                runCatching { view.destroy() }
            }
            runCatching { CookieManager.getInstance().flush() }

            val currentActivity = activity
            activity = null
            if (currentActivity != null && !currentActivity.isFinishing) {
                runCatching { currentActivity.finish() }
            }
            WebViewSessionManager.unregister(this@WebViewSession)
        }
    }

    companion object {
        /** 后台模式最后一次主框架加载后的静默期。
         *  Quiet period after the last main-frame load in headless mode. */
        private const val SETTLE_MS = 2_000L

        /** 重定向 / 截止时间看门狗的检查间隔。
         *  Check interval of the redirect / deadline watchdog. */
        private const val WATCHDOG_INTERVAL_MS = 500L

        /** evaluateJavascript 回调的单脚本上限。
         *  Per-script timeout for evaluateJavascript callbacks. */
        private const val JS_EVAL_TIMEOUT_MS = 6_000L

        /** 自定义 JS 的重复执行上限（防止自导航脚本）。
         *  Maximum re-runs of custom JS (guards self-navigating scripts). */
        private const val MAX_JS_RUNS = 10

        /** 两阶段（顶层尝试与恢复脚本）都无法运行用户脚本 ——
         *  真正的语法错误。
         *  Neither stage (top-level attempt nor recovery script) could run
         *  the user script — a genuine syntax error. */
        private const val CUSTOM_JS_SYNTAX_ERROR_MESSAGE = "custom JS did not execute (syntax error?)"

        /** 确认界面必须在此窗口内出现。
         *  The confirmation screen must appear within this window. */
        private const val ACTIVITY_READY_TIMEOUT_MS = 6_000L
    }
}

/**
 * 会话的最终结果。
 *
 * The final outcome of a session.
 */
sealed class SessionOutcome {
    data class Completed(val outputs: Map<String, Any?>) : SessionOutcome()
    data class Failed(val failure: SessionFailure, val detail: String?) : SessionOutcome()
}

/**
 * 失败原因；由模块翻译成本地化的错误标题与消息。
 *
 * Failure causes; translated by the module into localized error titles
 * and messages.
 */
enum class SessionFailure {
    NETWORK,
    REDIRECT_LOOP,
    TIMEOUT,
    ACTIVITY_LAUNCH_FAILED,
    CANCELLED,
    INTERNAL,
}

/**
 * 全进程范围的活跃 WebView 会话注册表。
 *
 * 注册表刻意独立于任何 Activity 实例：模块的 execute() 协程创建会话，
 * 而确认界面可能晚于会话启动出现，两者通过 sessionId 关联到同一个会话。
 *
 * A process-wide registry of active WebView sessions.
 *
 * The registry is deliberately independent of any Activity instance: the
 * module's execute() coroutine creates the session while the confirmation
 * screen may appear later; both are linked to the same session via the
 * sessionId.
 */
object WebViewSessionManager {

    private val sessions = ConcurrentHashMap<String, WebViewSession>()

    /** 共享主线程 Handler：WebView 生命周期严格限定于主线程。
     *  Shared main-thread Handler: the WebView lifecycle is strictly
     *  confined to the main thread. */
    val mainHandler: Handler = Handler(Looper.getMainLooper())

    fun register(session: WebViewSession) {
        sessions[session.sessionId] = session
    }

    fun unregister(session: WebViewSession) {
        sessions.remove(session.sessionId, session)
    }

    fun get(sessionId: String): WebViewSession? = sessions[sessionId]

    fun activeSessions(): List<WebViewSession> = sessions.values.toList()

    /**
     * 取消所有活跃会话（例如宿主界面被销毁）：结束确认界面、
     * 销毁后台 WebView 并释放全部资源。
     *
     * Cancels all active sessions (e.g. when the host UI is destroyed):
     * finishes confirmation screens, destroys background WebViews and
     * releases all resources.
     */
    fun cancelAll() {
        for (session in sessions.values.toList()) {
            session.postFailure(SessionFailure.CANCELLED)
        }
    }
}

/**
 * [CookieStore] 的 WebView CookieManager 实现 —— 模块进程唯一的
 * Cookie 权威。在这里应用的 Cookie 立刻对 WebView 的 JavaScript
 * （document.cookie）可见，并被自动附加到 WebView 自身发起的每个请求，
 * 因此跨重定向与导航的浏览器 Cookie 行为得以保留。
 *
 * The WebView CookieManager implementation of [CookieStore] — the single
 * cookie authority of the module process. Cookies applied here are
 * immediately visible to the WebView's JavaScript (document.cookie) and
 * automatically attached to every request the WebView itself makes, so
 * browser-like cookie behavior is preserved across redirects and
 * navigation.
 */
class WebViewCookieStore : CookieStore {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun requestCookieHeader(url: String): String? = try {
        cookieManager.getCookie(url)
    } catch (_: Exception) {
        null
    }

    override fun applySetCookie(url: String, setCookieValue: String) {
        cookieManager.setCookie(url, setCookieValue)
    }

    override fun flush() {
        runCatching { cookieManager.flush() }
    }
}
