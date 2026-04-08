package com.chaomixian.vflow.integration.feishu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuModuleConfigTest {

    @Test
    fun `single cached token stays valid before expiration`() {
        val cachedToken = FeishuModuleConfig.CachedAccessToken(
            token = "t-valid",
            expiresAtMillis = 2_000L
        )

        assertTrue(cachedToken.isValidAt(1_999L))
        assertFalse(cachedToken.isValidAt(2_000L))
    }

    @Test
    fun `single cached token requires both token and expiration`() {
        val blankToken = FeishuModuleConfig.CachedAccessToken(
            token = " ",
            expiresAtMillis = 2_000L
        )
        val expiredToken = FeishuModuleConfig.CachedAccessToken(
            token = "t-expired",
            expiresAtMillis = 0L
        )

        assertFalse(blankToken.isValidAt(1_000L))
        assertFalse(expiredToken.isValidAt(1_000L))
    }

    @Test
    fun `token pair exposes valid app and tenant tokens independently`() {
        val cachedTokens = FeishuModuleConfig.CachedAccessTokens(
            appAccessToken = FeishuModuleConfig.CachedAccessToken(
                token = "app-token",
                expiresAtMillis = 5_000L
            ),
            tenantAccessToken = FeishuModuleConfig.CachedAccessToken(
                token = "tenant-token",
                expiresAtMillis = 6_000L
            )
        )

        assertEquals("app-token", cachedTokens.getValidAppAccessToken(4_000L))
        assertEquals("tenant-token", cachedTokens.getValidTenantAccessToken(4_000L))
        assertEquals(null, cachedTokens.getValidAppAccessToken(5_000L))
        assertEquals("tenant-token", cachedTokens.getValidTenantAccessToken(5_000L))
    }

    @Test
    fun `calculate expiration uses expire seconds`() {
        assertEquals(123_000L, FeishuModuleConfig.calculateExpiresAtMillis(3_000L, 120L))
    }

    @Test
    fun `calculate expiration clamps negative seconds`() {
        assertEquals(3_000L, FeishuModuleConfig.calculateExpiresAtMillis(3_000L, -1L))
    }
}
