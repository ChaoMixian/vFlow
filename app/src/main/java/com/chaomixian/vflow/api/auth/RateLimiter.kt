package com.chaomixian.vflow.api.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Rate Limiter - 限制API请求频率
 */
class RateLimiter {

    private val clientRequests = ConcurrentHashMap<String, RequestHistory>()
    private val mutex = Mutex()

    companion object {
        // 默认限制（请求次数 / 时间窗口秒数）
        const val DEFAULT_AUTH_LIMIT = 5
        const val DEFAULT_AUTH_WINDOW = 60L // 1分钟

        const val DEFAULT_EXECUTE_LIMIT = 10
        const val DEFAULT_EXECUTE_WINDOW = 60L // 1分钟

        const val DEFAULT_QUERY_LIMIT = 100
        const val DEFAULT_QUERY_WINDOW = 60L // 1分钟

        const val DEFAULT_MODIFY_LIMIT = 60
        const val DEFAULT_MODIFY_WINDOW = 60L // 1分钟
    }

    /**
     * 请求类型
     */
    enum class RequestType {
        AUTH,
        EXECUTE,
        QUERY,
        MODIFY
    }

    /**
     * 请求历史
     */
    data class RequestHistory(
        var count: Int,
        var windowStart: Long,
        val limit: Int,
        val windowSize: Long
    ) {
        val resetAt: Long
            get() = windowStart + (windowSize * 1000)

        val remaining: Int
            get() = max(0, limit - count)
    }

    /**
     * 检查是否允许请求
     * @param clientId 客户端标识（Token或IP）
     * @param type 请求类型
     * @return RateLimitResult 包含是否允许及限制信息
     */
    suspend fun checkLimit(
        clientId: String,
        type: RequestType
    ): RateLimitResult {
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val (limit, windowSize) = getLimitForType(type)

            val history = clientRequests.getOrPut(clientId) {
                RequestHistory(
                    count = 0,
                    windowStart = now,
                    limit = limit,
                    windowSize = windowSize
                )
            }

            // 检查是否需要重置窗口
            if (now > history.resetAt) {
                history.count = 0
                history.windowStart = now
            }

            // 检查是否超过限制
            if (history.count >= history.limit) {
                return RateLimitResult(
                    allowed = false,
                    limit = history.limit,
                    remaining = 0,
                    resetAt = history.resetAt
                )
            }

            // 增加计数
            history.count++

            RateLimitResult(
                allowed = true,
                limit = history.limit,
                remaining = history.remaining,
                resetAt = history.resetAt
            )
        }
    }

    /**
     * 重置客户端限制
     */
    suspend fun reset(clientId: String) {
        mutex.withLock {
            clientRequests.remove(clientId)
        }
    }

    /**
     * 清理过期记录
     */
    suspend fun cleanup() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val expiredKeys = clientRequests.filterValues { now > it.resetAt }.keys
            expiredKeys.forEach { clientRequests.remove(it) }
        }
    }

    /**
     * 获取请求类型的限制
     */
    private fun getLimitForType(type: RequestType): Pair<Int, Long> {
        return when (type) {
            RequestType.AUTH -> DEFAULT_AUTH_LIMIT to DEFAULT_AUTH_WINDOW
            RequestType.EXECUTE -> DEFAULT_EXECUTE_LIMIT to DEFAULT_EXECUTE_WINDOW
            RequestType.QUERY -> DEFAULT_QUERY_LIMIT to DEFAULT_QUERY_WINDOW
            RequestType.MODIFY -> DEFAULT_MODIFY_LIMIT to DEFAULT_MODIFY_WINDOW
        }
    }
}

/**
 * Rate Limit结果
 */
data class RateLimitResult(
    val allowed: Boolean,
    val limit: Int,
    val remaining: Int,
    val resetAt: Long
)
