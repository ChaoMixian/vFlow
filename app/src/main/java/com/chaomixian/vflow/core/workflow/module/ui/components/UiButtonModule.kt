// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/UiButtonModule.kt
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
 * 按钮组件
 *
 * 用于触发操作或提交表单。
 *
 * 参数：
 * - key: 组件 ID，也是事件源标识（必填）
 * - text: 按钮上显示的文字
 * - trigger_event: 仅触发事件而不关闭窗口
 *
 * 输出：
 * - id: 组件的唯一标识符
 *
 * 行为：
 * - trigger_event = true: 触发点击事件，窗口保持打开
 * - trigger_event = false (默认): 收集所有数据并关闭窗口
 *
 * 使用场景：
 * - 交互按钮：trigger_event=true，配合"当组件被操作"模块处理点击
 * - 提交按钮：trigger_event=false，直接提交数据并关闭
 */
class UiButtonModule : BaseUiComponentModule() {
    override val id = "vflow.ui.component.button"
    override val metadata = ActionMetadata("按钮", "点击后提交表单或触发事件。", R.drawable.rounded_ads_click_24, "UI 组件")

    override fun getInputs() = listOf(
        InputDefinition("key", "ID (事件源)", ParameterType.STRING, "btn1", acceptsMagicVariable = false),
        InputDefinition("text", "按钮文字", ParameterType.STRING, "确定", acceptsMagicVariable = true),
        InputDefinition("trigger_event", "仅触发事件 (不关闭窗口)", ParameterType.BOOLEAN, true)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "按钮: ", PillUtil.createPillFromParam(step.parameters["key"], getInputs()[0]), ": ", PillUtil.createPillFromParam(step.parameters["text"], getInputs()[1]))

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val text = VariableResolver.resolve(step.parameters["text"]?.toString() ?: "", context)
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() } ?: "btn_${System.currentTimeMillis()}"
        val trigger = step.parameters["trigger_event"] as? Boolean ?: true

        return UiElement(id = key, type = UiElementType.BUTTON, label = text, defaultValue = "", placeholder = "", isRequired = false, triggerEvent = trigger)
    }
}
