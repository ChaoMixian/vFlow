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
    companion object {
        private const val FORMAT_TIMESTAMP_MILLIS = "timestamp_millis"
        private const val FORMAT_TIMESTAMP_SECONDS = "timestamp_seconds"
        private const val FORMAT_ISO_8601 = "iso_8601"
        private const val FORMAT_DATETIME = "datetime"
        private const val FORMAT_DATE = "date"
        private const val FORMAT_TIME = "time"
        private const val FORMAT_DATE_CN = "date_cn"
        private const val FORMAT_MONTH_DAY_TIME = "month_day_time"
    }

    override val id = "vflow.data.get_current_time"

    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取当前时间",
        nameStringRes = R.string.module_vflow_data_get_current_time_name,
        description = "获取当前时间，支持多种格式输出",
        descriptionStringRes = R.string.module_vflow_data_get_current_time_desc,
        iconRes = R.drawable.rounded_avg_time_24,
        category = "数据",
        categoryId = "data"
    )

    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Read the current time in a requested format and optional timezone.",
        inputHints = mapOf(
            "format" to "Choose a canonical format such as datetime, date, time, iso_8601, or timestamp.",
            "timezone" to "Optional IANA timezone like Asia/Shanghai. Leave empty to use the device timezone."
        ),
        requiredInputIds = setOf("format")
    )

    // 时间格式选项
    private val formatOptions = listOf(
        FORMAT_TIMESTAMP_MILLIS,
        FORMAT_TIMESTAMP_SECONDS,
        FORMAT_ISO_8601,
        FORMAT_DATETIME,
        FORMAT_DATE,
        FORMAT_TIME,
        FORMAT_DATE_CN,
        FORMAT_MONTH_DAY_TIME
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "format",
            name = "时间格式",
            staticType = ParameterType.ENUM,
            options = formatOptions,
            defaultValue = FORMAT_DATETIME,
            optionsStringRes = listOf(
                R.string.option_vflow_data_get_current_time_format_timestamp_millis,
                R.string.option_vflow_data_get_current_time_format_timestamp_seconds,
                R.string.option_vflow_data_get_current_time_format_iso_8601,
                R.string.option_vflow_data_get_current_time_format_datetime,
                R.string.option_vflow_data_get_current_time_format_date,
                R.string.option_vflow_data_get_current_time_format_time,
                R.string.option_vflow_data_get_current_time_format_date_cn,
                R.string.option_vflow_data_get_current_time_format_month_day_time
            ),
            legacyValueMap = mapOf(
                "时间戳 (毫秒)" to FORMAT_TIMESTAMP_MILLIS,
                "Timestamp (Milliseconds)" to FORMAT_TIMESTAMP_MILLIS,
                "时间戳 (秒)" to FORMAT_TIMESTAMP_SECONDS,
                "Timestamp (Seconds)" to FORMAT_TIMESTAMP_SECONDS,
                "ISO 8601" to FORMAT_ISO_8601,
                "yyyy-MM-dd HH:mm:ss" to FORMAT_DATETIME,
                "yyyy-MM-dd" to FORMAT_DATE,
                "HH:mm:ss" to FORMAT_TIME,
                "yyyy年MM月dd日" to FORMAT_DATE_CN,
                "MM月dd日 HH:mm" to FORMAT_MONTH_DAY_TIME
            ),
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
            id = "timestamp_seconds",
            name = "时间戳 (秒)",
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
        val formatInput = getInputs().first { it.id == "format" }
        val rawFormat = context.getVariableAsString("format", FORMAT_DATETIME)
        val format = formatInput.normalizeEnumValue(rawFormat) ?: rawFormat
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
                FORMAT_TIMESTAMP_MILLIS -> now.toString()
                FORMAT_TIMESTAMP_SECONDS -> (now / 1000).toString()
                FORMAT_ISO_8601 -> {
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
                    val pattern = when (format) {
                        FORMAT_DATE -> "yyyy-MM-dd"
                        FORMAT_TIME -> "HH:mm:ss"
                        FORMAT_DATE_CN -> "yyyy年MM月dd日"
                        FORMAT_MONTH_DAY_TIME -> "MM月dd日 HH:mm"
                        else -> "yyyy-MM-dd HH:mm:ss"
                    }
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                    if (timezoneId.isBlank()) {
                        sdf.timeZone = TimeZone.getDefault()
                    } else {
                        sdf.timeZone = TimeZone.getTimeZone(timezoneId)
                    }
                    sdf.format(calendar.time)
                }
            }

            val timestampSeconds = now / 1000

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

            val weekdayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

            return ExecutionResult.Success(mapOf(
                "time" to VString(timeString),
                "timestamp" to VNumber(now.toDouble()),
                "timestamp_seconds" to VNumber(timestampSeconds.toDouble()),
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
        val formatPill = PillUtil.createPillFromParam(step.parameters["format"], inputs.find { it.id == "format" }, isModuleOption = true)

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
            "format" to FORMAT_DATETIME,
            "timezone" to ""
        ))
    )
}
