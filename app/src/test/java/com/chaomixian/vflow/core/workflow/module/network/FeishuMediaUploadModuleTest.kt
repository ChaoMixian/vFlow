package com.chaomixian.vflow.core.workflow.module.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FeishuMediaUploadModuleTest {

    @Test
    fun `parse feishu media upload response extracts code msg and file token`() {
        val parsed = parseFeishuMediaUploadResponse(
            """
            {
              "code": 0,
              "msg": "success",
              "data": {
                "file_token": "file-token-123"
              }
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(0, parsed?.code)
        assertEquals("success", parsed?.msg)
        assertEquals("file-token-123", parsed?.fileToken)
    }

    @Test
    fun `parse feishu media upload response tolerates missing data`() {
        val parsed = parseFeishuMediaUploadResponse(
            """
            {
              "code": 999,
              "msg": "bad request"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(999, parsed?.code)
        assertEquals("bad request", parsed?.msg)
        assertEquals("", parsed?.fileToken)
    }

    @Test
    fun `parse feishu media upload response returns null for invalid json`() {
        assertNull(parseFeishuMediaUploadResponse("not-json"))
    }
}
