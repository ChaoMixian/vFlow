// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/ReadSmsModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ReadSmsModuleUIProvider : ModuleUIProvider {
    private data class FilterOption(val value: String, val label: String)

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val filterChipGroup: ChipGroup = view.findViewById(R.id.cg_filter_by)
        val senderLayout: TextInputLayout = view.findViewById(R.id.til_sender)
        val senderEditText: TextInputEditText = view.findViewById(R.id.et_sender)
        val contentLayout: TextInputLayout = view.findViewById(R.id.til_content)
        val contentEditText: TextInputEditText = view.findViewById(R.id.et_content)
        val scanSlider: Slider = view.findViewById(R.id.slider_scan_count)
        val scanCountText: TextView = view.findViewById(R.id.text_scan_count)
        val scanTitleText: TextView = view.findViewById(R.id.title_scan_range)
        val extractCodeSwitch: MaterialSwitch = view.findViewById(R.id.switch_extract_code)
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("filter_by", "sender", "content", "max_scan", "extract_code")
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_read_sms_editor, parent, false)
        val holder = ViewHolder(view)
        val module = ReadSmsModule()
        val filterInput = module.getInputs().first { it.id == "filter_by" }
        val filterOptions = filterInput.options.zip(filterInput.getLocalizedOptions(context))
            .map { (value, label) -> FilterOption(value, label) }

        for (index in 0 until holder.filterChipGroup.childCount) {
            val chip = holder.filterChipGroup.getChildAt(index) as? Chip ?: continue
            val option = filterOptions.getOrNull(index) ?: continue
            chip.tag = option.value
            chip.text = option.label
        }

        // 恢复已有参数
        val rawFilterBy = currentParameters["filter_by"] as? String
        val filterBy = filterInput.normalizeEnumValue(rawFilterBy, null)
            ?: rawFilterBy
            ?: module.filterOptions.first()
        for (i in 0 until holder.filterChipGroup.childCount) {
            val chip = holder.filterChipGroup.getChildAt(i) as? Chip
            if (chip?.tag == filterBy) {
                chip.isChecked = true
                break
            }
        }
        holder.senderEditText.setText(currentParameters["sender"] as? String ?: "")
        holder.contentEditText.setText(currentParameters["content"] as? String ?: "")
        val maxScan = (currentParameters["max_scan"] as? Number ?: 20.0).toFloat()
        holder.scanSlider.value = maxScan
        holder.scanCountText.text = context.getString(R.string.msg_vflow_system_read_sms_scanning, maxScan.toInt())
        holder.extractCodeSwitch.isChecked = currentParameters["extract_code"] as? Boolean ?: false

        fun getSelectedFilter(): String {
            return holder.filterChipGroup.findViewById<Chip>(holder.filterChipGroup.checkedChipId)
                ?.tag as? String ?: module.filterOptions.first()
        }

        // 根据筛选方式更新UI可见性
        fun updateUiVisibility() {
            val selectedFilter = getSelectedFilter()
            val extractCode = holder.extractCodeSwitch.isChecked

            holder.senderLayout.isVisible = selectedFilter == ReadSmsModule.FILTER_SENDER || selectedFilter == ReadSmsModule.FILTER_BOTH
            holder.contentLayout.isVisible = (selectedFilter == ReadSmsModule.FILTER_CONTENT || selectedFilter == ReadSmsModule.FILTER_BOTH) && !extractCode
            holder.extractCodeSwitch.isVisible = selectedFilter == ReadSmsModule.FILTER_CONTENT || selectedFilter == ReadSmsModule.FILTER_BOTH

            val isScanRangeVisible = selectedFilter != ReadSmsModule.FILTER_LATEST
            holder.scanTitleText.isVisible = isScanRangeVisible
            holder.scanCountText.isVisible = isScanRangeVisible
            holder.scanSlider.isVisible = isScanRangeVisible
        }

        updateUiVisibility()

        // 添加监听器
        holder.filterChipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateUiVisibility()
            onParametersChanged()
        }
        holder.extractCodeSwitch.setOnCheckedChangeListener { _, _ ->
            updateUiVisibility()
            onParametersChanged()
        }
        holder.scanSlider.addOnChangeListener { _, value, _ ->
            holder.scanCountText.text = context.getString(R.string.msg_vflow_system_read_sms_scanning, value.toInt())
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val filterBy = h.filterChipGroup.findViewById<Chip>(h.filterChipGroup.checkedChipId)?.tag as? String
            ?: ReadSmsModule.FILTER_LATEST

        return mapOf(
            "filter_by" to filterBy,
            "sender" to h.senderEditText.text.toString(),
            "content" to h.contentEditText.text.toString(),
            "max_scan" to h.scanSlider.value.toDouble(),
            "extract_code" to h.extractCodeSwitch.isChecked
        )
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
