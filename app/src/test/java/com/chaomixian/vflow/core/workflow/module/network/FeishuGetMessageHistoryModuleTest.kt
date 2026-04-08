package com.chaomixian.vflow.core.workflow.module.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuGetMessageHistoryModuleTest {

    @Test
    fun `sort type defaults to descending`() {
        val module = FeishuGetMessageHistoryModule()

        val sortTypeInput = module.getInputs().first { it.id == "sort_type" }

        assertEquals("ByCreateTimeDesc", sortTypeInput.defaultValue)
    }

    @Test
    fun `validate resolved request rejects time range for thread`() {
        val module = FeishuGetMessageHistoryModule()

        val error = module.validateResolvedRequest(
            containerIdType = "thread",
            containerId = "omt_xxx",
            startTime = "1710000000",
            endTime = "",
            sortType = "ByCreateTimeAsc",
            pageSize = 20
        )

        assertEquals("thread 模式暂不支持按时间范围查询，请清空开始时间和结束时间", error)
    }

    @Test
    fun `parser extracts text content for text message`() {
        val items = JsonParser.parseString(
            """
            [
              {
                "message_id": "om_123",
                "msg_type": "text",
                "chat_id": "oc_123",
                "body": {
                  "content": "{\"text\":\"hello world\"}"
                },
                "mentions": [
                  {
                    "key": "@_user_1",
                    "id": "ou_123",
                    "name": "Tom"
                  }
                ]
              }
            ]
            """.trimIndent()
        ).asJsonArray

        val parsed = FeishuMessageHistoryParser.parseItems(items)
        val first = parsed.first()
        val body = first["body"] as Map<*, *>
        val content = body["parsed_content"] as Map<*, *>

        assertEquals("hello world", first["text"])
        assertEquals("hello world", body["text"])
        assertEquals("hello world", content["text"])
        assertEquals("hello world", FeishuMessageHistoryParser.extractMessageText(first))
        assertTrue((first["mentions"] as List<*>).isNotEmpty())
    }
}
