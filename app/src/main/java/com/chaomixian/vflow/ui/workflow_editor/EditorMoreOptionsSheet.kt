// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/EditorMoreOptionsSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditorMoreOptionsSheet : BottomSheetDialogFragment() {

    var workflow: Workflow? = null
    var onAiGenerateClicked: (() -> Unit)? = null
    var onUiInspectorClicked: (() -> Unit)? = null
    var onMetadataSaved: ((Workflow) -> Unit)? = null

    private lateinit var textWorkflowName: TextView
    private lateinit var textWorkflowId: TextView
    private lateinit var textWorkflowModified: TextView
    private lateinit var cardWorkflowInfo: MaterialCardView

    private lateinit var editVersion: com.google.android.material.textfield.TextInputEditText
    private lateinit var editVFlowLevel: com.google.android.material.textfield.TextInputEditText
    private lateinit var editDescription: com.google.android.material.textfield.TextInputEditText
    private lateinit var editAuthor: com.google.android.material.textfield.TextInputEditText
    private lateinit var editHomepage: com.google.android.material.textfield.TextInputEditText
    private lateinit var editTags: com.google.android.material.textfield.TextInputEditText

    private lateinit var layoutAiGenerate: MaterialCardView
    private lateinit var layoutUiInspector: MaterialCardView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sheet_editor_more_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化工作流信息视图
        textWorkflowName = view.findViewById(R.id.text_workflow_name)
        textWorkflowId = view.findViewById(R.id.text_workflow_id)
        textWorkflowModified = view.findViewById(R.id.text_workflow_modified)
        cardWorkflowInfo = view.findViewById(R.id.card_workflow_info)

        // 初始化元数据编辑视图
        editVersion = view.findViewById(R.id.edit_workflow_version)
        editVFlowLevel = view.findViewById(R.id.edit_workflow_vflow_level)
        editDescription = view.findViewById(R.id.edit_workflow_description)
        editAuthor = view.findViewById(R.id.edit_workflow_author)
        editHomepage = view.findViewById(R.id.edit_workflow_homepage)
        editTags = view.findViewById(R.id.edit_workflow_tags)

        layoutAiGenerate = view.findViewById(R.id.card_ai_generate)
        layoutUiInspector = view.findViewById(R.id.card_ui_inspector)

        val btnSaveMetadata = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_metadata)

        // 填充工作流信息
        workflow?.let { wf ->
            textWorkflowName.text = wf.name
            textWorkflowId.text = wf.id

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            textWorkflowModified.text = dateFormat.format(Date(wf.modifiedAt))

            // 点击卡片复制 ID
            cardWorkflowInfo.setOnClickListener {
                copyToClipboard(wf.id)
            }

            // 填充元数据
            editVersion.setText(wf.version)
            editVFlowLevel.setText(wf.vFlowLevel.toString())
            editDescription.setText(wf.description)
            editAuthor.setText(wf.author)
            editHomepage.setText(wf.homepage)
            editTags.setText(wf.tags.joinToString(", "))
        } ?: run {
            textWorkflowName.text = getString(R.string.workflow_not_exists)
            textWorkflowId.text = "-"
            textWorkflowModified.text = "-"
        }

        // 保存元数据
        btnSaveMetadata.setOnClickListener {
            saveMetadata()
        }

        layoutAiGenerate.setOnClickListener {
            onAiGenerateClicked?.invoke()
        }

        layoutUiInspector.setOnClickListener {
            onUiInspectorClicked?.invoke()
        }
    }

    private fun saveMetadata() {
        val wf = workflow ?: return

        val version = editVersion.text?.toString()?.trim() ?: "1.0.0"
        val vFlowLevel = editVFlowLevel.text?.toString()?.toIntOrNull() ?: 1
        val description = editDescription.text?.toString()?.trim() ?: ""
        val author = editAuthor.text?.toString()?.trim() ?: ""
        val homepage = editHomepage.text?.toString()?.trim() ?: ""
        val tagsText = editTags.text?.toString()?.trim() ?: ""
        val tags = if (tagsText.isNotEmpty()) {
            tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        val updatedWorkflow = wf.copy(
            version = version,
            vFlowLevel = vFlowLevel,
            description = description,
            author = author,
            homepage = homepage,
            tags = tags
        )

        onMetadataSaved?.invoke(updatedWorkflow)
        Toast.makeText(requireContext(), R.string.metadata_saved, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.clipboard_label_workflow_id), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
