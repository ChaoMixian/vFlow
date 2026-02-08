// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/StandardControlFactory.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.PickerType
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 标准控件工厂类。
 * 提供 ActionEditorSheet 中使用的标准控件的创建方法，供 UIProvider 复用。
 */
object StandardControlFactory {

    /**
     * 创建一个标准的参数输入行视图（包含标签和值容器）。
     * @param context 上下文
     * @param inputDef 参数定义
     * @param currentValue 当前值
     * @param allSteps 所有步骤（用于显示变量名称）
     * @param onMagicVariableRequested 点击魔法变量按钮的回调
     * @return 完整的输入行视图
     */
    fun createParameterInputRow(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        allSteps: List<ActionStep>?,
        onMagicVariableRequested: ((String) -> Unit)?,
        onEnumItemSelected: ((String) -> Unit)? = null
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null, false)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.getLocalizedName(context)
        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        // 显示魔法变量按钮（如果支持魔法变量或命名变量）
        magicButton.isVisible = inputDef.acceptsMagicVariable || inputDef.acceptsNamedVariable
        magicButton.setOnClickListener {
            onMagicVariableRequested?.invoke(inputDef.id)
        }

        valueContainer.removeAllViews()

        // 根据参数类型创建对应的输入控件
        val valueView = when {
            inputDef.supportsRichText -> createRichTextEditor(
                context,
                currentValue?.toString() ?: "",
                allSteps,
                inputDef.id  // 使用 inputId 作为 tag，便于后续查找
            )
            isVariableReference(currentValue) -> createVariablePill(
                context,
                valueContainer,  // 传入 parent 以正确设置 LayoutParams
                currentValue as String,
                allSteps,
                onMagicVariableRequested?.let { { inputDef.id } }
            )
            else -> createViewForInput(context, inputDef, currentValue, onEnumItemSelected)
        }

        valueContainer.addView(valueView)
        row.tag = inputDef.id
        return row
    }

    /**
     * 判断值是否为变量引用（魔法变量或命名变量）。
     */
    fun isVariableReference(value: Any?): Boolean {
        if (value !is String) return false
        return value.isMagicVariable() || value.isNamedVariable()
    }

    /**
     * 根据参数类型和风格创建输入控件。
     * @param context 上下文
     * @param inputDef 参数定义
     * @param currentValue 当前值
     * @return 对应类型的控件
     */
    fun createViewForInput(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        onItemSelectedCallback: ((String) -> Unit)? = null
    ): View {
        return when (inputDef.inputStyle) {
            InputStyle.CHIP_GROUP -> createChipGroup(
                context,
                inputDef.options,
                currentValue as? String,
                onItemSelectedCallback
            )
            InputStyle.SWITCH -> createSwitch(context, currentValue as? Boolean ?: false)
            InputStyle.SLIDER -> createSliderWithLabel(
                context = context,
                label = inputDef.name,
                valueFrom = inputDef.sliderConfig?.first ?: 0f,
                valueTo = inputDef.sliderConfig?.second ?: 100f,
                stepSize = inputDef.sliderConfig?.third ?: 1f,
                currentValue = (currentValue as? Number)?.toFloat() ?: 0f,
                valueFormatter = { it.toString() }
            )
            else -> createBaseViewForInputType(context, inputDef, currentValue, onItemSelectedCallback)
        }
    }

    /**
     * 根据参数类型创建基础输入控件。
     * @param context 上下文
     * @param inputDef 参数定义
     * @param currentValue 当前值
     * @return 对应类型的控件
     */
    fun createBaseViewForInputType(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        onItemSelectedCallback: ((String) -> Unit)? = null
    ): View {
        return when (inputDef.staticType) {
            ParameterType.BOOLEAN -> createSwitch(context, currentValue as? Boolean ?: false)
            ParameterType.ENUM -> createSpinner(
                context,
                inputDef.options,
                currentValue as? String ?: inputDef.defaultValue as? String,
                onItemSelectedCallback
            )
            else -> createTextInputLayout(
                context,
                inputDef.staticType == ParameterType.NUMBER,
                currentValue,
                inputDef.hint
            )
        }
    }

    /**
     * 创建开关控件。
     */
    fun createSwitch(context: Context, isChecked: Boolean): SwitchCompat {
        return SwitchCompat(context).apply { this.isChecked = isChecked }
    }

    /**
     * 创建芯片按钮组。
     * @param context 上下文
     * @param options 选项值列表（序列化值）
     * @param currentValue 当前选中的值（序列化值）
     * @param onSelectionChanged 选项变化时的回调，返回选中的值（序列化值）
     * @param optionsStringRes 可选的本地化文本资源ID列表，与 options 一一对应
     * @return 包含标签和 ChipGroup 的视图
     */
    fun createChipGroup(
        context: Context,
        options: List<String>,
        currentValue: String?,
        onSelectionChanged: ((String) -> Unit)? = null,
        optionsStringRes: List<Int>? = null
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val chipGroup = com.google.android.material.chip.ChipGroup(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isSingleSelection = true
            setChipSpacingHorizontal((8 * context.resources.displayMetrics.density).toInt())
        }

        val selectedValue = currentValue ?: options.firstOrNull()
        val selectedIndex = options.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
        val inflater = LayoutInflater.from(context)

        options.forEachIndexed { index, optionValue ->
            val displayText = if (optionsStringRes != null && index < optionsStringRes.size) {
                context.getString(optionsStringRes[index])
            } else {
                optionValue
            }

            val chip = inflater.inflate(R.layout.chip_filter, chipGroup, false) as com.google.android.material.chip.Chip
            chip.text = displayText
            chip.tag = optionValue  // 使用 tag 存储序列化值
            chip.isChecked = (index == selectedIndex)
            chipGroup.addView(chip)
        }

        // 设置监听器，返回选中的值（序列化值）
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedChip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds.first())
                checkedChip?.let {
                    val selectedValueFromTag = it.tag as? String
                    if (selectedValueFromTag != null) {
                        onSelectionChanged?.invoke(selectedValueFromTag)
                    }
                }
            }
        }

        container.addView(chipGroup)
        return container
    }

    /**
     * 创建带选择器功能的输入框。
     * 点击输入框时会触发对应的选择器回调。
     * @param context 上下文
     * @param currentValue 当前值
     * @param pickerType 选择器类型
     * @param hint 提示文本
     * @param onPickerClicked 点击输入框时的回调，返回选择的结果
     * @return 包含 EditText 和选择图标的容器视图
     */
    fun createPickerInput(
        context: Context,
        currentValue: Any?,
        pickerType: PickerType,
        hint: String?,
        onPickerClicked: (() -> Unit)? = null
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val textInputLayout = TextInputLayout(context).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val editText = TextInputEditText(context).apply {
            val valueText = when (currentValue) {
                is Number -> if (currentValue.toDouble() == currentValue.toLong().toDouble()) {
                    currentValue.toLong().toString()
                } else {
                    currentValue.toString()
                }
                else -> currentValue?.toString() ?: ""
            }
            setText(valueText)
            isFocusable = false
            isClickable = true
            setOnClickListener {
                onPickerClicked?.invoke()
            }
        }

        textInputLayout.addView(editText)
        container.addView(textInputLayout)

        // 添加选择器图标
        val pickerIcon = ImageButton(context).apply {
            setImageResource(R.drawable.ic_picker)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * context.resources.displayMetrics.density).toInt()
            }
            background = null
            contentDescription = "选择"
            setOnClickListener {
                onPickerClicked?.invoke()
            }
        }
        container.addView(pickerIcon)

        return container
    }

    /**
     * 创建下拉选择器。
     */
    fun createSpinner(
        context: Context,
        options: List<String>,
        selectedValue: String?,
        onItemSelectedCallback: ((String) -> Unit)? = null
    ): Spinner {
        return Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val selectionIndex = options.indexOf(selectedValue)
            if (selectionIndex != -1) setSelection(selectionIndex)

            // 延迟设置监听器，避免初始化时触发
            post {
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedItem = options[position]
                        // 只在值真正改变时才触发回调
                        if (tag != selectedItem) {
                            tag = selectedItem  // 用 tag 记录当前值
                            onItemSelectedCallback?.invoke(selectedItem)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        // 不需要处理
                    }
                }
                // 设置初始 tag 值
                tag = selectedValue
            }
        }
    }

    /**
     * 创建文本输入框。
     */
    fun createTextInputLayout(
        context: Context,
        isNumber: Boolean,
        currentValue: Any?,
        hint: String? = null
    ): TextInputLayout {
        return TextInputLayout(context).apply {
            this.hint = hint ?: "值"
            val editText = TextInputEditText(context).apply {
                val valueToDisplay = when (currentValue) {
                    is Number -> if (currentValue.toDouble() == currentValue.toLong().toDouble()) {
                        currentValue.toLong().toString()
                    } else {
                        currentValue.toString()
                    }
                    else -> currentValue?.toString() ?: ""
                }
                setText(valueToDisplay)
                inputType = if (isNumber) {
                    InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
            }
            addView(editText)
        }
    }

    /**
     * 创建富文本编辑器（支持变量药丸）。
     */
    fun createRichTextEditor(
        context: Context,
        initialText: String,
        allSteps: List<ActionStep>?,
        tag: String?
    ): View {
        val richEditorLayout = LayoutInflater.from(context)
            .inflate(R.layout.rich_text_editor, null, false)
        val richTextView = richEditorLayout.findViewById<RichTextView>(R.id.rich_text_view)
        richTextView.minHeight = (80 * context.resources.displayMetrics.density).toInt()

        // 设置初始文本，并将变量引用渲染成"药丸"
        // 使用新的 API：直接传递 allSteps，内部使用 PillVariableResolver 和 RoundedBackgroundSpan
        richTextView.setRichText(initialText, allSteps ?: emptyList())

        if (tag != null) richTextView.tag = tag
        return richEditorLayout
    }

    /**
     * 创建变量药丸视图。
     */
    fun createVariablePill(
        context: Context,
        parent: ViewGroup,
        variableReference: String,
        allSteps: List<ActionStep>?,
        onClick: (() -> Unit)?
    ): View {
        val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, parent, false)
        val pillText = pill.findViewById<TextView>(R.id.pill_text)
        pillText.text = PillRenderer.getDisplayNameForVariableReference(
            variableReference,
            allSteps ?: emptyList(),
            context
        )
        onClick?.let { pill.setOnClickListener { it() } }
        return pill
    }

    /**
     * 创建滑块控件（带数值显示）。
     * @param context 上下文
     * @param label 标签文本
     * @param valueFrom 最小值
     * @param valueTo 最大值
     * @param stepSize 步长
     * @param currentValue 当前值
     * @param valueFormatter 数值格式化函数（例如 "3 次" 或 "1000 ms"）
     * @return 包含标签、数值和滑块的 LinearLayout
     */
    fun createSliderWithLabel(
        context: Context,
        label: String,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        currentValue: Float,
        valueFormatter: (Float) -> String
    ): LinearLayout {
        val density = context.resources.displayMetrics.density

        // 标题行：标签 + 数值
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvLabel = TextView(context).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvValue = TextView(context).apply {
            text = valueFormatter(currentValue)
            gravity = Gravity.END
        }

        header.addView(tvLabel)
        header.addView(tvValue)

        // 滑块
        val slider = Slider(context).apply {
            tag = "slider"
            this.valueFrom = valueFrom
            this.valueTo = valueTo
            this.stepSize = stepSize
            this.value = currentValue
        }

        // 更新数值显示
        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = valueFormatter(value)
        }

        // 容器
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 设置默认的 LayoutParams
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(header)
            addView(slider)
        }
    }

    /**
     * 从输入行视图中读取参数值。
     * 通过遍历视图树查找可识别的控件，不依赖具体的视图类型。
     * @param view 输入行视图
     * @param inputDef 参数定义
     * @return 读取到的值
     */
    fun readValueFromInputRow(view: View, inputDef: InputDefinition): Any? {
        val valueContainer = view.findViewById<ViewGroup>(R.id.input_value_container) ?: return null
        return findValueInViewTree(valueContainer, inputDef)
    }

    /**
     * 在视图树中递归查找可读取值的控件
     */
    private fun findValueInViewTree(view: View, inputDef: InputDefinition): Any? {
        return when (view) {
            is TextInputLayout -> view.editText?.text?.toString()
            is SwitchCompat -> view.isChecked
            is Spinner -> view.selectedItem?.toString()
            is Slider -> {
                when (inputDef.staticType) {
                    ParameterType.NUMBER -> view.value.toInt()
                    else -> view.value
                }
            }
            is ChipGroup -> {
                val checkedId = view.checkedChipId
                if (checkedId != View.NO_ID) {
                    view.findViewById<com.google.android.material.chip.Chip>(checkedId)?.text?.toString()
                } else {
                    null
                }
            }
            is ViewGroup -> {
                // 优先查找 RichTextView
                if (inputDef.supportsRichText) {
                    view.findViewById<RichTextView>(R.id.rich_text_view)?.getRawText()
                } else {
                    // 递归查找子视图
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        val value = findValueInViewTree(child, inputDef)
                        if (value != null) return value
                    }
                    null
                }
            }
            else -> null
        }
    }
}
