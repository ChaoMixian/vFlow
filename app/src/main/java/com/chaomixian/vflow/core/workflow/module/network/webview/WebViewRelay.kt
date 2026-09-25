// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRelay.kt
// 描述: WebView 模块的主框架请求中继引擎。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRelay.kt
// Description: The main-frame request relay engine of the WebView module.

package com.chaomixian.vflow.core.workflow.module.network.webview

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 单次不自动跟随重定向的阻塞式 HTTP 跳跃。由 OkHttp 实现生产版本，
 * JVM 单元测试中使用脚本化的假实现。
 *
 * One blocking HTTP hop that does not follow redirects automatically. The
 * production implementation is OkHttp-based; JVM unit tests use a scripted
 * fake implementation.
 */
interface HopHttpClient {
    @Throws(Exception::class)
    fun execute(request: HopRequest): HopResponse
}

data class HopRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?
) {
    override fun equals(other: Any?): Boolean = other is HopRequest &&
        other.url == url && other.method == method && other.headers == headers &&
        other.body.contentEquals(body)

    override fun hashCode(): Int = 31 * (31 * url.hashCode() + method.hashCode()) + headers.hashCode()
}

data class HopResponse(
    val requestedUrl: String,
    val statusCode: Int,
    val reasonPhrase: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val isRedirect: Boolean,
    val redirectLocation: String?,
    /** 跳跃实际发出的请求头（含 HTTP 客户端补充的 Host / Connection 等）。
     *  模块的 request_headers 输出由最终跳跃的这一字段提供。 / The
     *  request headers actually sent on this hop (including the ones added
     *  by the HTTP client, e.g. Host / Connection). The module's
     *  request_headers output comes from this field of the final hop. */
    val requestHeaders: List<Pair<String, String>> = emptyList()
) {
    fun headerValues(name: String): List<String> =
        headers.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

    fun header(name: String): String? = headerValues(name).firstOrNull()

    override fun equals(other: Any?): Boolean = other is HopResponse &&
        other.requestedUrl == requestedUrl && other.statusCode == statusCode &&
        other.reasonPhrase == reasonPhrase && other.headers == headers &&
        other.body.contentEquals(body) && other.isRedirect == isRedirect &&
        other.redirectLocation == redirectLocation && other.requestHeaders == requestHeaders

    override fun hashCode(): Int = requestedUrl.hashCode()
}

/**
 * WebView 的 Cookie 存储。读取提供某个 URL 当前的 `Cookie:` 头；
 * 写入应用 `Set-Cookie` 值，使 WebView、它的 JavaScript 以及后续所有
 * 请求都能观察到（正常的 WebView Cookie 行为）。
 *
 * The cookie store of the WebView. Reads provide the current `Cookie:`
 * header for a URL; writes apply `Set-Cookie` values so that the WebView,
 * its JavaScript and every subsequent request can observe them (normal
 * WebView cookie behavior).
 */
interface CookieStore {
    fun requestCookieHeader(url: String): String?
    fun applySetCookie(url: String, setCookieValue: String)
    fun flush()
}

/** 中继跳跃在网络层失败时抛出。
 *  Thrown when a relay hop fails at the network layer. */
class RelayHopException(val requestedUrl: String, cause: Throwable) :
    Exception("relay hop failed for $requestedUrl: ${cause.message}", cause)

/**
 * 驱动 WebView 初始主框架请求链：
 *
 *  - 第一个主框架请求由 [HopHttpClient] 以用户配置的方法、请求头与请求体
 *    执行（自定义方法/请求头/请求体都被尊重，同时 WebView 继续渲染结果）；
 *  - 沿途看到的每个 `Set-Cookie` 都会应用到 [CookieStore] 并记录到
 *    [CookieCollector]；
 *  - 3xx 响应被中继回 WebView（包含 Location），由 WebView 自行跟随重定向
 *    —— 这样每一跳的 document.location 与相对 URL 都保持正确；
 *    301/302/303 会把下一跳切换为 GET，307/308 保留方法与请求体；
 *  - 看门狗会在 WebView 未及时跟随中继重定向时接管（loadUrl），
 *    使引擎具备自愈能力；
 *  - 一旦非重定向响应交付完成，后续所有请求原样放行，因此用户导航、
 *    POST 表单、XHR 与子资源保持 100% 的原生 WebView 行为。
 *
 * Drives the initial main-frame request chain of the WebView:
 *
 *  - The first main-frame request is executed by [HopHttpClient] with the
 *    user-configured method, headers and body (custom methods/headers/bodies
 *    are all honored while the WebView keeps rendering the result);
 *  - every `Set-Cookie` seen along the way is applied to [CookieStore] and
 *    recorded into [CookieCollector];
 *  - 3xx responses are relayed back into the WebView (including Location)
 *    and followed by the WebView itself — so document.location and relative
 *    URLs stay correct on every hop; 301/302/303 switch the next hop to GET,
 *    307/308 preserve the method and body;
 *  - a watchdog takes over (loadUrl) when the WebView does not follow a
 *    relayed redirect in time, making the engine self-healing;
 *  - once a non-redirect response has been delivered, every subsequent
 *    request is passed through untouched, so user navigation, POST forms,
 *    XHR and subresources keep 100% native WebView behavior.
 */
class MainFrameRelay(
    private val plan: RequestPlan,
    private val client: HopHttpClient,
    private val cookieStore: CookieStore,
    val cookieCollector: CookieCollector,
    private val userAgent: String,
    private val maxHops: Int = 10,
    private val maxBodyBytes: Int = 8 * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
    private val redirectStallTimeoutMs: Long = 4_000,
    private val maxTakeovers: Int = 3
) {

    private sealed class State {
        /** 等待 WebView 的第一个主框架请求。
         *  Waiting for the WebView's first main-frame request. */
        object AwaitInitial : State()

        /** 已中继一个 3xx；等待后续主框架请求。
         *  A 3xx has been relayed; waiting for the follow-up main-frame
         *  request. */
        data class AwaitRedirect(
            val location: String,
            val nextMethod: String,
            val since: Long,
            val takeovers: Int = 0
        ) : State()

        /** 初始链已完成；从此一切请求放行。
         *  The initial chain has finished; every request passes through
         *  from now on. */
        object Done : State()
    }

    private var state: State = State.AwaitInitial
    private val hops = mutableListOf<ChainHop>()

    /** 最终（非重定向）跳跃的响应信息，可用时非空。
     *  Response info of the final (non-redirect) hop, non-null when
     *  available. */
    var finalResponse: HopResponse? = null
        private set

    val hopUrls: List<String> get() = hops.map { it.url }

    /** 最终响应的线程安全快照（写于 WebView IO 线程，读于主线程完成时）。
     *  Thread-safe snapshot of the final response (written on the WebView
     *  IO thread, read on the main thread at completion time). */
    @Synchronized
    fun finalResponseSnapshot(): HopResponse? = finalResponse

    @Synchronized
    fun hopUrlsSnapshot(): List<String> = hops.map { it.url }

    data class ChainHop(val url: String, val statusCode: Int)

    /**
     * shouldInterceptRequest 调用的决策结果。
     *
     * The decision result returned to shouldInterceptRequest.
     */
    sealed class RelayDecision {
        /** 不拦截；让 WebView 原生执行该请求。
         *  Do not intercept; let the WebView perform the request natively. */
        object PassThrough : RelayDecision()

        /** 用这个合成响应答复 WebView。
         *  Answer the WebView with this synthesized response. */
        data class Respond(
            val statusCode: Int,
            val reasonPhrase: String,
            val headers: List<Pair<String, String>>,
            val body: ByteArray
        ) : RelayDecision()
    }

    /**
     * 处理一次主框架拦截请求。
     *
     * @param requestUrl WebView 正在请求的 URL。
     *  The URL the WebView is requesting.
     *
     * Handles one main-frame intercepted request.
     */
    @Synchronized
    fun intercept(requestUrl: String): RelayDecision {
        return when (val current = state) {
            is State.Done -> RelayDecision.PassThrough
            is State.AwaitRedirect -> {
                if (requestUrl != current.location) {
                    // 重定向挂起期间出现了意料之外的主框架请求
                    // （例如用户在链中途点击了链接）：由我们代为跟随重定向，
                    // 让 WebView 随后赶上。
                    // An unexpected main-frame request appeared while a
                    // redirect is pending (e.g. the user clicked a link
                    // mid-chain): we follow the redirect on its behalf so
                    // the WebView can catch up.
                    performHop(current.location, current.nextMethod)
                } else {
                    performHop(requestUrl, current.nextMethod)
                }
            }
            is State.AwaitInitial -> performHop(requestUrl, plan.method)
        }
    }

    private fun performHop(url: String, method: String): RelayDecision {
        if (hops.size >= maxHops) {
            state = State.Done
            tooManyRedirects = true
            return RelayDecision.Respond(508, "Too Many Redirects", listOf("Content-Type" to "text/plain; charset=utf-8"), TOO_MANY_REDIRECTS_BODY)
        }

        val cookieHeader = cookieStore.requestCookieHeader(url)
        // 跳跃携带请求体的条件：跳跃方法不是 GET 且计划携带请求体 ——
        // 即带请求体的初始请求，以及保留方法的 307/308 重定向。
        // A hop carries a body when the hop method is not GET and the plan
        // has a body — i.e. the initial request with a body, plus
        // method-preserving 307/308 redirects.
        val hopHasBody = method != "GET" && plan.hasBody
        val headers = if (hops.isEmpty()) {
            plan.firstHopHeaders(userAgent, cookieHeader)
        } else {
            plan.redirectHopHeaders(userAgent, cookieHeader)
                .let { list -> if (hopHasBody) list + ("Content-Type" to plan.bodyContentType) else list }
        }
        val body = plan.bodyBytes.takeIf { hopHasBody }

        val response = try {
            client.execute(HopRequest(url, method, headers, body))
        } catch (e: Exception) {
            throw RelayHopException(url, e)
        }

        cookieCollector.observeUrl(url)
        response.headerValues("Set-Cookie").forEach { setCookie ->
            runCatching { cookieStore.applySetCookie(url, setCookie) }
            cookieCollector.recordSetCookie(url, setCookie)
        }
        hops.add(ChainHop(url, response.statusCode))

        if (response.isRedirect && response.redirectLocation != null) {
            val resolved = resolveLocation(url, response.redirectLocation)
            val nextMethod = RequestPlan.methodForRedirect(response.statusCode, plan.method)
            state = State.AwaitRedirect(resolved, nextMethod, clock())
            return RelayDecision.Respond(
                statusCode = response.statusCode,
                reasonPhrase = response.reasonPhrase,
                headers = responseHeadersForWebView(response, resolved),
                body = ByteArray(0)
            )
        }

        finalResponse = response
        state = State.Done
        val deliveredBody = if (response.body.size > maxBodyBytes) response.body.copyOf(maxBodyBytes) else response.body
        return RelayDecision.Respond(
            statusCode = response.statusCode,
            reasonPhrase = response.reasonPhrase,
            headers = responseHeadersForWebView(response, null),
            body = deliveredBody
        )
    }

    /**
     * 中继给 WebView 的响应头：Set-Cookie 被剥离（已经应用到 CookieManager，
     * 保持单一 Cookie 权威），Content-Length 按实际交付的请求体重算，
     * 重定向 Location 被绝对化。
     *
     * Response headers relayed to the WebView: Set-Cookie is stripped
     * (already applied to the CookieManager, keeping a single cookie
     * authority), Content-Length is recalculated for the body actually
     * delivered, and redirect Location is absolutized.
     */
    private fun responseHeadersForWebView(response: HopResponse, absoluteLocation: String?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for ((name, value) in response.headers) {
            val lower = name.lowercase()
            when {
                lower == "set-cookie" -> Unit
                lower == "content-length" -> Unit
                lower == "location" && absoluteLocation != null -> out.add(name to absoluteLocation)
                else -> out.add(name to value)
            }
        }
        if (absoluteLocation != null && out.none { it.first.equals("Location", ignoreCase = true) }) {
            out.add("Location" to absoluteLocation)
        }
        return out
    }

    /** 链超过跳跃上限时为真（由会话检查）。
     *  True when the chain exceeded the hop limit (checked by the session). */
    var tooManyRedirects: Boolean = false
        private set

    /**
     * 看门狗：当 WebView 未能及时跟随中继的 3xx 时，返回应该导航到的
     * 重定向 URL。返回值应喂给 WebView.loadUrl()；由此产生的请求会重新
     * 进入 [intercept] 并继续该链。
     *
     * Watchdog: when the WebView fails to follow a relayed 3xx in time,
     * returns the redirect URL to navigate to. The return value should be
     * fed to WebView.loadUrl(); the resulting request re-enters [intercept]
     * and the chain continues.
     */
    @Synchronized
    fun takeOverIfStalled(now: Long = clock()): String? {
        val current = state as? State.AwaitRedirect ?: return null
        val stalled = now - current.since >= redirectStallTimeoutMs
        if (!stalled) return null
        if (current.takeovers >= maxTakeovers) {
            redirectStalled = true
            return null
        }
        state = current.copy(since = now, takeovers = current.takeovers + 1)
        return current.location
    }

    /** 反复接管后重定向仍未被跟随时为真。
     *  True when the redirect is still not followed after repeated
     *  takeovers. */
    var redirectStalled: Boolean = false
        private set

    /**
     * 当重定向仍在挂起而 WebView 报告 page-finished 时加速看门狗
     * （某些 WebView 版本会为中继的 3xx 渲染空主体而不是跟随它）。
     *
     * Speeds up the watchdog when the WebView reports page-finished while a
     * redirect is still pending (some WebView versions render an empty body
     * for a relayed 3xx instead of following it).
     */
    @Synchronized
    fun notifyPageFinished() {
        val current = state as? State.AwaitRedirect ?: return
        state = current.copy(since = clock() - (redirectStallTimeoutMs - 600))
    }

    /** 初始请求链仍在进行中时为真。
     *  True while the initial request chain is still in progress. */
    @Synchronized
    fun isChainActive(): Boolean = state !is State.Done

    /**
     * 输出摘要: (url, status) 跳跃列表。
     *
     * Output summary: the list of (url, status) hops.
     */
    @Synchronized
    fun chainSummary(): List<ChainHop> = hops.toList()

    internal fun resolveLocation(baseUrl: String, location: String): String {
        return try {
            val base = java.net.URI(baseUrl)
            val resolved = base.resolve(java.net.URI(location))
            resolved.toString()
        } catch (_: Exception) {
            location
        }
    }

    companion object {
        private val TOO_MANY_REDIRECTS_BODY = "vFlow WebView: too many redirects".toByteArray()
    }
}

/**
 * [HopHttpClient] 的 OkHttp 实现。每次跳跃一次阻塞调用，
 * 不自动跟随重定向（中继引擎逐跳走链，以便在每一步把 Cookie 喂给
 * WebView 的 CookieManager）。
 *
 * The OkHttp implementation of [HopHttpClient]. One blocking call per hop,
 * with automatic redirects disabled (the relay engine walks the chain hop
 * by hop so it can feed cookies into the WebView's CookieManager at every
 * step).
 */
class OkHttpHopClient(
    connectTimeoutSeconds: Long = 12,
    readTimeoutSeconds: Long = 12
) : HopHttpClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Throws(Exception::class)
    override fun execute(request: HopRequest): HopResponse {
        val builder = Request.Builder().url(request.url)
        for ((name, value) in request.headers) {
            builder.header(name, value)
        }
        when (request.method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            else -> {
                val contentType = request.headers
                    .firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }
                    ?.second?.toMediaType()
                builder.method(request.method, (request.body ?: ByteArray(0)).toRequestBody(contentType))
            }
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.bytes() ?: ByteArray(0)
            val isRedirect = response.code in 300..399 && response.header("Location") != null
            // 实际发出的请求头：OkHttp 会补充 Host / Connection /
            // Content-Length 等传输层头，作为 request_headers 输出上报。
            // The request headers actually sent: OkHttp adds transport-level
            // headers such as Host / Connection / Content-Length; these are
            // reported as the request_headers output.
            val sentRequestHeaders = response.request.headers.map { it.first to it.second }
            return HopResponse(
                requestedUrl = request.url,
                statusCode = response.code,
                reasonPhrase = response.message.ifEmpty { "status ${response.code}" },
                headers = response.headers.map { it.first to it.second },
                body = body,
                isRedirect = isRedirect,
                redirectLocation = response.header("Location"),
                requestHeaders = sentRequestHeaders
            )
        }
    }
}
