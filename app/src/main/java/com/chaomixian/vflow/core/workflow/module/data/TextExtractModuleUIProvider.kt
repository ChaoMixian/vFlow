// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/TextExtractModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * TextExtractModule 的自定义编辑器 ViewHolder
 */
class TextExtractViewHolder(
    view: View,
    val modeSpinner: Spinner,
    val inputsContainer: LinearLayout
) : CustomEditorViewHolder(view) {
    val inputViews = mutableMapOf<String, View>()
}

/**
 * TextExtractModule 的自定义编辑器
 */
class TextExtractModuleUIProvider : ModuleUIProvider {

    private val modeOptions = listOf("提取中间", "提取前缀", "提取后缀", "提取字符")

    override fun getHandledInputIds(): Set<String> {
        return setOf("text", "mode", "start", "end", "index", "count")
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return null
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
        val module = TextExtractModule()
        val allInputs = module.getInputs()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        // 模式选择器
        val modeLabel = TextView(context).apply {
            text = "提取方式"
        }
        container.addView(modeLabel)

        val modeSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modeOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        container.addView(modeSpinner)

        // 输入框容器
        val inputsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        container.addView(inputsContainer)

        val holder = TextExtractViewHolder(container, modeSpinner, inputsContainer)

        // 动态更新输入框的函数
        val updateInputs = {
            val selectedMode = modeSpinner.selectedItem?.toString() ?: "提取中间"
            updateInputsVisibility(holder, selectedMode, allInputs, currentParameters, onMagicVariableRequested)
            onParametersChanged()
        }

        // 监听模式变化
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateInputs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 初始化模式选择
        val currentMode = currentParameters["mode"] as? String ?: "提取中间"
        val modeIndex = modeOptions.indexOf(currentMode)
        if (modeIndex != -1) {
            modeSpinner.setSelection(modeIndex)
        }

        // 初始化输入框
        updateInputs()

        return holder
    }

    private fun updateInputsVisibility(
        holder: TextExtractViewHolder,
        mode: String,
        allInputs: List<InputDefinition>,
        currentParameters: Map<String, Any?>,
        onMagicVariableRequested: ((String) -> Unit)?
    ) {
        val context = holder.view.context
        val container = holder.inputsContainer
        val inputViews = holder.inputViews

        // 先保存 text 输入框的引用
        val textInput = inputViews["text"]

        // 清空容器
        container.removeAllViews()
        inputViews.clear()

        // 重新添加 text 输入框
        textInput?.let {
            container.addView(it)
            inputViews["text"] = it
        } ?: run {
            // 如果 text 输入框不存在（首次创建），创建它
            val textInputDef = allInputs.find { it.id == "text" }
            if (textInputDef != null) {
                val textView = createInputView(context, textInputDef, currentParameters[textInputDef.id], onMagicVariableRequested)
                container.addView(textView)
                inputViews["text"] = textView
            }
        }

        val inputsToShow = when (mode) {
            "提取中间" -> allInputs.filter { it.id == "start" || it.id == "end" }
            "提取前缀", "提取后缀" -> allInputs.filter { it.id == "count" }
            "提取字符" -> allInputs.filter { it.id == "index" || it.id == "count" }
            else -> emptyList()
        }

        inputsToShow.forEach { inputDef ->
            val inputView = createInputView(context, inputDef, currentParameters[inputDef.id], onMagicVariableRequested)
            container.addView(inputView)
            inputViews[inputDef.id] = inputView
        }
    }

    private fun createInputView(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        onMagicVariableRequested: ((String) -> Unit)?
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<android.widget.ImageButton>(R.id.button_magic_variable)

        magicButton.visibility = if (inputDef.acceptsMagicVariable) View.VISIBLE else View.GONE
        magicButton.setOnClickListener {
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()

        if (inputDef.acceptsMagicVariable && (currentValue as? String)?.isMagicVariable() == true) {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            pill.setOnClickListener {
                onMagicVariableRequested?.invoke(inputDef.id)
            }
            valueContainer.addView(pill)
        } else {
            val textInputLayout = TextInputLayout(context).apply {
                hint = inputDef.name
                val editText = TextInputEditText(context).apply {
                    setText(currentValue?.toString() ?: inputDef.defaultValue?.toString() ?: "")
                    this.inputType = if (inputDef.staticType == ParameterType.NUMBER) {
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

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as TextExtractViewHolder
        val parameters = mutableMapOf<String, Any?>()

        parameters["mode"] = h.modeSpinner.selectedItem.toString()

        h.inputViews.forEach { (id, view) ->
            val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container)
            if (valueContainer != null && valueContainer.childCount > 0) {
                val child = valueContainer.getChildAt(0)
                if (child is TextInputLayout) {
                    val text = child.editText?.text?.toString()
                    val inputDef = TextExtractModule().getInputs().find { it.id == id }
                    if (inputDef?.staticType == ParameterType.NUMBER) {
                        parameters[id] = text?.toDoubleOrNull() ?: 0.0
                    } else {
                        parameters[id] = text
                    }
                }
            }
        }
        return parameters
    }
}
