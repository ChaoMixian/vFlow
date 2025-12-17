// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextUIProvider.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.VariableResolver // 引入
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep

class RichTextUIProvider(private val richTextInputId: String) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?, onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        throw NotImplementedError("RichTextUIProvider does not create a custom editor.")
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        throw NotImplementedError("RichTextUIProvider does not read from a custom editor.")
    }

    /**
     * 创建预览视图。
     * 使用 VariableResolver.isComplex 统一判断标准。
     */
    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val rawText = step.parameters[richTextInputId]?.toString() ?: ""

        // 如果内容不复杂，则不创建自定义预览，让 ActionStepAdapter 回退到 getSummary
        if (!VariableResolver.isComplex(rawText)) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val spannable = PillRenderer.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }
}