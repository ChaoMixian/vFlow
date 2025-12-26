// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
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

class AgentModuleUIProvider : ModuleUIProvider {

    private val richTextUIProvider = RichTextUIProvider("instruction")

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val providerGroup: ChipGroup = view.findViewById(R.id.cg_provider)
        val chipBigModel: Chip = view.findViewById(R.id.chip_bigmodel)
        val chipDashScope: Chip = view.findViewById(R.id.chip_dashscope)
        val chipCustom: Chip = view.findViewById(R.id.chip_custom)

        val baseUrlEdit: TextInputEditText = view.findViewById(R.id.et_base_url)
        val modelEdit: TextInputEditText = view.findViewById(R.id.et_model)
        val apiKeyEdit: TextInputEditText = view.findViewById(R.id.et_api_key)

        val instructionContainer: FrameLayout = view.findViewById(R.id.container_instruction)

        val stepsSlider: Slider = view.findViewById(R.id.slider_max_steps)
        val stepsText: TextView = view.findViewById(R.id.tv_max_steps_value)

        var instructionRichText: RichTextView? = null
        var allSteps: List<ActionStep>? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "provider", "base_url", "model", "api_key", "instruction", "max_steps"
    )

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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_agent_editor, parent, false)
        val holder = ViewHolder(view)
        holder.allSteps = allSteps

        // 隐藏旧的工具选择 UI，未来可能再次加入
        view.findViewById<View>(R.id.btn_select_tools)?.isVisible = false
        view.findViewById<View>(R.id.tv_selected_tools)?.isVisible = false

        // 恢复服务商配置
        val provider = currentParameters["provider"] as? String ?: "阿里云百炼"
        when (provider) {
            "阿里云百炼" -> holder.chipDashScope.isChecked = true
            "智谱" -> holder.chipBigModel.isChecked = true
            else -> holder.chipCustom.isChecked = true
        }

        holder.baseUrlEdit.setText(currentParameters["base_url"] as? String ?: "https://dashscope.aliyuncs.com/compatible-mode/v1")
        holder.modelEdit.setText(currentParameters["model"] as? String ?: "glm-4.6")
        holder.apiKeyEdit.setText(currentParameters["api_key"] as? String ?: "")

        // 恢复 Instruction
        setupInstructionEditor(context, holder, currentParameters["instruction"] as? String ?: "", onMagicVariableRequested)

        // 恢复 Max Steps
        val maxSteps = (currentParameters["max_steps"] as? Number)?.toFloat() ?: 10f
        holder.stepsSlider.value = maxSteps
        holder.stepsText.text = "${maxSteps.toInt()}"

        // 监听器
        holder.providerGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds[0]) {
                R.id.chip_bigmodel -> {
                    holder.baseUrlEdit.setText("https://open.bigmodel.cn/api/paas/v4")
                    holder.modelEdit.setText("glm-4.6v-flash")
                }
                R.id.chip_dashscope -> {
                    holder.baseUrlEdit.setText("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    holder.modelEdit.setText("glm-4.6")
                }
            }
            onParametersChanged()
        }

        val textWatcher = { _: Editable? -> onParametersChanged() }
        holder.baseUrlEdit.doAfterTextChanged(textWatcher)
        holder.modelEdit.doAfterTextChanged(textWatcher)
        holder.apiKeyEdit.doAfterTextChanged(textWatcher)

        holder.stepsSlider.addOnChangeListener { _, value, _ ->
            holder.stepsText.text = "${value.toInt()}"
            onParametersChanged()
        }

        return holder
    }

    private fun setupInstructionEditor(
        context: Context,
        holder: ViewHolder,
        initialValue: String,
        onMagicReq: ((String) -> Unit)?
    ) {
        holder.instructionContainer.removeAllViews()
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
        row.findViewById<TextView>(R.id.input_name).visibility = View.GONE
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        magicButton.setOnClickListener { onMagicReq?.invoke("instruction") }

        val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
        val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

        richTextView.setRichText(initialValue) { variableRef ->
            PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
        }

        holder.instructionRichText = richTextView
        valueContainer.addView(richEditorLayout)
        holder.instructionContainer.addView(row)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder

        val provider = when {
            h.chipDashScope.isChecked -> "阿里云百炼"
            h.chipCustom.isChecked -> "自定义"
            else -> "智谱"
        }

        return mapOf(
            "provider" to provider,
            "base_url" to h.baseUrlEdit.text.toString(),
            "model" to h.modelEdit.text.toString(),
            "api_key" to h.apiKeyEdit.text.toString(),
            "instruction" to (h.instructionRichText?.getRawText() ?: ""),
            "max_steps" to h.stepsSlider.value.toDouble()
        )
    }
}