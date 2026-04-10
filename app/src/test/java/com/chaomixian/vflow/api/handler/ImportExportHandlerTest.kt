package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.model.SimpleActionStepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExportHandlerTest {

    @Test
    fun `parse imported action step normalizes nested parameters`() {
        val step = parseImportedActionStep(
            mapOf(
                "moduleId" to "vflow.test.module",
                "parameters" to mapOf(
                    "headers" to mapOf(
                        "Authorization" to "Bearer token"
                    ),
                    "items" to listOf(
                        mapOf("name" to "first"),
                        2,
                        true
                    )
                ),
                "indentationLevel" to 2,
                "id" to "step-1"
            ),
            "step"
        )

        assertEquals("vflow.test.module", step.moduleId)
        assertEquals(2, step.indentationLevel)
        assertEquals("step-1", step.id)
        val headers = step.parameters["headers"] as Map<*, *>
        assertEquals("Bearer token", headers["Authorization"])
        val items = step.parameters["items"] as List<*>
        assertTrue(items.first() is Map<*, *>)
        assertEquals("first", (items.first() as Map<*, *>)["name"])
    }

    @Test
    fun `parse imported action step rejects non object parameters`() {
        val error = runCatching {
            parseImportedActionStep(
                mapOf(
                    "moduleId" to "vflow.test.module",
                    "parameters" to "bad"
                ),
                "step"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Invalid step format: parameters must be an object", error?.message)
    }

    @Test
    fun `simple action step dto normalizes nested parameters`() {
        val step = SimpleActionStepDto(
            id = "step-2",
            moduleId = "vflow.test.module",
            indentationLevel = 1,
            parameters = mapOf(
                "body" to mapOf(
                    "items" to listOf(
                        mapOf("value" to "nested")
                    )
                )
            )
        ).toActionStep()

        val body = step.parameters["body"] as Map<*, *>
        val items = body["items"] as List<*>
        assertTrue(items.first() is Map<*, *>)
        assertEquals("nested", (items.first() as Map<*, *>)["value"])
    }
}
