package com.chaomixian.vflow.integration.feishu

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

object FeishuOAuthManager {
    private const val AUTHORIZE_URL = "https://accounts.feishu.cn/open-apis/authen/v1/authorize"
    private const val CALLBACK_HOST = "127.0.0.1"
    private const val CALLBACK_PORT = 18789
    private const val CALLBACK_PATH = "/feishu/oauth/callback"
    private const val CALLBACK_TIMEOUT_MILLIS = 5 * 60 * 1000L
    private val requestedUserScopes = listOf(
        "im:message",
        "offline_access"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLock = Any()
    private val mutableState = MutableStateFlow(AuthUiState())

    private var currentSessionId: String? = null
    private var callbackServer: CallbackServer? = null

    data class AuthUiState(
        val phase: Phase = Phase.Idle,
        val message: String = ""
    )

    enum class Phase {
        Idle,
        WaitingForAuthorization,
        ExchangingToken,
        Success,
        Failure
    }

    sealed interface StartResult {
        data class OpenBrowser(val url: String) : StartResult
        data class Failure(val title: String, val message: String) : StartResult
    }

    private sealed interface CallbackResult {
        data class Authorized(val code: String) : CallbackResult
        data class Error(val message: String) : CallbackResult
    }

    val authState: StateFlow<AuthUiState> = mutableState.asStateFlow()

    fun getRedirectUri(): String {
        return "http://$CALLBACK_HOST:$CALLBACK_PORT$CALLBACK_PATH"
    }

    fun startAuthorization(context: Context): StartResult {
        val appContext = context.applicationContext
        val appId = FeishuModuleConfig.getAppId(appContext)
        val appSecret = FeishuModuleConfig.getAppSecret(appContext)
        if (appId.isBlank() || appSecret.isBlank()) {
            return StartResult.Failure(
                title = "缺少飞书配置",
                message = "请先填写飞书 App ID 和 App Secret"
            )
        }

        val sessionId = UUID.randomUUID().toString()
        val authorizeUrl = buildAuthorizeUrl(appId, sessionId)
        val server = CallbackServer(sessionId) { result ->
            handleCallbackResult(appContext, sessionId, result)
        }

        synchronized(sessionLock) {
            if (currentSessionId != null) {
                return StartResult.Failure(
                    title = "认证进行中",
                    message = "当前已有一个飞书认证流程正在进行，请先完成或取消当前认证"
                )
            }

            try {
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            } catch (e: IOException) {
                return StartResult.Failure(
                    title = "启动本地回调失败",
                    message = e.message ?: "无法监听飞书 OAuth 回调地址"
                )
            }

            currentSessionId = sessionId
            callbackServer = server
            mutableState.value = AuthUiState(
                phase = Phase.WaitingForAuthorization,
                message = "浏览器已打开，请在飞书完成授权。完成后这里会自动更新。"
            )
        }

        scope.launch {
            delay(CALLBACK_TIMEOUT_MILLIS)
            synchronized(sessionLock) {
                if (currentSessionId != sessionId) {
                    return@synchronized
                }
                finishSessionLocked()
                mutableState.value = AuthUiState(
                    phase = Phase.Failure,
                    message = "飞书授权已超时，请重新开始认证。"
                )
            }
        }

        return StartResult.OpenBrowser(authorizeUrl)
    }

    fun cancelAuthorization(message: String) {
        synchronized(sessionLock) {
            if (currentSessionId == null) {
                mutableState.value = AuthUiState(
                    phase = Phase.Failure,
                    message = message
                )
                return
            }
            finishSessionLocked()
            mutableState.value = AuthUiState(
                phase = Phase.Failure,
                message = message
            )
        }
    }

    fun resetUiState() {
        mutableState.value = AuthUiState()
    }

    private fun buildAuthorizeUrl(appId: String, sessionId: String): String {
        return Uri.parse(AUTHORIZE_URL)
            .buildUpon()
            .appendQueryParameter("client_id", appId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", getRedirectUri())
            .appendQueryParameter("scope", requestedUserScopes.joinToString(" "))
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("state", sessionId)
            .build()
            .toString()
    }

    private fun handleCallbackResult(
        context: Context,
        sessionId: String,
        result: CallbackResult
    ) {
        when (result) {
            is CallbackResult.Error -> {
                synchronized(sessionLock) {
                    if (currentSessionId != sessionId) {
                        return
                    }
                    finishSessionLocked()
                    mutableState.value = AuthUiState(
                        phase = Phase.Failure,
                        message = result.message
                    )
                }
            }

            is CallbackResult.Authorized -> {
                synchronized(sessionLock) {
                    if (currentSessionId != sessionId) {
                        return
                    }
                    stopCallbackServerLocked()
                    mutableState.value = AuthUiState(
                        phase = Phase.ExchangingToken,
                        message = "已收到飞书授权，正在换取用户令牌。"
                    )
                }

                scope.launch {
                    val resolution = FeishuModuleConfig.authorizeUserWithAuthCode(
                        context = context,
                        authCode = result.code,
                        redirectUri = getRedirectUri()
                    )
                    synchronized(sessionLock) {
                        if (currentSessionId != sessionId) {
                            return@synchronized
                        }
                        finishSessionLocked()
                        mutableState.value = when (resolution) {
                            is FeishuModuleConfig.UserAuthorizationResolution.Success -> {
                                AuthUiState(
                                    phase = Phase.Success,
                                    message = if (resolution.hasRefreshToken) {
                                        "飞书用户认证完成，后续会自动刷新登录状态。"
                                    } else {
                                        "飞书用户认证完成，但当前没有 refresh_token，到期后需要重新认证。"
                                    }
                                )
                            }

                            is FeishuModuleConfig.UserAuthorizationResolution.Failure -> {
                                AuthUiState(
                                    phase = Phase.Failure,
                                    message = resolution.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopCallbackServerLocked() {
        callbackServer?.stop()
        callbackServer = null
    }

    private fun finishSessionLocked() {
        stopCallbackServerLocked()
        currentSessionId = null
    }

    private class CallbackServer(
        private val expectedState: String,
        private val onResult: (CallbackResult) -> Unit
    ) : NanoHTTPD(CALLBACK_HOST, CALLBACK_PORT) {

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != CALLBACK_PATH) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "Not Found"
                )
            }

            val state = session.parameters["state"]?.firstOrNull().orEmpty()
            val code = session.parameters["code"]?.firstOrNull().orEmpty()
            val error = session.parameters["error"]?.firstOrNull().orEmpty()
            val errorDescription = session.parameters["error_description"]?.firstOrNull().orEmpty()

            return when {
                error.isNotBlank() -> {
                    val response = htmlResponse(
                        status = Response.Status.BAD_REQUEST,
                        body = buildHtmlPage(
                            title = "飞书授权失败",
                            message = errorDescription.ifBlank { "请返回 vFlow 重新开始认证。" }
                        )
                    )
                    dispatchResult(
                        CallbackResult.Error(
                            errorDescription.ifBlank { "飞书授权失败: $error" }
                        )
                    )
                    response
                }

                state != expectedState -> {
                    val response = htmlResponse(
                        status = Response.Status.BAD_REQUEST,
                        body = buildHtmlPage(
                            title = "飞书授权失败",
                            message = "回调状态无效，请返回 vFlow 重新开始认证。"
                        )
                    )
                    dispatchResult(CallbackResult.Error("飞书授权状态校验失败，请重新开始认证。"))
                    response
                }

                code.isBlank() -> {
                    val response = htmlResponse(
                        status = Response.Status.BAD_REQUEST,
                        body = buildHtmlPage(
                            title = "飞书授权失败",
                            message = "没有收到授权结果，请返回 vFlow 重新开始认证。"
                        )
                    )
                    dispatchResult(CallbackResult.Error("飞书未返回有效的授权码，请重新开始认证。"))
                    response
                }

                else -> {
                    val response = htmlResponse(
                        status = Response.Status.OK,
                        body = buildHtmlPage(
                            title = "飞书授权完成",
                            message = "你已经可以返回 vFlow 继续使用。"
                        )
                    )
                    dispatchResult(CallbackResult.Authorized(code))
                    response
                }
            }
        }

        private fun dispatchResult(result: CallbackResult) {
            scope.launch {
                delay(250)
                onResult(result)
            }
        }

        private fun htmlResponse(status: Response.Status, body: String): Response {
            return newFixedLengthResponse(
                status,
                "text/html; charset=utf-8",
                body
            )
        }

        private fun buildHtmlPage(title: String, message: String): String {
            return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>$title</title>
                  <style>
                    body {
                      margin: 0;
                      padding: 24px;
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      background: #f5f7fa;
                      color: #1f2937;
                    }
                    .card {
                      max-width: 520px;
                      margin: 48px auto;
                      padding: 24px;
                      background: #ffffff;
                      border-radius: 16px;
                      box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
                    }
                    h1 {
                      margin: 0 0 12px 0;
                      font-size: 24px;
                    }
                    p {
                      margin: 0;
                      line-height: 1.6;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>$title</h1>
                    <p>$message</p>
                  </div>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
