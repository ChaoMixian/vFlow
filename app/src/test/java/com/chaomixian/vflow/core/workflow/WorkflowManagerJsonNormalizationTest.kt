package com.chaomixian.vflow.core.workflow

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowManagerJsonNormalizationTest {

    @Test
    fun `normalize json element preserves nested objects and arrays`() {
        val element = JsonParser.parseString(
            """
            {
              "headers": {
                "Authorization": "Bearer token"
              },
              "items": [
                {
                  "name": "first",
                  "enabled": true
                },
                2
              ]
            }
            """.trimIndent()
        )

        val normalized = normalizeJsonElementValue(element) as Map<*, *>
        val headers = normalized["headers"] as Map<*, *>
        val items = normalized["items"] as List<*>

        assertEquals("Bearer token", headers["Authorization"])
        assertTrue(items.first() is Map<*, *>)
        assertEquals(true, (items.first() as Map<*, *>)["enabled"])
        assertEquals(2, (items[1] as Number).toInt())
    }
}
