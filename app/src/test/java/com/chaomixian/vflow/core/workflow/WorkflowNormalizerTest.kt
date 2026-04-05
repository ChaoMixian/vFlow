package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep
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
}
