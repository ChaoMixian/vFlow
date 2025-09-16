// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/TimeTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.TimeTriggerReceiver
import java.util.*

class TimeTriggerHandler : BaseTriggerHandler() {

    companion object {
        private const val TAG = "TimeTriggerHandler"

        // 公共静态方法，允许 Receiver 回调以重新调度
        fun rescheduleAlarm(context: Context, workflow: Workflow) {
            scheduleAlarm(context, workflow)
        }

        private fun scheduleAlarm(context: Context, workflow: Workflow) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = createPendingIntent(context, workflow.id)

            val triggerConfig = workflow.triggerConfig ?: return
            val time = triggerConfig["time"] as? String ?: return
            @Suppress("UNCHECKED_CAST")
            val days = (triggerConfig["days"] as? List<Double>)?.map { it.toInt() } ?: emptyList()

            val nextTriggerTime = calculateNextTriggerTime(time, days)
            if (nextTriggerTime == null) {
                Log.w(TAG, "无法为工作流 '${workflow.name}' 计算下一次触发时间 (可能已过期)。")
                cancelAlarm(context, workflow.id) // 如果是单次任务且已过期，则取消
                return
            }

            val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTriggerTime.timeInMillis, intent)
            alarmManager.setAlarmClock(alarmClockInfo, intent)

            Log.d(TAG, "已为 '${workflow.name}' 调度下一次闹钟: ${Date(nextTriggerTime.timeInMillis)}")
        }

        private fun cancelAlarm(context: Context, workflowId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = createPendingIntent(context, workflowId)
            alarmManager.cancel(intent)
            Log.d(TAG, "已取消工作流 '$workflowId' 的定时触发器。")
        }

        private fun createPendingIntent(context: Context, workflowId: String): PendingIntent {
            val intent = Intent(context, TimeTriggerReceiver::class.java).apply {
                action = TimeTriggerReceiver.ACTION_TRIGGER
                putExtra(TimeTriggerReceiver.EXTRA_WORKFLOW_ID, workflowId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, workflowId.hashCode(), intent, flags)
        }

        private fun calculateNextTriggerTime(time: String, days: List<Int>): Calendar? {
            val now = Calendar.getInstance()
            val (hour, minute) = time.split(":").map { it.toInt() }

            // 如果是单次任务
            if (days.isEmpty()) {
                val triggerTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // 如果今天的时间已过，则设为明天
                if (triggerTime.before(now)) {
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1)
                }
                // (注意：这里的逻辑是单次任务至少会触发一次，即使设置的是过去的时间)
                // 在实际应用中，可能需要根据产品需求调整，例如如果时间已过则不触发
                return triggerTime
            }

            // 如果是重复任务
            var triggerTime: Calendar? = null
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
                        triggerTime = candidateTime
                        break
                    }
                }
            }
            return triggerTime
        }
    }

    override fun start(context: Context) {
        super.start(context)
        Log.d(TAG, "TimeTriggerHandler 已启动。")
    }

    override fun addWorkflow(context: Context, workflow: Workflow) {
        scheduleAlarm(context, workflow)
    }

    override fun removeWorkflow(context: Context, workflowId: String) {
        cancelAlarm(context, workflowId)
    }
}