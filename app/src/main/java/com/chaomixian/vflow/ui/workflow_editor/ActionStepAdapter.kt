package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.card.MaterialCardView // 导入 MaterialCardView
import java.util.*
import kotlin.math.roundToInt

// 用于标记 "静态值" 药丸的 span.
private class StaticPillSpan : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {
        // 空操作。这是一个标记 span。
    }
}

// 用于标记 "魔法变量" 药丸的 span, 携带参数 ID.
private class MagicVariablePlaceholderSpan(val parameterId: String) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {
        // 空操作。这是一个标记 span。
    }
}

class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val hideConnections: Boolean,
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ActionStepAdapter.ActionStepViewHolder>() {

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition > 0 && toPosition > 0 && fromPosition < actionSteps.size && toPosition < actionSteps.size) {
            Collections.swap(actionSteps, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionStepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_step, parent, false)
        return ActionStepViewHolder(view, onEditClick, hideConnections)
    }

    override fun onBindViewHolder(holder: ActionStepViewHolder, position: Int) {
        val step = actionSteps[position]
        holder.itemView.tag = step
        // 在每次绑定时传递完整的、最新的列表。
        holder.bind(step, position, actionSteps)
        holder.deleteButton.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount(): Int = actionSteps.size

    class ActionStepViewHolder(
        itemView: View,
        private val onEditClick: (position: Int, inputId: String?) -> Unit,
        private val hideConnections: Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val itemContainer: LinearLayout = itemView.findViewById(R.id.card_action_item) // 外层容器
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container) // 原来的内容容器
        private val categoryColorBar: View = itemView.findViewById(R.id.category_color_bar) // 彩色指示条

        fun bind(step: ActionStep, position: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            // 设置彩色指示条的颜色
            val categoryColor = ContextCompat.getColor(context, getCategoryColor(module.metadata.category))
            categoryColorBar.setBackgroundColor(categoryColor)

            itemContainer.setOnClickListener { // 整个卡片都可以点击
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }
            contentContainer.removeAllViews()

            val rawSummary = module.getSummary(context, step)
            // 核心逻辑：处理摘要文本，用带颜色的 span 替换标记。
            val finalSummary = processSummarySpans(rawSummary, step, allSteps)

            val connectableInputs = module.getInputs().filter { it.acceptsMagicVariable }
            val outputs = module.getDynamicOutputs(step)

            val headerRow = createHeaderRow(step, module, finalSummary, connectableInputs)
            contentContainer.addView(headerRow)

            if (!hideConnections) {
                if (rawSummary == null) { // 如果有摘要，参数已经显示在其中了。
                    connectableInputs.forEach { inputDef ->
                        contentContainer.addView(createParameterRow(step, inputDef.id, inputDef.name, true, allSteps))
                    }
                }
                outputs.forEach { outputDef ->
                    contentContainer.addView(createParameterRow(step, outputDef.id, outputDef.name, false, allSteps))
                }
            }

            val behavior = module.blockBehavior
            val isDeletable = when {
                position == 0 -> false
                behavior.isIndividuallyDeletable -> true
                behavior.type == BlockType.BLOCK_START -> true
                behavior.type == BlockType.NONE -> true
                else -> false
            }
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        private fun createHeaderRow(step: ActionStep, module: com.chaomixian.vflow.core.module.ActionModule, summary: CharSequence?, inputs: List<InputDefinition>): View {
            val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, contentContainer, false)
            val icon = row.findViewById<ImageView>(R.id.icon_action_type)
            val nameTextView = row.findViewById<TextView>(R.id.parameter_name)
            val inputNode = row.findViewById<ImageView>(R.id.input_node)

            icon?.setImageResource(module.metadata.iconRes)
            icon?.isVisible = true

            if (summary != null) {
                nameTextView.text = summary
                inputNode?.isVisible = inputs.isNotEmpty() && !hideConnections
                if (inputs.isNotEmpty()) {
                    inputNode?.tag = "input_${step.id}_${inputs.first().id}"
                }
            } else {
                nameTextView.text = module.metadata.name
                inputNode?.isVisible = false
            }

            row.findViewById<View>(R.id.parameter_value_container).isVisible = false
            row.findViewById<View>(R.id.output_node).isVisible = false
            nameTextView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)

            return row
        }

        private fun createParameterRow(step: ActionStep, paramId: String, paramName: String, isInput: Boolean, allSteps: List<ActionStep>): View {
            val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, contentContainer, false)
            val name = row.findViewById<TextView>(R.id.parameter_name)
            val icon = row.findViewById<ImageView>(R.id.icon_action_type)
            val inputPoint = row.findViewById<ImageView>(R.id.input_node)
            val outputPoint = row.findViewById<ImageView>(R.id.output_node)
            val valueContainer = row.findViewById<FrameLayout>(R.id.parameter_value_container)

            icon?.isVisible = false
            name.text = paramName

            if (isInput) {
                inputPoint?.isVisible = true
                inputPoint?.tag = "input_${step.id}_${paramId}"
                val paramValue = step.parameters[paramId]
                val valueStr = paramValue?.toString()
                if (!valueStr.isNullOrBlank()) {
                    val pill = createPillView(valueStr, valueContainer, allSteps)
                    valueContainer.addView(pill)
                    valueContainer.isVisible = true
                } else {
                    valueContainer.isVisible = false
                }
                row.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onEditClick(adapterPosition, paramId)
                    }
                }
            } else {
                outputPoint?.isVisible = true
                outputPoint?.tag = "output_${step.id}_${paramId}"
            }
            return row
        }

        private fun createPillView(valueStr: String, parent: ViewGroup, allSteps: List<ActionStep>): View {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, parent, false)
            val pillText = pill.findViewById<TextView>(R.id.pill_text)
            val pillBackground = pill.background.mutate() as android.graphics.drawable.GradientDrawable

            if (valueStr.startsWith("{{")) {
                pillText.text = "已连接变量"
                val color = getMagicVariablePillColor(valueStr, allSteps)
                pillBackground.setColor(color)
            } else {
                pillText.text = valueStr
                pillBackground.setColor(ContextCompat.getColor(context, R.color.static_pill_color))
            }
            return pill
        }

        private fun getCategoryColor(category: String): Int {
            return when (category) {
                "设备" -> R.color.category_device
                "逻辑控制" -> R.color.category_logic
                "变量" -> R.color.category_variable
                "触发器" -> R.color.category_trigger
                else -> com.google.android.material.R.color.material_dynamic_neutral30
            }
        }

        private fun getMagicVariablePillColor(valueStr: String?, allSteps: List<ActionStep>): Int {
            val defaultColor = ContextCompat.getColor(context, R.color.variable_pill_color)
            if (valueStr == null || !valueStr.startsWith("{{")) return defaultColor

            val sourceStepId = valueStr.removeSurrounding("{{", "}}").split('.').firstOrNull()
            val sourceStep = allSteps.find { it.id == sourceStepId }
            val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) }
            val colorRes = sourceModule?.let { getCategoryColor(it.metadata.category) }

            return if (colorRes != null) ContextCompat.getColor(context, colorRes) else defaultColor
        }

        private fun processSummarySpans(summary: CharSequence?, step: ActionStep, allSteps: List<ActionStep>): CharSequence? {
            if (summary !is Spanned) return summary

            val spannable = SpannableStringBuilder(summary)
            // 反向迭代，以避免在替换 span 时出现索引问题。
            spannable.getSpans<CharacterStyle>().reversed().forEach { span ->
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val color = when (span) {
                    is MagicVariablePlaceholderSpan -> {
                        val paramValue = step.parameters[span.parameterId]?.toString()
                        getMagicVariablePillColor(paramValue, allSteps)
                    }
                    is StaticPillSpan -> ContextCompat.getColor(context, R.color.static_pill_color)
                    else -> null
                }

                if (color != null) {
                    val backgroundSpan = RoundedBackgroundSpan(context, color)
                    spannable.removeSpan(span) // 移除标记
                    spannable.setSpan(backgroundSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) // 添加真正的绘制 span
                }
            }
            return spannable
        }
    }
}

// --- 用于生成带标记文本的 PillUtil ---
object PillUtil {
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val pillText = " ${part.text} "
                    val start = builder.length
                    builder.append(pillText)
                    val end = builder.length
                    val span = if (part.isVariable && part.parameterId != null) {
                        MagicVariablePlaceholderSpan(part.parameterId)
                    } else {
                        StaticPillSpan()
                    }
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            // 这里确保 PillUtil 在创建 Span 时能够正确传递 context
            // 实际使用时，如果 PillUtil 不直接依赖 context 来创建 Span，
            // 那么这个参数可能就不需要了，或者只是用于其他辅助函数。
            // 目前这个 context 并没有被 PillUtil 内部用来创建 RoundedBackgroundSpan,
            // 而是由 ActionStepViewHolder 内部的 processSummarySpans 来创建的。
            // 所以这里的 context 参数是安全的，不会造成功能破坏。
        }
        return builder
    }

    data class Pill(val text: String, val isVariable: Boolean, val parameterId: String? = null)
}

class RoundedBackgroundSpan(
    context: Context,
    private val backgroundColor: Int,
) : ReplacementSpan() {
    private val textColor: Int = ContextCompat.getColor(context, R.color.white)
    private val cornerRadius: Float = 20f
    private val paddingHorizontal: Float = 12f
    private val strokeWidth = 1 * context.resources.displayMetrics.density // 1dp 描边宽度
    private val strokeColor = 0x40000000 // 25% 透明度的黑色作为描边颜色

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return (paint.measureText(text, start, end) + paddingHorizontal * 2).roundToInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val width = paint.measureText(text, start, end)
        val rect = android.graphics.RectF(x, top.toFloat() + 4, x + width + paddingHorizontal * 2, bottom.toFloat() - 4)

        // 绘制背景
        val backgroundPaint = Paint(paint).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

        // 绘制描边
        val strokePaint = Paint(paint).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = this@RoundedBackgroundSpan.strokeWidth
        }
        // 将矩形稍微缩小，使描边绘制在边缘上
        rect.inset(strokeWidth / 2, strokeWidth / 2)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        // 绘制文本
        val textPaint = Paint(paint).apply { color = textColor }
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), textPaint)
    }
}
