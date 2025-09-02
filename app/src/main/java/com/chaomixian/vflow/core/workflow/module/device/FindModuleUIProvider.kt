package com.chaomixian.vflow.modules.device

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep

// ViewHolder 现在需要持有两个 Spinner
class FindTextEditorViewHolder(
    view: View,
    val modeSpinner: Spinner,
    val formatSpinner: Spinner
) : CustomEditorViewHolder(view)

// 核心修改：构造函数现在接收两个列表参数
class FindModuleUIProvider(
    private val matchModeOptions: List<String>,
    private val outputFormatOptions: List<String>
) : ModuleUIProvider {

    // 告诉编辑器，这两个参数的UI都由我接管
    override fun getHandledInputIds(): Set<String> {
        return setOf("matchMode", "outputFormat")
    }

    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep): View? {
        return null
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }

        // 创建“匹配模式”Spinner
        val modeLabel = TextView(context).apply { text = "匹配模式" }
        val modeSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, matchModeOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val currentMode = currentParameters["matchMode"] as? String ?: matchModeOptions.firstOrNull() ?: ""
        val modeSelectionIndex = matchModeOptions.indexOf(currentMode)
        if (modeSelectionIndex != -1) modeSpinner.setSelection(modeSelectionIndex)

        // 创建“输出格式”Spinner
        val formatLabel = TextView(context).apply {
            text = "输出格式"
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        val formatSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, outputFormatOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val currentFormat = currentParameters["outputFormat"] as? String ?: outputFormatOptions.firstOrNull() ?: ""
        val formatSelectionIndex = outputFormatOptions.indexOf(currentFormat)
        if (formatSelectionIndex != -1) formatSpinner.setSelection(formatSelectionIndex)

        container.addView(modeLabel)
        container.addView(modeSpinner)
        container.addView(formatLabel)
        container.addView(formatSpinner)

        return FindTextEditorViewHolder(container, modeSpinner, formatSpinner)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as FindTextEditorViewHolder
        val mode = h.modeSpinner.selectedItem.toString()
        val format = h.formatSpinner.selectedItem.toString()
        return mapOf("matchMode" to mode, "outputFormat" to format)
    }
}