// 文件: main/java/com/chaomixian/vflow/core/workflow/module/variable/VariableModuleUIProvider.kt

package com.chaomixian.vflow.modules.variable

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.google.android.material.textfield.TextInputLayout

class SetVariableEditorViewHolder(
    view: View,
    val typeSpinner: Spinner,
    val valueContainer: LinearLayout
) : CustomEditorViewHolder(view) {
    var valueInputView: View? = null
    var dictionaryAdapter: DictionaryKVAdapter? = null
}

class VariableModuleUIProvider(
    private val typeOptions: List<String>
) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> {
        return setOf("type", "value")
    }

    /**
     * --- 核心修复 ---
     * 返回 null 来禁用自定义预览，并强制 ActionStepAdapter 回退到使用模块的 getSummary() 方法。
     * 这确保了UI的一致性。
     */
    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep): View? {
        return null
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val typeSpinner = Spinner(context)
        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (16 * context.resources.displayMetrics.density).toInt(), 0, 0)
        }

        val holder = SetVariableEditorViewHolder(view, typeSpinner, valueContainer)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, typeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter

        val currentType = currentParameters["type"] as? String ?: typeOptions.first()
        val selectionIndex = typeOptions.indexOf(currentType)
        if (selectionIndex != -1) typeSpinner.setSelection(selectionIndex)

        updateValueInputView(context, holder, currentType, currentParameters["value"])

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val selectedType = typeOptions[position]
                val oldType = (holder.typeSpinner.tag as? String) ?: currentType
                if (selectedType != oldType) {
                    holder.typeSpinner.tag = selectedType
                    updateValueInputView(context, holder, selectedType, null)
                    onParametersChanged()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        holder.typeSpinner.tag = currentType

        view.addView(typeSpinner)
        view.addView(valueContainer)

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as SetVariableEditorViewHolder
        val selectedType = h.typeSpinner.selectedItem.toString()
        val value: Any? = when(selectedType) {
            "字典" -> h.dictionaryAdapter?.getItemsAsMap()
            "布尔" -> (h.valueInputView as? SwitchCompat)?.isChecked ?: false
            else -> {
                val textInputLayout = h.valueInputView as? TextInputLayout
                textInputLayout?.editText?.text?.toString() ?: ""
            }
        }
        return mapOf("type" to selectedType, "value" to value)
    }

    private fun updateValueInputView(context: Context, holder: SetVariableEditorViewHolder, type: String, currentValue: Any?) {
        holder.valueContainer.removeAllViews()
        holder.dictionaryAdapter = null

        val valueView = when (type) {
            "字典" -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.valueContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)
                val currentMap = (currentValue as? Map<*, *>)
                    ?.map { it.key.toString() to it.value.toString() }
                    ?.toMutableList()
                    ?: mutableListOf()

                val dictAdapter = DictionaryKVAdapter(currentMap)
                holder.dictionaryAdapter = dictAdapter
                recyclerView.adapter = dictAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { dictAdapter.addItem() }
                editorView
            }
            "布尔" -> SwitchCompat(context).apply {
                text = "值"
                isChecked = (currentValue as? Boolean) ?: false
            }
            else -> TextInputLayout(context).apply {
                hint = "值"
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: "")
                    inputType = if (type == "数字") InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                }
                addView(editText)
            }
        }
        holder.valueInputView = valueView
        holder.valueContainer.addView(valueView)
    }
}