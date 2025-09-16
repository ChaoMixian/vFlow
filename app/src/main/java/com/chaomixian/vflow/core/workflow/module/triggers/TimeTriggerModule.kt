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
        name = "定时触发",
        description = "在指定的时间和日期触发工作流，类似闹钟。",
        iconRes = R.drawable.rounded_avg_time_24, // 复用一个时间图标
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.EXACT_ALARM)

    // 为该模块提供自定义的UI交互逻辑
    override val uiProvider: ModuleUIProvider? = TimeTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "time",
            name = "触发时间",
            staticType = ParameterType.STRING,
            defaultValue = "09:00",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "days",
            name = "重复日期",
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

        val timePill = PillUtil.Pill(time, false, "time")

        val daysText = when {
            daysInt.size == 7 -> "每天"
            daysInt.isEmpty() -> "单次"
            daysInt.sorted() == listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> "工作日"
            else -> daysInt.sorted().joinToString(", ") { dayInt ->
                when (dayInt) {
                    Calendar.SUNDAY -> "周日"
                    Calendar.MONDAY -> "周一"
                    Calendar.TUESDAY -> "周二"
                    Calendar.WEDNESDAY -> "周三"
                    Calendar.THURSDAY -> "周四"
                    Calendar.FRIDAY -> "周五"
                    Calendar.SATURDAY -> "周六"
                    else -> ""
                }
            }
        }
        val daysPill = PillUtil.Pill(daysText, false, "days")

        return PillUtil.buildSpannable(context, daysPill, " ", timePill, " 触发")
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("定时任务已触发"))
        return ExecutionResult.Success()
    }
}