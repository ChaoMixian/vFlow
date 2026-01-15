// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/UiTextModule.kt
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
 * 文本展示组件
 *
 * 用于显示静态文本内容。
 *
 * 参数：
 * - content: 要显示的文本内容（支持魔法变量和富文本）
 * - key: 组件 ID（可选，默认自动生成）
 *
 * 输出：
 * - component: VUiComponent 对象（包含组件的所有信息）
 * - id: 组件 ID（向后兼容）
 */
class UiTextModule : BaseUiComponentModule() {
    override val id = "vflow.ui.component.text"
    override val metadata = ActionMetadata("文本展示", "显示一段静态文字。", R.drawable.rounded_convert_to_text_24, "UI 组件")

    override fun getInputs() = listOf(
        InputDefinition("content", "内容", ParameterType.STRING, acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("key", "ID (可选)", ParameterType.STRING, "", isHidden = true)
    )

    override fun getSummary(context: Context, step: ActionStep) =
        PillUtil.buildSpannable(context, "展示文本: ", PillUtil.createPillFromParam(step.parameters["content"], getInputs()[0]))

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val content = VariableResolver.resolve(step.parameters["content"]?.toString() ?: "", context)
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() } ?: "text_${System.currentTimeMillis()}"
        return UiElement(id = key, type = UiElementType.TEXT, label = content, defaultValue = "", placeholder = "", isRequired = false)
    }
}
