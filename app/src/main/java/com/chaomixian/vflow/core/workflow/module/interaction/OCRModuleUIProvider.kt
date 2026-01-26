// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/OCRModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
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
import com.chaomixian.vflow.ui.workflow_editor.StandardControlFactory
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

        // 识别区域容器
        val regionContainer: FrameLayout = view.findViewById(R.id.container_region)
    }

    override fun getHandledInputIds(): Set<String> {
        return setOf("mode", "target_text", "language", "search_strategy", "region", "show_advanced")
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
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
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

        // 创建识别区域输入视图（魔法变量选择器）
        createRegionInputView(
            context,
            holder.regionContainer,
            currentParameters["region"] as? String ?: "",
            "选择识别区域变量",
            allSteps,
            onMagicVariableRequested,
            "region",
            onParametersChanged
        )

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

    /**
     * 创建识别区域输入视图
     * - 支持手动输入坐标字符串（格式：left,top,right,bottom）
     * - 支持选择变量（VCoordinateRegion 或 VString）
     */
    private fun createRegionInputView(
        context: Context,
        container: ViewGroup,
        currentValue: String,
        hint: String,
        allSteps: List<ActionStep>?,
        onMagicVariableRequested: ((String) -> Unit)?,
        inputId: String,
        onParametersChanged: () -> Unit
    ) {
        container.removeAllViews()

        // 创建包含输入框/变量显示和按钮的水平布局
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        if (StandardControlFactory.isVariableReference(currentValue)) {
            // 已选择变量：显示 pill
            val pill = StandardControlFactory.createVariablePill(
                context,
                row,
                currentValue,
                allSteps,
                onClick = { onMagicVariableRequested?.invoke(inputId) }
            )
            pill.tag = currentValue

            // 切换变量按钮
            val magicButton = createMagicButton(context, "切换变量") {
                onMagicVariableRequested?.invoke(inputId)
            }

            row.addView(pill)
            row.addView(magicButton)
        } else {
            // 未选择变量：显示输入框
            val inputLayout = TextInputLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                this.hint = "左上,右下坐标 (x1,y1,x2,y2)"
            }

            val editText = TextInputEditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setText(currentValue)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                // 允许输入数字、逗号
            }

            inputLayout.addView(editText)
            row.addView(inputLayout)

            // 变量按钮
            val magicButton = createMagicButton(context, "选择变量") {
                onMagicVariableRequested?.invoke(inputId)
            }

            row.addView(magicButton)

            editText.doAfterTextChanged { onParametersChanged() }
        }

        container.addView(row)
    }

    /**
     * 创建魔法变量按钮
     */
    private fun createMagicButton(context: Context, contentDescription: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context, null, android.R.attr.borderlessButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(context, 48),
                dpToPx(context, 48)
            ).apply {
                leftMargin = dpToPx(context, 4)
            }
            setImageResource(R.drawable.rounded_dataset_24)
            imageTintList = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary)).getColorStateList(0)
            this.contentDescription = contentDescription
            setOnClickListener { onClick() }
        }
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val mode = if (h.chipRecognize.isChecked) "识别全文" else "查找文本"
        val language = h.languageSpinner.selectedItem?.toString() ?: "中英混合"
        val strategy = h.strategySpinner.selectedItem?.toString() ?: "默认 (从上到下)"

        // 读取识别区域变量
        val region = readRegionValue(h.regionContainer)

        return mapOf(
            "mode" to mode,
            "target_text" to h.targetTextEdit.text.toString(),
            "language" to language,
            "search_strategy" to strategy,
            "region" to region,
            "show_advanced" to h.advancedContainer.isVisible
        )
    }

    /**
     * 从识别区域容器中读取值
     * - 如果是变量 pill，返回变量引用字符串
     * - 如果是输入框，返回输入的文本
     */
    private fun readRegionValue(container: ViewGroup): String {
        if (container.childCount == 0) return ""

        val child = container.getChildAt(0)
        if (child is LinearLayout && child.childCount > 0) {
            val firstChild = child.getChildAt(0)

            // 如果包含 pill_text TextView，则是 pill 视图（已选择变量）
            val pillText = firstChild.findViewById<TextView>(R.id.pill_text)
            if (pillText != null) {
                // 通过 tag 获取原始变量引用
                return firstChild.tag as? String ?: ""
            }

            // 如果是 TextInputLayout，则是输入框
            if (firstChild is TextInputLayout) {
                val editText = firstChild.editText
                return editText?.text?.toString() ?: ""
            }
        }

        return ""
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
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
