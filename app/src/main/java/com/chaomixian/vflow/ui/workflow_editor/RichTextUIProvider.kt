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
import com.chaomixian.vflow.core.module.PreviewPillModel
import com.chaomixian.vflow.core.module.PreviewPillType
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

    override fun createPreviewPills(
        context: Context,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): List<PreviewPillModel> {
        val rawText = step.parameters[richTextInputId]?.toString() ?: ""
        if (!VariableResolver.isComplex(rawText)) {
            return emptyList()
        }
        return listOf(
            PreviewPillModel(
                type = PreviewPillType.RICH_TEXT,
                content = rawText
            )
        )
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

        if (!VariableResolver.isComplex(rawText)) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        textView.text = PillRenderer.renderDisplayText(
            context = context,
            content = rawText,
            allSteps = allSteps,
            style = PillRenderer.DisplayStyle.RICH_TEXT
        )

        return previewView
    }
}
