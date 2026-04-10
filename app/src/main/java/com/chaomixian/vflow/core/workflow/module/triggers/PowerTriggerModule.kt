package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class PowerTriggerModule : BaseModule() {
    override val id = "vflow.trigger.power"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_power_name,
        descriptionStringRes = R.string.module_vflow_trigger_power_desc,
        name = "电源触发",  // Fallback
        description = "当电源连接或断开时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_battery_android_frame_full_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val uiProvider: ModuleUIProvider? = null

    // 序列化值使用与语言无关的标识符
    companion object {
        const val VALUE_CONNECTED = "connected"
        const val VALUE_DISCONNECTED = "disconnected"
        private val STATE_LEGACY_MAP = mapOf(
            "已连接" to VALUE_CONNECTED,
            "已断开" to VALUE_DISCONNECTED
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "power_state",
            name = "触发条件",
            nameStringRes = R.string.param_vflow_trigger_power_state_name,
            staticType = ParameterType.ENUM,
            defaultValue = VALUE_CONNECTED,
            options = listOf(VALUE_CONNECTED, VALUE_DISCONNECTED),
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_power_connected,
                R.string.option_vflow_trigger_power_disconnected
            ),
            legacyValueMap = STATE_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val powerState = getInputs().normalizeEnumValue("power_state", step.parameters["power_state"] as? String) ?: VALUE_CONNECTED

        // 序列化值转换为本地化显示文本
        val displayText = when (powerState) {
            VALUE_DISCONNECTED -> context.getString(R.string.option_vflow_trigger_power_disconnected)
            else -> context.getString(R.string.option_vflow_trigger_power_connected)
        }

        val conditionPill = PillUtil.Pill(displayText, "power_state", isModuleOption = true)
        val prefix = context.getString(R.string.summary_vflow_trigger_power_prefix)

        return PillUtil.buildSpannable(context, prefix, " ", conditionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("电源触发已执行"))
        return ExecutionResult.Success()
    }
}
