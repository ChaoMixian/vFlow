// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AgentModuleUIProvider : ModuleUIProvider {

    private val richTextUIProvider = RichTextUIProvider("instruction")

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val providerGroup: ChipGroup = view.findViewById(R.id.cg_provider)
        val chipOpenAI: Chip = view.findViewById(R.id.chip_openai)
        val chipDashScope: Chip = view.findViewById(R.id.chip_dashscope)
        val chipCustom: Chip = view.findViewById(R.id.chip_custom)

        val baseUrlEdit: TextInputEditText = view.findViewById(R.id.et_base_url)
        val modelEdit: TextInputEditText = view.findViewById(R.id.et_model)
        val apiKeyEdit: TextInputEditText = view.findViewById(R.id.et_api_key)

        val instructionContainer: FrameLayout = view.findViewById(R.id.container_instruction)

        // 绑定新的控件
        val btnSelectTools: Button = view.findViewById(R.id.btn_select_tools)
        val tvSelectedTools: TextView = view.findViewById(R.id.tv_selected_tools)

        val stepsSlider: Slider = view.findViewById(R.id.slider_max_steps)
        val stepsText: TextView = view.findViewById(R.id.tv_max_steps_value)

        var instructionRichText: RichTextView? = null
        var allSteps: List<ActionStep>? = null

        // 存储当前选中的模块 ID 列表
        var selectedToolIds: MutableList<String> = mutableListOf()
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "provider", "base_url", "model", "api_key", "instruction", "tools", "max_steps"
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

        // 恢复服务商配置
        val provider = currentParameters["provider"] as? String ?: "阿里云百炼"
        when (provider) {
            "阿里云百炼" -> holder.chipDashScope.isChecked = true
            "OpenAI" -> holder.chipOpenAI.isChecked = true
            else -> holder.chipCustom.isChecked = true
        }

        holder.baseUrlEdit.setText(currentParameters["base_url"] as? String ?: "https://dashscope.aliyuncs.com/compatible-mode/v1")
        holder.modelEdit.setText(currentParameters["model"] as? String ?: "glm-4.6")
        holder.apiKeyEdit.setText(currentParameters["api_key"] as? String ?: "")

        // 恢复 Instruction
        setupInstructionEditor(context, holder, currentParameters["instruction"] as? String ?: "", onMagicVariableRequested)

        // 恢复 Tools (列表)
        val savedTools = (currentParameters["tools"] as? List<*>)?.map { it.toString() }
            ?: AgentModule().defaultTools
        holder.selectedToolIds.clear()
        holder.selectedToolIds.addAll(savedTools)
        updateToolsSummary(holder)

        // 恢复 Max Steps
        val maxSteps = (currentParameters["max_steps"] as? Number)?.toFloat() ?: 10f
        holder.stepsSlider.value = maxSteps
        holder.stepsText.text = "${maxSteps.toInt()}"

        // 监听器
        holder.providerGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds[0]) {
                R.id.chip_openai -> {
                    holder.baseUrlEdit.setText("https://api.openai.com/v1")
                    holder.modelEdit.setText("gpt-4o")
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

        // 工具选择按钮点击事件
        holder.btnSelectTools.setOnClickListener {
            showToolSelectionDialog(context, holder, onParametersChanged)
        }

        holder.stepsSlider.addOnChangeListener { _, value, _ ->
            holder.stepsText.text = "${value.toInt()}"
            onParametersChanged()
        }

        return holder
    }

    /**
     * 显示多选对话框，列出所有可用的 ActionModule
     */
    private fun showToolSelectionDialog(context: Context, holder: ViewHolder, onParametersChanged: () -> Unit) {
        // 获取所有模块，并过滤掉不适合作为工具的模块
        val allModules = ModuleRegistry.getAllModules().filter {
            it.metadata.category != "触发器" &&
                    it.metadata.category != "逻辑控制" &&
                    it.metadata.category != "模板" &&
                    it.id != "vflow.ai.agent" // 避免递归调用自己
        }

        val moduleNames = allModules.map { "${it.metadata.name} (${it.metadata.category})" }.toTypedArray()
        val moduleIds = allModules.map { it.id }.toTypedArray()

        // 确定哪些是被选中的
        val checkedItems = BooleanArray(allModules.size) { i ->
            holder.selectedToolIds.contains(moduleIds[i])
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("选择 AI 可用工具")
            .setMultiChoiceItems(moduleNames, checkedItems) { _, which, isChecked ->
                val id = moduleIds[which]
                if (isChecked) {
                    if (!holder.selectedToolIds.contains(id)) holder.selectedToolIds.add(id)
                } else {
                    holder.selectedToolIds.remove(id)
                }
            }
            .setPositiveButton("确定") { _, _ ->
                updateToolsSummary(holder)
                onParametersChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateToolsSummary(holder: ViewHolder) {
        if (holder.selectedToolIds.isEmpty()) {
            holder.tvSelectedTools.text = "未选择任何工具"
            return
        }

        val names = holder.selectedToolIds.map { id ->
            ModuleRegistry.getModule(id)?.metadata?.name ?: id
        }
        holder.tvSelectedTools.text = "已选: " + names.joinToString(", ")
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
        val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
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
            else -> "OpenAI"
        }

        return mapOf(
            "provider" to provider,
            "base_url" to h.baseUrlEdit.text.toString(),
            "model" to h.modelEdit.text.toString(),
            "api_key" to h.apiKeyEdit.text.toString(),
            "instruction" to (h.instructionRichText?.getRawText() ?: ""),
            // 直接保存 ID 列表
            "tools" to h.selectedToolIds,
            "max_steps" to h.stepsSlider.value.toDouble()
        )
    }
}