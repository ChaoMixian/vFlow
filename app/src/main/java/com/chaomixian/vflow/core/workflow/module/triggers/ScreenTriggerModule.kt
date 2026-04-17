package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ScreenTriggerModule : BaseModule() {
    override val id = "vflow.trigger.screen"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_screen_name,
        descriptionStringRes = R.string.module_vflow_trigger_screen_desc,
        name = "屏幕触发",
        description = "当屏幕亮起、解锁或熄灭时触发工作流",
        iconRes = R.drawable.rounded_mobile_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val uiProvider: ModuleUIProvider? = null

    companion object {
        const val VALUE_SCREEN_ON = "screen_on"
        const val VALUE_UNLOCKED = "unlocked"
        const val VALUE_SCREEN_OFF = "screen_off"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "screen_event",
            name = "触发条件",
            nameStringRes = R.string.param_vflow_trigger_screen_event_name,
            staticType = ParameterType.ENUM,
            defaultValue = VALUE_SCREEN_ON,
            options = listOf(VALUE_SCREEN_ON, VALUE_UNLOCKED, VALUE_SCREEN_OFF),
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_screen_on,
                R.string.option_vflow_trigger_screen_unlocked,
                R.string.option_vflow_trigger_screen_off
            ),
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val event = getInputs().normalizeEnumValue("screen_event", step.parameters["screen_event"] as? String)
            ?: VALUE_SCREEN_ON
        val displayText = when (event) {
            VALUE_UNLOCKED -> context.getString(R.string.option_vflow_trigger_screen_unlocked)
            VALUE_SCREEN_OFF -> context.getString(R.string.option_vflow_trigger_screen_off)
            else -> context.getString(R.string.option_vflow_trigger_screen_on)
        }
        val eventPill = PillUtil.Pill(displayText, "screen_event", isModuleOption = true)
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_screen_prefix),
            " ",
            eventPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_screen_triggered)))
        return ExecutionResult.Success()
    }
}
