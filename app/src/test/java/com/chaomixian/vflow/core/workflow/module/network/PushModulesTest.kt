package com.chaomixian.vflow.core.workflow.module.network

import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushModulesTest {

    @Test
    fun `parse bark response extracts code and message`() {
        val parsed = parseBarkPushResponse(
            """
            {
              "code": 200,
              "message": "success"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(200, parsed?.code)
        assertEquals("success", parsed?.message)
    }

    @Test
    fun `bark payload includes optional fields when provided`() {
        val payload = buildBarkPushPayload(
            title = "Alarm",
            body = "Triggered",
            subtitle = "Server A",
            level = "critical",
            volume = "8.5",
            badge = "3",
            icon = "https://example.com/icon.png",
            image = "https://example.com/image.png",
            autoCopy = true,
            copy = "copy text",
            jumpUrl = "https://example.com"
        )

        assertTrue(payload.contains("\"subtitle\":\"Server A\""))
        assertTrue(payload.contains("\"level\":\"critical\""))
        assertTrue(payload.contains("\"volume\":8.5"))
        assertTrue(payload.contains("\"badge\":3"))
        assertTrue(payload.contains("\"icon\":\"https://example.com/icon.png\""))
        assertTrue(payload.contains("\"image\":\"https://example.com/image.png\""))
        assertTrue(payload.contains("\"autoCopy\":\"1\""))
        assertTrue(payload.contains("\"copy\":\"copy text\""))
        assertTrue(payload.contains("\"url\":\"https://example.com\""))
    }

    @Test
    fun `bark payload omits blank optional fields`() {
        val payload = buildBarkPushPayload(
            title = "Alarm",
            body = "Triggered",
            subtitle = "",
            level = "",
            volume = "",
            badge = "",
            icon = "",
            image = "",
            autoCopy = false,
            copy = "",
            jumpUrl = ""
        )

        assertFalse(payload.contains("\"subtitle\""))
        assertFalse(payload.contains("\"level\""))
        assertFalse(payload.contains("\"volume\""))
        assertFalse(payload.contains("\"badge\""))
        assertFalse(payload.contains("\"icon\""))
        assertFalse(payload.contains("\"image\""))
        assertFalse(payload.contains("\"autoCopy\""))
        assertFalse(payload.contains("\"copy\""))
        assertFalse(payload.contains("\"url\""))
    }

    @Test
    fun `parse telegram response extracts ok message id and chat id`() {
        val parsed = parseTelegramPushResponse(
            """
            {
              "ok": true,
              "result": {
                "message_id": 42,
                "chat": {
                  "id": -1001234567890
                }
              }
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertTrue(parsed?.ok == true)
        assertEquals(42L, parsed?.messageId)
        assertEquals("-1001234567890", parsed?.chatId)
    }

    @Test
    fun `telegram parse mode input normalizes localized alias`() {
        val input = TelegramPushModule().getInputs().first { it.id == "parse_mode" }

        assertEquals("markdown", input.normalizeEnumValue("Markdown"))
        assertEquals("none", input.normalizeEnumValue("无"))
    }

    @Test
    fun `default webhook form fields keep message and optional title`() {
        val fieldsWithTitle = buildDefaultWebhookFormFields("Build", "Finished")
        val fieldsWithoutTitle = buildDefaultWebhookFormFields("", "Finished")

        assertEquals("Build", fieldsWithTitle["title"])
        assertEquals("Finished", fieldsWithTitle["message"])
        assertFalse(fieldsWithoutTitle.containsKey("title"))
        assertEquals("Finished", fieldsWithoutTitle["message"])
    }

    @Test
    fun `default webhook raw body joins title and message`() {
        assertEquals("Alarm\nTriggered", buildDefaultWebhookRawBody("Alarm", "Triggered"))
        assertEquals("Triggered", buildDefaultWebhookRawBody("", "Triggered"))
    }

    @Test
    fun `discord content prepends mentions`() {
        val content = buildDiscordContent("deploy done", "123|456")

        assertEquals("<@123> <@456> deploy done", content)
    }

    @Test
    fun `discord payload includes optional fields`() {
        val payload = buildDiscordPushPayload(
            content = "hello",
            username = "vFlow",
            avatarUrl = "https://example.com/a.png",
            tts = true
        )

        assertTrue(payload.contains("\"content\":\"hello\""))
        assertTrue(payload.contains("\"username\":\"vFlow\""))
        assertTrue(payload.contains("\"avatar_url\":\"https://example.com/a.png\""))
        assertTrue(payload.contains("\"tts\":true"))
    }

    @Test
    fun `webhook custom json mode validates without message`() {
        val module = WebhookPushModule()
        val result = module.validate(
            ActionStep(
                moduleId = module.id,
                parameters = mapOf(
                    "url" to "https://discord.com/api/webhooks/demo",
                    "body_type" to "custom_json",
                    "custom_json_body" to """{"content":"test"}""",
                    "message" to ""
                )
            ),
            emptyList()
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `webhook custom json mode rejects invalid json`() {
        val module = WebhookPushModule()
        val result = module.validate(
            ActionStep(
                moduleId = module.id,
                parameters = mapOf(
                    "url" to "https://discord.com/api/webhooks/demo",
                    "body_type" to "custom_json",
                    "custom_json_body" to """{"content":}"""
                )
            ),
            emptyList()
        )

        assertFalse(result.isValid)
        assertEquals("自定义 JSON 格式错误", result.errorMessage)
    }
}
