// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextProcessingModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * TextProcessingModule 的自定义编辑器 ViewHolder。
 * @param view 根视图。
 * @param operationSpinner 用于选择操作类型的 Spinner。
 * @param inputsContainer 用于动态添加参数输入视图的容器。
 * @param onMagicVariableRequested 请求魔法变量的回调。
 */
class TextProcessingViewHolder(
    view: View,
    val operationSpinner: Spinner,
    val inputsContainer: LinearLayout,
    val onMagicVariableRequested: ((inputId: String) -> Unit)?
) : CustomEditorViewHolder(view) {
    // 存储动态创建的输入视图，以便后续读取数据
    val inputViews = mutableMapOf<String, View>()
}

/**
 * TextProcessingModule 的 UIProvider 实现。
 */
class TextProcessingModuleUIProvider : ModuleUIProvider {

    /**
     * 声明此 UIProvider 将处理模块的所有输入参数。
     */
    override fun getHandledInputIds(): Set<String> {
        return setOf(
            "operation", "join_prefix", "join_list", "join_delimiter", "join_suffix",
            "source_text", "split_delimiter", "replace_from", "replace_to",
            "regex_pattern", "regex_group"
        )
    }

    /**
     * 不提供自定义预览，回退到模块的 getSummary 方法以保持样式统一。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return null
    }

    /**
     * 创建自定义编辑器界面。
     */
    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        // 创建垂直布局的根容器
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val allInputs = TextProcessingModule().getInputs()
        val operationOptions = allInputs.find { it.id == "operation" }?.options ?: emptyList()

        // 创建操作类型选择 Spinner
        val operationSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, operationOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        // 创建用于放置动态输入框的容器
        val inputsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (16 * context.resources.displayMetrics.density).toInt(), 0, 0)
        }

        container.addView(operationSpinner)
        container.addView(inputsContainer)

        val holder = TextProcessingViewHolder(container, operationSpinner, inputsContainer, onMagicVariableRequested)

        // 监听操作选择变化，动态更新UI
        operationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedOperation = operationOptions[position]
                updateInputsVisibility(holder, selectedOperation, allInputs, currentParameters)
                onParametersChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 初始化UI
        val currentOperation = currentParameters["operation"] as? String ?: operationOptions.first()
        val selectionIndex = operationOptions.indexOf(currentOperation)
        if (selectionIndex != -1) {
            operationSpinner.setSelection(selectionIndex)
        }
        updateInputsVisibility(holder, currentOperation, allInputs, currentParameters)

        return holder
    }

    /**
     * 根据选择的操作，动态更新显示的输入框。
     */
    private fun updateInputsVisibility(
        holder: TextProcessingViewHolder,
        operation: String,
        allInputs: List<InputDefinition>,
        currentParameters: Map<String, Any?>
    ) {
        holder.inputsContainer.removeAllViews()
        holder.inputViews.clear()

        val inputsToShow = when (operation) {
            "拼接" -> allInputs.filter { it.id.startsWith("join_") }
            "分割" -> allInputs.filter { it.id == "source_text" || it.id == "split_delimiter" }
            "替换" -> allInputs.filter { it.id == "source_text" || it.id.startsWith("replace_") }
            "正则提取" -> allInputs.filter { it.id == "source_text" || it.id.startsWith("regex_") }
            else -> emptyList()
        }

        inputsToShow.forEach { inputDef ->
            // [修复] 使用 holder.view.context 替代 holder.itemView.context
            val inputView = createInputView(holder.view.context, inputDef, currentParameters[inputDef.id], holder.onMagicVariableRequested)
            holder.inputsContainer.addView(inputView)
            holder.inputViews[inputDef.id] = inputView
        }
    }

    /**
     * 为单个输入参数创建统一风格的视图（标签、输入框、魔法变量按钮）。
     */
    private fun createInputView(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        onMagicVariableRequested: ((inputId: String) -> Unit)?
    ): View {
        // 复用通用的 row_editor_input 布局以保持UI一致性
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        magicButton.isVisible = inputDef.acceptsMagicVariable
        magicButton.setOnClickListener {
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()

        // 如果已连接魔法变量，显示药丸
        if (inputDef.acceptsMagicVariable && (currentValue as? String).isMagicVariable()) {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            pill.setOnClickListener {
                onMagicVariableRequested?.invoke(inputDef.id)
            }
            valueContainer.addView(pill)
        } else {
            // 否则，显示标准的输入框
            val textInputLayout = TextInputLayout(context).apply {
                hint = inputDef.name
                val editText = TextInputEditText(context).apply {
                    setText(currentValue?.toString() ?: inputDef.defaultValue?.toString() ?: "")
                    inputType = if (inputDef.staticType == ParameterType.NUMBER) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                }
                addView(editText)
            }
            valueContainer.addView(textInputLayout)
        }
        return row
    }

    /**
     * 从编辑器视图中读取参数。
     */
    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as TextProcessingViewHolder
        val parameters = mutableMapOf<String, Any?>()

        parameters["operation"] = h.operationSpinner.selectedItem.toString()

        h.inputViews.forEach { (id, view) ->
            val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container)
            if (valueContainer != null && valueContainer.childCount > 0) {
                val child = valueContainer.getChildAt(0)
                // 从 TextInputLayout 中提取文本
                if (child is TextInputLayout) {
                    val text = child.editText?.text?.toString()
                    // 尝试转换为数字（如果需要）
                    val inputDef = TextProcessingModule().getInputs().find { it.id == id }
                    if (inputDef?.staticType == ParameterType.NUMBER) {
                        parameters[id] = text?.toDoubleOrNull() ?: 0.0
                    } else {
                        parameters[id] = text
                    }
                }
                // 注意：如果已连接魔法变量，这里不会读取到值，它的值在 ActionEditorSheet 中被直接设置
            }
        }
        return parameters
    }
}