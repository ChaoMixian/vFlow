// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/UiInputModule.kt
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
 * 输入框组件
 *
 * 用于接收用户输入的文本内容。
 *
 * 参数：
 * - key: 组件 ID，也是变量名（必填）
 * - label: 输入框上方的标签文本
 * - hint: 输入框内的提示文本
 * - default_value: 默认值
 * - trigger_event: 是否在输入时触发事件
 *
 * 输出：
 * - id: 组件的唯一标识符
 *
 * 注意：
 * - 启用 trigger_event 后，每次输入都会触发事件
 */
class UiInputModule : BaseUiComponentModule() {
    override val id = "vflow.ui.component.input"
    override val metadata = ActionMetadata(
        name = "输入框",  // Fallback
        nameStringRes = R.string.module_vflow_ui_component_input_name,
        description = "文本输入框。",  // Fallback
        descriptionStringRes = R.string.module_vflow_ui_component_input_desc,
        iconRes = R.drawable.rounded_keyboard_external_input_24,
        category = "UI 组件"
    )

    override fun getInputs() = listOf(
        InputDefinition("key", "ID (变量名)", ParameterType.STRING, "input1", acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_ui_key_variable),
        InputDefinition("label", "标签", ParameterType.STRING, "请输入", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_ui_label),
        InputDefinition("hint", "提示词", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_ui_hint),
        InputDefinition("default_value", "默认值", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_ui_default_value),
        InputDefinition("trigger_event", "输入时触发事件", ParameterType.BOOLEAN, true, nameStringRes = R.string.param_vflow_ui_input_trigger)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, context.getString(R.string.summary_prefix_input), PillUtil.createPillFromParam(step.parameters["key"], getInputs()[0]))

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val label = VariableResolver.resolve(step.parameters["label"]?.toString() ?: "", context)
        val defaultVal = VariableResolver.resolve(step.parameters["default_value"]?.toString() ?: "", context)
        val hint = VariableResolver.resolve(step.parameters["hint"]?.toString() ?: "", context)
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() } ?: "input_${System.currentTimeMillis()}"
        val trigger = step.parameters["trigger_event"] as? Boolean ?: true

        return UiElement(id = key, type = UiElementType.INPUT, label = label, defaultValue = defaultVal, placeholder = hint, isRequired = false, triggerEvent = trigger)
    }
}
