package com.chaomixian.vflow.integration.feishu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuModuleConfigTest {

    @Test
    fun `cached token stays valid before expiration`() {
        val cachedToken = FeishuModuleConfig.CachedToken(
            token = "t-valid",
            expiresAtMillis = 2_000L
        )

        assertTrue(cachedToken.isValidAt(1_999L))
        assertFalse(cachedToken.isValidAt(2_000L))
    }

    @Test
    fun `cached token requires both token and expiration`() {
        val blankToken = FeishuModuleConfig.CachedToken(
            token = " ",
            expiresAtMillis = 2_000L
        )
        val expiredToken = FeishuModuleConfig.CachedToken(
            token = "t-expired",
            expiresAtMillis = 0L
        )

        assertFalse(blankToken.isValidAt(1_000L))
        assertFalse(expiredToken.isValidAt(1_000L))
    }

    @Test
    fun `app and tenant tokens are resolved independently`() {
        val cachedTokens = FeishuModuleConfig.CachedAccessTokens(
            appAccessToken = FeishuModuleConfig.CachedToken(
                token = "app-token",
                expiresAtMillis = 5_000L
            ),
            tenantAccessToken = FeishuModuleConfig.CachedToken(
                token = "tenant-token",
                expiresAtMillis = 6_000L
            )
        )

        assertEquals("app-token", cachedTokens.getValidAppAccessToken(4_000L))
        assertEquals("tenant-token", cachedTokens.getValidTenantAccessToken(4_000L))
        assertNull(cachedTokens.getValidAppAccessToken(5_000L))
        assertEquals("tenant-token", cachedTokens.getValidTenantAccessToken(5_000L))
    }

    @Test
    fun `user access token and refresh token are resolved independently`() {
        val cachedTokens = FeishuModuleConfig.CachedUserTokens(
            userAccessToken = FeishuModuleConfig.CachedToken(
                token = "user-token",
                expiresAtMillis = 5_000L
            ),
            refreshToken = FeishuModuleConfig.CachedToken(
                token = "refresh-token",
                expiresAtMillis = 10_000L
            )
        )

        assertEquals("user-token", cachedTokens.getValidUserAccessToken(4_000L))
        assertEquals("refresh-token", cachedTokens.getValidRefreshToken(4_000L))
        assertNull(cachedTokens.getValidUserAccessToken(5_000L))
        assertEquals("refresh-token", cachedTokens.getValidRefreshToken(9_999L))
    }

    @Test
    fun `calculate expiration uses expire seconds`() {
        assertEquals(123_000L, FeishuModuleConfig.calculateExpiresAtMillis(3_000L, 120L))
    }

    @Test
    fun `calculate expiration clamps negative seconds`() {
        assertEquals(3_000L, FeishuModuleConfig.calculateExpiresAtMillis(3_000L, -1L))
    }

    @Test
    fun `user authorization status is authorized when refresh token is still valid`() {
        val status = FeishuModuleConfig.UserAuthorizationStatus(
            accessTokenExpiresAtMillis = 0L,
            refreshTokenExpiresAtMillis = 10_000L,
            hasValidAccessToken = false,
            hasValidRefreshToken = true
        )

        assertTrue(status.isAuthorized)
    }

    @Test
    fun `user authorization status is unauthorized when both tokens are invalid`() {
        val status = FeishuModuleConfig.UserAuthorizationStatus(
            accessTokenExpiresAtMillis = 0L,
            refreshTokenExpiresAtMillis = 0L,
            hasValidAccessToken = false,
            hasValidRefreshToken = false
        )

        assertFalse(status.isAuthorized)
    }
}
