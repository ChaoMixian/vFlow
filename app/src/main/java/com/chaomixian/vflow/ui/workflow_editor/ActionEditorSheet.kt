// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt

package com.chaomixian.vflow.ui.workflow_editor

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

/**
 * 一个通用的、用于编辑模块参数的底部动作表单。
 * 它不包含任何针对特定模块的硬编码逻辑，完全由模块自身的定义来驱动UI的生成。
 */
class ActionEditorSheet : BottomSheetDialogFragment() {

    // --- 核心属性 ---
    private lateinit var module: ActionModule // 当前正在编辑的模块
    private var existingStep: ActionStep? = null // 如果是编辑现有步骤，则为该步骤的实例
    private var focusedInputId: String? = null // 如果是只编辑单个参数，则为该参数的ID
    private var allSteps: ArrayList<ActionStep>? = null // 整个工作流的步骤列表，用于为动态输入提供上下文

    // --- 回调函数 ---
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null

    // --- UI及状态管理 ---
    private val inputViews = mutableMapOf<String, View>() // 存储每个通用输入控件的引用
    private var customEditorHolder: CustomEditorViewHolder? = null // 用于持有模块自定义UI的ViewHolder
    private val currentParameters = mutableMapOf<String, Any?>() // 存储编辑器中当前的参数值

    companion object {
        /**
         * 创建 ActionEditorSheet 实例的工厂方法。
         */
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String?,
            allSteps: List<ActionStep>? = null // 新增一个可选参数，用于传递整个工作流的上下文
        ): ActionEditorSheet {
            return ActionEditorSheet().apply {
                arguments = Bundle().apply {
                    putString("moduleId", module.id)
                    putParcelable("existingStep", existingStep)
                    putString("focusedInputId", focusedInputId)
                    allSteps?.let {
                        putParcelableArrayList("allSteps", ArrayList(it))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从 arguments 中恢复核心数据
        val moduleId = arguments?.getString("moduleId")
        module = moduleId?.let { ModuleRegistry.getModule(it) } ?: return dismiss()
        existingStep = arguments?.getParcelable("existingStep")
        focusedInputId = arguments?.getString("focusedInputId")
        allSteps = arguments?.getParcelableArrayList("allSteps")

        // 初始化当前参数：首先从模块定义中获取所有输入的默认值，然后用现有步骤的参数覆盖它们
        currentParameters.putAll(module.getInputs().associate { it.id to it.defaultValue }.filterValues { it != null })
        existingStep?.parameters?.let { currentParameters.putAll(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val title = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val paramsContainer = view.findViewById<LinearLayout>(R.id.container_action_params)
        val saveButton = view.findViewById<Button>(R.id.button_save)

        // 根据是编辑整个模块还是单个参数来设置标题
        val focusedInputDef = module.getInputs().find { it.id == focusedInputId }
        title.text = if (focusedInputId != null && focusedInputDef != null) {
            "编辑 ${focusedInputDef.name}"
        } else {
            "编辑 ${module.metadata.name}"
        }

        // 构建UI界面
        buildUi(paramsContainer)

        saveButton.setOnClickListener {
            // 1. 从UI控件中读取最新的参数值
            readParametersFromUi()

            val uiProvider = module.uiProvider
            val handledIds = uiProvider?.getHandledInputIds() ?: emptySet()

            // 2. 根据编辑模式，决定要保存哪些参数
            val paramsToSave: Map<String, Any?> = if (focusedInputId != null) {
                // 只编辑单个参数模式
                val id = focusedInputId!!  // 此时一定非空
                if (handledIds.contains(id) && uiProvider != null && customEditorHolder != null) {
                    uiProvider.readFromEditor(customEditorHolder!!)
                } else {
                    mapOf(id to currentParameters[id])
                }
            } else {
                // 编辑整个模块模式
                currentParameters.toMap()
            }

            // 3. 在验证前，合并旧参数和新参数，以提供完整的上下文
            val finalParamsForValidation = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
            finalParamsForValidation.putAll(paramsToSave)

            // 4. 执行模块的验证逻辑
            val stepForValidation = ActionStep(moduleId = module.id, parameters = finalParamsForValidation)
            val validationResult = module.validate(stepForValidation)

            if (validationResult.isValid) {
                // 验证通过，调用回调函数保存数据并关闭窗口
                val stepToSave = ActionStep(moduleId = module.id, parameters = paramsToSave)
                onSave?.invoke(stepToSave)
                dismiss()
            } else {
                // 验证失败，显示错误提示
                Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        return view
    }

    /**
     * 公开方法：当魔法变量选择器返回结果时，更新参数并重建UI。
     */
    fun updateInputWithVariable(inputId: String, variableReference: String) {
        currentParameters[inputId] = variableReference
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
            buildUi(it)
        }
    }

    /**
     * 公开方法：当用户选择清除变量连接时，恢复为默认值并重建UI。
     */
    fun clearInputVariable(inputId: String) {
        val inputDef = module.getInputs().find { it.id == inputId } ?: return
        currentParameters[inputId] = inputDef.defaultValue
        view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
            buildUi(it)
        }
    }

    /**
     * 构建编辑器UI的核心函数。
     */
    private fun buildUi(container: LinearLayout) {
        container.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

        // 【核心解耦逻辑】
        // 编辑器不再关心模块的具体类型。它只是向模块请求当前的输入项定义。
        // `getDynamicInputs` 是实现这一解耦的关键。模块自己决定在当前状态下应该显示哪些输入项。
        val stepForUi = ActionStep(module.id, currentParameters)
        val inputsToShow = module.getDynamicInputs(stepForUi, allSteps)

        // 自动校正：检查当前已选的参数值是否在新提供的选项列表中依然有效，如果无效则重置为默认值。
        // 这能防止因输入类型改变而导致保存一个无效的旧值。
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

        // 根据是编辑单个参数还是整个模块来决定渲染范围
        if (focusedInputId != null) {
            val inputDef = inputsToShow.find { it.id == focusedInputId } ?: return
            if (handledInputIds.contains(inputDef.id) && uiProvider != null) {
                customEditorHolder = uiProvider.createEditor(requireContext(), container, currentParameters) { readParametersFromUi() }
                container.addView(customEditorHolder!!.view)
            } else {
                val inputView = createViewForInputDefinition(inputDef, container)
                container.addView(inputView)
                inputViews[inputDef.id] = inputView
            }
        } else {
            // 渲染模块的所有输入项
            if (uiProvider != null) {
                customEditorHolder = uiProvider.createEditor(requireContext(), container, currentParameters) { readParametersFromUi() }
                container.addView(customEditorHolder!!.view)
            }
            inputsToShow.forEach { inputDef ->
                if (!handledInputIds.contains(inputDef.id) && !inputDef.isHidden) {
                    val inputView = createViewForInputDefinition(inputDef, container)
                    container.addView(inputView)
                    inputViews[inputDef.id] = inputView
                }
            }
        }
    }

    /**
     * 为单个输入项定义创建对应的UI视图（包含标签、输入区和魔法变量按钮）。
     */
    private fun createViewForInputDefinition(
        inputDef: InputDefinition,
        parent: ViewGroup
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, parent, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name
        val valueContainer = row.findViewById<FrameLayout>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)
        val currentValue = currentParameters[inputDef.id]

        magicButton.isVisible = inputDef.acceptsMagicVariable
        magicButton.setOnClickListener {
            readParametersFromUi() // 在打开新窗口前，保存当前UI的状态
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()
        // 判断当前值是否为魔法变量
        if (inputDef.acceptsMagicVariable && currentValue is String && currentValue.startsWith("{{")) {
            // 如果是，则显示一个不可编辑的“药丸”
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            pill.setOnClickListener {
                readParametersFromUi()
                onMagicVariableRequested?.invoke(inputDef.id)
            }
            valueContainer.addView(pill)
        } else {
            // 如果不是，则创建对应的静态输入控件（输入框、开关等）
            val staticInputView = createBaseViewForInputType(inputDef, currentValue)
            valueContainer.addView(staticInputView)
        }

        row.tag = inputDef.id // 将输入ID存入tag，便于后续读取
        return row
    }

    /**
     * 根据输入类型创建基础的输入控件（如输入框、开关、下拉菜单）。
     */
    private fun createBaseViewForInputType(
        inputDef: InputDefinition,
        currentValue: Any?
    ): View {
        return when (inputDef.staticType) {
            ParameterType.BOOLEAN -> SwitchCompat(requireContext()).apply {
                isChecked = currentValue as? Boolean ?: (inputDef.defaultValue as? Boolean ?: false)
            }
            ParameterType.ENUM -> Spinner(requireContext()).apply {
                // 使用模块动态提供的选项列表
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, inputDef.options).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val currentEnum = currentValue as? String ?: inputDef.defaultValue as? String
                val selectionIndex = inputDef.options.indexOf(currentEnum)
                if (selectionIndex != -1) setSelection(selectionIndex)

                // 监听器现在变得通用：任何下拉菜单选项的改变都可能导致UI结构变化，因此需要重建UI。
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        val previousValue = currentParameters[inputDef.id]
                        val newValue = selectedItem.toString()
                        // 只有在值确实发生变化时才重建UI，防止不必要的刷新
                        if (previousValue != newValue) {
                            readParametersFromUi() // 在重建前，先读取所有控件的当前值
                            view?.findViewById<LinearLayout>(R.id.container_action_params)?.let {
                                buildUi(it)
                            }
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
            else -> TextInputLayout(requireContext()).apply {
                val editText = EditText(context).apply {
                    setText(currentValue?.toString() ?: inputDef.defaultValue?.toString() ?: "")
                    hint = "输入值..."
                    inputType = if (inputDef.staticType == ParameterType.NUMBER) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                }
                addView(editText)
            }
        }
    }

    /**
     * 从所有UI控件中读取用户输入的值，并更新到 `currentParameters` 这个Map中。
     */
    private fun readParametersFromUi() {
        // 如果有自定义UI，先从自定义UI中读取
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null) {
            val customParams = uiProvider.readFromEditor(customEditorHolder!!)
            currentParameters.putAll(customParams)
        }

        // 遍历所有通用的输入控件
        inputViews.forEach { (id, view) ->
            // 如果这个输入项已经连接了魔法变量，则它的值已经在currentParameters中了，无需读取
            if ((currentParameters[id] as? String)?.startsWith("{{") == true) {
                return@forEach
            }

            val valueContainer = view.findViewById<FrameLayout>(R.id.input_value_container) ?: return@forEach
            if (valueContainer.childCount == 0) return@forEach

            // 根据控件类型读取值
            val value: Any? = when(val staticView = valueContainer.getChildAt(0)) {
                is TextInputLayout -> staticView.editText?.text?.toString()
                is SwitchCompat -> staticView.isChecked
                is Spinner -> staticView.selectedItem?.toString()
                else -> null
            }

            if (value != null) {
                val inputDef = module.getInputs().find { it.id == id }
                // 根据模块的定义，将读取到的字符串转换为正确的类型（特别是数字）
                val convertedValue = when (inputDef?.staticType) {
                    ParameterType.NUMBER -> value.toString().toDoubleOrNull() ?: value.toString().toLongOrNull() ?: value
                    else -> value
                }
                currentParameters[id] = convertedValue
            }
        }
    }
}