package com.chaomixian.vflow.core.workflow.module.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModuleTest {

    @Test
    fun `parse agent tool arguments preserves nested json structure`() {
        val parsed = parseAgentToolArguments(
            """
            {
              "x": 123,
              "enabled": true,
              "target": "搜索",
              "payload": {
                "items": [1, "two", false],
                "position": {
                  "left": 0.1,
                  "top": 0.2
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(123, (parsed["x"] as Number).toInt())
        assertEquals(true, parsed["enabled"])
        assertEquals("搜索", parsed["target"])

        val payload = parsed["payload"] as Map<*, *>
        val items = payload["items"] as List<*>
        val position = payload["position"] as Map<*, *>

        assertEquals(1, (items[0] as Number).toInt())
        assertEquals("two", items[1])
        assertEquals(false, items[2])
        assertEquals(0.1, (position["left"] as Number).toDouble(), 0.0)
        assertEquals(0.2, (position["top"] as Number).toDouble(), 0.0)
    }

    @Test
    fun `parse agent tool arguments returns empty map for invalid json`() {
        assertTrue(parseAgentToolArguments("not-json").isEmpty())
        assertTrue(parseAgentToolArguments("[1,2,3]").isEmpty())
    }

    @Test
    fun `parse agent tool arguments preserves null values`() {
        val parsed = parseAgentToolArguments(
            """
            {
              "result": null,
              "success": false
            }
            """.trimIndent()
        )

        assertTrue(parsed.containsKey("result"))
        assertEquals(null, parsed["result"])
        assertEquals(false, parsed["success"])
    }
}
