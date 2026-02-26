package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.AuthManager
import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 认证Handler
 * 处理认证相关API
 */
class AuthHandler(
    private val authManager: AuthManager,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    /**
     * 生成Token
     */
    fun handleGenerateToken(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // 检查Rate Limit
        runBlocking {
            val clientId = session.remoteIpAddress
            val rateLimitResult = checkRateLimit(clientId, RateLimiter.RequestType.AUTH)
            if (rateLimitResult != null) {
                rateLimitResponse(rateLimitResult)
            }
        }

        // 解析请求
        val request = parseRequestBody(session, GenerateTokenRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        // 验证请求
        if (request.deviceId.isBlank()) {
            return errorResponse(400, "Device ID is required")
        }

        // 生成Token
        val tokenInfo = authManager.generateToken(
            deviceId = request.deviceId,
            deviceName = request.deviceName
        )

        // 构建响应
        val response = TokenResponse(
            token = tokenInfo.token,
            refreshToken = tokenInfo.refreshToken,
            expiresIn = ((tokenInfo.expiresAt - tokenInfo.createdAt) / 1000).toInt(),
            tokenType = "Bearer",
            deviceInfo = DeviceInfoSimple(
                brand = android.os.Build.BRAND,
                model = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE_OR_CODENAME,
                apiLevel = android.os.Build.VERSION.SDK_INT
            )
        )

        return successResponse(response)
    }

    /**
     * 刷新Token
     */
    fun handleRefreshToken(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // 从Header中提取RefreshToken
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(6001, "Missing refresh token")
        }

        val refreshToken = authHeader.substring(7)

        // 刷新Token
        val tokenInfo = authManager.refreshToken(refreshToken)
        if (tokenInfo == null) {
            return errorResponse(6002, "Invalid or expired refresh token")
        }

        // 构建响应
        val response = RefreshTokenResponse(
            token = tokenInfo.token,
            expiresIn = ((tokenInfo.expiresAt - tokenInfo.createdAt) / 1000).toInt()
        )

        return successResponse(response)
    }

    /**
     * 验证Token
     */
    fun handleVerifyToken(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // 从Header中提取Token
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(6001, "Missing token")
        }

        val token = authHeader.substring(7)
        val tokenInfo = authManager.verifyToken(token)

        if (tokenInfo == null) {
            return successResponse(mapOf(
                "valid" to false,
                "deviceInfo" to null,
                "expiresAt" to null
            ))
        }

        // 构建响应
        val response = VerifyTokenResponse(
            valid = true,
            deviceInfo = DeviceInfoSimple(
                brand = android.os.Build.BRAND,
                model = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE_OR_CODENAME,
                apiLevel = android.os.Build.VERSION.SDK_INT
            ),
            expiresAt = tokenInfo.expiresAt
        )

        return successResponse(response)
    }

    /**
     * 撤销Token
     */
    fun handleRevokeToken(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // 从Header中提取Token
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(6001, "Missing token")
        }

        val token = authHeader.substring(7)
        val revoked = authManager.revokeToken(token)

        if (!revoked) {
            return errorResponse(6001, "Invalid token")
        }

        return successResponse(mapOf("revoked" to true))
    }
}
