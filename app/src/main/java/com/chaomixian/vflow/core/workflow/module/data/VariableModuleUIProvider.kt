// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/VariableModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.chaomixian.vflow.ui.workflow_editor.ListItemAdapter
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextView
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import com.chaomixian.vflow.ui.workflow_editor.VariableValueUIProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// ViewHolder 和接口定义保持不变
class VariableEditorViewHolder(
    view: View, // ViewHolder 的根视图
    val typeSpinner: Spinner, // 用于选择变量类型的 Spinner
    val valueContainer: LinearLayout // 用于动态添加值输入视图的容器
) : CustomEditorViewHolder(view) {
    var valueInputView: View? = null // 当前值输入视图的引用
    var dictionaryAdapter: DictionaryKVAdapter? = null // 如果类型是字典，则为该字典的适配器
    var listAdapter: ListItemAdapter? = null
    var coordXInput: TextInputEditText? = null // 坐标类型的 X 输入框
    var coordYInput: TextInputEditText? = null // 坐标类型的 Y 输入框
    var coordXVariable: String? = null // 坐标类型的 X 变量引用
    var coordYVariable: String? = null // 坐标类型的 Y 变量引用
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null // 用于存储魔法变量请求的回调
    var allSteps: List<ActionStep>? = null // 存储工作流步骤
}

class VariableModuleUIProvider(
    private val typeOptions: List<String>
) : ModuleUIProvider {

    // 实例化内部需要用到的 UI 提供者
    private val richTextUIProvider = RichTextUIProvider("value")
    private val variableValueUIProvider = VariableValueUIProvider()

    override fun getHandledInputIds(): Set<String> {
        return setOf("type", "value")
    }

    /**
     * 实现 createPreview 方法。
     * 这个方法现在是预览逻辑的入口点，它会根据变量类型进行分发。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val type = step.parameters["type"] as? String
        // 根据变量类型，委托给相应的 UIProvider 来创建预览
        return when (type) {
            "文本" -> richTextUIProvider.createPreview(context, parent, step, allSteps, onStartActivityForResult)
            "字典", "列表" -> variableValueUIProvider.createPreview(context, parent, step, allSteps, onStartActivityForResult)
            else -> null // 其他类型（如数字、布尔）不需要自定义预览，返回null即可
        }
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
        // 添加 Padding 以统一视觉风格
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padding, padding, padding, padding)
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

    // readFromEditor 的代码保持不变
    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as VariableEditorViewHolder
        val selectedType = h.typeSpinner.selectedItem.toString()

        // 检查 valueContainer 的 tag，如果是变量引用字符串，直接返回该变量
        val variableRef = h.valueContainer.tag as? String
        if (!variableRef.isNullOrBlank()) {
            return mapOf("type" to selectedType, "value" to variableRef)
        }

        val value: Any? = when(selectedType) {
            "文本" -> {
                val row = h.valueInputView as? ViewGroup
                row?.findViewById<RichTextView>(R.id.rich_text_view)?.getRawText()
            }
            "字典" -> h.dictionaryAdapter?.getItemsAsMap()
            "列表" -> h.listAdapter?.getItems()
            "坐标" -> {
                // 优先使用 coordXVariable/coordYVariable（当显示变量药丸时）
                val xText = h.coordXVariable ?: h.coordXInput?.text?.toString()?.trim() ?: ""
                val yText = h.coordYVariable ?: h.coordYInput?.text?.toString()?.trim() ?: ""

                val x = if (xText.isBlank()) 0 else {
                    // 如果是变量引用，保持原样
                    if (xText.isMagicVariable() || xText.isNamedVariable()) {
                        xText
                    } else {
                        // 否则尝试转换为数字
                        xText.toIntOrNull() ?: 0
                    }
                }

                val y = if (yText.isBlank()) 0 else {
                    // 如果是变量引用，保持原样
                    if (yText.isMagicVariable() || yText.isNamedVariable()) {
                        yText
                    } else {
                        // 否则尝试转换为数字
                        yText.toIntOrNull() ?: 0
                    }
                }

                mapOf("x" to x, "y" to y)
            }
            "布尔" -> (h.valueInputView as? SwitchCompat)?.isChecked ?: false
            "数字" -> {
                // 检查是否是变量引用（通过容器 tag）
                val variableRef = h.valueContainer.tag as? String
                if (!variableRef.isNullOrBlank()) {
                    variableRef
                } else {
                    // 从输入框读取 - 遍历 input_value_container 的子视图找 TextInputLayout
                    val valueContainer = h.valueInputView?.findViewById<ViewGroup>(R.id.input_value_container)
                    val textInputLayout = valueContainer?.children?.find { it is TextInputLayout } as? TextInputLayout
                    textInputLayout?.editText?.text?.toString() ?: ""
                }
            }
            else -> {
                val textInputLayout = h.valueInputView as? TextInputLayout
                textInputLayout?.editText?.text?.toString() ?: ""
            }
        }
        return mapOf("type" to selectedType, "value" to value)
    }


    private fun updateValueInputView(context: Context, holder: VariableEditorViewHolder, type: String, currentValue: Any?) {
        holder.valueContainer.removeAllViews()
        holder.valueContainer.tag = null
        holder.dictionaryAdapter = null
        holder.listAdapter = null
        holder.coordXInput = null
        holder.coordYInput = null
        holder.coordXVariable = null
        holder.coordYVariable = null

        // 检查是否为整个列表/字典/坐标赋值了变量
        if (type != "文本" && currentValue is String && (currentValue.isMagicVariable() || currentValue.isNamedVariable())) {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, holder.valueContainer, false)
            val pillText = pill.findViewById<TextView>(R.id.pill_text)
            pillText.text = PillRenderer.getDisplayNameForVariableReference(currentValue, holder.allSteps ?: emptyList(), context)

            // 允许点击药丸重新选择（包括“清除”以恢复列表编辑）
            pill.setOnClickListener {
                holder.onMagicVariableRequested?.invoke("value")
            }

            holder.valueContainer.addView(pill)
            holder.valueContainer.tag = currentValue // 标记当前显示的是变量
            return
        }

        val valueView: View = when (type) {
            "文本" -> {
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, holder.valueContainer, false)
                row.findViewById<TextView>(R.id.input_name).text = "值"

                val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
                magicButton.isVisible = true
                magicButton.setOnClickListener {
                    holder.onMagicVariableRequested?.invoke("value")
                }

                val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
                val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

                richTextView.setRichText(currentValue?.toString() ?: "", holder.allSteps ?: emptyList())
                valueContainer.addView(richEditorLayout)
                row
            }
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

                val dictAdapter = DictionaryKVAdapter(currentMap, holder.allSteps) { key ->
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

                val listAdapter = ListItemAdapter(currentList, holder.allSteps) { position ->
                    holder.onMagicVariableRequested?.invoke("value.$position")
                }
                holder.listAdapter = listAdapter
                recyclerView.adapter = listAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { listAdapter.addItem() }
                editorView
            }
            "坐标" -> {
                val coordContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, 0)
                }

                // 解析当前坐标值
                var currentX = ""
                var currentY = ""
                when (currentValue) {
                    is Map<*, *> -> {
                        currentX = currentValue["x"]?.toString() ?: "0"
                        currentY = currentValue["y"]?.toString() ?: "0"
                    }
                    is List<*> -> {
                        currentX = currentValue.getOrNull(0)?.toString() ?: "0"
                        currentY = currentValue.getOrNull(1)?.toString() ?: "0"
                    }
                }

                // 检查是否使用了变量引用
                val xIsVariable = currentX.isMagicVariable() || currentX.isNamedVariable()
                val yIsVariable = currentY.isMagicVariable() || currentY.isNamedVariable()

                // X 坐标输入框
                val xRow = LayoutInflater.from(context).inflate(R.layout.row_editor_input, coordContainer, false)
                xRow.findViewById<TextView>(R.id.input_name).text = "X 坐标"

                val xValueContainer = xRow.findViewById<ViewGroup>(R.id.input_value_container)
                val xMagicButton = xRow.findViewById<ImageButton>(R.id.button_magic_variable)
                xMagicButton.isVisible = true
                xMagicButton.setOnClickListener {
                    holder.onMagicVariableRequested?.invoke("value.x")
                }

                if (xIsVariable) {
                    // 显示变量药丸
                    val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, xValueContainer, false)
                    val pillText = pill.findViewById<TextView>(R.id.pill_text)
                    pillText.text = PillRenderer.getDisplayNameForVariableReference(currentX, holder.allSteps ?: emptyList(), context)
                    pill.setOnClickListener {
                        holder.onMagicVariableRequested?.invoke("value.x")
                    }
                    xValueContainer.addView(pill)
                    holder.coordXVariable = currentX
                } else {
                    // 显示普通输入框
                    val xInput = TextInputEditText(context).apply {
                        setText(currentX)
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                    val xInputLayout = TextInputLayout(context).apply {
                        addView(xInput)
                    }
                    xValueContainer.addView(xInputLayout)
                    holder.coordXInput = xInput
                }

                coordContainer.addView(xRow)

                // Y 坐标输入框
                val yRow = LayoutInflater.from(context).inflate(R.layout.row_editor_input, coordContainer, false)
                yRow.findViewById<TextView>(R.id.input_name).text = "Y 坐标"

                val yValueContainer = yRow.findViewById<ViewGroup>(R.id.input_value_container)
                val yMagicButton = yRow.findViewById<ImageButton>(R.id.button_magic_variable)
                yMagicButton.isVisible = true
                yMagicButton.setOnClickListener {
                    holder.onMagicVariableRequested?.invoke("value.y")
                }

                if (yIsVariable) {
                    // 显示变量药丸
                    val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, yValueContainer, false)
                    val pillText = pill.findViewById<TextView>(R.id.pill_text)
                    pillText.text = PillRenderer.getDisplayNameForVariableReference(currentY, holder.allSteps ?: emptyList(), context)
                    pill.setOnClickListener {
                        holder.onMagicVariableRequested?.invoke("value.y")
                    }
                    yValueContainer.addView(pill)
                    holder.coordYVariable = currentY
                } else {
                    // 显示普通输入框
                    val yInput = TextInputEditText(context).apply {
                        setText(currentY)
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                    val yInputLayout = TextInputLayout(context).apply {
                        addView(yInput)
                    }
                    yValueContainer.addView(yInputLayout)
                    holder.coordYInput = yInput
                }

                coordContainer.addView(yRow)
                coordContainer
            }
            "布尔" -> SwitchCompat(context).apply {
                text = "值"
                isChecked = (currentValue as? Boolean) ?: false
            }
            "数字" -> {
                // 数字类型也使用 row_editor_input 布局，支持变量选择
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, holder.valueContainer, false)
                row.findViewById<TextView>(R.id.input_name).text = "值"

                val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
                magicButton.isVisible = true
                magicButton.setOnClickListener {
                    holder.onMagicVariableRequested?.invoke("value")
                }

                // 清除默认的 EditText，添加数字输入框
                valueContainer.removeAllViews()
                val editText = TextInputEditText(context).apply {
                    setText(currentValue?.toString() ?: "")
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                }
                val inputLayout = TextInputLayout(context).apply {
                    addView(editText)
                }
                valueContainer.addView(inputLayout)
                row
            }
            else -> TextInputLayout(context).apply {
                hint = "值"
                val editText = TextInputEditText(this.context)
                editText.setText(currentValue?.toString() ?: "")
                addView(editText)
            }
        }
        holder.valueInputView = valueView
        holder.valueContainer.addView(valueView)
    }
}