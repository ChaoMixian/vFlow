package com.chaomixian.vflow.core.workflow.module.triggers

import com.chaomixian.vflow.core.module.normalizeEnumValueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppStartTriggerModuleTest {

    private val eventInput = AppStartTriggerModule().getInputs().first { it.id == "event" }

    @Test
    fun `normalizes canonical and legacy opened values`() {
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            eventInput.normalizeEnumValueOrNull(AppStartTriggerModule.EVENT_OPENED)
        )
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            eventInput.normalizeEnumValueOrNull("打开时")
        )
        assertEquals(
            AppStartTriggerModule.EVENT_OPENED,
            eventInput.normalizeEnumValueOrNull("Opened")
        )
    }

    @Test
    fun `normalizes canonical and legacy closed values`() {
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            eventInput.normalizeEnumValueOrNull(AppStartTriggerModule.EVENT_CLOSED)
        )
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            eventInput.normalizeEnumValueOrNull("关闭时")
        )
        assertEquals(
            AppStartTriggerModule.EVENT_CLOSED,
            eventInput.normalizeEnumValueOrNull("Closed")
        )
    }

    @Test
    fun `returns null for unknown values`() {
        assertNull(eventInput.normalizeEnumValueOrNull(null))
        assertNull(eventInput.normalizeEnumValueOrNull("unexpected"))
    }
}
