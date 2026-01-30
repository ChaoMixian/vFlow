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
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider

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
    override val metadata = ActionMetadata(
        name = "文本展示",  // Fallback
        nameStringRes = R.string.module_vflow_ui_component_text_name,
        description = "显示一段静态文字。",  // Fallback
        descriptionStringRes = R.string.module_vflow_ui_component_text_desc,
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "UI 组件"
    )
    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("content")

    override fun getInputs() = listOf(
        InputDefinition("content", "内容", ParameterType.STRING, acceptsMagicVariable = true, supportsRichText = true, nameStringRes = R.string.param_vflow_ui_content),
        InputDefinition("key", "ID (可选)", ParameterType.STRING, "", isHidden = true, nameStringRes = R.string.param_vflow_ui_content_optional)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["content"]?.toString() ?: ""

        // 如果内容复杂（包含变量），只显示模块名称，让 RichTextUIProvider 显示富文本预览卡片
        if (VariableResolver.isComplex(rawText)) {
            return metadata.getLocalizedName(context)
        }

        // 内容简单，显示完整的摘要
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_prefix_display_text), contentPill)
    }

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val content = VariableResolver.resolve(step.parameters["content"]?.toString() ?: "", context)
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() } ?: "text_${System.currentTimeMillis()}"
        return UiElement(id = key, type = UiElementType.TEXT, label = content, defaultValue = "", placeholder = "", isRequired = false)
    }
}
