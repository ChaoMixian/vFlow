package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ReceiveShareTriggerModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowEnumMigrationTest {

    @After
    fun tearDown() {
        ModuleRegistry.reset()
    }

    @Test
    fun `scan migrates legacy enum values in workflow triggers`() {
        ModuleRegistry.register(ReceiveShareTriggerModule())

        val workflow = Workflow(
            id = "legacy-share",
            name = "Legacy Share",
            triggers = listOf(
                ActionStep(
                    moduleId = "vflow.trigger.share",
                    parameters = mapOf("acceptedType" to "Text")
                )
            ),
            steps = emptyList()
        )

        val preview = WorkflowEnumMigration.scan(workflow)

        assertNotNull(preview)
        assertEquals(1, preview!!.affectedStepCount)
        assertEquals(1, preview.affectedFieldCount)
        assertEquals("text", preview.migratedWorkflow.triggers.first().parameters["acceptedType"])
        assertEquals("Text", workflow.triggers.first().parameters["acceptedType"])
    }

    @Test
    fun `batch scan returns only affected workflows`() {
        ModuleRegistry.register(ReceiveShareTriggerModule())

        val legacyWorkflow = Workflow(
            id = "legacy-share",
            name = "Legacy Share",
            triggers = listOf(
                ActionStep(
                    moduleId = "vflow.trigger.share",
                    parameters = mapOf("acceptedType" to "Text")
                )
            ),
            steps = emptyList()
        )
        val cleanWorkflow = Workflow(
            id = "canonical-share",
            name = "Canonical Share",
            triggers = listOf(
                ActionStep(
                    moduleId = "vflow.trigger.share",
                    parameters = mapOf("acceptedType" to "text")
                )
            ),
            steps = emptyList()
        )

        val preview = WorkflowEnumMigration.scan(listOf(legacyWorkflow, cleanWorkflow))

        assertNotNull(preview)
        assertEquals(1, preview!!.affectedWorkflowCount)
        assertEquals(1, preview.affectedStepCount)
        assertEquals(1, preview.affectedFieldCount)
        assertEquals(listOf("legacy-share"), preview.migratedWorkflows.map { it.id })
        assertNull(WorkflowEnumMigration.scan(listOf(cleanWorkflow)))
    }
}
