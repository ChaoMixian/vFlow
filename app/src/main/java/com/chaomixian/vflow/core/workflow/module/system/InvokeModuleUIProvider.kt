// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/InvokeModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter

class InvokeModuleUIProvider : ModuleUIProvider {
    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val extrasRecyclerView: RecyclerView = view.findViewById(R.id.recycler_view_dictionary)
        val extrasAddButton: Button = view.findViewById(R.id.button_add_kv_pair)
        var extrasAdapter: DictionaryKVAdapter? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf("extras")

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
        val view = createExtrasEditorView(context, parent)
        val holder = ViewHolder(view)
        val currentExtras = (currentParameters["extras"] as? Map<*, *>)
            ?.map { it.key.toString() to it.value.toString() }
            ?.toMutableList() ?: mutableListOf()

        holder.extrasAdapter = DictionaryKVAdapter(currentExtras, allSteps) { key ->
            if (key.isNotBlank()) onMagicVariableRequested?.invoke("extras.$key")
        }
        holder.extrasRecyclerView.layoutManager = LinearLayoutManager(context)
        holder.extrasRecyclerView.adapter = holder.extrasAdapter
        holder.extrasAddButton.setOnClickListener {
            holder.extrasAdapter?.addItem()
            onParametersChanged()
        }

        return holder
    }

    private fun createExtrasEditorView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )

            addView(TextView(context).apply {
                text = context.getString(R.string.label_extended_parameters_with_extras)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.text_auto_infer_type)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, parent, false))
        }
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "extras" to (h.extrasAdapter?.getItemsAsMap() ?: emptyMap<String, String>())
        )
    }
}
