package com.chaomixian.vflow.core.workflow.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowReentryBehaviorTest {

    @Test
    fun `defaults to block new for null or unknown values`() {
        assertEquals(WorkflowReentryBehavior.BLOCK_NEW, WorkflowReentryBehavior.fromStoredValue(null))
        assertEquals(WorkflowReentryBehavior.BLOCK_NEW, WorkflowReentryBehavior.fromStoredValue(""))
        assertEquals(WorkflowReentryBehavior.BLOCK_NEW, WorkflowReentryBehavior.fromStoredValue("unknown"))
    }

    @Test
    fun `parses known stored values`() {
        assertEquals(
            WorkflowReentryBehavior.STOP_CURRENT_AND_RUN_NEW,
            WorkflowReentryBehavior.fromStoredValue("stop_current_and_run_new")
        )
        assertEquals(
            WorkflowReentryBehavior.ALLOW_PARALLEL,
            WorkflowReentryBehavior.fromStoredValue("allow_parallel")
        )
    }
}
