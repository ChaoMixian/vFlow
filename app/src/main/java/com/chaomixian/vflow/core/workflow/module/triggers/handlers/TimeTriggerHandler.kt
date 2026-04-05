package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.services.TimeTriggerReceiver
import java.util.Calendar
import java.util.Date

class TimeTriggerHandler : BaseTriggerHandler() {
    private val scheduledTriggers = mutableListOf<TriggerSpec>()

    companion object {
        private const val TAG = "TimeTriggerHandler"

        fun rescheduleAlarm(context: Context, trigger: TriggerSpec) {
            scheduleAlarm(context, trigger)
        }

        private fun scheduleAlarm(context: Context, trigger: TriggerSpec) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = createPendingIntent(context, trigger)

            val time = trigger.parameters["time"] as? String ?: return
            val days = (trigger.parameters["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

            val nextTriggerTime = calculateNextTriggerTime(time, days)
            if (nextTriggerTime == null) {
                DebugLogger.w(TAG, "无法为工作流 '${trigger.workflowName}' 计算下一次触发时间。")
                cancelAlarm(context, trigger)
                return
            }

            val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTriggerTime.timeInMillis, intent)
            alarmManager.setAlarmClock(alarmClockInfo, intent)
            DebugLogger.d(TAG, "已为 '${trigger.workflowName}' 调度下一次闹钟: ${Date(nextTriggerTime.timeInMillis)}")
        }

        private fun cancelAlarm(context: Context, trigger: TriggerSpec) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(createPendingIntent(context, trigger))
            DebugLogger.d(TAG, "已取消触发器 '${trigger.triggerId}' 的定时任务。")
        }

        private fun createPendingIntent(context: Context, trigger: TriggerSpec): PendingIntent {
            val intent = Intent(context, TimeTriggerReceiver::class.java).apply {
                action = TimeTriggerReceiver.ACTION_TRIGGER
                putExtra(TimeTriggerReceiver.EXTRA_WORKFLOW_ID, trigger.workflowId)
                putExtra(TimeTriggerReceiver.EXTRA_TRIGGER_STEP_ID, trigger.stepId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, trigger.triggerId.hashCode(), intent, flags)
        }

        private fun calculateNextTriggerTime(time: String, days: List<Int>): Calendar? {
            val now = Calendar.getInstance()
            val (hour, minute) = time.split(":").map { it.toInt() }

            if (days.isEmpty()) {
                val triggerTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (triggerTime.before(now)) {
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1)
                }
                return triggerTime
            }

            for (i in 0..7) {
                val checkDay = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, i)
                }
                val dayOfWeek = checkDay.get(Calendar.DAY_OF_WEEK)
                if (days.contains(dayOfWeek)) {
                    val candidateTime = Calendar.getInstance().apply {
                        timeInMillis = checkDay.timeInMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (candidateTime.after(now)) {
                        return candidateTime
                    }
                }
            }
            return null
        }
    }

    override fun start(context: Context) {
        super.start(context)
        DebugLogger.d(TAG, "TimeTriggerHandler 已启动。")
    }

    override fun addTrigger(context: Context, trigger: TriggerSpec) {
        scheduledTriggers.removeAll { it.triggerId == trigger.triggerId }
        scheduledTriggers.add(trigger)
        scheduleAlarm(context, trigger)
    }

    override fun removeTrigger(context: Context, triggerId: String) {
        val trigger = scheduledTriggers.firstOrNull { it.triggerId == triggerId } ?: return
        cancelAlarm(context, trigger)
        scheduledTriggers.removeAll { it.triggerId == triggerId }
    }
}
