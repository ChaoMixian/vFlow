package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkflowJumpReferenceUpdaterTest {

    @Test
    fun remapAfterReorder_updatesJumpTargetToMovedStepNewIndex() {
        val target = step("target")
        val filler = step("filler")
        val jump = step(
            "jump",
            moduleId = "vflow.logic.jump",
            parameters = mapOf("target_step_index" to 1)
        )
        val original = listOf(target, filler, jump)
        val reordered = listOf(filler, jump, target)

        val result = WorkflowJumpReferenceUpdater.remapAfterReorder(original, reordered)

        assertEquals(3, result[1].parameters["target_step_index"])
    }

    @Test
    fun remapAfterReorder_keepsJumpPointingToSameStepWhenJumpItselfMoves() {
        val first = step("first")
        val target = step("target")
        val jump = step(
            "jump",
            moduleId = "vflow.logic.jump",
            parameters = mapOf("target_step_index" to 2L)
        )
        val original = listOf(first, target, jump)
        val reordered = listOf(jump, first, target)

        val result = WorkflowJumpReferenceUpdater.remapAfterReorder(original, reordered)

        assertEquals(3L, result[0].parameters["target_step_index"])
    }

    @Test
    fun remapAfterReorder_ignoresNonNumericJumpTarget() {
        val first = step("first")
        val jump = step(
            "jump",
            moduleId = "vflow.logic.jump",
            parameters = mapOf("target_step_index" to "{{some.value}}")
        )
        val original = listOf(first, jump)
        val reordered = listOf(jump, first)

        val result = WorkflowJumpReferenceUpdater.remapAfterReorder(original, reordered)

        assertSame(jump, result[0])
    }

    private fun step(
        id: String,
        moduleId: String = "demo.module",
        parameters: Map<String, Any?> = emptyMap()
    ): ActionStep {
        return ActionStep(
            moduleId = moduleId,
            parameters = parameters,
            id = id
        )
    }
}
