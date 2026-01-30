
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

    override val uiProvider: ModuleUIProvider? = BatteryTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "level",
            name = "电量阈值",
            nameStringRes = R.string.param_vflow_trigger_battery_level_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 50,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "above_or_below",
            name = "触发条件",
            nameStringRes = R.string.param_vflow_trigger_battery_above_or_below_name,
            staticType = ParameterType.STRING, // "above" or "below"
            defaultValue = "below",
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val level = (step.parameters["level"] as? Number)?.toInt() ?: 50
        val aboveOrBelow = step.parameters["above_or_below"] as? String ?: "below"
        val conditionText = if (aboveOrBelow == "below") {
            context.getString(R.string.option_vflow_trigger_battery_below)
        } else {
            context.getString(R.string.option_vflow_trigger_battery_above)
        }

        // 更新 Pill 的构造以匹配新的签名
        val levelPill = PillUtil.Pill("$level%", "level")
        val conditionPill = PillUtil.Pill(conditionText, "above_or_below", isModuleOption = true)

        val prefix = context.getString(R.string.summary_vflow_trigger_battery_prefix)
        val suffix = context.getString(R.string.summary_vflow_trigger_battery_suffix)

        return PillUtil.buildSpannable(context, "$prefix", " ", conditionPill, " ", levelPill, " $suffix")
    }


    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("电量任务已触发"))
        return ExecutionResult.Success()
    }
}
