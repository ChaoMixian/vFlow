
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BatteryTriggerModule : BaseModule() {
    override val id = "vflow.trigger.battery"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_battery_name,
        descriptionStringRes = R.string.module_vflow_trigger_battery_desc,
        name = "电量触发",  // Fallback
        description = "当电池电量满足特定条件时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_battery_android_frame_full_24,
        category = "触发器"
    )

    override val uiProvider: ModuleUIProvider? = null

    // 序列化值使用与语言无关的标识符
    companion object {
        const val VALUE_BELOW = "below"
        const val VALUE_ABOVE = "above"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "above_or_below",
            name = "触发条件",
            nameStringRes = R.string.param_vflow_trigger_battery_above_or_below_name,
            staticType = ParameterType.ENUM,
            defaultValue = VALUE_BELOW,
            options = listOf(VALUE_BELOW, VALUE_ABOVE),
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_battery_below,
                R.string.option_vflow_trigger_battery_above
            ),
            inputStyle = InputStyle.CHIP_GROUP
        ),
        InputDefinition(
            id = "level",
            name = "电量阈值",
            nameStringRes = R.string.param_vflow_trigger_battery_level_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 50,
            inputStyle = InputStyle.SLIDER,
            sliderConfig = InputDefinition.slider(0f, 100f, 1f),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val level = (step.parameters["level"] as? Number)?.toInt() ?: 50
        val aboveOrBelow = step.parameters["above_or_below"] as? String ?: VALUE_BELOW

        // 序列化值转换为本地化显示文本
        val displayText = when (aboveOrBelow) {
            VALUE_ABOVE -> context.getString(R.string.option_vflow_trigger_battery_above)
            else -> context.getString(R.string.option_vflow_trigger_battery_below)
        }

        val levelPill = PillUtil.Pill("$level%", "level")
        val conditionPill = PillUtil.Pill(displayText, "above_or_below", isModuleOption = true)

        val prefix = context.getString(R.string.summary_vflow_trigger_battery_prefix)
        val suffix = context.getString(R.string.summary_vflow_trigger_battery_suffix)

        return PillUtil.buildSpannable(context, "$prefix", " ", conditionPill, " ", levelPill, " $suffix")
    }


    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("电量任务已触发"))
        return ExecutionResult.Success()
    }
}
