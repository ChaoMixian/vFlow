// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Intent
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 模块参数编辑器底部表单。
 * UI 由模块定义驱动，支持通用输入类型和模块自定义UI。
 */
class ActionEditorSheet : BottomSheetDialogFragment() {
    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    private var focusedInputId: String? = null
    private var allSteps: ArrayList<ActionStep>? = null
    private var availableNamedVariables: List<String>? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null
    var onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null
    private val inputViews = mutableMapOf<String, View>()
    private var customEditorHolder: CustomEditorViewHolder? = null
    private val currentParameters = mutableMapOf<String, Any?>()


    companion object {
        /** 创建 ActionEditorSheet 实例。 */
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String?,
            allSteps: List<ActionStep>? = null,
            availableNamedVariables: List<String>? = null
        ): ActionEditorSheet {
            return ActionEditorSheet().apply {
                arguments = Bundle().apply {
                    putString("moduleId", module.id)
                    putParcelable("existingStep", existingStep)
                    putString("focusedInputId", focusedInputId)
                    allSteps?.let { putParcelableArrayList("allSteps", ArrayList(it)) }
                    availableNamedVariables?.let { putStringArrayList("namedVariables", ArrayList(it)) }
                }
            }
        }
    }

    /** 初始化核心数据。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = arguments?.getString("moduleId")
        module = moduleId?.let { ModuleRegistry.getModule(it) } ?: return dismiss()
        existingStep = arguments?.getParcelable("existingStep")
        focusedInputId = arguments?.getString("focusedInputId")
        allSteps = arguments?.getParcelableArrayList("allSteps")
        availableNamedVariables = arguments?.getStringArrayList("namedVariables")

        // 初始化参数，首先使用模块定义的默认值
        module.getInputs().forEach { def ->
            def.defaultValue?.let { currentParameters[def.id] = it }
        }
        // 然后用步骤已有的参数覆盖默认值
        existingStep?.parameters?.let { currentParameters.putAll(it) }
    }

    /** 创建视图并构建UI。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val titleTextView = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val paramsContainer = view.findViewById<LinearLayout>(R.id.container_action_params)
        val saveButton = view.findViewById<Button>(R.id.button_save)

        // 设置标题
        val focusedInputDef = module.getInputs().find { it.id == focusedInputId }
        titleTextView.text = if (focusedInputId != null && focusedInputDef != null) {
            "编辑 ${focusedInputDef.name}"
        } else {
            "编辑 ${module.metadata.name}"
        }

        buildUi(paramsContainer)

        saveButton.setOnClickListener {
            readParametersFromUi()
            val finalParams = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
            finalParams.putAll(currentParameters)
            val stepForValidation = ActionStep(moduleId = module.id, parameters = finalParams, id = existingStep?.id ?: "")
            // 调用新的 validate 方法，并传入 allSteps
            val validationResult = module.validate(stepForValidation, allSteps ?: emptyList())
            if (validationResult.isValid) {
                onSave?.invoke(ActionStep(module.id, currentParameters))
                dismiss()
            } else {
                Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        return view
    }


    /** 更新函数以处理两种变量引用格式 */
    private fun getDisplayNameForVariableReference(variableReference: String): String {
        // 情况1: 是命名变量
        if (variableReference.isNamedVariable()) {
            return variableReference.removeSurrounding("[[", "]]")
        }

        // 情况2: 是魔法变量
        if (variableReference.isMagicVariable()) {
            val parts = variableReference.removeSurrounding("{{", "}}").split('.')
            val sourceStepId = parts.getOrNull(0)
            val sourceOutputId = parts.getOrNull(1)

            if (sourceStepId != null && sourceOutputId != null) {
                val sourceStep = allSteps?.find { it.id == sourceStepId }
                if (sourceStep != null) {
                    val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId)
                    val outputDef = sourceModule?.getOutputs(sourceStep)?.find { it.id == sourceOutputId }
                    if (outputDef != null) {
                        return outputDef.name
                    }
                }
            }
            return sourceOutputId ?: variableReference
        }

        // 情况3: 都不是，返回原始值
        return variableReference
    }

    /** 更新函数以检查两种变量引用 */
    private fun isVariableReference(value: Any?): Boolean {
        if (value !is String) return false
        return value.isMagicVariable() || value.isNamedVariable()
    }

    private fun createViewForInputDefinition(inputDef: InputDefinition, parent: ViewGroup): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, parent, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
        val currentValue = currentParameters[inputDef.id]

        // 同时检查是否接受魔法变量和命名变量
        magicButton.isVisible = inputDef.acceptsMagicVariable || inputDef.acceptsNamedVariable
        magicButton.setOnClickListener {
            readParametersFromUi()
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()
        if (isVariableReference(currentValue)) {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            val pillText = pill.findViewById<TextView>(R.id.pill_text)
            pillText.text = getDisplayNameForVariableReference(currentValue as String)
            pill.setOnClickListener {
                readParametersFromUi()
                onMagicVariableRequested?.invoke(inputDef.id)
            }
            valueContainer.addView(pill)
        } else {
            val staticInputView = createBaseViewForInputType(inputDef, currentValue)
            valueContainer.addView(staticInputView)
        }
        row.tag = inputDef.id
        return row
    }

    private fun readParametersFromUi() {
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null) {
            currentParameters.putAll(uiProvider.readFromEditor(customEditorHolder!!))
        }

        inputViews.forEach { (id, view) ->
            if (isVariableReference(currentParameters[id])) return@forEach

            val valueContainer = view.findViewById<FrameLayout>(R.id.input_value_container) ?: return@forEach
            if (valueContainer.childCount == 0) return@forEach

            val staticView = valueContainer.getChildAt(0)
            val value: Any? = when(staticView) {
                is TextInputLayout -> staticView.editText?.text?.toString()
                is SwitchCompat -> staticView.isChecked
                is Spinner -> staticView.selectedItem?.toString()
                else -> null
            }

            if (value != null) {
                val stepForUi = ActionStep(module.id, currentParameters)
                val dynamicInputDef = module.getDynamicInputs(stepForUi, allSteps).find { it.id == id }
                val convertedValue: Any? = when (dynamicInputDef?.staticType) {
                    ParameterType.NUMBER -> {
                        val strVal = value.toString()
                        strVal.toLongOrNull() ?: strVal.toDoubleOrNull()
                    }
                    else -> value
                }
                currentParameters[id] = convertedValue
            }
        }
    }
    fun updateInputWithVariable(inputId: String, variableReference: String) {
        // 支持更新点分隔的嵌套参数 (主要用于字典类型)
        if (inputId.contains('.')) {
            val parts = inputId.split('.', limit = 2)
            val mainInputId = parts[0]
            val subKey = parts[1]
            val dict = (currentParameters[mainInputId] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
            dict[subKey] = variableReference
            currentParameters[mainInputId] = dict
        } else {
            currentParameters[inputId] = variableReference
        }
        // 更新参数后，重建UI以显示“已连接变量”的药丸
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let { buildUi(it) }
    }

    fun updateParametersAndRebuildUi(newParameters: Map<String, Any?>) {
        currentParameters.putAll(newParameters)
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let { buildUi(it) }
    }


    /** 当用户清除变量连接时，恢复为默认值并重建UI。 */
    fun clearInputVariable(inputId: String) {
        // 支持清除点分隔的嵌套参数
        if (inputId.contains('.')) {
            val parts = inputId.split('.', limit = 2)
            val mainInputId = parts[0]
            val subKey = parts[1]
            val dict = (currentParameters[mainInputId] as? Map<*, *>)?.toMutableMap() ?: return
            dict[subKey] = ""
            currentParameters[mainInputId] = dict
        } else {
            val inputDef = module.getInputs().find { it.id == inputId } ?: return
            currentParameters[inputId] = inputDef.defaultValue
        }
        // 清除后重建UI
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let { buildUi(it) }
    }

    /**
     * 当一个参数值在UI上发生变化时调用此方法。
     * @param updatedId 被更新的参数的ID。
     * @param updatedValue 新的参数值。
     */
    private fun parameterUpdated(updatedId: String, updatedValue: Any?) {
        // 1. 基于当前参数状态，应用刚刚发生变化的那个值
        val parametersBeforeModuleUpdate = currentParameters.toMutableMap()
        parametersBeforeModuleUpdate[updatedId] = updatedValue

        // 2. 创建一个临时的ActionStep实例，用于传递给模块
        val stepForUpdate = ActionStep(module.id, parametersBeforeModuleUpdate)

        // 3. 调用模块的onParameterUpdated方法，获取模块处理后的全新参数集
        val newParametersFromServer = module.onParameterUpdated(stepForUpdate, updatedId, updatedValue)

        // 4. 使用模块返回的参数集，完全更新编辑器内部的当前参数状态
        currentParameters.clear()
        currentParameters.putAll(newParametersFromServer)

        // 5. 使用新的参数状态，重建整个UI
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let { buildUi(it) }
    }


    /** 构建编辑器UI。 */
    private fun buildUi(container: LinearLayout) {
        container.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

        // 模块通过 getDynamicInputs 决定当前应显示的输入项
        val stepForUi = ActionStep(module.id, currentParameters)
        val inputsToShow = module.getDynamicInputs(stepForUi, allSteps)

        // 校正无效的枚举参数值，防止因模块更新导致崩溃
        inputsToShow.forEach { inputDef ->
            if (inputDef.staticType == ParameterType.ENUM) {
                val currentValue = currentParameters[inputDef.id] as? String
                if (currentValue != null && !inputDef.options.contains(currentValue)) {
                    currentParameters[inputDef.id] = inputDef.defaultValue
                }
            }
        }

        val uiProvider = module.uiProvider
        val handledInputIds = uiProvider?.getHandledInputIds() ?: emptySet()

        // 如果模块提供了自定义UI，则创建并添加它
        if (uiProvider != null) {
            customEditorHolder = uiProvider.createEditor(
                context = requireContext(),
                parent = container,
                currentParameters = currentParameters,
                onParametersChanged = { readParametersFromUi() },
                onMagicVariableRequested = { inputId ->
                    readParametersFromUi()
                    this.onMagicVariableRequested?.invoke(inputId)
                },
                // 将启动Activity的回调传递给自定义UI提供者
                onStartActivityForResult = onStartActivityForResult
            )
            container.addView(customEditorHolder!!.view)
        }

        // 为其余未被自定义UI处理的参数创建通用输入控件
        inputsToShow.forEach { inputDef ->
            if (!handledInputIds.contains(inputDef.id) && !inputDef.isHidden) {
                val inputView = createViewForInputDefinition(inputDef, container)
                container.addView(inputView)
                inputViews[inputDef.id] = inputView
            }
        }
    }

    /** 为单个输入定义创建视图 (标签、输入控件、魔法变量按钮)。 */
    private fun createBaseViewForInputType(inputDef: InputDefinition, currentValue: Any?): View {
        return when (inputDef.staticType) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                isChecked = currentValue as? Boolean ?: (inputDef.defaultValue as? Boolean ?: false)
                // 添加监听器以触发参数更新流程
                setOnCheckedChangeListener { _, isChecked ->
                    parameterUpdated(inputDef.id, isChecked)
                }
            }
            ParameterType.ENUM -> Spinner(requireContext()).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, inputDef.options).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val currentEnum = currentValue as? String ?: inputDef.defaultValue as? String
                val selectionIndex = inputDef.options.indexOf(currentEnum)
                if (selectionIndex != -1) setSelection(selectionIndex)

                // 修改监听器以调用新的参数更新流程
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                        val selectedValue = inputDef.options.getOrNull(position)
                        // 仅当选项实际发生变化时才触发更新，避免不必要地重建UI
                        if (currentParameters[inputDef.id] != selectedValue) {
                            parameterUpdated(inputDef.id, selectedValue)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
            else -> TextInputLayout(requireContext()).apply { // 默认是文本或数字输入
                hint = "值" // 将提示文本放在 TextInputLayout 上
                val editText = TextInputEditText(context).apply {
                    val valueToDisplay = when (currentValue) {
                        is Number -> if (currentValue.toDouble() == currentValue.toLong().toDouble()) currentValue.toLong().toString() else currentValue.toString()
                        else -> currentValue?.toString() ?: ""
                    }
                    setText(valueToDisplay)
                    inputType = if (inputDef.staticType == ParameterType.NUMBER) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    } else {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE //允许多行输入
                    }
                }
                addView(editText)
            }
        }
    }
}