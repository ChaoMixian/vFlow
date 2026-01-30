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

    override val id = "vflow.trigger.key_event"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_key_event_name,
        descriptionStringRes = R.string.module_vflow_trigger_key_event_desc,
        name = "按键触发",  // Fallback
        description = "当指定的物理按键被按下时，触发此工作流（需要Shizuku或Root）",  // Fallback
        iconRes = R.drawable.rounded_horizontal_align_bottom_24,
        category = "触发器"
    )

    override val requiredPermissions: List<Permission>
        get() = ShellManager.getRequiredPermissions(LogManager.applicationContext)

    private val presetOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_key_event_preset_manual),
            appContext.getString(R.string.option_vflow_trigger_key_event_preset_oneplus_13t),
            appContext.getString(R.string.option_vflow_trigger_key_event_preset_oneplus_13)
        )
    }
    private val onePlus13TActions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_key_event_action_single_click),
            appContext.getString(R.string.option_vflow_trigger_key_event_action_double_click),
            appContext.getString(R.string.option_vflow_trigger_key_event_action_long_press),
            appContext.getString(R.string.option_vflow_trigger_key_event_action_short_press)
        )
    }
    private val onePlus13Actions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_key_event_action_swipe_down),
            appContext.getString(R.string.option_vflow_trigger_key_event_action_swipe_up)
        )
    }
    private val allActionOptions by lazy { (onePlus13TActions + onePlus13Actions).distinct() }

    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue

        if (updatedParameterId == "device_preset") {
            // 清理旧参数，确保数据干净
            newParameters.remove("device")
            newParameters.remove("key_code")
            newParameters.remove("_internal_device_name")
            newParameters.remove("_internal_key_code")

            when (updatedValue as? String) {
                presetOptions[1] -> { // 一加 13T (侧键)
                    newParameters["action_type"] = onePlus13TActions[0] // 单击
                    // [关键] 为后台服务准备好内部参数
                    newParameters["_internal_device_name"] = "key-handler"
                    newParameters["_internal_key_code"] = "BTN_TRIGGER_HAPPY32"
                }
                presetOptions[2] -> { // 一加 13 (三段式)
                    newParameters["action_type"] = onePlus13Actions[0] // 向下滑动
                    newParameters["_internal_device_name"] = "oplus,hall_tri_state_key"
                    newParameters["_internal_key_code"] = "KEY_F3" // 虚拟按键码
                }
                else -> { // "手动/自定义"
                    newParameters["action_type"] = onePlus13TActions[0] // 单击
                }
            }
        }
        return newParameters
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("device_preset", "设备预设", ParameterType.ENUM, presetOptions[0], options = presetOptions, nameStringRes = R.string.param_vflow_trigger_key_event_device_preset_name),
        InputDefinition("device", "输入设备", ParameterType.STRING, "/dev/input/event0", nameStringRes = R.string.param_vflow_trigger_key_event_device_name),
        InputDefinition("key_code", "按键码", ParameterType.STRING, "KEY_POWER", nameStringRes = R.string.param_vflow_trigger_key_event_key_code_name),
        InputDefinition("action_type", "操作类型", ParameterType.ENUM, onePlus13TActions[0], options = allActionOptions, nameStringRes = R.string.param_vflow_trigger_key_event_action_type_name)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val allInputs = getInputs()
        val currentPreset = step?.parameters?.get("device_preset") as? String ?: presetOptions[0]
        val dynamicInputs = mutableListOf(allInputs.first { it.id == "device_preset" })

        when (currentPreset) {
            presetOptions[1] -> { // 一加 13T (侧键)
                dynamicInputs.add(allInputs.first { it.id == "action_type" }.copy(options = onePlus13TActions))
            }
            presetOptions[2] -> { // 一加 13 (三段式)
                dynamicInputs.add(allInputs.first { it.id == "action_type" }.copy(options = onePlus13Actions))
            }
            else -> { // 手动/自定义
                dynamicInputs.add(allInputs.first { it.id == "device" })
                dynamicInputs.add(allInputs.first { it.id == "key_code" })
                dynamicInputs.add(allInputs.first { it.id == "action_type" }.copy(options = onePlus13TActions))
            }
        }
        return dynamicInputs
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val preset = step.parameters["device_preset"] as? String ?: presetOptions[0]
        val actionType = step.parameters["action_type"] as? String

        if (actionType.isNullOrEmpty()) return "配置按键触发器"

        val presetPill = PillUtil.Pill(
            preset.replace(" (侧键)", "").replace(" (Side Key)", "").replace(" (三段式)", "").replace(" (Three-stage)", ""),
            "device_preset",
            isModuleOption = true
        )
        val actionTypePill = PillUtil.Pill(
            actionType.replace(" (立即触发)", "").replace(" (Immediate)", ""),
            "action_type",
            isModuleOption = true
        )

        val customPrefix = context.getString(R.string.summary_vflow_trigger_key_event_custom_prefix)
        val customSuffix = context.getString(R.string.summary_vflow_trigger_key_event_custom_suffix)
        val presetSuffix = context.getString(R.string.summary_vflow_trigger_key_event_preset_suffix)

        return when (preset) {
            presetOptions[0] -> { // 手动/自定义
                val keyCode = step.parameters["key_code"] as? String ?: "N/A"
                val keyCodePill = PillUtil.Pill(keyCode, "key_code")
                PillUtil.buildSpannable(context, customPrefix, actionTypePill, " ", keyCodePill, customSuffix)
            }
            else -> {
                PillUtil.buildSpannable(context, customPrefix, presetPill, " ", actionTypePill, presetSuffix)
            }
        }
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("按键事件已触发"))
        return ExecutionResult.Success()
    }
}