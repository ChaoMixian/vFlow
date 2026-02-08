// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 模块参数编辑器底部表单。
 * UI 由模块定义驱动，支持通用输入类型和模块自定义UI。
 * 支持配置异常处理策略。
 */
class ActionEditorSheet : BottomSheetDialogFragment() {
    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    private var focusedInputId: String? = null

    /**
     * 获取当前编辑的模块
     */
    fun getModule(): ActionModule? = if (::module.isInitialized) module else null
    private var allSteps: ArrayList<ActionStep>? = null
    private var availableNamedVariables: List<String>? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String) -> Unit)? = null
    var onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null
    private val inputViews = mutableMapOf<String, View>()
    private var customEditorHolder: CustomEditorViewHolder? = null
    private val currentParameters = mutableMapOf<String, Any?>()

    // 引用容器视图
    private var customUiCard: MaterialCardView? = null
    private var customUiContainer: LinearLayout? = null
    private var genericInputsCard: MaterialCardView? = null
    private var genericInputsContainer: LinearLayout? = null

    // 异常处理 UI 组件
    private var errorSettingsContent: LinearLayout? = null
    private var errorPolicyGroup: RadioGroup? = null
    private var retryOptionsContainer: LinearLayout? = null
    private var retryCountSlider: Slider? = null
    private var retryIntervalSlider: Slider? = null

    companion object {
        // 异常处理策略相关的常量 Key
        const val KEY_ERROR_POLICY = "__error_policy"
        const val KEY_RETRY_COUNT = "__retry_count"
        const val KEY_RETRY_INTERVAL = "__retry_interval"

        const val POLICY_STOP = "STOP"
        const val POLICY_SKIP = "SKIP"
        const val POLICY_RETRY = "RETRY"

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
        val saveButton = view.findViewById<Button>(R.id.button_save)

        // 绑定视图容器
        customUiCard = view.findViewById(R.id.card_custom_ui)
        customUiContainer = view.findViewById(R.id.container_custom_ui)
        genericInputsCard = view.findViewById(R.id.card_generic_inputs)
        genericInputsContainer = view.findViewById(R.id.container_generic_inputs)

        // 绑定错误处理容器
        errorSettingsContent = view.findViewById(R.id.container_execution_settings_content)
        val errorHeader = view.findViewById<View>(R.id.header_execution_settings)
        val errorArrow = view.findViewById<View>(R.id.arrow_execution_settings)

        // 错误处理区域的折叠/展开逻辑
        errorHeader.setOnClickListener {
            val isVisible = errorSettingsContent?.isVisible == true
            errorSettingsContent?.isVisible = !isVisible
            errorArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
        }

        // 设置标题
        val focusedInputDef = module.getInputs().find { it.id == focusedInputId }
        titleTextView.text = if (focusedInputId != null && focusedInputDef != null) {
            "编辑 ${focusedInputDef.name}"
        } else {
            val localizedName = module.metadata.getLocalizedName(requireContext())
            "编辑 $localizedName"
        }

        buildUi()
        buildErrorHandlingUi() // 构建错误处理 UI

        saveButton.setOnClickListener {
            readParametersFromUi()
            readErrorSettingsFromUi() // 读取错误处理配置

            val finalParams = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
            finalParams.putAll(currentParameters)
            val stepForValidation = ActionStep(moduleId = module.id, parameters = finalParams, id = existingStep?.id ?: "")
            // 调用 validate 方法进行验证
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

    /**
     * 构建异常处理策略的 UI。
     * 包含一个 RadioGroup 选择策略，以及重试相关的 Slider。
     */
    private fun buildErrorHandlingUi() {
        val context = requireContext()
        errorSettingsContent?.removeAllViews()

        val radioGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val rbStop = RadioButton(context).apply { text = getString(R.string.editor_error_policy_stop); tag = POLICY_STOP }
        val rbSkip = RadioButton(context).apply { text = getString(R.string.editor_error_policy_skip); tag = POLICY_SKIP }
        val rbRetry = RadioButton(context).apply { text = getString(R.string.editor_error_policy_retry); tag = POLICY_RETRY }

        radioGroup.addView(rbStop)
        radioGroup.addView(rbSkip)
        radioGroup.addView(rbRetry)

        val retryContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }

        // 恢复状态
        val currentPolicy = currentParameters[KEY_ERROR_POLICY] as? String ?: POLICY_STOP
        val currentRetryCount = (currentParameters[KEY_RETRY_COUNT] as? Number)?.toFloat() ?: 3f
        val currentRetryInterval = (currentParameters[KEY_RETRY_INTERVAL] as? Number)?.toFloat() ?: 1000f

        // --- 重试次数 ---
        val sliderRetryCount = StandardControlFactory.createSliderWithLabel(
            context = context,
            label = getString(R.string.editor_retry_count),
            valueFrom = 1f,
            valueTo = 10f,
            stepSize = 1f,
            currentValue = currentRetryCount,
            valueFormatter = { getString(R.string.editor_retry_times, it.toInt()) }
        )
        retryContainer.addView(sliderRetryCount)

        // --- 重试间隔 ---
        val sliderRetryInterval = StandardControlFactory.createSliderWithLabel(
            context = context,
            label = getString(R.string.editor_retry_interval),
            valueFrom = 100f,
            valueTo = 5000f,
            stepSize = 100f,
            currentValue = currentRetryInterval,
            valueFormatter = { "${it.toLong()} ms" }
        ).apply {
            // 增加间距区分上下两个滑块
            (layoutParams as LinearLayout.LayoutParams).topMargin = (24 * resources.displayMetrics.density).toInt()
        }
        retryContainer.addView(sliderRetryInterval)

        when (currentPolicy) {
            POLICY_SKIP -> rbSkip.isChecked = true
            POLICY_RETRY -> rbRetry.isChecked = true
            else -> rbStop.isChecked = true
        }
        retryContainer.isVisible = (currentPolicy == POLICY_RETRY)

        // 获取滑块引用
        val sliderRetryCountView = sliderRetryCount.getChildAt(1) as Slider
        val sliderRetryIntervalView = sliderRetryInterval.getChildAt(1) as Slider

        // 监听器
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            retryContainer.isVisible = (checkedId == rbRetry.id)
        }

        // 保存引用
        this.errorPolicyGroup = radioGroup
        this.retryOptionsContainer = retryContainer
        this.retryCountSlider = sliderRetryCountView
        this.retryIntervalSlider = sliderRetryIntervalView

        errorSettingsContent?.addView(radioGroup)
        errorSettingsContent?.addView(retryContainer)
    }

    /**
     * 读取异常处理配置到 currentParameters。
     */
    private fun readErrorSettingsFromUi() {
        val selectedId = errorPolicyGroup?.checkedRadioButtonId
        val view = errorPolicyGroup?.findViewById<View>(selectedId ?: -1)
        val policy = view?.tag as? String ?: POLICY_STOP

        currentParameters[KEY_ERROR_POLICY] = policy

        if (policy == POLICY_RETRY) {
            currentParameters[KEY_RETRY_COUNT] = retryCountSlider?.value?.toInt() ?: 3
            currentParameters[KEY_RETRY_INTERVAL] = retryIntervalSlider?.value?.toLong() ?: 1000L
        } else {
            // 清理无用参数
            currentParameters.remove(KEY_RETRY_COUNT)
            currentParameters.remove(KEY_RETRY_INTERVAL)
        }
    }

    /**
     * 构建UI的逻辑。
     */
    private fun buildUi() {
        // 清空所有容器
        customUiContainer?.removeAllViews()
        genericInputsContainer?.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

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

        // 构建自定义 UI
        if (uiProvider != null && uiProvider !is RichTextUIProvider) {
            customEditorHolder = uiProvider.createEditor(
                context = requireContext(),
                parent = customUiContainer!!,
                currentParameters = currentParameters,
                onParametersChanged = { readParametersFromUi() },
                onMagicVariableRequested = { inputId ->
                    readParametersFromUi()
                    this.onMagicVariableRequested?.invoke(inputId)
                },
                allSteps = allSteps,
                onStartActivityForResult = onStartActivityForResult
            )
            customUiContainer?.addView(customEditorHolder!!.view)
            customUiCard?.isVisible = true
        } else {
            customUiCard?.isVisible = false
        }

        /**
         * 检查输入参数是否应该显示。
         * 优先使用新的 visibility 条件系统，同时保持对 isHidden 的向后兼容。
         */
        fun isInputVisible(inputDef: InputDefinition, params: Map<String, Any?>): Boolean {
            // 如果设置了 visibility 条件，使用新系统
            inputDef.visibility?.let { visibility ->
                return visibility.isVisible(params)
            }
            // 否则使用传统的 isHidden 标志
            return !inputDef.isHidden
        }

        // 构建通用参数列表 (分离普通参数和折叠参数)
        val normalInputs = mutableListOf<InputDefinition>()
        val foldedInputs = mutableListOf<InputDefinition>()

        inputsToShow.forEach { inputDef ->
            if (!handledInputIds.contains(inputDef.id) && isInputVisible(inputDef, currentParameters)) {
                if (inputDef.isFolded) {
                    foldedInputs.add(inputDef)
                } else {
                    normalInputs.add(inputDef)
                }
            }
        }

        // 添加普通参数
        normalInputs.forEach { inputDef ->
            val inputView = createViewForInputDefinition(inputDef, genericInputsContainer!!)
            genericInputsContainer?.addView(inputView)
            inputViews[inputDef.id] = inputView
        }

        // 如果有折叠参数，创建“更多设置”区域
        if (foldedInputs.isNotEmpty()) {
            // 创建分隔线
            if (normalInputs.isNotEmpty()) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(0, 32, 0, 16)
                    }
                    setBackgroundColor(requireContext().getColor(android.R.color.darker_gray))
                    alpha = 0.2f
                }
                genericInputsContainer?.addView(divider)
            }

            // 创建折叠容器结构
            val advancedSection = createAdvancedSection(foldedInputs)
            genericInputsContainer?.addView(advancedSection)
        }

        // 只有当有通用参数（普通或折叠）时才显示卡片
        genericInputsCard?.isVisible = normalInputs.isNotEmpty() || foldedInputs.isNotEmpty()
    }

    /**
     * 动态创建“更多设置”折叠区域。
     */
    private fun createAdvancedSection(inputs: List<InputDefinition>): View {
        val context = requireContext()
        val density = resources.displayMetrics.density

        // 根容器
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 标题栏 (点击区域)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 使用系统可点击背景
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = context.obtainStyledAttributes(attrs)
            background = typedArray.getDrawable(0)
            typedArray.recycle()

            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt()
            )
        }

        // 图标
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.rounded_settings_24)
            layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
            val color = com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.graphics.Color.GRAY
            )
            setColorFilter(color)
            alpha = 0.7f
        }

        // 文字
        val title = TextView(context).apply {
            text = getString(R.string.editor_more_settings)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // 箭头
        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.rounded_arrow_drop_down_24)
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
            val color = com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.graphics.Color.GRAY
            )
            setColorFilter(color)
            alpha = 0.7f
        }

        headerLayout.addView(icon)
        headerLayout.addView(title)
        headerLayout.addView(arrow)

        // 内容容器 (默认隐藏)
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        // 填充参数到内容容器
        inputs.forEach { inputDef ->
            val inputView = createViewForInputDefinition(inputDef, contentLayout)
            contentLayout.addView(inputView)
            // 注册到 inputViews，这样 readParametersFromUi 就能自动读取它们
            inputViews[inputDef.id] = inputView
        }

        // 设置点击事件
        var isExpanded = false
        contentLayout.isVisible = isExpanded
        arrow.rotation = if (isExpanded) 180f else 0f

        headerLayout.setOnClickListener {
            isExpanded = !isExpanded
            contentLayout.isVisible = isExpanded
            arrow.animate()
                .rotation(if (isExpanded) 180f else 0f)
                .setDuration(200)
                .start()
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(contentLayout)
        return rootLayout
    }

    /**
     * 为输入参数创建视图。
     * 对于 CHIP_GROUP 风格，使用简化的布局（不带标签和魔法变量按钮）。
     * 对于 PICKER 类型，使用带选择器图标的输入框。
     * 对于其他风格，使用完整的参数输入行布局。
     */
    private fun createViewForInputDefinition(inputDef: InputDefinition, parent: ViewGroup): View {
        // CHIP_GROUP 风格不需要魔法变量按钮，使用简化布局
        if (inputDef.inputStyle == InputStyle.CHIP_GROUP) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_editor_input, null, false)
            row.findViewById<TextView>(R.id.input_name).text = inputDef.getLocalizedName(requireContext())
            row.findViewById<ImageButton>(R.id.button_magic_variable).visibility = View.GONE

            val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
            valueContainer.removeAllViews()

            val chipGroupView = StandardControlFactory.createChipGroup(
                context = requireContext(),
                options = inputDef.options,
                currentValue = currentParameters[inputDef.id] as? String,
                onSelectionChanged = { selectedItem ->
                    if (currentParameters[inputDef.id] != selectedItem) {
                        readParametersFromUi()
                        currentParameters[inputDef.id] = selectedItem
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                buildUi()
                            } catch (e: Exception) {
                                // 忽略视图已销毁的情况
                            }
                        }
                    }
                },
                optionsStringRes = if (inputDef.optionsStringRes.isNotEmpty()) inputDef.optionsStringRes else null
            )
            valueContainer.addView(chipGroupView)
            row.tag = inputDef.id
            return row
        }

        // PICKER 类型使用带选择器图标的输入框
        if (inputDef.pickerType != PickerType.NONE) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_editor_input, null, false)
            row.findViewById<TextView>(R.id.input_name).text = inputDef.getLocalizedName(requireContext())
            row.findViewById<ImageButton>(R.id.button_magic_variable).visibility = View.GONE

            val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
            valueContainer.removeAllViews()

            val pickerInputView = StandardControlFactory.createPickerInput(
                context = requireContext(),
                currentValue = currentParameters[inputDef.id],
                pickerType = inputDef.pickerType,
                hint = inputDef.hint,
                onPickerClicked = {
                    onPickerRequested?.invoke(inputDef)
                }
            )
            valueContainer.addView(pickerInputView)
            row.tag = inputDef.id
            return row
        }

        // 其他风格使用标准布局
        return StandardControlFactory.createParameterInputRow(
            context = requireContext(),
            inputDef = inputDef,
            currentValue = currentParameters[inputDef.id],
            allSteps = allSteps,
            onMagicVariableRequested = { inputId ->
                readParametersFromUi()
                this.onMagicVariableRequested?.invoke(inputId)
            },
            onEnumItemSelected = { selectedItem ->
                // 防止重复触发：只在值真正改变时才处理
                if (currentParameters[inputDef.id] != selectedItem) {
                    // 先读取当前UI中的所有参数值，防止切换条件时丢失其他输入框的值
                    readParametersFromUi()
                    currentParameters[inputDef.id] = selectedItem
                    // 使用 Handler 延迟重建UI以避免在事件处理期间冻结
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            buildUi()
                        } catch (e: Exception) {
                            // 忽略视图已销毁的情况
                        }
                    }
                }
            }
        )
    }

    /**
     * picker 被点击时的回调，由外部设置（Activity/Fragment）。
     * 格式：inputDef 为被点击的输入定义，result 为选择的结果
     */
    var onPickerResult: ((inputDef: InputDefinition, result: Any?) -> Unit)? = null

    /**
     * 获取当前参数值，供 PickerHandler 使用
     */
    fun getCurrentParameter(inputDef: InputDefinition): Any? {
        return currentParameters[inputDef.id]
    }

    /**
     * 内部触发 picker 请求的回调。
     */
    private var onPickerRequested: ((inputDef: InputDefinition) -> Unit)? = { inputDef ->
        // 默认实现：交给外部处理
        // 子类可以重写此方法来实际显示选择器
    }

    fun setOnPickerRequestedListener(listener: (InputDefinition) -> Unit) {
        onPickerRequested = listener
    }

    fun updateParametersAndRebuildUi(newParameters: Map<String, Any?>) {
        currentParameters.putAll(newParameters)
        buildUi()
    }

    private fun isVariableReference(value: Any?): Boolean {
        return StandardControlFactory.isVariableReference(value)
    }

    private fun readParametersFromUi() {
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null && uiProvider !is RichTextUIProvider) {
            currentParameters.putAll(uiProvider.readFromEditor(customEditorHolder!!))
        }

        inputViews.forEach { (id, view) ->
            val stepForUi = ActionStep(module.id, currentParameters)
            val inputDef = module.getDynamicInputs(stepForUi, allSteps).find { it.id == id }

            if (inputDef?.supportsRichText == false && isVariableReference(currentParameters[id])) {
                return@forEach
            }

            val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container) ?: return@forEach
            if (valueContainer.childCount == 0) return@forEach

            val staticView = valueContainer.getChildAt(0)

            val value: Any? = if (inputDef != null) {
                StandardControlFactory.readValueFromInputRow(view, inputDef)
            } else {
                null
            }

            if (value != null) {
                val convertedValue: Any? = when (inputDef?.staticType) {
                    ParameterType.NUMBER -> {
                        if (inputDef.supportsRichText) value else {
                            val strVal = value.toString()
                            strVal.toLongOrNull() ?: strVal.toDoubleOrNull()
                        }
                    }
                    else -> value
                }
                currentParameters[id] = convertedValue
            }
        }
    }

    /**
     * 更新输入框的变量。
     */
    fun updateInputWithVariable(inputId: String, variableReference: String) {
        val stepForUi = ActionStep(module.id, currentParameters)
        val inputDef = module.getDynamicInputs(stepForUi, allSteps).find { it.id == inputId }

        var richTextView: RichTextView? = null

        // 尝试从通用输入视图中查找
        if (inputDef?.supportsRichText == true) {
            val view = inputViews[inputId]
            richTextView = (view?.findViewById<ViewGroup>(R.id.input_value_container)?.getChildAt(0) as? ViewGroup)
                ?.findViewById(R.id.rich_text_view)
        }

        // 如果在通用视图中找不到，则尝试在自定义编辑器视图中查找
        if (richTextView == null && customEditorHolder != null) {
            // 首先尝试用 inputId 作为 tag 查找（标准控件使用的方式）
            richTextView = customEditorHolder?.view?.findViewWithTag<RichTextView>(inputId)
            // 如果找不到，尝试旧的方式
            if (richTextView == null) {
                richTextView = customEditorHolder?.view?.findViewWithTag<RichTextView>("rich_text_view_value")
            }
            // 最后尝试用ID查找作为后备
            if (richTextView == null) {
                richTextView = customEditorHolder?.view?.findViewById(R.id.rich_text_view)
            }
        }

        if (richTextView != null) {
            // 使用新的 API：直接传递变量引用，内部使用 PillVariableResolver 解析
            richTextView.insertVariablePill(variableReference)
            return // 直接操作视图后返回，避免重建UI
        }

        if (inputId.contains('.')) {
            val parts = inputId.split('.', limit = 2)
            val mainInputId = parts[0]
            val subKey = parts[1]

            // 获取当前参数值
            val currentValue = currentParameters[mainInputId]

            if (currentValue is List<*>) {
                // 如果当前是列表，则按索引更新列表
                val mutableList = currentValue.toMutableList()
                val index = subKey.toIntOrNull()
                // 确保索引有效
                if (index != null && index >= 0 && index < mutableList.size) {
                    mutableList[index] = variableReference // 更新指定位置
                    currentParameters[mainInputId] = mutableList
                }
            } else {
                // 如果是字典或默认情况，按 Map 更新
                val dict = (currentValue as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
                dict[subKey] = variableReference
                currentParameters[mainInputId] = dict
            }
        } else {
            currentParameters[inputId] = variableReference
        }
        buildUi()
    }

    /**
     * 清除输入框的变量。
     */
    fun clearInputVariable(inputId: String) {
        if (inputId.contains('.')) {
            val parts = inputId.split('.', limit = 2)
            val mainInputId = parts[0]
            val subKey = parts[1]

            val currentValue = currentParameters[mainInputId]

            if (currentValue is List<*>) {
                // 列表：清除指定索引的内容（置为空字符串）
                val mutableList = currentValue.toMutableList()
                val index = subKey.toIntOrNull()
                if (index != null && index >= 0 && index < mutableList.size) {
                    mutableList[index] = ""
                    currentParameters[mainInputId] = mutableList
                }
            } else {
                // 字典：清除指定 Key 的内容
                val dict = (currentValue as? Map<*, *>)?.toMutableMap() ?: return
                dict[subKey] = ""
                currentParameters[mainInputId] = dict
            }
        } else {
            val inputDef = module.getInputs().find { it.id == inputId } ?: return
            currentParameters[inputId] = inputDef.defaultValue
        }
        // 清除后重建UI
        buildUi()
    }

    /**
     * 当一个参数值在UI上发生变化时调用此方法。
     * @param updatedId 被更新的参数的ID。
     * @param updatedValue 新的参数值。
     */
    private fun parameterUpdated(updatedId: String, updatedValue: Any?) {
        // 基于当前参数状态，应用刚刚发生变化的那个值
        val parametersBeforeModuleUpdate = currentParameters.toMutableMap()
        parametersBeforeModuleUpdate[updatedId] = updatedValue

        // 创建一个临时的ActionStep实例，用于传递给模块
        val stepForUpdate = ActionStep(module.id, parametersBeforeModuleUpdate)

        // 调用模块的onParameterUpdated方法，获取模块处理后的全新参数集
        val newParametersFromServer = module.onParameterUpdated(stepForUpdate, updatedId, updatedValue)

        // 使用模块返回的参数集，完全更新编辑器内部的当前参数状态
        currentParameters.clear()
        currentParameters.putAll(newParametersFromServer)
        // 使用新的参数状态，重建整个UI
        buildUi()
    }

    private fun createBaseViewForInputType(inputDef: InputDefinition, currentValue: Any?): View {
        val view = StandardControlFactory.createBaseViewForInputType(requireContext(), inputDef, currentValue)

        // 添加参数变更监听器
        when (view) {
            is SwitchCompat -> {
                view.setOnCheckedChangeListener { _, isChecked ->
                    parameterUpdated(inputDef.id, isChecked)
                }
            }
            is Spinner -> {
                view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                        val selectedValue = inputDef.options.getOrNull(position)
                        if (currentParameters[inputDef.id] != selectedValue) {
                            parameterUpdated(inputDef.id, selectedValue)
                        }
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
        }

        return view
    }
}