// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleWithCustomEditor
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout

class ActionEditorSheet : BottomSheetDialogFragment() {

    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null

    // 用于存储通用输入控件或自定义UI的ViewHolder
    private val inputViews = mutableMapOf<String, View>()
    private var customEditorHolder: CustomEditorViewHolder? = null

    private val currentParameters = mutableMapOf<String, Any?>()

    companion object {
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String?
        ): ActionEditorSheet {
            return ActionEditorSheet().apply {
                arguments = Bundle().apply {
                    putString("moduleId", module.id)
                    putParcelable("existingStep", existingStep)
                    putString("focusedInputId", focusedInputId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = arguments?.getString("moduleId")
        module = moduleId?.let { com.chaomixian.vflow.core.module.ModuleRegistry.getModule(it) } ?: return dismiss()
        existingStep = arguments?.getParcelable("existingStep")
        currentParameters.putAll(existingStep?.parameters ?: module.getParameters().associate { it.id to it.defaultValue })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val title = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val paramsContainer = view.findViewById<LinearLayout>(R.id.container_action_params)
        val saveButton = view.findViewById<Button>(R.id.button_save)

        title.text = "编辑 ${module.metadata.name}"

        buildUi(paramsContainer)

        saveButton.setOnClickListener {
            readParametersFromUi()
            val newStep = existingStep?.copy(parameters = currentParameters)
                ?: ActionStep(moduleId = module.id, parameters = currentParameters)
            onSave?.invoke(newStep)
            dismiss()
        }
        return view
    }

    fun updateInputWithVariable(inputId: String, variableReference: String) {
        currentParameters[inputId] = variableReference
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
            buildUi(it) // 重新构建整个UI以显示"药丸"
        }
    }

    // --- 通用UI构建逻辑 ---
    private fun buildUi(container: LinearLayout) {
        container.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

        // 检查模块是否提供自定义UI
        if (module is ModuleWithCustomEditor) {
            val customModule = module as ModuleWithCustomEditor
            customEditorHolder = customModule.createEditorView(requireContext(), container, currentParameters) {
                // 当自定义UI内部状态改变时，立即从UI读取参数，确保数据同步
                readParametersFromUi()
            }
            container.addView(customEditorHolder!!.view)
        } else {
            // 如果没有，则根据 getParameters() 生成标准UI
            module.getParameters().forEach { paramDef ->
                val paramView = createViewForParameter(paramDef)
                container.addView(paramView)
                inputViews[paramDef.id] = paramView
            }
        }

        // 总是为 getInputs() 创建UI，用于魔法变量连接
        module.getInputs().forEach { inputDef ->
            val inputRow = createViewForInput(inputDef, container)
            container.addView(inputRow)
            inputViews[inputDef.id] = inputRow // 这里保存的是整行View
        }
    }

    // 创建标准参数的UI (用于没有自定义编辑器的模块)
    private fun createViewForParameter(paramDef: com.chaomixian.vflow.core.module.ParameterDefinition): View {
        val currentValue = currentParameters[paramDef.id]
        return when (paramDef.type) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                text = paramDef.name
                isChecked = currentValue as? Boolean ?: false
            }
            else -> TextInputLayout(requireContext()).apply { // 字符串、数字等
                hint = paramDef.name
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: "")
                    inputType = if (paramDef.type == ParameterType.NUMBER) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                }
                addView(editText)
            }
        }
    }

    // 创建输入参数（魔法变量连接点）的UI (通用逻辑)
    private fun createViewForInput(inputDef: InputDefinition, parent: ViewGroup): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, parent, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
        val currentValue = currentParameters[inputDef.id]

        magicButton.isVisible = inputDef.acceptsMagicVariable
        magicButton.setOnClickListener {
            readParametersFromUi()
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()
        if (currentValue is String && currentValue.startsWith("{{")) {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            valueContainer.addView(pill)
        } else {
            val textInputLayout = TextInputLayout(requireContext())
            val editText = EditText(context).apply {
                setText(currentValue?.toString() ?: "")
                hint = "输入值..."
            }
            textInputLayout.addView(editText)
            valueContainer.addView(textInputLayout)
        }
        return row
    }

    // --- 通用UI读取逻辑 ---
    private fun readParametersFromUi() {
        // 如果有自定义UI，从自定义UI读取
        if (module is ModuleWithCustomEditor && customEditorHolder != null) {
            val customParams = (module as ModuleWithCustomEditor).readParametersFromEditorView(customEditorHolder!!)
            currentParameters.putAll(customParams)
        } else {
            // 否则，从标准UI控件读取
            inputViews.forEach { (id, view) ->
                if (module.getParameters().any{it.id == id}) { // 只读取静态参数
                    val value = when (view) {
                        is SwitchCompat -> view.isChecked
                        is TextInputLayout -> view.editText?.text?.toString() ?: ""
                        else -> null
                    }
                    if (value != null) currentParameters[id] = value
                }
            }
        }

        // 总是从输入行中读取可能存在的静态值
        inputViews.forEach { (id, view) ->
            if (module.getInputs().any{it.id == id}) { // 只读取输入参数
                // 仅当当前值不是魔法变量引用时，才尝试从UI读取
                if ((currentParameters[id] as? String)?.startsWith("{{") != true) {
                    val valueContainer = view.findViewById<FrameLayout>(R.id.input_value_container)
                    val textInputLayout = valueContainer?.getChildAt(0) as? TextInputLayout
                    val text = textInputLayout?.editText?.text?.toString()
                    if (text != null) {
                        currentParameters[id] = text
                    }
                }
            }
        }
    }
}