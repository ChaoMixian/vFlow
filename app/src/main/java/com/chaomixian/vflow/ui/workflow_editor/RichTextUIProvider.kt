// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextUIProvider.kt
// 描述: 通用的富文本UI提供者，现在使用PillRenderer进行预览渲染。
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 一个可复用的 ModuleUIProvider，专门用于处理富文本输入。
 * 它的核心功能是 createPreview，用于在步骤摘要中显示一个包含富文本内容的自定义视图。
 * 它不处理 createEditor，因为富文本编辑器的创建由 ActionEditorSheet 的通用逻辑完成。
 * @param richTextInputId 该模块中支持富文本的输入框的ID。
 */
class RichTextUIProvider(private val richTextInputId: String) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        throw NotImplementedError("RichTextUIProvider does not create a custom editor.")
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        throw NotImplementedError("RichTextUIProvider does not read from a custom editor.")
    }

    /**
     * 创建在工作流步骤卡片中显示的自定义富文本预览视图。
     * 现在调用 PillRenderer 来完成渲染，保持了逻辑的解耦。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View {
        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val rawText = step.parameters[richTextInputId]?.toString() ?: ""

        // 使用 PillRenderer 将原始文本（含变量引用）转换为带“药丸”样式的Spannable文本
        val spannable = PillRenderer.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }
}