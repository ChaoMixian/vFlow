// 文件: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRelayTest.kt
// 描述: 主框架中继引擎的单元测试。
//      使用脚本化的假 HopHttpClient 直接驱动中继状态机
//      （与运行时 WebView 驱动的请求链完全一致）。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewRelayTest.kt
// Description: Unit tests for the main-frame relay engine.
//      Drives the relay state machine directly with a scripted fake
//      HopHttpClient (identical to the request chain the WebView drives at
//      runtime).

package com.chaomixian.vflow.core.workflow.module.network.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainFrameRelayTest {

    private val base = "https://relay.test"
    private lateinit var client: ScriptedHopClient
    private lateinit var cookieStore: RecordingCookieStore

    private fun setUp(vararg responses: FakeResponse.() -> Unit) {
        client = ScriptedHopClient(responses.map { FakeResponse().apply(it) })
        cookieStore = RecordingCookieStore()
    }

    private fun plan(
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String = ""
    ) = RequestPlan("$base/start", method, headers, body)

    private fun relay(
        plan: RequestPlan,
        collector: CookieCollector = CookieCollector(),
        clock: () -> Long = { 0L }
    ) = MainFrameRelay(plan, client, cookieStore, collector, "RelayTestUA/1.0", clock = clock)

    @Test
    fun `get request applies user headers and returns final response`() {
        setUp(
            {
                statusCode = 200
                headers = listOf(
                    "Content-Type" to "text/html; charset=utf-8",
                    "X-Custom" to "v"
                )
                body = "<html><body>hello</body></html>".toByteArray()
            }
        )

        val decision = relay(plan(headers = mapOf("Authorization" to "Bearer tok")))
            .intercept("$base/start") as MainFrameRelay.RelayDecision.Respond

        assertEquals(200, decision.statusCode)
        assertEquals("<html><body>hello</body></html>", String(decision.body))
        assertTrue(decision.headers.any { it.first.equals("X-Custom", true) && it.second == "v" })

        val recorded = client.requests.single()
        assertEquals("GET", recorded.method)
        assertEquals("$base/start", recorded.url)
        assertEquals("Bearer tok", recorded.header("Authorization"))
        assertEquals("RelayTestUA/1.0", recorded.header("User-Agent"))
    }

    @Test
    fun `post request sends the configured body and content type`() {
        setUp({ statusCode = 200; body = "ok".toByteArray() })

        val decision = relay(plan(method = "POST", body = "user=a&pass=b"))
            .intercept("$base/start") as MainFrameRelay.RelayDecision.Respond

        assertEquals(200, decision.statusCode)
        val recorded = client.requests.single()
        assertEquals("POST", recorded.method)
        assertEquals("user=a&pass=b", recorded.body?.decodeToString())
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", recorded.header("Content-Type"))
    }

    @Test
    fun `redirect chain relays each hop and applies set-cookies`() {
        setUp(
            {
                statusCode = 302
                headers = listOf(
                    "Location" to "/home",
                    "Set-Cookie" to "session=abc; Path=/; HttpOnly"
                )
                body = ByteArray(0)
            },
            {
                statusCode = 200
                body = "<html><body>home</body></html>".toByteArray()
            }
        )

        val collector = CookieCollector()
        val relay = relay(plan(), collector)

        val first = relay.intercept("$base/start") as MainFrameRelay.RelayDecision.Respond
        assertEquals(302, first.statusCode)
        // Location 为 WebView 被绝对化 / Location is absolutized for the WebView
        val absoluteLocation = first.headers.first { it.first.equals("Location", true) }.second
        assertEquals("$base/home", absoluteLocation)
        // Set-Cookie 从中继响应中剥离（CookieManager 是唯一权威）/ Set-Cookie is stripped from the relayed response (the CookieManager is the single authority)
        assertTrue(first.headers.none { it.first.equals("Set-Cookie", true) })

        val second = relay.intercept("$base/home") as MainFrameRelay.RelayDecision.Respond
        assertEquals(200, second.statusCode)
        assertEquals("<html><body>home</body></html>", String(second.body))

        // Set-Cookie 连同属性被记录并应用到存储 / Set-Cookie is recorded with its attributes and applied to the store
        assertEquals(1, collector.allCookies().size)
        assertEquals("abc", collector.allCookies()[0].value)
        assertTrue(collector.allCookies()[0].httpOnly)
        assertEquals(listOf("$base/start" to "session=abc; Path=/; HttpOnly"), cookieStore.applied)

        // 链完成 -> 后续请求原样放行 / Chain finished -> later requests pass through untouched
        assertTrue(relay.intercept("$base/anything") is MainFrameRelay.RelayDecision.PassThrough)
    }

    @Test
    fun `redirect request carries cookies from the cookie store`() {
        setUp(
            { statusCode = 302; headers = listOf("Location" to "/home"); body = ByteArray(0) },
            { statusCode = 200; body = "ok".toByteArray() }
        )
        cookieStore.header = "session=abc"

        relay(plan()).apply {
            intercept("$base/start")
            intercept("$base/home")
        }

        val followUp = client.requests[1]
        assertEquals("session=abc", followUp.header("Cookie"))
    }

    @Test
    fun `301 redirect switches POST to GET`() {
        setUp(
            { statusCode = 301; headers = listOf("Location" to "/done"); body = ByteArray(0) },
            { statusCode = 200; body = "ok".toByteArray() }
        )

        relay(plan(method = "POST", body = "a=1")).apply {
            intercept("$base/start")
            intercept("$base/done")
        }

        val followUp = client.requests[1]
        assertEquals("GET", followUp.method)
        assertNull(followUp.body)
    }

    @Test
    fun `307 redirect preserves method and body`() {
        setUp(
            { statusCode = 307; headers = listOf("Location" to "/retry"); body = ByteArray(0) },
            { statusCode = 200; body = "ok".toByteArray() }
        )

        relay(plan(method = "POST", body = "a=1")).apply {
            intercept("$base/start")
            intercept("$base/retry")
        }

        val followUp = client.requests[1]
        assertEquals("POST", followUp.method)
        assertEquals("a=1", followUp.body?.decodeToString())
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", followUp.header("Content-Type"))
    }

    @Test
    fun `unexpected main-frame request follows the pending redirect instead`() {
        setUp(
            { statusCode = 302; headers = listOf("Location" to "/home"); body = ByteArray(0) },
            { statusCode = 200; body = "ok".toByteArray() }
        )

        val relay = relay(plan())
        relay.intercept("$base/start")
        // 用户在链中途点击了别的链接 / The user clicked a different link mid-chain
        val decision = relay.intercept("$base/elsewhere") as MainFrameRelay.RelayDecision.Respond
        assertEquals(200, decision.statusCode)
        assertEquals("$base/home", client.requests[1].url)
    }

    @Test
    fun `watchdog takes over a stalled redirect exactly maxTakeovers times`() {
        setUp(
            { statusCode = 302; headers = listOf("Location" to "/away"); body = ByteArray(0) }
        )
        var now = 0L
        val relay = relay(plan(), clock = { now })
        relay.intercept("$base/start")

        // 尚未停滞 / Not stalled yet
        now = 1_000
        assertNull(relay.takeOverIfStalled(now))

        // 停滞 -> 接管 1 / Stalled -> takeover 1
        now = 5_000
        assertEquals("$base/away", relay.takeOverIfStalled(now))
        // 立刻再次检查 -> 未停滞（时间戳已重置）/ Checking again immediately -> not stalled (timestamp was reset)
        assertNull(relay.takeOverIfStalled(now))

        // 第二与第三次接管 / The second and third takeovers
        now = 12_000
        assertEquals("$base/away", relay.takeOverIfStalled(now))
        now = 20_000
        assertEquals("$base/away", relay.takeOverIfStalled(now))

        // 第四次接管请求被拒绝，中继报告停滞。/ The fourth takeover request is refused and the relay reports a stall.
        now = 30_000
        assertNull(relay.takeOverIfStalled(now))
        assertTrue(relay.redirectStalled)
    }

    @Test
    fun `page finished accelerates the watchdog`() {
        setUp(
            { statusCode = 302; headers = listOf("Location" to "/away"); body = ByteArray(0) }
        )
        var now = 0L
        val relay = relay(plan(), clock = { now })
        relay.intercept("$base/start")

        now = 1_000
        relay.notifyPageFinished() // WebView 忽略了 3xx 并“完成”了空页面
        now = 1_700 // 700ms 后接管即触发
        assertEquals("$base/away", relay.takeOverIfStalled(now))
    }

    @Test
    fun `too many redirects terminates the chain`() {
        val looping = List(11) { index ->
            FakeResponse().apply {
                statusCode = 302
                headers = listOf("Location" to "/loop$index")
                body = ByteArray(0)
            }
        }
        client = ScriptedHopClient(looping)
        cookieStore = RecordingCookieStore()

        val relay = relay(plan())
        var decision: MainFrameRelay.RelayDecision = relay.intercept("$base/start")
        var count = 1
        while (decision is MainFrameRelay.RelayDecision.Respond && decision.statusCode in 300..399 && count < 12) {
            decision = relay.intercept("$base/loop${count - 1}")
            count++
        }
        val last = decision as MainFrameRelay.RelayDecision.Respond
        assertEquals(508, last.statusCode)
        assertTrue(relay.tooManyRedirects)
    }

    @Test
    fun `chain summary records every hop`() {
        setUp(
            { statusCode = 302; headers = listOf("Location" to "/h2"); body = ByteArray(0) },
            { statusCode = 200; body = "done".toByteArray() }
        )
        val relay = relay(plan())
        relay.intercept("$base/start")
        relay.intercept("$base/h2")

        val summary = relay.chainSummary()
        assertEquals(2, summary.size)
        assertEquals("$base/start", summary[0].url)
        assertEquals(302, summary[0].statusCode)
        assertEquals(200, summary[1].statusCode)

        assertNotNull(relay.finalResponse)
        assertEquals(200, relay.finalResponse!!.statusCode)
        assertEquals("done", String(relay.finalResponse!!.body))
    }

    @Test
    fun `error responses are delivered to the webview`() {
        setUp({ statusCode = 404; body = "not found".toByteArray() })
        val relay = relay(plan())
        val decision = relay.intercept("$base/start") as MainFrameRelay.RelayDecision.Respond
        assertEquals(404, decision.statusCode)
        assertEquals("not found", String(decision.body))
        assertNotNull(relay.finalResponse)
        assertEquals(404, relay.finalResponse!!.statusCode)
    }

    @Test
    fun `duplicate set-cookie headers are all recorded`() {
        setUp(
            {
                statusCode = 200
                headers = listOf(
                    "Set-Cookie" to "a=1; Path=/",
                    "Set-Cookie" to "b=2; Path=/"
                )
                body = "ok".toByteArray()
            }
        )
        val collector = CookieCollector()
        val relay = relay(plan(), collector)
        val decision = relay.intercept("$base/start") as MainFrameRelay.RelayDecision.Respond

        assertEquals(200, decision.statusCode)
        assertEquals(2, collector.allCookies().size)
        assertEquals(setOf("a", "b"), collector.allCookies().map { it.name }.toSet())
    }

    @Test
    fun `final response exposes the request headers actually sent`() {
        // request_headers 输出来自最终跳跃的请求头（含传输层补充头）。
        // The request_headers output comes from the final hop's request
        // headers (including transport-level additions).
        setUp(
            {
                statusCode = 302
                headers = listOf("Location" to "/home")
            },
            {
                statusCode = 200
                requestHeaders = listOf(
                    "Host" to "relay.test",
                    "Connection" to "keep-alive",
                    "User-Agent" to "RelayTestUA/1.0"
                )
            }
        )

        val relay = relay(plan())
        relay.intercept("$base/start")
        relay.intercept("$base/home")

        val finalResponse = relay.finalResponseSnapshot()
        assertNotNull(finalResponse)
        assertEquals(
            listOf(
                "Host" to "relay.test",
                "Connection" to "keep-alive",
                "User-Agent" to "RelayTestUA/1.0"
            ),
            finalResponse!!.requestHeaders
        )
    }

    @Test
    fun `hop failure is wrapped into a relay exception`() {
        client = ScriptedHopClient(emptyList(), failWith = RuntimeException("connection refused"))
        cookieStore = RecordingCookieStore()

        val relay = relay(plan())
        try {
            relay.intercept("$base/start")
            throw AssertionError("expected RelayHopException")
        } catch (e: RelayHopException) {
            assertEquals("$base/start", e.requestedUrl)
            assertTrue(e.message.orEmpty().contains("connection refused"))
        }
    }

    // --- 测试替身 / Test doubles ------------------------------------------------

    /** 可脚本化的 HopHttpClient：按序返回预置响应并记录所有请求。
     *  A scriptable HopHttpClient: returns the scripted responses in order
     *  and records every request. */
    private class ScriptedHopClient(
        private val responses: List<FakeResponse>,
        private val failWith: Exception? = null
    ) : HopHttpClient {
        val requests = mutableListOf<HopRequest>()
        private var index = 0

        override fun execute(request: HopRequest): HopResponse {
            requests.add(request)
            failWith?.let { throw it }
            if (index >= responses.size) {
                throw IllegalStateException("no scripted response left")
            }
            val response = responses[index++]
            return HopResponse(
                requestedUrl = request.url,
                statusCode = response.statusCode,
                reasonPhrase = response.reasonPhrase,
                headers = response.headers,
                body = response.body,
                isRedirect = response.statusCode in 300..399 &&
                    response.headers.any { it.first.equals("Location", true) },
                redirectLocation = response.headers
                    .firstOrNull { it.first.equals("Location", true) }?.second,
                requestHeaders = response.requestHeaders ?: request.headers
            )
        }
    }

    private class FakeResponse {
        var statusCode: Int = 200
        var reasonPhrase: String = "OK"
        var headers: List<Pair<String, String>> = emptyList()
        var body: ByteArray = ByteArray(0)

        /** 该跳跃实际发出的请求头；缺省时镜像发出的请求。
         *  The request headers actually sent on this hop; defaults to
         *  mirroring the outgoing request. */
        var requestHeaders: List<Pair<String, String>>? = null
    }

    /** 内存版 CookieStore。
     *  An in-memory CookieStore. */
    private class RecordingCookieStore : CookieStore {
        val applied = mutableListOf<Pair<String, String>>()
        var header: String? = null

        override fun requestCookieHeader(url: String): String? = header

        override fun applySetCookie(url: String, setCookieValue: String) {
            applied.add(url to setCookieValue)
        }

        override fun flush() = Unit
    }

    private fun HopRequest.header(name: String): String? =
        headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
}
