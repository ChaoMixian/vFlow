// 文件: VariableModuleUIProvider.kt
// 描述: 为变量设置模块提供自定义UI。
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 为设置变量模块提供的自定义编辑器 ViewHolder。
 * 持有对类型选择 Spinner 和值输入区域容器的引用。
 */
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

/**
 * 为变量设置模块（如 SetVariableModule）提供自定义用户界面逻辑。
 * 负责创建和管理变量类型选择和对应值输入的编辑器界面。
 */
class VariableModuleUIProvider(
    private val typeOptions: List<String> // 可供选择的变量类型列表 (例如 "文本", "数字", "布尔", "字典")
) : ModuleUIProvider {

    /**
     * 返回此 UIProvider 处理的输入参数的 ID 集合。
     */
    override fun getHandledInputIds(): Set<String> {
        return setOf("type", "value") // 指明处理 "type" 和 "value" 这两个输入参数
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        val type = step.parameters["type"] as? String
        // 只为“文本”类型创建预览
        if (type != "文本") {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val previewView = inflater.inflate(R.layout.partial_rich_text_preview, parent, false)
        val textView = previewView.findViewById<TextView>(R.id.rich_text_preview_content)

        val rawText = step.parameters["value"]?.toString() ?: ""

        // 使用可以解析变量名称和颜色的工具方法
        val spannable = PillUtil.renderRichTextToSpannable(context, rawText, allSteps)
        textView.text = spannable

        return previewView
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup, // 父视图组
        currentParameters: Map<String, Any?>, // 当前已保存的参数值
        onParametersChanged: () -> Unit, // 参数发生变化时的回调
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        // 编辑器的主布局，垂直排列
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 类型选择的 Spinner
        val typeSpinner = Spinner(context)
        // 值输入区域的容器
        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 添加一些内边距
            setPadding(0, (16 * context.resources.displayMetrics.density).toInt(), 0, 0)
        }

        val holder = VariableEditorViewHolder(view, typeSpinner, valueContainer)
        holder.onMagicVariableRequested = onMagicVariableRequested // 存储回调
        holder.allSteps = allSteps // 存储步骤列表

        // 设置 Spinner 的适配器和选项
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, typeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter

        // 根据当前参数设置 Spinner 的初始选中项和对应的值输入视图
        val currentType = currentParameters["type"] as? String ?: typeOptions.first()
        val selectionIndex = typeOptions.indexOf(currentType)
        if (selectionIndex != -1) typeSpinner.setSelection(selectionIndex)

        // 根据当前类型更新值输入部分
        updateValueInputView(context, holder, currentType, currentParameters["value"])

        // Spinner 选项选中事件监听
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                val selectedType = typeOptions[position]
                val oldType = (holder.typeSpinner.tag as? String) ?: currentType // 获取之前的类型
                // 如果类型发生变化，则更新值输入视图并通知参数已更改
                if (selectedType != oldType) {
                    holder.typeSpinner.tag = selectedType // 保存新类型以备下次比较
                    updateValueInputView(context, holder, selectedType, null) // 类型改变，值输入视图重置，旧值不保留
                    onParametersChanged() // 通知外部参数已更新
                }
            }
            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
        holder.typeSpinner.tag = currentType // 初始化时保存当前类型

        // 将 Spinner 和值容器添加到主视图
        view.addView(typeSpinner)
        view.addView(valueContainer)

        return holder
    }

    /**
     * 从编辑器视图中读取用户输入的参数值。
     */
    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as VariableEditorViewHolder
        val selectedType = h.typeSpinner.selectedItem.toString()
        // 根据选中的类型，从对应的输入视图中获取值
        val value: Any? = when(selectedType) {
            "文本" -> {
                val row = h.valueInputView as? ViewGroup
                row?.findViewById<RichTextView>(R.id.rich_text_view)?.getRawText()
            }
            "字典" -> h.dictionaryAdapter?.getItemsAsMap() // 从字典适配器获取键值对 Map
            "列表" -> h.listAdapter?.getItems()
            "布尔" -> (h.valueInputView as? SwitchCompat)?.isChecked ?: false // 获取 Switch 的选中状态
            else -> { // 其他类型（文本、数字等）从 EditText 获取
                val textInputLayout = h.valueInputView as? TextInputLayout
                textInputLayout?.editText?.text?.toString() ?: ""
            }
        }
        return mapOf("type" to selectedType, "value" to value) // 返回包含类型和值的 Map
    }

    /**
     * 根据选择的变量类型动态更新值输入区域的视图。
     */
    private fun updateValueInputView(context: Context, holder: VariableEditorViewHolder, type: String, currentValue: Any?) {
        holder.valueContainer.removeAllViews() // 清空旧的值输入视图
        holder.dictionaryAdapter = null // 重置字典适配器（如果之前是字典类型）
        holder.listAdapter = null

        // 根据类型创建不同的输入视图
        val valueView: View = when (type) {
            "文本" -> {
                // 手动构建一个类似默认编辑器的行布局
                val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, holder.valueContainer, false)
                row.findViewById<TextView>(R.id.input_name).text = "值"

                val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
                val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
                magicButton.isVisible = true
                magicButton.setOnClickListener {
                    // [核心修复] 直接调用回调，ActionEditorSheet会在调用前回读最新值
                    holder.onMagicVariableRequested?.invoke("value")
                }

                val richEditorLayout = LayoutInflater.from(context).inflate(R.layout.rich_text_editor, valueContainer, false)
                val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)

                richTextView.setRichText(currentValue?.toString() ?: "") { variableRef ->
                    PillUtil.createPillDrawable(context, PillUtil.getDisplayNameForVariableReference(variableRef, holder.allSteps ?: emptyList()))
                }
                valueContainer.addView(richEditorLayout)
                row
            }
            "字典" -> {
                // 加载字典编辑器的布局
                val editorView = LayoutInflater.from(context).inflate(R.layout.partial_dictionary_editor, holder.valueContainer, false)
                val recyclerView = editorView.findViewById<RecyclerView>(R.id.recycler_view_dictionary)
                val addButton = editorView.findViewById<Button>(R.id.button_add_kv_pair)

                // 将 currentValue (如果是 Map) 转换为 List<Pair<String, String>> 以适配 DictionaryKVAdapter
                val currentMap = (currentValue as? Map<*, *>)
                    ?.mapNotNull { (key, value) ->
                        val kStr = key?.toString()
                        val vStr = value?.toString()
                        if (kStr != null) {
                            kStr to (vStr ?: "")
                        } else {
                            null // 如果键为 null，则过滤掉此项
                        }
                    }
                    ?.toMutableList()
                    ?: mutableListOf() // 如果 currentValue 不是 Map 或为空，则使用空列表

                // 在创建适配器时传入魔法变量回调
                val dictAdapter = DictionaryKVAdapter(currentMap) { key ->
                    if (key.isNotBlank()) {
                        holder.onMagicVariableRequested?.invoke("value.$key")
                    }
                }
                holder.dictionaryAdapter = dictAdapter // 保存适配器引用
                recyclerView.adapter = dictAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { dictAdapter.addItem() } // 添加按钮点击事件
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
                holder.listAdapter = listAdapter // 保存适配器引用
                recyclerView.adapter = listAdapter
                recyclerView.layoutManager = LinearLayoutManager(context)
                addButton.setOnClickListener { listAdapter.addItem() }
                editorView
            }
            "布尔" -> SwitchCompat(context).apply {
                text = "值" // 标签文本
                isChecked = (currentValue as? Boolean) ?: false // 设置初始选中状态
            }
            else -> TextInputLayout(context).apply { // 默认为文本或数字输入
                hint = "值" // 提示文本
                val editText = TextInputEditText(this.context)
                editText.setText(currentValue?.toString() ?: "") // 设置初始文本
                // 根据类型设置输入法类型
                editText.inputType = if (type == "数字") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
                addView(editText)
            }
        }
        holder.valueInputView = valueView // 保存当前值输入视图的引用
        holder.valueContainer.addView(valueView) // 将新的值输入视图添加到容器中
    }
}