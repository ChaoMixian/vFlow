// main/java/com/chaomixian/vflow/core/workflow/module/device/FindModuleUIProvider.kt

package com.chaomixian.vflow.modules.device

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep

// ViewHolder 现在只需要持有 Spinner
class FindTextEditorViewHolder(
    view: View,
    val modeSpinner: Spinner
) : CustomEditorViewHolder(view)

class FindModuleUIProvider(
    private val matchModeOptions: List<String>
) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> {
        // 声明此UI提供者只处理 "matchMode" 这一个输入
        return setOf("matchMode")
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

        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, matchModeOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val currentMode = currentParameters["matchMode"] as? String ?: matchModeOptions.firstOrNull() ?: ""
        val selectionIndex = matchModeOptions.indexOf(currentMode)
        if (selectionIndex != -1) spinner.setSelection(selectionIndex)

        container.addView(spinner)

        return FindTextEditorViewHolder(container, spinner)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as FindTextEditorViewHolder
        val mode = h.modeSpinner.selectedItem.toString()
        return mapOf("matchMode" to mode)
    }
}