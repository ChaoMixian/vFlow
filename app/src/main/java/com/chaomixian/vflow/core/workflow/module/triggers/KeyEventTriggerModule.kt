// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/KeyEventTriggerModule.kt
// 描述: 通过 onParameterUpdated 实现了智能预设，为后台服务提供清晰、具体的参数。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class KeyEventTriggerModule : BaseModule() {

    override val id = "vflow.trigger.key_event"
    override val metadata = ActionMetadata(
        name = "按键触发",
        description = "当指定的物理按键被按下时，触发此工作流（需要Shizuku）。",
        iconRes = R.drawable.rounded_horizontal_align_bottom_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.SHIZUKU)

    private val presetOptions = listOf("手动/自定义", "一加 13T (侧键)", "一加 13 (三段式)")
    private val onePlus13TActions = listOf("单击", "双击", "长按", "短按 (立即触发)")
    private val onePlus13Actions = listOf("向下滑动", "向上滑动")
    private val allActionOptions = (onePlus13TActions + onePlus13Actions).distinct()

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
                "一加 13T (侧键)" -> {
                    newParameters["action_type"] = "单击"
                    // [关键] 为后台服务准备好内部参数
                    newParameters["_internal_device_name"] = "key-handler"
                    newParameters["_internal_key_code"] = "BTN_TRIGGER_HAPPY32"
                }
                "一加 13 (三段式)" -> {
                    newParameters["action_type"] = "向下滑动"
                    newParameters["_internal_device_name"] = "oplus,hall_tri_state_key"
                    newParameters["_internal_key_code"] = "KEY_F3" // 虚拟按键码
                }
                else -> { // "手动/自定义"
                    newParameters["action_type"] = "单击"
                }
            }
        }
        return newParameters
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("device_preset", "设备预设", ParameterType.ENUM, "手动/自定义", options = presetOptions),
        InputDefinition("device", "输入设备", ParameterType.STRING, "/dev/input/event0"),
        InputDefinition("key_code", "按键码", ParameterType.STRING, "KEY_POWER"),
        InputDefinition("action_type", "操作类型", ParameterType.ENUM, "单击", options = allActionOptions)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val allInputs = getInputs()
        val currentPreset = step?.parameters?.get("device_preset") as? String ?: "手动/自定义"
        val dynamicInputs = mutableListOf(allInputs.first { it.id == "device_preset" })

        when (currentPreset) {
            "一加 13T (侧键)" -> {
                dynamicInputs.add(allInputs.first { it.id == "action_type" }.copy(options = onePlus13TActions))
            }
            "一加 13 (三段式)" -> {
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
        val preset = step.parameters["device_preset"] as? String ?: "手动/自定义"
        val actionType = step.parameters["action_type"] as? String

        if (actionType.isNullOrEmpty()) return "配置按键触发器"

        val presetPill = PillUtil.Pill(preset.replace(" (侧键)", "").replace(" (三段式)", ""), "device_preset", isModuleOption = true)
        val actionTypePill = PillUtil.Pill(actionType.replace(" (立即触发)", ""), "action_type", isModuleOption = true)

        return when (preset) {
            "手动/自定义" -> {
                val keyCode = step.parameters["key_code"] as? String ?: "N/A"
                val keyCodePill = PillUtil.Pill(keyCode, "key_code")
                PillUtil.buildSpannable(context, "当 ", actionTypePill, " ", keyCodePill, " 键时")
            }
            else -> {
                PillUtil.buildSpannable(context, "当 ", presetPill, " ", actionTypePill, " 时")
            }
        }
    }
    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("按键事件已触发"))
        return ExecutionResult.Success()
    }
}