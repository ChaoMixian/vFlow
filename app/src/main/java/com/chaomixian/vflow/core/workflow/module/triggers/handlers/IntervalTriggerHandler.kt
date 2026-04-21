package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TriggerSpec

class IntervalTriggerHandler : BaseTriggerHandler() {
    private val scheduledTriggers = mutableListOf<TriggerSpec>()

    companion object {
        private const val TAG = "IntervalTriggerHandler"

        fun rescheduleAlarm(context: Context, trigger: TriggerSpec) {
            AlarmTriggerScheduler.schedule(context, trigger)
        }
    }

    override fun start(context: Context) {
        super.start(context)
        DebugLogger.d(TAG, "IntervalTriggerHandler 已启动。")
    }

    override fun addTrigger(context: Context, trigger: TriggerSpec) {
        scheduledTriggers.removeAll { it.triggerId == trigger.triggerId }
        scheduledTriggers.add(trigger)
        AlarmTriggerScheduler.schedule(context, trigger)
    }

    override fun removeTrigger(context: Context, triggerId: String) {
        val trigger = scheduledTriggers.firstOrNull { it.triggerId == triggerId } ?: return
        AlarmTriggerScheduler.cancel(context, trigger)
        scheduledTriggers.removeAll { it.triggerId == triggerId }
    }
}
