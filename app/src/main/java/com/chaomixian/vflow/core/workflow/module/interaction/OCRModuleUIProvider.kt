// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/OCRModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class OCRModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val modeChipGroup: ChipGroup = view.findViewById(R.id.cg_ocr_mode)
        val chipRecognize: Chip = view.findViewById(R.id.chip_mode_recognize)
        val chipFind: Chip = view.findViewById(R.id.chip_mode_find)
        val targetTextLayout: TextInputLayout = view.findViewById(R.id.til_target_text)
        val targetTextEdit: TextInputEditText = view.findViewById(R.id.et_target_text)

        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced_options)
        val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)

        val languageSpinner: Spinner = view.findViewById(R.id.spinner_language)
        val strategySpinner: Spinner = view.findViewById(R.id.spinner_strategy)
        val strategyLabel: TextView = view.findViewById(R.id.tv_strategy_label)
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("mode", "target_text", "language", "search_strategy", "show_advanced")
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_ocr_editor, parent, false)
        val holder = ViewHolder(view)
        val module = OCRModule()

        // 恢复状态
        val mode = currentParameters["mode"] as? String ?: "识别全文"
        if (mode == "识别全文") holder.chipRecognize.isChecked = true else holder.chipFind.isChecked = true

        holder.targetTextEdit.setText(currentParameters["target_text"] as? String ?: "")

        // 恢复高级菜单的状态
        val showAdvanced = currentParameters["show_advanced"] as? Boolean ?: false
        holder.advancedContainer.isVisible = showAdvanced
        holder.expandArrow.rotation = if (showAdvanced) 180f else 0f

        // 初始化 Spinner
        setupSpinner(context, holder.languageSpinner, module.languageOptions, currentParameters["language"] as? String) { onParametersChanged() }
        setupSpinner(context, holder.strategySpinner, module.strategyOptions, currentParameters["search_strategy"] as? String) { onParametersChanged() }

        // UI 逻辑
        fun updateVisibility() {
            val isFindMode = holder.chipFind.isChecked
            val isAdvancedShown = holder.advancedContainer.isVisible

            holder.targetTextLayout.isVisible = isFindMode
            holder.strategySpinner.isVisible = isFindMode
            holder.strategyLabel.isVisible = isFindMode
        }

        updateVisibility()

        // 监听器
        holder.modeChipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateVisibility()
            onParametersChanged()
        }

        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            updateVisibility() // 需要更新策略Spinner的可见性
            onParametersChanged()
        }

        holder.targetTextEdit.doAfterTextChanged { onParametersChanged() }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val mode = if (h.chipRecognize.isChecked) "识别全文" else "查找文本"
        val language = h.languageSpinner.selectedItem?.toString() ?: "中英混合"
        val strategy = h.strategySpinner.selectedItem?.toString() ?: "默认 (从上到下)"

        return mapOf(
            "mode" to mode,
            "target_text" to h.targetTextEdit.text.toString(),
            "language" to language,
            "search_strategy" to strategy,
            "show_advanced" to h.advancedContainer.isVisible
        )
    }

    private fun setupSpinner(context: Context, spinner: Spinner, options: List<String>, currentValue: String?, onChanged: () -> Unit) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val index = options.indexOf(currentValue ?: options.first())
        if (index != -1) spinner.setSelection(index)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (spinner.tag != position) {
                    spinner.tag = position
                    onChanged()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
}