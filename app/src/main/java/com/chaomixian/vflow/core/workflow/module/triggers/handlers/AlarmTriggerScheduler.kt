package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.IntervalTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.TimeTriggerModule
import com.chaomixian.vflow.services.TimeTriggerReceiver
import java.util.Calendar
import java.util.Date

object AlarmTriggerScheduler {
    private const val TAG = "AlarmTriggerScheduler"

    fun schedule(context: Context, trigger: TriggerSpec) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = createPendingIntent(context, trigger)
        val nextTriggerTime = calculateNextTriggerTime(trigger)

        if (nextTriggerTime == null) {
            DebugLogger.w(TAG, "无法为工作流 '${trigger.workflowName}' 计算下一次触发时间。")
            cancel(context, trigger)
            return
        }

        when (trigger.type) {
            TimeTriggerModule().id -> {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTriggerTime.timeInMillis, intent)
                alarmManager.setAlarmClock(alarmClockInfo, intent)
            }
            else -> {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextTriggerTime.timeInMillis,
                            intent
                        )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ->
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            nextTriggerTime.timeInMillis,
                            intent
                        )
                    else ->
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            nextTriggerTime.timeInMillis,
                            intent
                        )
                }
            }
        }

        DebugLogger.d(TAG, "已为 '${trigger.workflowName}' 调度下一次触发: ${Date(nextTriggerTime.timeInMillis)}")
    }

    fun cancel(context: Context, trigger: TriggerSpec) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(createPendingIntent(context, trigger))
        DebugLogger.d(TAG, "已取消触发器 '${trigger.triggerId}' 的定时任务。")
    }

    internal fun calculateNextTriggerTime(
        trigger: TriggerSpec,
        now: Calendar = Calendar.getInstance()
    ): Calendar? {
        return when (trigger.type) {
            TimeTriggerModule().id -> calculateTimeTriggerTime(trigger, now)
            IntervalTriggerModule().id -> calculateIntervalTriggerTime(trigger, now)
            else -> null
        }
    }

    internal fun calculateTimeTriggerTime(
        trigger: TriggerSpec,
        now: Calendar = Calendar.getInstance()
    ): Calendar? {
        val time = trigger.parameters["time"] as? String ?: return null
        val days = (trigger.parameters["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return null
        val hour = parts[0]
        val minute = parts[1]

        if (days.isEmpty()) {
            return Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        for (offset in 0..7) {
            val checkDay = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayOfWeek = checkDay.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek !in days) continue

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
        return null
    }

    internal fun calculateIntervalTriggerTime(
        trigger: TriggerSpec,
        now: Calendar = Calendar.getInstance()
    ): Calendar? {
        val interval = (trigger.parameters["interval"] as? Number)?.toLong() ?: return null
        if (interval <= 0L) return null

        val unit = (trigger.parameters["unit"] as? String)?.takeIf { it in IntervalTriggerModule.UNIT_OPTIONS }
            ?: IntervalTriggerModule.UNIT_MINUTE

        return Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.MILLISECOND, 0)
            when (unit) {
                IntervalTriggerModule.UNIT_SECOND -> add(Calendar.SECOND, interval.toInt())
                IntervalTriggerModule.UNIT_HOUR -> {
                    set(Calendar.SECOND, 0)
                    add(Calendar.HOUR_OF_DAY, interval.toInt())
                }
                IntervalTriggerModule.UNIT_DAY -> {
                    set(Calendar.SECOND, 0)
                    add(Calendar.DAY_OF_YEAR, interval.toInt())
                }
                else -> {
                    set(Calendar.SECOND, 0)
                    add(Calendar.MINUTE, interval.toInt())
                }
            }
        }
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
}
