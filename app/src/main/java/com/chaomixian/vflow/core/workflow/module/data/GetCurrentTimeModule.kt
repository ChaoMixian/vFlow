package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * 获取当前时间模块
 * 支持输出多种常用时间格式
 */
class GetCurrentTimeModule : BaseModule() {

    override val id = "vflow.data.get_current_time"

    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取当前时间",
        nameStringRes = R.string.module_vflow_data_get_current_time_name,
        description = "获取当前时间，支持多种格式输出",
        descriptionStringRes = R.string.module_vflow_data_get_current_time_desc,
        iconRes = R.drawable.rounded_avg_time_24,
        category = "数据"
    )

    // 时间格式选项
    private val formatOptions = listOf(
        "时间戳 (毫秒)",
        "时间戳 (秒)",
        "ISO 8601",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "HH:mm:ss",
        "yyyy年MM月dd日",
        "MM月dd日 HH:mm"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "format",
            name = "时间格式",
            staticType = ParameterType.ENUM,
            options = formatOptions,
            defaultValue = "yyyy-MM-dd HH:mm:ss",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "timezone",
            name = "时区 (可选)",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "例如: Asia/Shanghai, 留空使用系统默认时区"
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "time",
            name = "时间",
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "timestamp",
            name = "时间戳 (毫秒)",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "year",
            name = "年份",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "month",
            name = "月份",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "day",
            name = "日期",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "hour",
            name = "小时",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "minute",
            name = "分钟",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "second",
            name = "秒",
            typeName = VTypeRegistry.NUMBER.id
        ),
        OutputDefinition(
            id = "weekday",
            name = "星期",
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "weekday_number",
            name = "星期数字",
            typeName = VTypeRegistry.NUMBER.id
        )
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val format = context.getVariableAsString("format", "yyyy-MM-dd HH:mm:ss")
        val timezoneId = context.getVariableAsString("timezone", "")

        try {
            // 获取当前时间
            val now = if (timezoneId.isBlank()) {
                System.currentTimeMillis()
            } else {
                // 使用指定时区
                val timezone = TimeZone.getTimeZone(timezoneId)
                Calendar.getInstance(timezone).timeInMillis
            }

            val calendar = if (timezoneId.isBlank()) {
                Calendar.getInstance()
            } else {
                Calendar.getInstance(TimeZone.getTimeZone(timezoneId))
            }
            calendar.timeInMillis = now

            // 根据格式获取时间字符串
            val timeString = when (format) {
                "时间戳 (毫秒)" -> now.toString()
                "时间戳 (秒)" -> (now / 1000).toString()
                "ISO 8601" -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    if (timezoneId.isBlank()) {
                        sdf.timeZone = TimeZone.getDefault()
                    } else {
                        sdf.timeZone = TimeZone.getTimeZone(timezoneId)
                    }
                    sdf.format(calendar.time)
                }
                else -> {
                    // 自定义格式
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    if (timezoneId.isBlank()) {
                        sdf.timeZone = TimeZone.getDefault()
                    } else {
                        sdf.timeZone = TimeZone.getTimeZone(timezoneId)
                    }
                    sdf.format(calendar.time)
                }
            }

            // 获取时间分量
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH 从0开始
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            val weekdayNumber = calendar.get(Calendar.DAY_OF_WEEK) // 1=周日, 2=周一, ...

            // 转换为 1=周一, 7=周日 的格式
            val weekdayNumberAdjusted = when (weekdayNumber) {
                Calendar.SUNDAY -> 7
                else -> weekdayNumber - 1
            }

            val weekdayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
            val weekdayName = weekdayNames[weekdayNumber - 1]

            return ExecutionResult.Success(mapOf(
                "time" to VString(timeString),
                "timestamp" to VNumber(now.toDouble()),
                "year" to VNumber(year.toDouble()),
                "month" to VNumber(month.toDouble()),
                "day" to VNumber(day.toDouble()),
                "hour" to VNumber(hour.toDouble()),
                "minute" to VNumber(minute.toDouble()),
                "second" to VNumber(second.toDouble()),
                "weekday" to VString(weekdayName),
                "weekday_number" to VNumber(weekdayNumberAdjusted.toDouble())
            ))

        } catch (e: Exception) {
            return ExecutionResult.Failure("获取时间失败", e.message ?: "未知错误")
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val timezone = step.parameters["timezone"] as? String ?: ""
        if (timezone.isNotBlank()) {
            try {
                TimeZone.getTimeZone(timezone)
            } catch (e: Exception) {
                return ValidationResult(false, "无效的时区: $timezone")
            }
        }
        return ValidationResult(true)
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val formatPill = PillUtil.createPillFromParam(
            step.parameters["format"],
            inputs.find { it.id == "format" },
            isModuleOption = true
        )

        val timezone = step.parameters["timezone"] as? String
        return if (timezone.isNullOrBlank()) {
            PillUtil.buildSpannable(context, "获取时间: ", formatPill)
        } else {
            val timezonePill = PillUtil.Pill(timezone, "timezone")
            PillUtil.buildSpannable(context, "获取时间: ", formatPill, " (", timezonePill, ")")
        }
    }

    override fun createSteps(): List<ActionStep> = listOf(
        ActionStep(moduleId = this.id, parameters = mapOf(
            "format" to "yyyy-MM-dd HH:mm:ss",
            "timezone" to ""
        ))
    )
}
