package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowNormalizerTest {

    @Test
    fun `migrates leading trigger steps out of legacy step list`() {
        val triggerStep = ActionStep(
            id = "trigger-1",
            moduleId = "vflow.trigger.share",
            parameters = mapOf("mimeType" to "text/plain")
        )
        val actionStep = ActionStep(
            id = "action-1",
            moduleId = "vflow.action.log",
            parameters = emptyMap()
        )

        val normalized = WorkflowNormalizer.normalize(
            triggers = null,
            steps = listOf(triggerStep, actionStep)
        )

        assertEquals(listOf(triggerStep), normalized.triggers)
        assertEquals(listOf(actionStep), normalized.steps)
    }

    @Test
    fun `prefers explicit triggers and strips legacy trigger prefix from steps`() {
        val explicitTrigger = ActionStep(
            id = "trigger-explicit",
            moduleId = "vflow.trigger.manual",
            parameters = emptyMap()
        )
        val legacyTriggerInSteps = ActionStep(
            id = "trigger-legacy",
            moduleId = "vflow.trigger.time",
            parameters = mapOf("time" to "08:30")
        )
        val actionStep = ActionStep(
            id = "action-1",
            moduleId = "vflow.action.log",
            parameters = emptyMap()
        )

        val normalized = WorkflowNormalizer.normalize(
            triggers = listOf(explicitTrigger),
            steps = listOf(legacyTriggerInSteps, actionStep)
        )

        assertEquals(listOf(explicitTrigger), normalized.triggers)
        assertEquals(listOf(actionStep), normalized.steps)
    }

    @Test
    fun `falls back to manual trigger when none are present`() {
        val actionStep = ActionStep(
            id = "action-1",
            moduleId = "vflow.action.log",
            parameters = emptyMap()
        )

        val normalized = WorkflowNormalizer.normalize(
            triggers = emptyList(),
            steps = listOf(actionStep)
        )

        assertEquals(1, normalized.triggers.size)
        assertEquals(WorkflowNormalizer.MANUAL_TRIGGER_MODULE_ID, normalized.triggers.first().moduleId)
        assertTrue(normalized.steps.contains(actionStep))
    }

    @Test
    fun `migrates legacy triggerConfig into trigger list`() {
        val actionStep = ActionStep(
            id = "action-1",
            moduleId = "vflow.data.comment",
            parameters = mapOf("text" to "hello")
        )

        val normalized = WorkflowNormalizer.normalize(
            triggers = null,
            steps = listOf(actionStep),
            legacyTriggerConfigs = listOf(
                mapOf(
                    "type" to "vflow.trigger.share",
                    "mimeType" to "text/plain"
                )
            )
        )

        assertEquals(1, normalized.triggers.size)
        assertEquals("vflow.trigger.share", normalized.triggers.first().moduleId)
        assertEquals("text/plain", normalized.triggers.first().parameters["mimeType"])
        assertEquals(listOf(actionStep), normalized.steps)
    }

    @Test
    fun `migrates legacy exported workflow steps into separated triggers and actions`() {
        val legacyWorkflowJson = """
            {
              "id": "legacy-workflow",
              "name": "Legacy Share Workflow",
              "steps": [
                {
                  "id": "trigger-1",
                  "moduleId": "vflow.trigger.share",
                  "indentationLevel": 0,
                  "parameters": {
                    "mimeType": "text/plain"
                  }
                },
                {
                  "id": "action-1",
                  "moduleId": "vflow.data.comment",
                  "indentationLevel": 0,
                  "parameters": {
                    "text": "imported"
                  }
                }
              ],
              "triggerConfig": {
                "type": "vflow.trigger.share",
                "mimeType": "text/plain"
              },
              "isEnabled": true
            }
        """.trimIndent()

        val parsedWorkflow = WorkflowJsonImportParser().parse(legacyWorkflowJson).workflows.single()
        val normalized = WorkflowNormalizer.normalize(
            triggers = parsedWorkflow.triggers,
            steps = parsedWorkflow.steps
        )

        assertEquals("legacy-workflow", parsedWorkflow.id)
        assertTrue(parsedWorkflow.triggers.isEmpty())
        assertEquals(2, parsedWorkflow.steps.size)

        assertEquals(1, normalized.triggers.size)
        assertEquals("vflow.trigger.share", normalized.triggers.first().moduleId)
        assertEquals("text/plain", normalized.triggers.first().parameters["mimeType"])
        assertEquals(1, normalized.steps.size)
        assertEquals("vflow.data.comment", normalized.steps.first().moduleId)
        assertFalse(normalized.steps.any { it.moduleId.startsWith("vflow.trigger.") })
    }
}
