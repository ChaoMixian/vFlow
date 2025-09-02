// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt

package com.chaomixian.vflow.ui.workflow_editor

// ... (imports 保持不变) ...
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout


class ActionEditorSheet : BottomSheetDialogFragment() {

    // ... (属性和 companion object 保持不变) ...
    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null

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


    // ... (onCreate, onCreateView, updateInputWithVariable 保持不变) ...
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = arguments?.getString("moduleId")
        module = moduleId?.let { ModuleRegistry.getModule(it) } ?: return dismiss()
        existingStep = arguments?.getParcelable("existingStep")
        currentParameters.putAll(module.getInputs().associate { it.id to it.defaultValue }.filterValues { it != null })
        existingStep?.parameters?.let { currentParameters.putAll(it) }
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

            val validationResult = module.validate(newStep)
            if (validationResult.isValid) {
                onSave?.invoke(newStep)
                dismiss()
            } else {
                Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        return view
    }

    fun updateInputWithVariable(inputId: String, variableReference: String) {
        currentParameters[inputId] = variableReference
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
            buildUi(it)
        }
    }

    private fun buildUi(container: LinearLayout) {
        container.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

        val uiProvider = module.uiProvider
        val handledInputIds = uiProvider?.getHandledInputIds() ?: emptySet()

        if (uiProvider != null) {
            customEditorHolder = uiProvider.createEditor(requireContext(), container, currentParameters) {
                readParametersFromUi()
            }
            container.addView(customEditorHolder!!.view)
        }

        module.getInputs().forEach { inputDef ->
            // --- 核心修改：增加对 isHidden 的判断 ---
            if (!handledInputIds.contains(inputDef.id) && !inputDef.isHidden) {
                val inputView = if (inputDef.acceptsMagicVariable) {
                    createViewForInput(inputDef, container)
                } else {
                    createViewForStaticParameter(inputDef)
                }
                container.addView(inputView)
                inputViews[inputDef.id] = inputView
            }
        }
    }

    // ... (所有其他方法 createViewForInput, readParametersFromUi 等保持不变) ...
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
            val staticInputView = createBaseViewForInputType(inputDef, currentValue)
            valueContainer.addView(staticInputView)
        }
        return row
    }

    private fun createViewForStaticParameter(paramDef: InputDefinition): View {
        val currentValue = currentParameters[paramDef.id]
        val view = when (paramDef.staticType) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                text = paramDef.name
                isChecked = currentValue as? Boolean ?: (paramDef.defaultValue as? Boolean ?: false)
            }
            ParameterType.ENUM -> LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
                val label = TextView(context).apply {
                    text = paramDef.name
                    setTextAppearance(android.R.style.TextAppearance_Material_Body2)
                }
                val spinner = Spinner(context).apply {
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, paramDef.options).also {
                        it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    val currentEnum = currentValue as? String ?: paramDef.defaultValue as? String
                    val selectionIndex = paramDef.options.indexOf(currentEnum)
                    if (selectionIndex != -1) setSelection(selectionIndex)
                }
                addView(label)
                addView(spinner)
            }
            else -> TextInputLayout(requireContext()).apply {
                hint = paramDef.name
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: paramDef.defaultValue?.toString() ?: "")
                    inputType = if (paramDef.staticType == ParameterType.NUMBER) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
                }
                addView(editText)
            }
        }
        view.tag = paramDef.id
        return view
    }

    private fun createBaseViewForInputType(inputDef: InputDefinition, currentValue: Any?): View {
        return when (inputDef.staticType) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                isChecked = currentValue as? Boolean ?: (inputDef.defaultValue as? Boolean ?: false)
            }
            ParameterType.ENUM -> Spinner(requireContext()).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, inputDef.options).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val currentEnum = currentValue as? String ?: inputDef.defaultValue as? String
                val selectionIndex = inputDef.options.indexOf(currentEnum)
                if (selectionIndex != -1) setSelection(selectionIndex)
            }
            else -> TextInputLayout(requireContext()).apply {
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: inputDef.defaultValue?.toString() ?: "")
                    hint = "输入值..."
                    inputType = if (inputDef.staticType == ParameterType.NUMBER) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
                }
                addView(editText)
            }
        }
    }

    private fun readParametersFromUi() {
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null) {
            val customParams = uiProvider.readFromEditor(customEditorHolder!!)
            currentParameters.putAll(customParams)
        }

        inputViews.forEach { (id, view) ->
            if ((currentParameters[id] as? String)?.startsWith("{{") == true) {
                return@forEach
            }

            val value: Any? = when(view) {
                is TextInputLayout -> view.editText?.text?.toString()
                is SwitchCompat -> view.isChecked
                is LinearLayout -> (view.getChildAt(1) as? Spinner)?.selectedItem?.toString()
                is RelativeLayout -> {
                    val valueContainer = view.findViewById<FrameLayout>(R.id.input_value_container)
                    when(val staticView = valueContainer.getChildAt(0)) {
                        is TextInputLayout -> staticView.editText?.text?.toString()
                        is SwitchCompat -> staticView.isChecked
                        is Spinner -> staticView.selectedItem?.toString()
                        else -> null
                    }
                }
                else -> null
            }

            if (value != null) {
                currentParameters[id] = value
            }
        }
    }
}