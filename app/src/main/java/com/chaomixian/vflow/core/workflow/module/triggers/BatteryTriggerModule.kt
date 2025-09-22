
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
        name = "电量触发（损坏）",
        description = "当电池电量满足特定条件时触发工作流。",
        iconRes = R.drawable.rounded_battery_android_frame_full_24,
        category = "触发器"
    )

    override val uiProvider: ModuleUIProvider? = BatteryTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "level",
            name = "电量阈值",
            staticType = ParameterType.NUMBER,
            defaultValue = 50,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "above_or_below",
            name = "触发条件",
            staticType = ParameterType.STRING, // "above" or "below"
            defaultValue = "below",
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val level = (step.parameters["level"] as? Number)?.toInt() ?: 50
        val aboveOrBelow = step.parameters["above_or_below"] as? String ?: "below"
        val conditionText = if (aboveOrBelow == "below") "低于" else "高于"

        val levelPill = PillUtil.Pill("$level%", false, "level")
        val conditionPill = PillUtil.Pill(conditionText, false, "above_or_below")

        return PillUtil.buildSpannable(context, "当电量", " ", conditionPill, " ", levelPill, " 时触发")
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("电量任务已触发"))
        return ExecutionResult.Success()
    }
}
