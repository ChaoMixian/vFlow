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
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var cardMoreMetadata: MaterialCardView
    private lateinit var layoutMoreMetadataHeader: LinearLayout
    private lateinit var layoutMoreMetadataContent: LinearLayout
    private lateinit var imageExpandIcon: ImageView

    private lateinit var switchMaxExecutionTime: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var layoutMaxExecutionTimeSlider: LinearLayout
    private lateinit var textMaxExecutionTimeValue: TextView
    private lateinit var sliderMaxExecutionTime: com.google.android.material.slider.Slider

    private var isMoreMetadataExpanded = false

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
        cardMoreMetadata = view.findViewById(R.id.card_more_metadata)
        layoutMoreMetadataHeader = view.findViewById(R.id.layout_more_metadata_header)
        layoutMoreMetadataContent = view.findViewById(R.id.layout_more_metadata_content)
        imageExpandIcon = view.findViewById(R.id.image_expand_icon)

        switchMaxExecutionTime = view.findViewById(R.id.switch_max_execution_time)
        layoutMaxExecutionTimeSlider = view.findViewById(R.id.layout_max_execution_time_slider)
        textMaxExecutionTimeValue = view.findViewById(R.id.text_max_execution_time_value)
        sliderMaxExecutionTime = view.findViewById(R.id.slider_max_execution_time)

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

            // 填充最大执行时长配置
            wf.maxExecutionTime?.let { maxTime ->
                switchMaxExecutionTime.isChecked = true
                layoutMaxExecutionTimeSlider.visibility = View.VISIBLE
                sliderMaxExecutionTime.value = maxTime.toFloat()
                updateMaxExecutionTimeValue(maxTime)
            } ?: run {
                switchMaxExecutionTime.isChecked = false
                layoutMaxExecutionTimeSlider.visibility = View.GONE
                sliderMaxExecutionTime.value = 60f // 默认值
                updateMaxExecutionTimeValue(60)
            }
        } ?: run {
            textWorkflowName.text = getString(R.string.workflow_not_exists)
            textWorkflowId.text = "-"
            textWorkflowModified.text = "-"

            // 初始化默认配置
            switchMaxExecutionTime.isChecked = false
            layoutMaxExecutionTimeSlider.visibility = View.GONE
            sliderMaxExecutionTime.value = 60f
            updateMaxExecutionTimeValue(60)
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

        // 折叠/展开更多元数据
        layoutMoreMetadataHeader.setOnClickListener {
            toggleMoreMetadataExpansion()
        }

        // 最大执行时长开关监听
        switchMaxExecutionTime.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutMaxExecutionTimeSlider.visibility = View.VISIBLE
            } else {
                layoutMaxExecutionTimeSlider.visibility = View.GONE
            }
        }

        // 最大执行时长 Slider 监听
        sliderMaxExecutionTime.addOnChangeListener { _, value, _ ->
            updateMaxExecutionTimeValue(value.toInt())
        }
    }

    private fun updateMaxExecutionTimeValue(seconds: Int) {
        textMaxExecutionTimeValue.text = getString(R.string.workflow_max_execution_time_value, seconds)
    }

    private fun toggleMoreMetadataExpansion() {
        isMoreMetadataExpanded = !isMoreMetadataExpanded

        if (isMoreMetadataExpanded) {
            layoutMoreMetadataContent.visibility = View.VISIBLE
            imageExpandIcon.rotation = 180f
        } else {
            layoutMoreMetadataContent.visibility = View.GONE
            imageExpandIcon.rotation = 0f
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

        val maxExecutionTime = if (switchMaxExecutionTime.isChecked) {
            sliderMaxExecutionTime.value.toInt()
        } else {
            null
        }

        val updatedWorkflow = wf.copy(
            version = version,
            vFlowLevel = vFlowLevel,
            description = description,
            author = author,
            homepage = homepage,
            tags = tags,
            maxExecutionTime = maxExecutionTime
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
