package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmTriggerSchedulerTest {

    @Test
    fun `time trigger schedules next matching weekday`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 21, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val trigger = TriggerSpec(
            workflow = Workflow(id = "workflow-1", name = "Morning Alarm"),
            step = ActionStep(
                id = "trigger-1",
                moduleId = "vflow.trigger.time",
                parameters = mapOf(
                    "time" to "09:00",
                    "days" to listOf(Calendar.WEDNESDAY)
                )
            )
        )

        val next = AlarmTriggerScheduler.calculateNextTriggerTime(trigger, now)

        assertNotNull(next)
        assertEquals(Calendar.WEDNESDAY, next!!.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, next.get(Calendar.MINUTE))
        assertEquals(2026, next.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, next.get(Calendar.MONTH))
        assertEquals(22, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `interval trigger schedules next hour based on interval`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 21, 10, 15, 48)
            set(Calendar.MILLISECOND, 512)
        }
        val trigger = TriggerSpec(
            workflow = Workflow(id = "workflow-2", name = "Hourly Poll"),
            step = ActionStep(
                id = "trigger-2",
                moduleId = "vflow.trigger.interval",
                parameters = mapOf(
                    "interval" to 2L,
                    "unit" to "hour"
                )
            )
        )

        val next = AlarmTriggerScheduler.calculateNextTriggerTime(trigger, now)

        assertNotNull(next)
        assertEquals(12, next!!.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, next.get(Calendar.MINUTE))
        assertEquals(0, next.get(Calendar.SECOND))
        assertEquals(0, next.get(Calendar.MILLISECOND))
    }

    @Test
    fun `interval trigger supports seconds`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 21, 10, 15, 48)
            set(Calendar.MILLISECOND, 512)
        }
        val trigger = TriggerSpec(
            workflow = Workflow(id = "workflow-4", name = "Second Poll"),
            step = ActionStep(
                id = "trigger-4",
                moduleId = "vflow.trigger.interval",
                parameters = mapOf(
                    "interval" to 5L,
                    "unit" to "second"
                )
            )
        )

        val next = AlarmTriggerScheduler.calculateNextTriggerTime(trigger, now)

        assertNotNull(next)
        assertEquals(10, next!!.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, next.get(Calendar.MINUTE))
        assertEquals(53, next.get(Calendar.SECOND))
        assertEquals(0, next.get(Calendar.MILLISECOND))
    }

    @Test
    fun `interval trigger rejects non positive interval`() {
        val now = Calendar.getInstance()
        val trigger = TriggerSpec(
            workflow = Workflow(id = "workflow-3", name = "Broken"),
            step = ActionStep(
                id = "trigger-3",
                moduleId = "vflow.trigger.interval",
                parameters = mapOf(
                    "interval" to 0L,
                    "unit" to "minute"
                )
            )
        )

        val next = AlarmTriggerScheduler.calculateNextTriggerTime(trigger, now)

        assertNull(next)
    }
}
