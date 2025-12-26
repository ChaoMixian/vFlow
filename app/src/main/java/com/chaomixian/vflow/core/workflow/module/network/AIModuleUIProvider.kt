// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/AIModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AIModuleUIProvider : ModuleUIProvider {

    // 实例化 RichTextUIProvider，指定要预览的字段为 "prompt"
    private val richTextUIProvider = RichTextUIProvider("prompt")

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val providerGroup: ChipGroup = view.findViewById(R.id.cg_provider)
        val chipOpenAI: Chip = view.findViewById(R.id.chip_openai)
        val chipDeepSeek: Chip = view.findViewById(R.id.chip_deepseek)
        val chipCustom: Chip = view.findViewById(R.id.chip_custom)

        val apiKeyEdit: TextInputEditText = view.findViewById(R.id.et_api_key)
        val modelEdit: TextInputEditText = view.findViewById(R.id.et_model)
        val promptContainer: FrameLayout = view.findViewById(R.id.container_prompt)

        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced)
        val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)

        val baseUrlEdit: TextInputEditText = view.findViewById(R.id.et_base_url)
        val systemPromptEdit: TextInputEditText = view.findViewById(R.id.et_system_prompt)
        val tempSlider: Slider = view.findViewById(R.id.slider_temperature)
        val tempText: TextView = view.findViewById(R.id.tv_temperature_value)

        // 引用动态创建的 RichTextView
        var promptRichText: RichTextView? = null
        var allSteps: List<ActionStep>? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "provider", "api_key", "base_url", "model", "prompt", "system_prompt", "temperature", "show_advanced"
    )

    // 实现 createPreview，委托给 richTextUIProvider
    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return richTextUIProvider.createPreview(context, parent, step, allSteps, onStartActivityForResult)
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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_ai_completion_editor, parent, false)
        val holder = ViewHolder(view)
        holder.allSteps = allSteps

        // 恢复参数
        val provider = currentParameters["provider"] as? String ?: "OpenAI"
        when (provider) {
            "OpenAI" -> holder.chipOpenAI.isChecked = true
            "DeepSeek" -> holder.chipDeepSeek.isChecked = true
            else -> holder.chipCustom.isChecked = true
        }

        holder.apiKeyEdit.setText(currentParameters["api_key"] as? String ?: "")
        holder.modelEdit.setText(currentParameters["model"] as? String ?: "gpt-3.5-turbo")
        holder.baseUrlEdit.setText(currentParameters["base_url"] as? String ?: "https://api.openai.com/v1")
        holder.systemPromptEdit.setText(currentParameters["system_prompt"] as? String ?: "You are a helpful assistant.")

        val temp = (currentParameters["temperature"] as? Number)?.toFloat() ?: 0.7f
        holder.tempSlider.value = temp
        holder.tempText.text = String.format(Locale.US, "%.1f", temp)

        val showAdvanced = currentParameters["show_advanced"] as? Boolean ?: false
        holder.advancedContainer.isVisible = showAdvanced
        holder.expandArrow.rotation = if (showAdvanced) 180f else 0f

        // 创建 Prompt 富文本编辑器
        val promptValue = currentParameters["prompt"] as? String ?: ""
        setupPromptEditor(context, holder, promptValue, onMagicVariableRequested)

        // 监听器逻辑
        holder.providerGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds[0]

            // 预设填充逻辑
            when (checkedId) {
                R.id.chip_openai -> {
                    holder.baseUrlEdit.setText("https://api.openai.com/v1")
                    holder.modelEdit.setText("gpt-3.5-turbo")
                }
                R.id.chip_deepseek -> {
                    holder.baseUrlEdit.setText("https://api.deepseek.com")
                    holder.modelEdit.setText("deepseek-chat")
                }
                // 自定义模式保持当前值不变
            }
            onParametersChanged()
        }

        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            onParametersChanged()
        }

        holder.tempSlider.addOnChangeListener { _, value, _ ->
            holder.tempText.text = String.format(Locale.US, "%.1f", value)
            onParametersChanged()
        }

        val textWatcher = { _: Editable? -> onParametersChanged() }

        holder.apiKeyEdit.doAfterTextChanged(textWatcher)
        holder.modelEdit.doAfterTextChanged(textWatcher)
        holder.baseUrlEdit.doAfterTextChanged(textWatcher)
        holder.systemPromptEdit.doAfterTextChanged(textWatcher)

        return holder
    }

    private fun setupPromptEditor(
        context: Context,
        holder: ViewHolder,
        initialValue: String,
        onMagicReq: ((String) -> Unit)?
    ) {
        holder.promptContainer.removeAllViews()
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
        row.findViewById<TextView>(R.id.input_name).visibility = View.GONE // 隐藏左侧标签，因为上方已有标题
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        magicButton.setOnClickListener { onMagicReq?.invoke("prompt") }

        // 加载富文本编辑器布局
        val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
        val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

        richTextView.setRichText(initialValue) { variableRef ->
            PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
        }

        // 将引用保存到 ViewHolder
        holder.promptRichText = richTextView

        valueContainer.addView(richEditorLayout)
        holder.promptContainer.addView(row)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder

        val provider = when {
            h.chipDeepSeek.isChecked -> "DeepSeek"
            h.chipCustom.isChecked -> "自定义"
            else -> "OpenAI"
        }

        return mapOf(
            "provider" to provider,
            "api_key" to h.apiKeyEdit.text.toString(),
            "base_url" to h.baseUrlEdit.text.toString(),
            "model" to h.modelEdit.text.toString(),
            "prompt" to (h.promptRichText?.getRawText() ?: ""),
            "system_prompt" to h.systemPromptEdit.text.toString(),
            "temperature" to h.tempSlider.value.toDouble(),
            "show_advanced" to h.advancedContainer.isVisible
        )
    }
}