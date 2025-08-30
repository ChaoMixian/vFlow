// main/java/com/chaomixian/vflow/core/workflow/module/variable/VariableModule.kt

package com.chaomixian.vflow.modules.variable

import android.content.Context
import android.os.Parcelable
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
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.google.android.material.textfield.TextInputLayout
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// 1. 定义模块化的、可传递的变量类型
@Parcelize data class TextVariable(val value: String) : Parcelable
@Parcelize data class NumberVariable(val value: Double) : Parcelable
@Parcelize data class BooleanVariable(val value: Boolean) : Parcelable
@Parcelize data class ListVariable(val value: @RawValue List<Any?>) : Parcelable
@Parcelize data class DictionaryVariable(val value: @RawValue Map<String, Any?>) : Parcelable


/**
 * 设置变量模块
 * 职责：根据用户选择的类型，创建并输出一个强类型的变量。
 * 实现了自定义编辑器、自定义预览和动态输出接口。
 */
class SetVariableModule : ActionModule, ModuleWithCustomEditor, ModuleWithPreview {
    override val id = "vflow.variable.set"
    override val metadata = ActionMetadata("设置变量", "创建文本、数字、布尔值等变量", R.drawable.ic_variable, "变量")

    // 定义模块的静态参数结构和默认值
    override fun getParameters(): List<ParameterDefinition> = listOf(
        ParameterDefinition(
            id = "type",
            name = "变量类型",
            type = ParameterType.ENUM,
            defaultValue = "文本",
            options = listOf("文本", "数字", "布尔", "字典")
        ),
        ParameterDefinition("value", "值", ParameterType.ANY, defaultValue = "")
    )

    // 此模块没有输入连接点
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 静态输出列表为空，因为输出是动态的
    override fun getOutputs(): List<OutputDefinition> = emptyList()

    /**
     * 根据步骤参数动态提供唯一的输出类型。
     */
    override fun getDynamicOutputs(step: ActionStep): List<OutputDefinition> {
        val selectedType = step.parameters["type"] as? String
        return when (selectedType) {
            "文本" -> listOf(OutputDefinition("variable", "变量 (文本)", TextVariable::class.java))
            "数字" -> listOf(OutputDefinition("variable", "变量 (数字)", NumberVariable::class.java))
            "布尔" -> listOf(OutputDefinition("variable", "变量 (布尔)", BooleanVariable::class.java))
            "字典" -> listOf(OutputDefinition("variable", "变量 (字典)", DictionaryVariable::class.java))
            else -> emptyList()
        }
    }

    /**
     * 提供在工作流编辑器中显示的自定义预览视图。
     */
    override fun createPreviewView(context: Context, parent: ViewGroup, step: ActionStep): View? {
        val params = step.parameters
        val type = params["type"]?.toString() ?: "未知"
        val value = params["value"]

        val previewView = LayoutInflater.from(context)
            .inflate(R.layout.partial_variable_preview, parent, false)

        val typeTextView = previewView.findViewById<TextView>(R.id.text_view_variable_type)
        val valueTextView = previewView.findViewById<TextView>(R.id.text_view_variable_value)

        // --- 核心修改：移除 "类型：" 的前缀 ---
        typeTextView.text = type

        if (value != null && value.toString().isNotEmpty()) {
            valueTextView.text = value.toString()
            valueTextView.visibility = View.VISIBLE
        } else {
            valueTextView.visibility = View.GONE
        }

        return previewView
    }

    // --- ModuleWithCustomEditor 实现 ---

    class SetVariableEditorViewHolder(
        view: View,
        val typeSpinner: Spinner,
        val valueContainer: LinearLayout
    ) : CustomEditorViewHolder(view) {
        var valueInputView: View? = null
        var dictionaryAdapter: DictionaryKVAdapter? = null
    }

    override fun createEditorView(
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
        val typeOptions = getParameters().find { it.id == "type" }?.options ?: emptyList()
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

    override fun readParametersFromEditorView(holder: CustomEditorViewHolder): Map<String, Any?> {
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

    /**
     * 执行模块逻辑。
     */
    override suspend fun execute(context: ExecutionContext): ActionResult {
        val type = context.variables["type"] as? String ?: "文本"
        val value = context.variables["value"]

        val variable: Parcelable = when (type) {
            "数字" -> NumberVariable((value as? String)?.toDoubleOrNull() ?: (value as? Number ?: 0.0).toDouble())
            "布尔" -> BooleanVariable(value as? Boolean ?: false)
            "字典" -> DictionaryVariable(value as? Map<String, Any?> ?: emptyMap())
            else -> TextVariable(value?.toString() ?: "")
        }
        return ActionResult(true, mapOf("variable" to variable))
    }
}