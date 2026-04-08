package com.chaomixian.vflow.core.workflow.module.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppStartTriggerModuleTest {

    @Test
    fun `normalizes canonical and legacy opened values`() {
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            AppStartTriggerModule.normalizeEvent(AppStartTriggerModule.EVENT_OPENED)
        )
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            AppStartTriggerModule.normalizeEvent("打开时")
        )
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            AppStartTriggerModule.normalizeEvent("Opened")
        )
    }

    @Test
    fun `normalizes canonical and legacy closed values`() {
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            AppStartTriggerModule.normalizeEvent(AppStartTriggerModule.EVENT_CLOSED)
        )
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            AppStartTriggerModule.normalizeEvent("关闭时")
        )
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            AppStartTriggerModule.normalizeEvent("Closed")
        )
    }

    @Test
    fun `returns null for unknown values`() {
        assertNull(AppStartTriggerModule.normalizeEvent(null))
        assertNull(AppStartTriggerModule.normalizeEvent("unexpected"))
    }
}
