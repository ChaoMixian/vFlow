// 文件: main/java/com/chaomixian/vflow/core/workflow/module/core/CoreScreenOperationModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.view.isEmpty
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * CoreScreenOperationModule 的自定义 UI 提供者。
 * 复用 screen_operation 的 UI，但简化为仅支持坐标输入和 vFlow Core 执行。
 */
class CoreScreenOperationModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val typeGroup: ChipGroup = view.findViewById(R.id.cg_operation_type)
        val chipTap: Chip = view.findViewById(R.id.chip_tap)
        val chipLongPress: Chip = view.findViewById(R.id.chip_long_press)
        val chipSwipe: Chip = view.findViewById(R.id.chip_swipe)

        val startContainer: FrameLayout = view.findViewById(R.id.container_target_start)
        val endContainer: FrameLayout = view.findViewById(R.id.container_target_end)

        val durationContainer: LinearLayout = view.findViewById(R.id.container_duration)
        val durationSlider: Slider = view.findViewById(R.id.slider_duration)
        val durationText: TextView = view.findViewById(R.id.tv_duration_value)

        val advancedHeader: LinearLayout = view.findViewById(R.id.layout_advanced_header)
        val advancedContainer: LinearLayout = view.findViewById(R.id.container_advanced_options)

        // 动态创建的输入框引用
        var startInputView: View? = null
        var endInputView: View? = null
        var allSteps: List<ActionStep>? = null
    }

    override fun getHandledInputIds(): Set<String> = setOf("operation_type", "target", "target_end", "duration")

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_screen_operation_editor, parent, false)
        val holder = ViewHolder(view)
        holder.allSteps = allSteps

        val module = CoreScreenOperationModule()
        val inputs = module.getInputs()

        // 隐藏高级选项区域（Core模块固定使用vFlow Core执行）
        holder.advancedHeader.isVisible = false
        holder.advancedContainer.isVisible = false

        // 恢复操作类型
        val opType = currentParameters["operation_type"] as? String ?: "点击"
        when (opType) {
            "长按" -> holder.chipLongPress.isChecked = true
            "滑动" -> holder.chipSwipe.isChecked = true
            else -> holder.chipTap.isChecked = true
        }

        // 动态创建输入框
        fun setupInput(container: FrameLayout, inputId: String, label: String): View {
            container.removeAllViews()
            val inputDef = inputs.find { it.id == inputId }!!.copy(name = label)
            val inputView = createInputRow(context, inputDef, currentParameters[inputId], onMagicVariableRequested, allSteps)
            container.addView(inputView)
            return inputView
        }

        holder.startInputView = setupInput(holder.startContainer, "target", "目标位置 / 起点")
        holder.endInputView = setupInput(holder.endContainer, "target_end", "滑动终点")

        // 恢复持续时间
        val duration = (currentParameters["duration"] as? Number)?.toFloat()
            ?: (if (opType == "滑动") 500f else if (opType == "长按") 1000f else 0f)
        holder.durationSlider.value = duration.coerceIn(0f, 5000f)
        holder.durationText.text = "${duration.toInt()} ms"

        // 更新 UI 状态
        fun updateUiState() {
            val type = when {
                holder.chipLongPress.isChecked -> "长按"
                holder.chipSwipe.isChecked -> "滑动"
                else -> "点击"
            }

            holder.endContainer.isVisible = (type == "滑动")
            holder.durationContainer.isVisible = (type == "滑动" || type == "长按")

            val startLabel = if (type == "滑动") "滑动起点" else "目标位置"
            holder.startInputView?.findViewById<TextView>(R.id.input_name)?.text = startLabel

            // 智能设置默认时间
            if (type != "点击" && holder.durationSlider.value == 0f) {
                val newDuration = if (type == "滑动") 500f else 1000f
                holder.durationSlider.value = newDuration
                holder.durationText.text = "${newDuration.toInt()} ms"
            }
        }
        updateUiState()

        // 监听器
        holder.typeGroup.setOnCheckedStateChangeListener { _, _ ->
            updateUiState()
            onParametersChanged()
        }
        holder.durationSlider.addOnChangeListener { _, value, _ ->
            holder.durationText.text = "${value.toInt()} ms"
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val type = when {
            h.chipLongPress.isChecked -> "长按"
            h.chipSwipe.isChecked -> "滑动"
            else -> "点击"
        }

        val params = mutableMapOf<String, Any?>()
        params["operation_type"] = type
        params["duration"] = h.durationSlider.value.toDouble()
        readInputValue(h.startInputView)?.let { params["target"] = it }
        if (type == "滑动") {
            readInputValue(h.endInputView)?.let { params["target_end"] = it }
        }

        return params
    }

    /**
     * 创建坐标输入行。
     * 支持手动输入坐标（格式 "x,y"）或选择魔术变量。
     */
    private fun createInputRow(
        context: Context,
        inputDef: InputDefinition,
        currentValue: Any?,
        onMagicReq: ((String) -> Unit)?,
        allSteps: List<ActionStep>?
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.row_editor_input, null)
        row.findViewById<TextView>(R.id.input_name).text = inputDef.name

        val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
        val magicButton = row.findViewById<ImageButton>(R.id.button_magic_variable)

        magicButton.isVisible = inputDef.acceptsMagicVariable
        magicButton.setOnClickListener { onMagicReq?.invoke(inputDef.id) }

        valueContainer.removeAllViews()

        val valStr = currentValue?.toString()
        if (valStr.isMagicVariable() || valStr.isNamedVariable()) {
            // 变量药丸（支持魔法变量和命名变量）
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueContainer, false)
            val displayName = PillRenderer.getDisplayNameForVariableReference(valStr!!, allSteps ?: emptyList())
            pill.findViewById<TextView>(R.id.pill_text).text = displayName
            pill.setOnClickListener { onMagicReq?.invoke(inputDef.id) }
            valueContainer.addView(pill)
        } else {
            // 普通输入框
            val textInputLayout = TextInputLayout(context).apply {
                hint = "输入坐标 x,y (例如: 500,1000)"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            }
            val editText = TextInputEditText(context).apply {
                setText(valStr ?: "")
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
            }
            textInputLayout.addView(editText)
            valueContainer.addView(textInputLayout)
        }
        return row
    }

    /**
     * 从输入行读取值。
     * 如果是药丸则返回 null（保持原值），如果是输入框则返回文本。
     */
    private fun readInputValue(view: View?): String? {
        if (view == null) return null
        val container = view.findViewById<ViewGroup>(R.id.input_value_container)
        if (container.isEmpty()) return null

        val child = container.getChildAt(0)
        // 如果是药丸，返回 null (ActionEditorSheet 会保持原值)
        // 如果是 TextInputLayout，返回文本
        return if (child is TextInputLayout) {
            child.editText?.text?.toString()
        } else {
            null // 是药丸，值未变
        }
    }
}
