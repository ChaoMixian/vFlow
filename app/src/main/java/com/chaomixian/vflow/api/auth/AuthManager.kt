package com.chaomixian.vflow.api.auth

import android.content.Context
import android.content.SharedPreferences
import com.chaomixian.vflow.api.model.DeviceInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Token信息
 */
data class TokenInfo(
    val token: String,
    val refreshToken: String?,
    val deviceId: String,
    val deviceName: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val refreshExpiresAt: Long?
)

/**
 * 认证管理器
 * 负责Token的生成、验证、刷新和撤销
 * 支持Token持久化
 */
class AuthManager(context: Context) {

    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val deviceTokens = ConcurrentHashMap<String, String>() // deviceId -> token
    private val secureRandom = SecureRandom()
    private val prefs: SharedPreferences = context.getSharedPreferences("vflow_api_tokens", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadTokens()
    }

    companion object {
        const val DEFAULT_TOKEN_EXPIRY = 86400L * 7 // 7 days in seconds
        const val DEFAULT_REFRESH_EXPIRY = 86400L * 30 // 30 days in seconds
    }

    /**
     * 生成Token
     */
    fun generateToken(
        deviceId: String,
        deviceName: String?,
        expiresIn: Long = DEFAULT_TOKEN_EXPIRY,
        refreshExpiresIn: Long = DEFAULT_REFRESH_EXPIRY
    ): TokenInfo {
        // 撤销该设备的旧Token
        revokeDeviceToken(deviceId)

        val token = generateSecureToken()
        val refreshToken = generateSecureToken()
        val now = System.currentTimeMillis()

        val tokenInfo = TokenInfo(
            token = token,
            refreshToken = refreshToken,
            deviceId = deviceId,
            deviceName = deviceName,
            createdAt = now,
            expiresAt = now + (expiresIn * 1000),
            refreshExpiresAt = now + (refreshExpiresIn * 1000)
        )

        tokens[token] = tokenInfo
        deviceTokens[deviceId] = token

        saveTokens()

        return tokenInfo
    }

    /**
     * 验证Token
     */
    fun verifyToken(token: String): TokenInfo? {
        val tokenInfo = tokens[token] ?: return null

        val now = System.currentTimeMillis()
        if (now > tokenInfo.expiresAt) {
            // Token已过期
            tokens.remove(token)
            deviceTokens.remove(tokenInfo.deviceId)
            saveTokens()
            return null
        }

        return tokenInfo
    }

    /**
     * 刷新Token
     */
    fun refreshToken(refreshToken: String, expiresIn: Long = DEFAULT_TOKEN_EXPIRY): TokenInfo? {
        // 查找使用该refreshToken的TokenInfo
        val tokenInfo = tokens.values.find { it.refreshToken == refreshToken }
            ?: return null

        val now = System.currentTimeMillis()
        if (now > (tokenInfo.refreshExpiresAt ?: return null)) {
            // RefreshToken已过期
            tokens.remove(tokenInfo.token)
            deviceTokens.remove(tokenInfo.deviceId)
            saveTokens()
            return null
        }

        // 生成新Token
        return generateToken(
            deviceId = tokenInfo.deviceId,
            deviceName = tokenInfo.deviceName,
            expiresIn = expiresIn,
            refreshExpiresIn = DEFAULT_REFRESH_EXPIRY
        )
    }

    /**
     * 撤销Token
     */
    fun revokeToken(token: String): Boolean {
        val tokenInfo = tokens.remove(token) ?: return false
        deviceTokens.remove(tokenInfo.deviceId)
        saveTokens()
        return true
    }

    /**
     * 撤销设备的所有Token
     */
    fun revokeDeviceToken(deviceId: String): Boolean {
        val token = deviceTokens.remove(deviceId) ?: return false
        tokens.remove(token)
        saveTokens()
        return true
    }

    /**
     * 检查设备是否已授权
     */
    fun isDeviceAuthorized(deviceId: String): Boolean {
        return deviceTokens.containsKey(deviceId)
    }

    /**
     * 获取Token信息
     */
    fun getTokenInfo(token: String): TokenInfo? {
        return tokens[token]
    }

    /**
     * 清理过期Token
     */
    fun cleanupExpiredTokens() {
        val now = System.currentTimeMillis()
        val expiredTokens = tokens.filterValues { now > it.expiresAt }.keys
        expiredTokens.forEach { token ->
            val tokenInfo = tokens.remove(token)
            if (tokenInfo?.deviceId != null) {
                deviceTokens.remove(tokenInfo.deviceId)
            }
        }
        if (expiredTokens.isNotEmpty()) {
            saveTokens()
        }
    }

    /**
     * 生成安全的随机Token
     */
    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * 获取统计信息
     */
    fun getStats(): AuthStats {
        cleanupExpiredTokens()
        return AuthStats(
            totalTokens = tokens.size,
            activeDevices = deviceTokens.size
        )
    }

    /**
     * 获取所有Tokens
     */
    fun getTokens(): Map<String, TokenInfo> {
        return tokens
    }

    /**
     * 从持久化存储加载Tokens
     */
    private fun loadTokens() {
        val now = System.currentTimeMillis()
        val json = prefs.getString("tokens", null)
        if (json != null) {
            try {
                val type = object : TypeToken<Map<String, TokenInfo>>() {}.type
                val loadedTokens = gson.fromJson<Map<String, TokenInfo>>(json, type) ?: emptyMap()

                // 只加载未过期的Tokens
                loadedTokens.forEach { (token, tokenInfo) ->
                    if (now <= tokenInfo.expiresAt) {
                        tokens[token] = tokenInfo
                        deviceTokens[tokenInfo.deviceId] = token
                    }
                }
            } catch (e: Exception) {
                // 加载失败，忽略
            }
        }
    }

    /**
     * 持久化Tokens
     */
    private fun saveTokens() {
        val json = gson.toJson(tokens)
        prefs.edit().putString("tokens", json).apply()
    }

    /**
     * 保存Tokens（公共方法，供Token变更后调用）
     */
    fun persist() {
        saveTokens()
    }
}

/**
 * 认证统计
 */
data class AuthStats(
    val totalTokens: Int,
    val activeDevices: Int
)
