package com.chaomixian.vflow.integration.feishu

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object FeishuModuleConfig {
    private const val TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val cacheLock = Any()

    sealed interface TokenResolution {
        data class Success(val token: String) : TokenResolution
        data class Failure(val title: String, val message: String) : TokenResolution
    }

    internal data class CachedAccessToken(
        val token: String,
        val expiresAtMillis: Long
    ) {
        fun isValidAt(nowMillis: Long): Boolean {
            return token.isNotBlank() && expiresAtMillis > nowMillis
        }
    }

    internal data class CachedAccessTokens(
        val appAccessToken: CachedAccessToken,
        val tenantAccessToken: CachedAccessToken
    ) {
        fun getValidAppAccessToken(nowMillis: Long): String? {
            return appAccessToken.token.takeIf { appAccessToken.isValidAt(nowMillis) }
        }

        fun getValidTenantAccessToken(nowMillis: Long): String? {
            return tenantAccessToken.token.takeIf { tenantAccessToken.isValidAt(nowMillis) }
        }
    }

    fun getAppId(context: Context): String {
        return prefs(context)
            .getString(ModuleConfigActivity.KEY_FEISHU_APP_ID, "")
            ?.trim()
            .orEmpty()
    }

    fun getAppSecret(context: Context): String {
        return prefs(context)
            .getString(ModuleConfigActivity.KEY_FEISHU_APP_SECRET, "")
            ?.trim()
            .orEmpty()
    }

    fun resolveAppAccessToken(context: Context, timeoutSeconds: Long = 15L): TokenResolution {
        return resolveAccessToken(
            context = context,
            timeoutSeconds = timeoutSeconds,
            title = "获取飞书应用令牌失败"
        ) { cachedTokens, nowMillis ->
            cachedTokens.getValidAppAccessToken(nowMillis)
        }
    }

    fun resolveTenantAccessToken(context: Context, timeoutSeconds: Long = 15L): TokenResolution {
        return resolveAccessToken(
            context = context,
            timeoutSeconds = timeoutSeconds,
            title = "获取飞书租户令牌失败"
        ) { cachedTokens, nowMillis ->
            cachedTokens.getValidTenantAccessToken(nowMillis)
        }
    }

    internal fun calculateExpiresAtMillis(nowMillis: Long, expireSeconds: Long): Long {
        return nowMillis + expireSeconds.coerceAtLeast(0L) * 1000L
    }

    private fun resolveAccessToken(
        context: Context,
        timeoutSeconds: Long,
        title: String,
        tokenSelector: (CachedAccessTokens, Long) -> String?
    ): TokenResolution {
        val safeTimeoutSeconds = timeoutSeconds.coerceAtLeast(1L)
        val appContext = context.applicationContext
        val prefs = prefs(appContext)

        synchronized(cacheLock) {
            val nowMillis = System.currentTimeMillis()
            val cachedTokens = readCachedTokens(prefs)
            tokenSelector(cachedTokens, nowMillis)?.let { token ->
                return TokenResolution.Success(token)
            }

            val appId = getAppId(appContext)
            val appSecret = getAppSecret(appContext)
            if (appId.isBlank() || appSecret.isBlank()) {
                return TokenResolution.Failure(
                    title = "缺少飞书配置",
                    message = "请先在设置 -> 模块配置中填写飞书 App ID 和 App Secret"
                )
            }

            return try {
                val refreshedTokens = requestAccessTokens(
                    appId = appId,
                    appSecret = appSecret,
                    timeoutSeconds = safeTimeoutSeconds
                )
                saveCachedTokens(prefs, refreshedTokens)
                val refreshedNowMillis = System.currentTimeMillis()
                val resolvedToken = tokenSelector(refreshedTokens, refreshedNowMillis)
                    ?: throw FeishuAuthException("飞书返回的访问令牌无效")
                TokenResolution.Success(resolvedToken)
            } catch (e: IOException) {
                TokenResolution.Failure(
                    title = "网络错误",
                    message = e.message ?: "请求飞书访问令牌时发生网络错误"
                )
            } catch (e: FeishuAuthException) {
                TokenResolution.Failure(
                    title = title,
                    message = e.message ?: "请求飞书访问令牌失败"
                )
            } catch (e: Exception) {
                TokenResolution.Failure(
                    title = title,
                    message = e.localizedMessage ?: "请求飞书访问令牌失败"
                )
            }
        }
    }

    private fun requestAccessTokens(
        appId: String,
        appSecret: String,
        timeoutSeconds: Long
    ): CachedAccessTokens {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val requestBody = Gson().toJson(
            mapOf(
                "app_id" to appId,
                "app_secret" to appSecret
            )
        ).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                throw FeishuAuthException("飞书返回了空响应")
            }

            val responseJson = try {
                JsonParser.parseString(responseBody).asJsonObject
            } catch (_: Exception) {
                throw FeishuAuthException("飞书返回了无法解析的响应")
            }

            val code = responseJson.get("code")?.asInt ?: -1
            val msg = responseJson.get("msg")?.asString ?: response.message
            if (!response.isSuccessful) {
                throw FeishuAuthException("HTTP ${response.code}: $msg")
            }
            if (code != 0) {
                throw FeishuAuthException("错误码: $code, 消息: $msg")
            }

            val appAccessToken = responseJson.get("app_access_token")?.asString?.trim().orEmpty()
            val tenantAccessToken = responseJson.get("tenant_access_token")?.asString?.trim().orEmpty()
            val expireSeconds = responseJson.get("expire")?.asLong ?: 0L
            if (appAccessToken.isBlank() || tenantAccessToken.isBlank() || expireSeconds <= 0L) {
                throw FeishuAuthException("飞书返回的 app_access_token 或 tenant_access_token 无效")
            }

            val expiresAtMillis = calculateExpiresAtMillis(
                nowMillis = System.currentTimeMillis(),
                expireSeconds = expireSeconds
            )

            return CachedAccessTokens(
                appAccessToken = CachedAccessToken(
                    token = appAccessToken,
                    expiresAtMillis = expiresAtMillis
                ),
                tenantAccessToken = CachedAccessToken(
                    token = tenantAccessToken,
                    expiresAtMillis = expiresAtMillis
                )
            )
        }
    }

    private fun readCachedTokens(prefs: SharedPreferences): CachedAccessTokens {
        return CachedAccessTokens(
            appAccessToken = CachedAccessToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN_EXPIRES_AT, 0L)
            ),
            tenantAccessToken = CachedAccessToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN_EXPIRES_AT, 0L)
            )
        )
    }

    private fun saveCachedTokens(
        prefs: SharedPreferences,
        cachedTokens: CachedAccessTokens
    ) {
        prefs.edit {
            putString(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN, cachedTokens.appAccessToken.token)
            putLong(
                ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN_EXPIRES_AT,
                cachedTokens.appAccessToken.expiresAtMillis
            )
            putString(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN, cachedTokens.tenantAccessToken.token)
            putLong(
                ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN_EXPIRES_AT,
                cachedTokens.tenantAccessToken.expiresAtMillis
            )
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)

    private class FeishuAuthException(message: String) : Exception(message)
}
