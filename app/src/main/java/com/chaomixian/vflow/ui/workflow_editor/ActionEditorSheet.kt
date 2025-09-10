// 文件: ActionEditorSheet.kt
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

    private lateinit var module: ActionModule // 当前编辑的模块
    private var existingStep: ActionStep? = null // 若为编辑，此为现有步骤实例
    private var focusedInputId: String? = null // 若为编辑单个参数，此为参数ID
    private var allSteps: ArrayList<ActionStep>? = null // 工作流中所有步骤，用于动态输入上下文

    // 回调
    var onSave: ((ActionStep) -> Unit)? = null // 保存回调
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null // 请求魔法变量选择器回调
    // 回调，用于从编辑器内部请求启动一个新的Activity并获取结果
    var onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null

    // UI及状态
    private val inputViews = mutableMapOf<String, View>() // 通用输入控件引用
    private var customEditorHolder: CustomEditorViewHolder? = null // 模块自定义UI的ViewHolder
    private val currentParameters = mutableMapOf<String, Any?>() // 编辑器中当前的参数值

    companion object {
        /** 创建 ActionEditorSheet 实例。 */
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String?,
            allSteps: List<ActionStep>? = null // 工作流上下文，可选
        ): ActionEditorSheet {
            return ActionEditorSheet().apply {
                arguments = Bundle().apply {
                    putString("moduleId", module.id)
                    putParcelable("existingStep", existingStep)
                    putString("focusedInputId", focusedInputId)
                    allSteps?.let { putParcelableArrayList("allSteps", ArrayList(it)) }
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

        buildUi(paramsContainer) // 构建参数编辑界面

        saveButton.setOnClickListener {
            readParametersFromUi()
            val finalParams = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
            finalParams.putAll(currentParameters)
            val stepForValidation = ActionStep(moduleId = module.id, parameters = finalParams)
            val validationResult = module.validate(stepForValidation)
            if (validationResult.isValid) {
                onSave?.invoke(ActionStep(module.id, currentParameters))
                dismiss()
            } else {
                Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_LONG).show() // 验证失败，显示错误
            }
        }
        return view
    }

    /**
     * 更新指定输入框的值为魔法变量引用。
     * @param inputId 目标输入参数的ID。
     * @param variableReference 魔法变量的引用字符串, e.g., "{{stepId.outputId}}"。
     */
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
            dict[subKey] = "" // 将值设置为空字符串
            currentParameters[mainInputId] = dict
        } else {
            val inputDef = module.getInputs().find { it.id == inputId } ?: return
            currentParameters[inputId] = inputDef.defaultValue // 恢复为模块定义的默认值
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
                    currentParameters[inputDef.id] = inputDef.defaultValue // 重置为默认值
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
    private fun createViewForInputDefinition(inputDef: InputDefinition, parent: ViewGroup): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, parent, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
        val currentValue = currentParameters[inputDef.id]

        magicButton.isVisible = inputDef.acceptsMagicVariable // 是否显示魔法变量按钮
        magicButton.setOnClickListener {
            readParametersFromUi() // 保存当前UI状态
            onMagicVariableRequested?.invoke(inputDef.id) // 请求魔法变量选择
        }

        valueContainer.removeAllViews()
        if (inputDef.acceptsMagicVariable && (currentValue as? String).isMagicVariable()) {
            // 若已连接魔法变量，显示药丸
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            pill.setOnClickListener {
                readParametersFromUi()
                onMagicVariableRequested?.invoke(inputDef.id)
            }
            valueContainer.addView(pill)
        } else {
            // 未连接魔法变量，创建静态输入控件
            val staticInputView = createBaseViewForInputType(inputDef, currentValue)
            valueContainer.addView(staticInputView)
        }
        row.tag = inputDef.id // 存储输入ID
        return row
    }

    /** 根据输入类型创建基础输入控件 (EditText, Switch, Spinner)。 */
    private fun createBaseViewForInputType(inputDef: InputDefinition, currentValue: Any?): View {
        return when (inputDef.staticType) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                isChecked = currentValue as? Boolean ?: (inputDef.defaultValue as? Boolean ?: false)
                // [修改] 添加监听器以触发参数更新流程
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

                // [修改] 修改监听器以调用新的参数更新流程
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

    /** 从所有UI控件读取值并更新 `currentParameters`。 */
    private fun readParametersFromUi() {
        // 1. 从自定义UI读取 (如果存在)
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null) {
            currentParameters.putAll(uiProvider.readFromEditor(customEditorHolder!!))
        }

        // 2. 从通用UI控件读取
        inputViews.forEach { (id, view) ->
            if ((currentParameters[id] as? String)?.isMagicVariable() == true) return@forEach // 跳过已连接魔法变量的

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
                // 数字类型统一存为Double或Long，便于序列化和解析
                val convertedValue: Any? = when (dynamicInputDef?.staticType) {
                    ParameterType.NUMBER -> {
                        val strVal = value.toString()
                        strVal.toLongOrNull() ?: strVal.toDoubleOrNull() // 优先尝试Long，失败则Double
                    }
                    else -> value
                }
                currentParameters[id] = convertedValue
            }
        }
    }
}