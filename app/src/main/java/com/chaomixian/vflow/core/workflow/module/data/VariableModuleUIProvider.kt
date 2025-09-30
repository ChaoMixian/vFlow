// 文件: VariableModuleUIProvider.kt
// 描述: 为变量设置模块提供自定义UI。
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.chaomixian.vflow.ui.workflow_editor.ListItemAdapter
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer // [新增] 导入 PillRenderer
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// ... (ViewHolder 和接口定义保持不变) ...
class VariableEditorViewHolder(
    view: View, // ViewHolder 的根视图
    val typeSpinner: Spinner, // 用于选择变量类型的 Spinner
    val valueContainer: LinearLayout // 用于动态添加值输入视图的容器
) : CustomEditorViewHolder(view) {
    var valueInputView: View? = null // 当前值输入视图的引用
    var dictionaryAdapter: DictionaryKVAdapter? = null // 如果类型是字典，则为该字典的适配器
    var listAdapter: ListItemAdapter? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null // 用于存储魔法变量请求的回调
    var allSteps: List<ActionStep>? = null // 存储工作流步骤
}

class VariableModuleUIProvider(
    private val typeOptions: List<String>
) : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> {
        return setOf("type", "value")
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val type = step.parameters["type"] as? String
        if (type != "文本") {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val rawText = step.parameters["value"]?.toString() ?: ""

        // [修正] 使用 PillRenderer.renderRichTextToSpannable
        val spannable = PillRenderer.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        // ... (createEditor 的前半部分代码保持不变) ...
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

        val holder = VariableEditorViewHolder(view, typeSpinner, valueContainer)
        holder.onMagicVariableRequested = onMagicVariableRequested
        holder.allSteps = allSteps

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, typeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter

        val currentType = currentParameters["type"] as? String ?: typeOptions.first()
        val selectionIndex = typeOptions.indexOf(currentType)
        if (selectionIndex != -1) typeSpinner.setSelection(selectionIndex)

        updateValueInputView(context, holder, currentType, currentParameters["value"])

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                val selectedType = typeOptions[position]
                val oldType = (holder.typeSpinner.tag as? String) ?: currentType
                if (selectedType != oldType) {
                    holder.typeSpinner.tag = selectedType
                    updateValueInputView(context, holder, selectedType, null)
                    onParametersChanged()
                }
            }
            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
        holder.typeSpinner.tag = currentType

        view.addView(typeSpinner)
        view.addView(valueContainer)

        return holder
    }

    // ... (readFromEditor 保持不变) ...
    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as VariableEditorViewHolder
        val selectedType = h.typeSpinner.selectedItem.toString()
        val value: Any? = when(selectedType) {
            "文本" -> {
                val row = h.valueInputView as? ViewGroup
                row?.findViewById<RichTextView>(R.id.rich_text_view)?.getRawText()
            }
            "字典" -> h.dictionaryAdapter?.getItemsAsMap()
            "列表" -> h.listAdapter?.getItems()
            "布尔" -> (h.valueInputView as? SwitchCompat)?.isChecked ?: false
            else -> {
                val textInputLayout = h.valueInputView as? TextInputLayout
                textInputLayout?.editText?.text?.toString() ?: ""
            }
        }
        return mapOf("type" to selectedType, "value" to value)
    }


    private fun updateValueInputView(context: Context, holder: VariableEditorViewHolder, type: String, currentValue: Any?) {
        holder.valueContainer.removeAllViews()
        holder.dictionaryAdapter = null
        holder.listAdapter = null

        val valueView: View = when (type) {
            "文本" -> {
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, holder.valueContainer, false)
                row.findViewById<TextView>(R.id.input_name).text = "值"

                val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
                magicButton.isVisible = true
                magicButton.setOnClickListener {
                    holder.onMagicVariableRequested?.invoke("value")
                }

                val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
                val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

                richTextView.setRichText(currentValue?.toString() ?: "") { variableRef ->
                    // [修正] 使用 PillRenderer.getDisplayNameForVariableReference
                    PillUtil.createPillDrawable(context, PillRenderer.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
                }
                valueContainer.addView(richEditorLayout)
                row
            }
            // ... (其他 "字典", "列表", "布尔" 等 case 保持不变) ...
            "字典" -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.valueContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)

                val currentMap = (currentValue as? Map<*, *>)
                    ?.mapNotNull { (key, value) ->
                        val kStr = key?.toString()
                        val vStr = value?.toString()
                        if (kStr != null) {
                            kStr to (vStr ?: "")
                        } else {
                            null
                        }
                    }
                    ?.toMutableList()
                    ?: mutableListOf()

                val dictAdapter = DictionaryKVAdapter(currentMap) { key ->
                    if (key.isNotBlank()) {
                        holder.onMagicVariableRequested?.invoke("value.$key")
                    }
                }
                holder.dictionaryAdapter = dictAdapter
                recyclerView.adapter = dictAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { dictAdapter.addItem() }
                editorView
            }
            "列表" -> {
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_list_editor, holder.valueContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_list)
                val addButton = editorView.findViewById<Button>(R.id.button_add_list_item)

                val currentList = (currentValue as? List<*>)
                    ?.map { it?.toString() ?: "" }
                    ?.toMutableList()
                    ?: mutableListOf()

                val listAdapter = ListItemAdapter(currentList) { position ->
                    holder.onMagicVariableRequested?.invoke("value.$position")
                }
                holder.listAdapter = listAdapter
                recyclerView.adapter = listAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { listAdapter.addItem() }
                editorView
            }
            "布尔" -> SwitchCompat(context).apply {
                text = "值"
                isChecked = (currentValue as? Boolean) ?: false
            }
            else -> TextInputLayout(context).apply {
                hint = "值"
                val editText = TextInputEditText(this.context)
                editText.setText(currentValue?.toString() ?: "")
                editText.inputType = if (type == "数字") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
                addView(editText)
            }
        }
        holder.valueInputView = valueView
        holder.valueContainer.addView(valueView)
    }
}