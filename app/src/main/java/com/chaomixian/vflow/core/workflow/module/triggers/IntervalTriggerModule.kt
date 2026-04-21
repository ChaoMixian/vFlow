package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class IntervalTriggerModule : BaseModule() {
    companion object {
        const val UNIT_SECOND = "second"
        const val UNIT_MINUTE = "minute"
        const val UNIT_HOUR = "hour"
        const val UNIT_DAY = "day"

        val UNIT_OPTIONS = listOf(UNIT_SECOND, UNIT_MINUTE, UNIT_HOUR, UNIT_DAY)
    }

    override val id = "vflow.trigger.interval"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_interval_name,
        descriptionStringRes = R.string.module_vflow_trigger_interval_desc,
        name = "间隔触发",
        description = "按指定时间间隔重复触发工作流",
        iconRes = R.drawable.rounded_avg_time_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions = listOf(PermissionManager.EXACT_ALARM)

    override val uiProvider: ModuleUIProvider? = IntervalTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "interval",
            name = "时间间隔",
            nameStringRes = R.string.param_vflow_trigger_interval_value_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 1L,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "unit",
            name = "间隔单位",
            nameStringRes = R.string.param_vflow_trigger_interval_unit_name,
            staticType = ParameterType.ENUM,
            defaultValue = UNIT_MINUTE,
            options = UNIT_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_interval_unit_second,
                R.string.option_vflow_trigger_interval_unit_minute,
                R.string.option_vflow_trigger_interval_unit_hour,
                R.string.option_vflow_trigger_interval_unit_day
            ),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val intervalValue = when (val rawValue = step.parameters["interval"]) {
            is Number -> rawValue.toLong().toString()
            else -> "1"
        }
        val unit = normalizeUnit(step.parameters["unit"] as? String)
        val unitLabel = context.getString(unitLabelRes(unit))

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_interval_prefix),
            " ",
            PillUtil.Pill(intervalValue, "interval"),
            " ",
            PillUtil.Pill(unitLabel, "unit"),
            context.getString(R.string.summary_vflow_trigger_interval_suffix)
        )
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val interval = (step.parameters["interval"] as? Number)?.toLong()
        if (interval == null || interval <= 0L) {
            return ValidationResult(
                false,
                appContext.getString(R.string.error_vflow_trigger_interval_invalid)
            )
        }

        val unit = normalizeUnit(step.parameters["unit"] as? String)
        if (unit !in UNIT_OPTIONS) {
            return ValidationResult(
                false,
                appContext.getString(R.string.error_vflow_trigger_interval_invalid_unit)
            )
        }

        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("间隔任务已触发"))
        return ExecutionResult.Success()
    }

    internal fun normalizeUnit(rawValue: String?): String {
        return rawValue?.takeIf { it in UNIT_OPTIONS } ?: UNIT_MINUTE
    }

    private fun unitLabelRes(unit: String): Int {
        return when (unit) {
            UNIT_SECOND -> R.string.option_vflow_trigger_interval_unit_second
            UNIT_HOUR -> R.string.option_vflow_trigger_interval_unit_hour
            UNIT_DAY -> R.string.option_vflow_trigger_interval_unit_day
            else -> R.string.option_vflow_trigger_interval_unit_minute
        }
    }
}
