// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/InputTextModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import com.chaomixian.vflow.ui.workflow_editor.RichTextView

class InputTextModuleUIProvider : ModuleUIProvider {

    private val richTextUIProvider = RichTextUIProvider("text")

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val textContainer: ViewGroup = view.findViewById(R.id.container_text_input)
        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced)
        val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)
        val modeSpinner: Spinner = view.findViewById(R.id.spinner_mode)

        var richTextView: RichTextView? = null
        var allSteps: List<ActionStep>? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf("text", "mode", "show_advanced")

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

        // 设置富文本编辑器
        setupRichTextEditor(context, holder, currentParameters["text"] as? String ?: "", onMagicVariableRequested)

        // 设置模式 Spinner
        val modes = listOf("自动", "无障碍", "Shell")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.modeSpinner.adapter = adapter

        val currentMode = currentParameters["mode"] as? String ?: "自动"
        holder.modeSpinner.setSelection(modes.indexOf(currentMode).coerceAtLeast(0))

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

        holder.modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (holder.modeSpinner.tag != position) {
                    holder.modeSpinner.tag = position
                    onParametersChanged()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        holder.modeSpinner.tag = holder.modeSpinner.selectedItemPosition

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

        richTextView.setRichText(initialValue) { variableRef ->
            PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
        }

        // 给 RichTextView 设置 tag，以便 ActionEditorSheet 能找到它进行变量插入
        richTextView.tag = "rich_text_view_value"

        holder.richTextView = richTextView
        valueContainer.addView(richEditorLayout)
        holder.textContainer.addView(row)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "text" to (h.richTextView?.getRawText() ?: ""),
            "mode" to h.modeSpinner.selectedItem.toString(),
            "show_advanced" to h.advancedContainer.isVisible
        )
    }
}