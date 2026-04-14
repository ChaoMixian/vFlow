// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/InputTextModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.chaomixian.vflow.ui.workflow_editor.StandardControlFactory
import com.google.android.material.textfield.TextInputLayout

class InputTextModuleUIProvider : ModuleUIProvider {

    private val richTextUIProvider = RichTextUIProvider("text")

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val textContainer: ViewGroup = view.findViewById(R.id.container_text_input)
        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced)
        val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)
        val modeSpinner: TextInputLayout = view.findViewById(R.id.layout_mode)
        val actionAfterSpinner: TextInputLayout = view.findViewById(R.id.layout_action_after)

        var richTextView: RichTextView? = null
        var allSteps: List<ActionStep>? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf("text", "mode", "action_after", "show_advanced")

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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_input_text_editor, parent, false)
        val holder = ViewHolder(view)
        holder.allSteps = allSteps
        val module = InputTextModule()
        val modeInput = module.getInputs().first { it.id == "mode" }
        val modeValues = modeInput.options
        val modeLabels = modeInput.getLocalizedOptions(context)
        val actionInput = module.getInputs().first { it.id == "action_after" }
        val actionValues = actionInput.options
        val actionLabels = actionInput.getLocalizedOptions(context)

        // 设置富文本编辑器
        setupRichTextEditor(context, holder, currentParameters["text"] as? String ?: "", onMagicVariableRequested)

        // 设置模式下拉框
        val rawMode = currentParameters["mode"] as? String ?: InputTextModule.MODE_AUTO
        val currentMode = modeInput.normalizeEnumValue(rawMode) ?: rawMode
        StandardControlFactory.bindDropdown(
            textInputLayout = holder.modeSpinner,
            options = modeValues,
            selectedValue = currentMode,
            onItemSelectedCallback = {
                if (holder.modeSpinner.tag != it) {
                    holder.modeSpinner.tag = it
                    onParametersChanged()
                }
            },
            optionsStringRes = modeInput.optionsStringRes
        )
        holder.modeSpinner.tag = currentMode

        val rawAction = currentParameters["action_after"] as? String ?: InputTextModule.ACTION_NONE
        val currentAction = actionInput.normalizeEnumValue(rawAction) ?: rawAction
        StandardControlFactory.bindDropdown(
            textInputLayout = holder.actionAfterSpinner,
            options = actionValues,
            selectedValue = currentAction,
            onItemSelectedCallback = {
                if (holder.actionAfterSpinner.tag != it) {
                    holder.actionAfterSpinner.tag = it
                    onParametersChanged()
                }
            },
            optionsStringRes = actionInput.optionsStringRes
        )
        holder.actionAfterSpinner.tag = currentAction

        // 恢复展开状态
        val showAdvanced = currentParameters["show_advanced"] as? Boolean ?: false
        holder.advancedContainer.isVisible = showAdvanced
        holder.expandArrow.rotation = if (showAdvanced) 180f else 0f

        // 折叠/展开逻辑
        holder.advancedHeader.setOnClickListener {
            val isVisible = holder.advancedContainer.isVisible
            holder.advancedContainer.isVisible = !isVisible
            holder.expandArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
            onParametersChanged() // 触发保存 show_advanced 状态
        }

        return holder
    }

    private fun setupRichTextEditor(context: Context, holder: ViewHolder, initialValue: String, onMagicReq: ((String) -> Unit)?) {
        holder.textContainer.removeAllViews()

        // 创建带有魔法变量按钮的容器
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
        row.findViewById<TextView>(R.id.input_name).visibility = View.GONE
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        magicButton.setOnClickListener { onMagicReq?.invoke("text") }

        val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
        val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

        richTextView.setRichText(initialValue, holder.allSteps ?: emptyList())

        // 给 RichTextView 设置 tag，以便 ActionEditorSheet 能找到它进行变量插入
        richTextView.tag = "text"

        holder.richTextView = richTextView
        valueContainer.addView(richEditorLayout)
        holder.textContainer.addView(row)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val module = InputTextModule()
        return mapOf(
            "text" to (h.richTextView?.getRawText() ?: ""),
            "mode" to (StandardControlFactory.getDropdownValue(h.modeSpinner) ?: InputTextModule.MODE_AUTO),
            "action_after" to (StandardControlFactory.getDropdownValue(h.actionAfterSpinner) ?: InputTextModule.ACTION_NONE),
            "show_advanced" to h.advancedContainer.isVisible
        )
    }
}
