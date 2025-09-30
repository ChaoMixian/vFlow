// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/SmsTriggerUIProvider.kt
// 描述: 为短信触发器模块提供自定义编辑器UI。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout

class SmsTriggerUIProvider : ModuleUIProvider {

    // ViewHolder 用于缓存视图引用
    private class EditorViewHolder(view: View) : CustomEditorViewHolder(view) {
        val senderChipGroup: ChipGroup = view.findViewById(R.id.cg_sender_filter_type)
        val senderValueLayout: TextInputLayout = view.findViewById(R.id.til_sender_filter_value)
        val senderValueEdit: EditText = view.findViewById(R.id.et_sender_filter_value)
        val contentChipGroup: ChipGroup = view.findViewById(R.id.cg_content_filter_type)
        val contentValueLayout: TextInputLayout = view.findViewById(R.id.til_content_filter_value)
        val contentValueEdit: EditText = view.findViewById(R.id.et_content_filter_value)
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "sender_filter_type", "sender_filter_value", "content_filter_type", "content_filter_value"
    )

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_sms_trigger_editor, parent, false)
        val holder = EditorViewHolder(view)
        val module = SmsTriggerModule()

        // --- 恢复状态 ---
        setupChipGroup(context, holder.senderChipGroup, module.senderFilterOptions, currentParameters["sender_filter_type"] as? String) { selectedText ->
            updateValueLayoutVisibility(holder.senderValueLayout, selectedText)
            onParametersChanged()
        }
        holder.senderValueEdit.setText(currentParameters["sender_filter_value"] as? String ?: "")

        setupChipGroup(context, holder.contentChipGroup, module.contentFilterOptions, currentParameters["content_filter_type"] as? String) { selectedText ->
            updateValueLayoutVisibility(holder.contentValueLayout, selectedText, "识别验证码")
            onParametersChanged()
        }
        // 当类型不是“识别验证码”时，才设置文本
        if (currentParameters["content_filter_type"] as? String != "识别验证码") {
            holder.contentValueEdit.setText(currentParameters["content_filter_value"] as? String ?: "")
        }


        // --- 添加监听器 ---
        holder.senderValueEdit.doAfterTextChanged { onParametersChanged() }
        holder.contentValueEdit.doAfterTextChanged { onParametersChanged() }

        // --- 初始化UI可见性 ---
        updateValueLayoutVisibility(holder.senderValueLayout, getSelectedChipText(holder.senderChipGroup))
        updateValueLayoutVisibility(holder.contentValueLayout, getSelectedChipText(holder.contentChipGroup), "识别验证码")


        return holder
    }

    // 辅助函数：获取ChipGroup中选中的文本
    private fun getSelectedChipText(chipGroup: ChipGroup): String {
        val checkedChip = chipGroup.findViewById<Chip>(chipGroup.checkedChipId)
        return checkedChip?.text?.toString() ?: ""
    }


    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as EditorViewHolder
        val contentFilterType = getSelectedChipText(h.contentChipGroup)
        // “识别验证码”模式下，value 字段为空字符串，不再需要硬编码正则表达式
        val contentFilterValue = if (contentFilterType == "识别验证码") {
            ""
        } else {
            h.contentValueEdit.text.toString()
        }

        return mapOf(
            "sender_filter_type" to getSelectedChipText(h.senderChipGroup),
            "sender_filter_value" to h.senderValueEdit.text.toString(),
            "content_filter_type" to contentFilterType,
            "content_filter_value" to contentFilterValue
        )
    }

    /**
     * 辅助函数：动态创建、设置并监听ChipGroup。
     */
    private fun setupChipGroup(
        context: Context,
        chipGroup: ChipGroup,
        options: List<String>,
        currentValue: String?,
        onSelectionChanged: (String) -> Unit
    ) {
        chipGroup.removeAllViews() // 清空容器
        val selectedValue = currentValue ?: options.first()
        val inflater = LayoutInflater.from(context)

        // 遍历选项列表，为每个选项创建一个Chip
        options.forEach { optionText ->
            // 使用新的 chip_filter.xml 布局
            val chip = inflater.inflate(R.layout.chip_filter, chipGroup, false) as Chip
            chip.text = optionText
            chip.isChecked = (optionText == selectedValue)
            chipGroup.addView(chip)
        }

        // 设置监听器
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedChip = group.findViewById<Chip>(checkedIds.first())
                if (checkedChip != null) {
                    onSelectionChanged(checkedChip.text.toString())
                }
            }
        }
    }


    // 辅助函数：更新值输入框的可见性
    private fun updateValueLayoutVisibility(layout: TextInputLayout, selectedOption: String, vararg optionsToHide: String) {
        val defaultOptions = arrayOf("任意号码", "任意内容")
        val allHideOptions = defaultOptions + optionsToHide
        val needsValue = !allHideOptions.contains(selectedOption)
        layout.isVisible = needsValue
        if (needsValue) {
            layout.hint = selectedOption
        }
    }


    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>, onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?): View? = null
}