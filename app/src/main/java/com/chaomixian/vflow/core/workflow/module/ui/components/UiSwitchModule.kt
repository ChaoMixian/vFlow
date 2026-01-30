// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/UiSwitchModule.kt
package com.chaomixian.vflow.core.workflow.module.ui.components

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 开关组件
 *
 * 用于表示布尔值（开/关）的切换开关。
 *
 * 参数：
 * - key: 组件 ID，也是变量名（必填）
 * - label: 开关旁边的标签文本
 * - default_value: 默认开启状态
 * - trigger_event: 是否在切换时触发事件
 *
 * 输出：
 * - id: 组件的唯一标识符
 *
 * 注意：
 * - 值为 true（开）或 false（关）
 */
class UiSwitchModule : BaseUiComponentModule() {
    override val id = "vflow.ui.component.switch"
    override val metadata = ActionMetadata(
        name = "开关",  // Fallback
        nameStringRes = R.string.module_vflow_ui_component_switch_name,
        description = "布尔值开关。",  // Fallback
        descriptionStringRes = R.string.module_vflow_ui_component_switch_desc,
        iconRes = R.drawable.rounded_change_circle_24,
        category = "UI 组件"
    )

    override fun getInputs() = listOf(
        InputDefinition("key", "ID (变量名)", ParameterType.STRING, "switch1", acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_ui_key_variable),
        InputDefinition("label", "标签", ParameterType.STRING, "选项", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_ui_label),
        InputDefinition("default_value", "默认开启", ParameterType.BOOLEAN, false, nameStringRes = R.string.param_vflow_ui_switch_default),
        InputDefinition("trigger_event", "切换时触发事件", ParameterType.BOOLEAN, true, nameStringRes = R.string.param_vflow_ui_switch_trigger)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, context.getString(R.string.summary_prefix_switch), PillUtil.createPillFromParam(step.parameters["key"], getInputs()[0]))

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val label = VariableResolver.resolve(step.parameters["label"]?.toString() ?: "", context)
        val defaultVal = step.parameters["default_value"] as? Boolean ?: false
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() } ?: "switch_${System.currentTimeMillis()}"
        val trigger = step.parameters["trigger_event"] as? Boolean ?: true

        return UiElement(id = key, type = UiElementType.SWITCH, label = label, defaultValue = defaultVal.toString(), placeholder = "", isRequired = false, triggerEvent = trigger)
    }
}
