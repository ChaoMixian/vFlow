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
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ActionEditorSheet : BottomSheetDialogFragment() {

    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null

    private val inputViews = mutableMapOf<String, View>()
    private val currentParameters = mutableMapOf<String, Any?>()

    companion object {
        // --- 核心修复：确保 newInstance 方法有3个参数 ---
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String? // 这个参数之前缺失了
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
        currentParameters.putAll(existingStep?.parameters ?: emptyMap())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val title = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val paramsContainer = view.findViewById<LinearLayout>(R.id.container_action_params)
        val saveButton = view.findViewById<Button>(R.id.button_save)

        title.text = "编辑 ${module.metadata.name}"

        buildUiForInputs(paramsContainer)

        saveButton.setOnClickListener {
            readParametersFromUi()
            val newStep = ActionStep(moduleId = module.id, parameters = currentParameters)
            onSave?.invoke(newStep)
            dismiss()
        }
        return view
    }

    fun updateInputWithVariable(inputId: String, variableReference: String) {
        currentParameters[inputId] = variableReference
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
            buildUiForInputs(it)
        }
    }

    private fun buildUiForInputs(container: LinearLayout) {
        container.removeAllViews()
        inputViews.clear()

        module.getInputs().forEach { inputDef ->
            val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, container, false)
            row.findViewById<TextView>(R.id.input_name).text = inputDef.name

            val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
            val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

            val currentValue = currentParameters[inputDef.id]

            if (inputDef.acceptsMagicVariable) {
                magicButton.visibility = View.VISIBLE
                magicButton.setOnClickListener {
                    readParametersFromUi()
                    onMagicVariableRequested?.invoke(inputDef.id)
                }
            } else {
                magicButton.visibility = View.GONE
            }

            if (currentValue is String && currentValue.startsWith("{{")) {
                val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
                pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
                valueContainer.addView(pill)
            } else {
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: "")
                    hint = "输入或选择变量"
                }
                valueContainer.addView(editText)
                inputViews[inputDef.id] = editText
            }
            container.addView(row)
        }
    }

    private fun readParametersFromUi() {
        inputViews.forEach { (inputId, view) ->
            if (view is EditText) {
                currentParameters[inputId] = view.text.toString()
            }
        }
    }
}