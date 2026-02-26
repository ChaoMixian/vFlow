package com.chaomixian.vflow.api.model

/**
 * 生成Token请求
 */
data class GenerateTokenRequest(
    val deviceId: String,
    val deviceName: String? = null,
    val timestamp: Long
)

/**
 * Token响应
 */
data class TokenResponse(
    val token: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val tokenType: String,
    val deviceInfo: DeviceInfoSimple
)

/**
 * 刷新Token响应
 */
data class RefreshTokenResponse(
    val token: String,
    val expiresIn: Int
)

/**
 * 验证Token响应
 */
data class VerifyTokenResponse(
    val valid: Boolean,
    val deviceInfo: DeviceInfoSimple?,
    val expiresAt: Long?
)

/**
 * 设备信息（简化版）- 用于Token响应
 */
data class DeviceInfoSimple(
    val brand: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int?
)

/**
 * Rate Limit信息
 */
data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetAt: Long
)
