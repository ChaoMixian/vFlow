// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/KeyEventTriggerModule.kt
// 描述: 通过 onParameterUpdated 实现了智能预设，为后台服务提供清晰、具体的参数。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.*
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class KeyEventTriggerModule : BaseModule() {
    companion object {
        const val ACTION_SINGLE_CLICK = "single_click"
        const val ACTION_DOUBLE_CLICK = "double_click"
        const val ACTION_LONG_PRESS = "long_press"
        const val ACTION_SHORT_PRESS = "short_press"
        const val ACTION_SWIPE_DOWN = "swipe_down"
        const val ACTION_SWIPE_UP = "swipe_up"
    }

    override val id = "vflow.trigger.key_event"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_key_event_name,
        descriptionStringRes = R.string.module_vflow_trigger_key_event_desc,
        name = "按键触发",  // Fallback
        description = "当指定的物理按键被按下时，触发此工作流（需要Shizuku或Root）",  // Fallback
        iconRes = R.drawable.rounded_horizontal_align_bottom_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions: List<Permission>
        get() = ShellManager.getRequiredPermissions(LogManager.applicationContext)

    private val allActionOptions by lazy {
        listOf(
            ACTION_SINGLE_CLICK,
            ACTION_DOUBLE_CLICK,
            ACTION_LONG_PRESS,
            ACTION_SHORT_PRESS,
            ACTION_SWIPE_DOWN,
            ACTION_SWIPE_UP
        )
    }

    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue
        return newParameters
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("device", "输入设备", ParameterType.STRING, "/dev/input/event0", nameStringRes = R.string.param_vflow_trigger_key_event_device_name),
        InputDefinition("key_code", "按键码", ParameterType.STRING, "116", nameStringRes = R.string.param_vflow_trigger_key_event_key_code_name),
        InputDefinition(
            "action_type",
            "操作类型",
            ParameterType.ENUM,
            ACTION_SINGLE_CLICK,
            options = allActionOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_key_event_action_single_click,
                R.string.option_vflow_trigger_key_event_action_double_click,
                R.string.option_vflow_trigger_key_event_action_long_press,
                R.string.option_vflow_trigger_key_event_action_short_press,
                R.string.option_vflow_trigger_key_event_action_swipe_down,
                R.string.option_vflow_trigger_key_event_action_swipe_up
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_key_event_action_single_click) to ACTION_SINGLE_CLICK,
                appContext.getString(R.string.option_vflow_trigger_key_event_action_double_click) to ACTION_DOUBLE_CLICK,
                appContext.getString(R.string.option_vflow_trigger_key_event_action_long_press) to ACTION_LONG_PRESS,
                appContext.getString(R.string.option_vflow_trigger_key_event_action_short_press) to ACTION_SHORT_PRESS,
                appContext.getString(R.string.option_vflow_trigger_key_event_action_swipe_down) to ACTION_SWIPE_DOWN,
                appContext.getString(R.string.option_vflow_trigger_key_event_action_swipe_up) to ACTION_SWIPE_UP
            ),
            nameStringRes = R.string.param_vflow_trigger_key_event_action_type_name
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> = getInputs()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionType = step.parameters["action_type"] as? String

        if (actionType.isNullOrEmpty()) return context.getString(R.string.summary_vflow_trigger_key_event_configure)

        val actionTypePill = PillUtil.createPillFromParam(actionType, getInputs().find { it.id == "action_type" }, isModuleOption = true)
        val device = step.parameters["device"] as? String ?: "/dev/input/event0"
        val keyCode = step.parameters["key_code"] as? String ?: "N/A"
        val devicePill = PillUtil.Pill(device, "device")
        val keyCodePill = PillUtil.Pill(keyCode, "key_code")
        val customPrefix = context.getString(R.string.summary_vflow_trigger_key_event_custom_prefix)
        val customSuffix = context.getString(R.string.summary_vflow_trigger_key_event_custom_suffix)
        return PillUtil.buildSpannable(context, customPrefix, actionTypePill, " ", keyCodePill, " @ ", devicePill, customSuffix)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_key_event_triggered)))
        return ExecutionResult.Success()
    }
}
