// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt

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
import com.google.android.material.card.MaterialCardView
import java.util.*
import kotlin.math.roundToInt

// --- FIX: Implement the required method for the marker spans ---

// Used to mark "static value" pills.
private class StaticPillSpan : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {
        // No-op. This is a marker span.
    }
}

// Used to mark "magic variable" pills, carrying the parameter ID.
private class MagicVariablePlaceholderSpan(val parameterId: String) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {
        // No-op. This is a marker span.
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
        // Pass the full, up-to-date list on each bind.
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
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_action_item)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container)

        fun bind(step: ActionStep, position: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()
            val categoryColor = ContextCompat.getColor(context, getCategoryColor(module.metadata.category))
            cardView.setCardBackgroundColor(categoryColor)
            cardView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }
            contentContainer.removeAllViews()

            val rawSummary = module.getSummary(context, step)
            // The core logic: process the summary text, replacing markers with colored spans.
            val finalSummary = processSummarySpans(rawSummary, step, allSteps)

            val connectableInputs = module.getInputs().filter { it.acceptsMagicVariable }
            val outputs = module.getDynamicOutputs(step)

            val headerRow = createHeaderRow(step, module, finalSummary, connectableInputs)
            contentContainer.addView(headerRow)

            if (!hideConnections) {
                if (rawSummary == null) { // If there's a summary, parameters are already shown in it.
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
            // Iterate backwards to avoid index issues when replacing spans.
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
                    spannable.removeSpan(span) // Remove the marker
                    spannable.setSpan(backgroundSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) // Add the real drawing span
                }
            }
            return spannable
        }
    }
}

// --- PillUtil to generate marked-up text ---
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

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return (paint.measureText(text, start, end) + paddingHorizontal * 2).roundToInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val width = paint.measureText(text, start, end)
        val paintBackground = Paint(paint).apply { color = backgroundColor }
        canvas.drawRoundRect(x, top.toFloat() + 4, x + width + paddingHorizontal * 2, bottom.toFloat() - 4, cornerRadius, cornerRadius, paintBackground)
        val paintText = Paint(paint).apply { color = textColor }
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paintText)
    }
}
