package com.chaomixian.vflow.integration.feishu

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object FeishuModuleConfig {
    private const val APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"
    private const val USER_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v2/oauth/token"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val cacheLock = Any()

    sealed interface TokenResolution {
        data class Success(val token: String) : TokenResolution
        data class Failure(val title: String, val message: String) : TokenResolution
    }

    internal data class CachedToken(
        val token: String,
        val expiresAtMillis: Long
    ) {
        fun isValidAt(nowMillis: Long): Boolean {
            return token.isNotBlank() && expiresAtMillis > nowMillis
        }
    }

    internal data class CachedAccessTokens(
        val appAccessToken: CachedToken,
        val tenantAccessToken: CachedToken
    ) {
        fun getValidAppAccessToken(nowMillis: Long): String? {
            return appAccessToken.token.takeIf { appAccessToken.isValidAt(nowMillis) }
        }

        fun getValidTenantAccessToken(nowMillis: Long): String? {
            return tenantAccessToken.token.takeIf { tenantAccessToken.isValidAt(nowMillis) }
        }
    }

    internal data class UserOAuthConfig(
        val authCode: String,
        val redirectUri: String,
        val codeVerifier: String,
        val scope: String
    )

    internal data class CachedUserTokens(
        val userAccessToken: CachedToken,
        val refreshToken: CachedToken
    ) {
        fun getValidUserAccessToken(nowMillis: Long): String? {
            return userAccessToken.token.takeIf { userAccessToken.isValidAt(nowMillis) }
        }

        fun getValidRefreshToken(nowMillis: Long): String? {
            return refreshToken.token.takeIf { refreshToken.isValidAt(nowMillis) }
        }
    }

    data class UserAuthorizationStatus(
        val accessTokenExpiresAtMillis: Long,
        val refreshTokenExpiresAtMillis: Long,
        val hasValidAccessToken: Boolean,
        val hasValidRefreshToken: Boolean
    ) {
        val isAuthorized: Boolean
            get() = hasValidAccessToken || hasValidRefreshToken
    }

    private data class UserTokenResponse(
        val userAccessToken: CachedToken,
        val refreshToken: CachedToken
    )

    sealed interface UserAuthorizationResolution {
        data class Success(
            val accessTokenExpiresAtMillis: Long,
            val refreshTokenExpiresAtMillis: Long,
            val hasRefreshToken: Boolean
        ) : UserAuthorizationResolution

        data class Failure(val title: String, val message: String) : UserAuthorizationResolution
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
        return resolveAppScopedToken(
            context = context,
            timeoutSeconds = timeoutSeconds,
            title = "获取飞书应用令牌失败"
        ) { cachedTokens, nowMillis ->
            cachedTokens.getValidAppAccessToken(nowMillis)
        }
    }

    fun resolveTenantAccessToken(context: Context, timeoutSeconds: Long = 15L): TokenResolution {
        return resolveAppScopedToken(
            context = context,
            timeoutSeconds = timeoutSeconds,
            title = "获取飞书租户令牌失败"
        ) { cachedTokens, nowMillis ->
            cachedTokens.getValidTenantAccessToken(nowMillis)
        }
    }

    fun resolveUserAccessToken(context: Context, timeoutSeconds: Long = 15L): TokenResolution {
        val safeTimeoutSeconds = timeoutSeconds.coerceAtLeast(1L)
        val appContext = context.applicationContext
        val prefs = prefs(appContext)

        synchronized(cacheLock) {
            val nowMillis = System.currentTimeMillis()
            val cachedUserTokens = readCachedUserTokens(prefs)
            cachedUserTokens.getValidUserAccessToken(nowMillis)?.let { token ->
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

            val oauthConfig = readUserOAuthConfig(prefs)
            val refreshToken = cachedUserTokens.getValidRefreshToken(nowMillis)
            var refreshFailure: FeishuAuthException? = null

            if (refreshToken != null) {
                try {
                    val refreshedTokens = refreshUserAccessToken(
                        appId = appId,
                        appSecret = appSecret,
                        refreshToken = refreshToken,
                        scope = oauthConfig.scope,
                        timeoutSeconds = safeTimeoutSeconds
                    )
                    saveCachedUserTokens(prefs, refreshedTokens)
                    return TokenResolution.Success(refreshedTokens.userAccessToken.token)
                } catch (e: IOException) {
                    return TokenResolution.Failure(
                        title = "网络错误",
                        message = e.message ?: "刷新飞书用户令牌时发生网络错误"
                    )
                } catch (e: FeishuAuthException) {
                    if (shouldClearUserRefreshToken(e.code)) {
                        clearUserTokenCache(prefs)
                    }
                    refreshFailure = e
                } catch (e: Exception) {
                    return TokenResolution.Failure(
                        title = "获取飞书用户令牌失败",
                        message = e.localizedMessage ?: "刷新飞书用户令牌失败"
                    )
                }
            }

            if (oauthConfig.authCode.isBlank()) {
                return TokenResolution.Failure(
                    title = if (refreshFailure != null) "获取飞书用户令牌失败" else "缺少飞书用户授权",
                    message = refreshFailure?.message
                        ?: "请先在设置 -> 模块配置中完成飞书用户认证。若需要自动刷新，请在飞书后台为应用开启 offline_access 权限，并打开刷新 user_access_token 开关"
                )
            }

            return try {
                val exchangedTokens = exchangeUserAccessToken(
                    appId = appId,
                    appSecret = appSecret,
                    oauthConfig = oauthConfig,
                    timeoutSeconds = safeTimeoutSeconds
                )
                saveCachedUserTokens(prefs, exchangedTokens)
                clearUserAuthorizationCode(prefs)
                TokenResolution.Success(exchangedTokens.userAccessToken.token)
            } catch (e: IOException) {
                TokenResolution.Failure(
                    title = "网络错误",
                    message = e.message ?: "获取飞书用户令牌时发生网络错误"
                )
            } catch (e: FeishuAuthException) {
                if (shouldClearUserAuthorizationCode(e.code)) {
                    clearUserAuthorizationCode(prefs)
                }
                TokenResolution.Failure(
                    title = "获取飞书用户令牌失败",
                    message = e.message ?: "获取飞书用户令牌失败"
                )
            } catch (e: Exception) {
                TokenResolution.Failure(
                    title = "获取飞书用户令牌失败",
                    message = e.localizedMessage ?: "获取飞书用户令牌失败"
                )
            }
        }
    }

    fun getUserAuthorizationStatus(context: Context): UserAuthorizationStatus {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val nowMillis = System.currentTimeMillis()
        val cachedTokens = synchronized(cacheLock) {
            readCachedUserTokens(prefs)
        }
        return UserAuthorizationStatus(
            accessTokenExpiresAtMillis = cachedTokens.userAccessToken.expiresAtMillis,
            refreshTokenExpiresAtMillis = cachedTokens.refreshToken.expiresAtMillis,
            hasValidAccessToken = cachedTokens.userAccessToken.isValidAt(nowMillis),
            hasValidRefreshToken = cachedTokens.refreshToken.isValidAt(nowMillis)
        )
    }

    fun authorizeUserWithAuthCode(
        context: Context,
        authCode: String,
        redirectUri: String,
        timeoutSeconds: Long = 15L
    ): UserAuthorizationResolution {
        val safeTimeoutSeconds = timeoutSeconds.coerceAtLeast(1L)
        val trimmedAuthCode = authCode.trim()
        val trimmedRedirectUri = redirectUri.trim()
        val appContext = context.applicationContext
        val prefs = prefs(appContext)

        synchronized(cacheLock) {
            val appId = getAppId(appContext)
            val appSecret = getAppSecret(appContext)
            if (appId.isBlank() || appSecret.isBlank()) {
                return UserAuthorizationResolution.Failure(
                    title = "缺少飞书配置",
                    message = "请先在设置 -> 模块配置中填写飞书 App ID 和 App Secret"
                )
            }

            if (trimmedAuthCode.isBlank()) {
                return UserAuthorizationResolution.Failure(
                    title = "获取飞书用户令牌失败",
                    message = "飞书未返回有效的授权结果，请重新开始认证"
                )
            }

            saveUserAuthorizationCode(
                prefs = prefs,
                authCode = trimmedAuthCode,
                redirectUri = trimmedRedirectUri
            )

            return try {
                val exchangedTokens = exchangeUserAccessToken(
                    appId = appId,
                    appSecret = appSecret,
                    oauthConfig = UserOAuthConfig(
                        authCode = trimmedAuthCode,
                        redirectUri = trimmedRedirectUri,
                        codeVerifier = "",
                        scope = ""
                    ),
                    timeoutSeconds = safeTimeoutSeconds
                )
                saveCachedUserTokens(prefs, exchangedTokens)
                clearUserAuthorizationCode(prefs)
                UserAuthorizationResolution.Success(
                    accessTokenExpiresAtMillis = exchangedTokens.userAccessToken.expiresAtMillis,
                    refreshTokenExpiresAtMillis = exchangedTokens.refreshToken.expiresAtMillis,
                    hasRefreshToken = exchangedTokens.refreshToken.token.isNotBlank()
                )
            } catch (e: IOException) {
                UserAuthorizationResolution.Failure(
                    title = "网络错误",
                    message = e.message ?: "获取飞书用户令牌时发生网络错误"
                )
            } catch (e: FeishuAuthException) {
                if (shouldClearUserAuthorizationCode(e.code)) {
                    clearUserAuthorizationCode(prefs)
                }
                UserAuthorizationResolution.Failure(
                    title = "获取飞书用户令牌失败",
                    message = e.message ?: "获取飞书用户令牌失败"
                )
            } catch (e: Exception) {
                UserAuthorizationResolution.Failure(
                    title = "获取飞书用户令牌失败",
                    message = e.localizedMessage ?: "获取飞书用户令牌失败"
                )
            }
        }
    }

    fun clearUserAuthorization(context: Context) {
        val prefs = prefs(context.applicationContext)
        synchronized(cacheLock) {
            clearUserAuthorizationCode(prefs)
            clearUserTokenCache(prefs)
        }
    }

    internal fun calculateExpiresAtMillis(nowMillis: Long, expireSeconds: Long): Long {
        return nowMillis + expireSeconds.coerceAtLeast(0L) * 1000L
    }

    private fun resolveAppScopedToken(
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
            val cachedTokens = readCachedAppTokens(prefs)
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
                val refreshedTokens = requestAppScopedTokens(
                    appId = appId,
                    appSecret = appSecret,
                    timeoutSeconds = safeTimeoutSeconds
                )
                saveCachedAppTokens(prefs, refreshedTokens)
                val resolvedToken = tokenSelector(refreshedTokens, System.currentTimeMillis())
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

    private fun requestAppScopedTokens(
        appId: String,
        appSecret: String,
        timeoutSeconds: Long
    ): CachedAccessTokens {
        val responseJson = postJson(
            url = APP_TOKEN_URL,
            bodyMap = mapOf(
                "app_id" to appId,
                "app_secret" to appSecret
            ),
            timeoutSeconds = timeoutSeconds
        )

        val code = responseJson.get("code")?.asInt ?: -1
        val msg = responseJson.get("msg")?.asString ?: "未知错误"
        if (code != 0) {
            throw FeishuAuthException("错误码: $code, 消息: $msg", code)
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
            appAccessToken = CachedToken(
                token = appAccessToken,
                expiresAtMillis = expiresAtMillis
            ),
            tenantAccessToken = CachedToken(
                token = tenantAccessToken,
                expiresAtMillis = expiresAtMillis
            )
        )
    }

    private fun exchangeUserAccessToken(
        appId: String,
        appSecret: String,
        oauthConfig: UserOAuthConfig,
        timeoutSeconds: Long
    ): UserTokenResponse {
        val requestBody = linkedMapOf<String, String>(
            "grant_type" to "authorization_code",
            "client_id" to appId,
            "client_secret" to appSecret,
            "code" to oauthConfig.authCode
        )
        oauthConfig.redirectUri.takeIf { it.isNotBlank() }?.let {
            requestBody["redirect_uri"] = it
        }
        oauthConfig.codeVerifier.takeIf { it.isNotBlank() }?.let {
            requestBody["code_verifier"] = it
        }
        oauthConfig.scope.takeIf { it.isNotBlank() }?.let {
            requestBody["scope"] = it
        }
        return requestUserTokens(requestBody, timeoutSeconds)
    }

    private fun refreshUserAccessToken(
        appId: String,
        appSecret: String,
        refreshToken: String,
        scope: String,
        timeoutSeconds: Long
    ): UserTokenResponse {
        val requestBody = linkedMapOf<String, String>(
            "grant_type" to "refresh_token",
            "client_id" to appId,
            "client_secret" to appSecret,
            "refresh_token" to refreshToken
        )
        scope.takeIf { it.isNotBlank() }?.let {
            requestBody["scope"] = it
        }
        return requestUserTokens(requestBody, timeoutSeconds)
    }

    private fun requestUserTokens(
        requestBody: Map<String, String>,
        timeoutSeconds: Long
    ): UserTokenResponse {
        val responseJson = postJson(
            url = USER_TOKEN_URL,
            bodyMap = requestBody,
            timeoutSeconds = timeoutSeconds
        )

        val code = responseJson.get("code")?.asInt ?: -1
        val errorDescription = responseJson.get("error_description")?.asString
        val error = responseJson.get("error")?.asString
        if (code != 0) {
            val message = errorDescription ?: error ?: responseJson.get("msg")?.asString ?: "未知错误"
            throw FeishuAuthException("错误码: $code, 消息: $message", code)
        }

        val userAccessToken = responseJson.get("access_token")?.asString?.trim().orEmpty()
        val expiresIn = responseJson.get("expires_in")?.asLong ?: 0L
        if (userAccessToken.isBlank() || expiresIn <= 0L) {
            throw FeishuAuthException("飞书返回的 user_access_token 无效")
        }

        val nowMillis = System.currentTimeMillis()
        val refreshToken = responseJson.get("refresh_token")?.asString?.trim().orEmpty()
        val refreshTokenExpiresIn = responseJson.get("refresh_token_expires_in")?.asLong ?: 0L

        return UserTokenResponse(
            userAccessToken = CachedToken(
                token = userAccessToken,
                expiresAtMillis = calculateExpiresAtMillis(nowMillis, expiresIn)
            ),
            refreshToken = if (refreshToken.isNotBlank() && refreshTokenExpiresIn > 0L) {
                CachedToken(
                    token = refreshToken,
                    expiresAtMillis = calculateExpiresAtMillis(nowMillis, refreshTokenExpiresIn)
                )
            } else {
                CachedToken(token = "", expiresAtMillis = 0L)
            }
        )
    }

    private fun postJson(
        url: String,
        bodyMap: Map<String, String>,
        timeoutSeconds: Long
    ): JsonObject {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(Gson().toJson(bodyMap).toRequestBody(jsonMediaType))
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

            if (!response.isSuccessful) {
                val code = responseJson.get("code")?.asInt
                val message = responseJson.get("error_description")?.asString
                    ?: responseJson.get("msg")?.asString
                    ?: response.message
                throw FeishuAuthException("HTTP ${response.code}: $message", code)
            }

            return responseJson
        }
    }

    private fun readCachedAppTokens(prefs: SharedPreferences): CachedAccessTokens {
        return CachedAccessTokens(
            appAccessToken = CachedToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN_EXPIRES_AT, 0L)
            ),
            tenantAccessToken = CachedToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN_EXPIRES_AT, 0L)
            )
        )
    }

    private fun saveCachedAppTokens(
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

    private fun readUserOAuthConfig(prefs: SharedPreferences): UserOAuthConfig {
        return UserOAuthConfig(
            authCode = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_AUTH_CODE, "")?.trim().orEmpty(),
            redirectUri = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_REDIRECT_URI, "")?.trim().orEmpty(),
            codeVerifier = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_CODE_VERIFIER, "")?.trim().orEmpty(),
            scope = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_SCOPE, "")?.trim().orEmpty()
        )
    }

    private fun readCachedUserTokens(prefs: SharedPreferences): CachedUserTokens {
        return CachedUserTokens(
            userAccessToken = CachedToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN_EXPIRES_AT, 0L)
            ),
            refreshToken = CachedToken(
                token = prefs.getString(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN, "")?.trim().orEmpty(),
                expiresAtMillis = prefs.getLong(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN_EXPIRES_AT, 0L)
            )
        )
    }

    private fun saveCachedUserTokens(
        prefs: SharedPreferences,
        cachedTokens: UserTokenResponse
    ) {
        prefs.edit {
            putString(ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN, cachedTokens.userAccessToken.token)
            putLong(
                ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN_EXPIRES_AT,
                cachedTokens.userAccessToken.expiresAtMillis
            )

            if (cachedTokens.refreshToken.token.isBlank()) {
                remove(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN)
                remove(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN_EXPIRES_AT)
            } else {
                putString(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN, cachedTokens.refreshToken.token)
                putLong(
                    ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN_EXPIRES_AT,
                    cachedTokens.refreshToken.expiresAtMillis
                )
            }
        }
    }

    private fun saveUserAuthorizationCode(
        prefs: SharedPreferences,
        authCode: String,
        redirectUri: String
    ) {
        prefs.edit {
            putString(ModuleConfigActivity.KEY_FEISHU_USER_AUTH_CODE, authCode)
            putString(ModuleConfigActivity.KEY_FEISHU_USER_REDIRECT_URI, redirectUri)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_CODE_VERIFIER)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_SCOPE)
        }
    }

    private fun clearUserAuthorizationCode(prefs: SharedPreferences) {
        prefs.edit {
            remove(ModuleConfigActivity.KEY_FEISHU_USER_AUTH_CODE)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_REDIRECT_URI)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_CODE_VERIFIER)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_SCOPE)
        }
    }

    private fun clearUserTokenCache(prefs: SharedPreferences) {
        prefs.edit {
            remove(ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_ACCESS_TOKEN_EXPIRES_AT)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN)
            remove(ModuleConfigActivity.KEY_FEISHU_USER_REFRESH_TOKEN_EXPIRES_AT)
        }
    }

    private fun shouldClearUserRefreshToken(code: Int?): Boolean {
        return code in setOf(20024, 20026, 20037, 20064, 20073, 20074)
    }

    private fun shouldClearUserAuthorizationCode(code: Int?): Boolean {
        return code in setOf(20003, 20004, 20024, 20049, 20065, 20071)
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)

    private class FeishuAuthException(
        message: String,
        val code: Int? = null
    ) : Exception(message)
}
