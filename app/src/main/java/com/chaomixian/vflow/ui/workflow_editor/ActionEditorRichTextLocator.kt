package com.chaomixian.vflow.ui.workflow_editor

import android.view.ViewGroup
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.InputDefinition

internal object ActionEditorRichTextLocator {
    fun findRichTextView(
        inputId: String,
        inputDefinition: InputDefinition?,
        inputViews: Map<String, android.view.View>,
        customEditorHolder: CustomEditorViewHolder?
    ): RichTextView? {
        if (inputDefinition?.supportsRichText == true) {
            val genericInputView = inputViews[inputId]
            val genericRichTextView = (genericInputView
                ?.findViewById<ViewGroup>(R.id.input_value_container)
                ?.getChildAt(0) as? ViewGroup)
                ?.findViewById<RichTextView>(R.id.rich_text_view)
            if (genericRichTextView != null) {
                return genericRichTextView
            }
        }

        val customRootView = customEditorHolder?.view ?: return null
        return customRootView.findViewWithTag<RichTextView>(inputId)
            ?: customRootView.findViewWithTag("rich_text_view_value")
    }
}
