// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/TimeTriggerModule.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.*

class TimeTriggerModule : BaseModule() {
    override val id = "vflow.trigger.time"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_time_name,
        descriptionStringRes = R.string.module_vflow_trigger_time_desc,
        name = "定时触发",  // Fallback
        description = "在指定的时间和日期触发工作流，类似闹钟",  // Fallback
        iconRes = R.drawable.rounded_avg_time_24,  // 复用一个时间图标
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.EXACT_ALARM)

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider? = TimeTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "time",
            name = "触发时间",
            nameStringRes = R.string.param_vflow_trigger_time_time_name,
            staticType = ParameterType.STRING,
            defaultValue = "09:00",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "days",
            name = "重复日期",
            nameStringRes = R.string.param_vflow_trigger_time_days_name,
            staticType = ParameterType.ANY, // 存储为 List<Int>
            // 默认值为包含所有星期的列表
            defaultValue = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val time = step.parameters["time"] as? String ?: "00:00"

        // 兼容从JSON加载的 Double 列表和默认的 Int 列表
        val daysAny = step.parameters["days"]
        val daysInt = when (daysAny) {
            is List<*> -> daysAny.mapNotNull { (it as? Number)?.toInt() }
            else -> emptyList()
        }

        val timePill = PillUtil.Pill(time, "time")

        val everyday = context.getString(R.string.summary_vflow_trigger_time_everyday)
        val once = context.getString(R.string.summary_vflow_trigger_time_once)
        val workdays = context.getString(R.string.summary_vflow_trigger_time_workdays)

        val dayName = { dayInt: Int ->
            when (dayInt) {
                Calendar.SUNDAY -> context.getString(R.string.day_sunday)
                Calendar.MONDAY -> context.getString(R.string.day_monday)
                Calendar.TUESDAY -> context.getString(R.string.day_tuesday)
                Calendar.WEDNESDAY -> context.getString(R.string.day_wednesday)
                Calendar.THURSDAY -> context.getString(R.string.day_thursday)
                Calendar.FRIDAY -> context.getString(R.string.day_friday)
                Calendar.SATURDAY -> context.getString(R.string.day_saturday)
                else -> ""
            }
        }

        val daysText = when {
            daysInt.size == 7 -> everyday
            daysInt.isEmpty() -> once
            daysInt.sorted() == listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> workdays
            else -> daysInt.sorted().joinToString(", ") { dayName(it) }
        }
        val daysPill = PillUtil.Pill(daysText, "days")

        return PillUtil.buildSpannable(context, daysPill, " ", timePill, context.getString(R.string.summary_vflow_trigger_time_suffix))
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("定时任务已触发"))
        return ExecutionResult.Success()
    }
}