package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader

/**
 * 基础Handler
 * 提供通用方法
 */
abstract class BaseHandler(
    protected val rateLimiter: RateLimiter,
    protected val gson: Gson
) {

    /**
     * 从请求中读取Body
     */
    protected fun readBody(session: NanoHTTPD.IHTTPSession): String? {
        return try {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: return null
            val buffer = CharArray(contentLength)
            val reader = BufferedReader(session.inputStream.reader())
            reader.read(buffer)
            String(buffer)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析JSON请求体
     */
    protected fun <T> parseRequestBody(session: NanoHTTPD.IHTTPSession, clazz: Class<T>): T? {
        val body = readBody(session) ?: return null
        return try {
            gson.fromJson(body, clazz)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建JSON响应
     */
    protected fun createJsonResponse(
        code: Int,
        message: String,
        data: Any? = null,
        details: Map<String, Any?>? = null
    ): NanoHTTPD.Response {
        val responseBody = mapOf(
            "code" to code,
            "message" to message,
            "data" to data,
            "details" to details
        )
        val status = when (code) {
            0 -> NanoHTTPD.Response.Status.OK
            400 -> NanoHTTPD.Response.Status.BAD_REQUEST
            401 -> NanoHTTPD.Response.Status.UNAUTHORIZED
            404 -> NanoHTTPD.Response.Status.NOT_FOUND
            500 -> NanoHTTPD.Response.Status.INTERNAL_ERROR
            else -> NanoHTTPD.Response.Status.OK
        }
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            gson.toJson(responseBody)
        )
    }

    /**
     * 成功响应
     */
    protected fun successResponse(data: Any? = null, message: String = "success"): NanoHTTPD.Response {
        return createJsonResponse(0, message, data)
    }

    /**
     * 错误响应
     */
    protected fun errorResponse(
        code: Int,
        message: String,
        details: Map<String, Any?>? = null
    ): NanoHTTPD.Response {
        return createJsonResponse(code, message, null, details)
    }

    /**
     * 检查Rate Limit
     */
    protected suspend fun checkRateLimit(
        token: String,
        type: RateLimiter.RequestType
    ): com.chaomixian.vflow.api.auth.RateLimitResult? {
        val result = rateLimiter.checkLimit(token, type)
        if (!result.allowed) {
            return result
        }
        return null
    }

    /**
     * 创建Rate Limit响应
     */
    protected fun rateLimitResponse(result: com.chaomixian.vflow.api.auth.RateLimitResult): NanoHTTPD.Response {
        val response = errorResponse(
            7001,
            "Rate limit exceeded",
            mapOf(
                "limit" to result.limit,
                "remaining" to result.remaining,
                "resetAt" to result.resetAt
            )
        )
        response.addHeader("X-RateLimit-Limit", result.limit.toString())
        response.addHeader("X-RateLimit-Remaining", result.remaining.toString())
        response.addHeader("X-RateLimit-Reset", result.resetAt.toString())
        return response
    }

    /**
     * 从URL中提取路径参数
     * 例如: /api/v1/workflows/{id} -> 提取id
     */
    protected fun getPathParameter(uri: String, prefix: String, index: Int = 0): String? {
        val path = uri.removePrefix(prefix).trim('/')
        val segments = path.split('/')
        return segments.getOrNull(index)
    }

    /**
     * 解析查询参数
     */
    protected fun parseQueryParams(session: NanoHTTPD.IHTTPSession): Map<String, String> {
        return session.queryParameterString?.split('&')?.associate { param ->
            val parts = param.split('=', limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        } ?: emptyMap()
    }
}
