// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
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
        holder.bind(step, position)
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

        fun bind(step: ActionStep, position: Int) {
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

            val summary = module.getSummary(context, step)
            val connectableInputs = module.getInputs().filter { it.acceptsMagicVariable }
            val outputs = module.getDynamicOutputs(step)

            // 1. 创建并添加头部行（无论是摘要还是普通标题）
            val headerRow = createHeaderRow(step, module, summary, connectableInputs)
            contentContainer.addView(headerRow)

            // 2. 如果设置为显示连接，则添加额外的参数行
            if (!hideConnections) {
                // 为没有摘要的模块创建独立的输入行
                if (summary == null) {
                    connectableInputs.forEach { inputDef ->
                        contentContainer.addView(createParameterRow(step, inputDef.id, inputDef.name, true))
                    }
                }
                // 为所有模块创建输出行
                outputs.forEach { outputDef ->
                    contentContainer.addView(createParameterRow(step, outputDef.id, outputDef.name, false))
                }
            }

            // 3. 设置删除按钮的可见性
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

        private fun createParameterRow(step: ActionStep, paramId: String, paramName: String, isInput: Boolean): View {
            val row = LayoutInflater.from(context).inflate(R.layout.row_action_parameter, contentContainer, false)
            val name = row.findViewById<TextView>(R.id.parameter_name)
            val icon = row.findViewById<ImageView>(R.id.icon_action_type)
            val inputPoint = row.findViewById<ImageView>(R.id.input_node)
            val outputPoint = row.findViewById<ImageView>(R.id.output_node)
            val valueContainer = row.findViewById<FrameLayout>(R.id.parameter_value_container)

            icon?.isVisible = false
            name.text = paramName

            if (isInput) {
                inputPoint?.isVisible = true // --- 核心修复 ---
                inputPoint?.tag = "input_${step.id}_${paramId}"
                val paramValue = step.parameters[paramId]
                val valueStr = paramValue?.toString()
                if (!valueStr.isNullOrBlank()) {
                    val pill = createPillView(valueStr, valueContainer)
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
                outputPoint?.isVisible = true // --- 核心修复 ---
                outputPoint?.tag = "output_${step.id}_${paramId}"
            }
            return row
        }

        private fun createPillView(valueStr: String, parent: ViewGroup): View {
            val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, parent, false)
            val pillText = pill.findViewById<TextView>(R.id.pill_text)
            val pillBackground = pill.background.mutate() as android.graphics.drawable.GradientDrawable

            if (valueStr.startsWith("{{")) {
                pillText.text = "已连接变量"
                pillBackground.setColor(ContextCompat.getColor(context, R.color.variable_pill_color))
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
    }
}
// PillUtil 和 RoundedBackgroundSpan 保持不变
object PillUtil {
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val pillText = " ${part.text} "
                    builder.append(pillText)
                    val start = builder.length - pillText.length
                    val end = builder.length
                    builder.setSpan(
                        RoundedBackgroundSpan(context, part.isVariable),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return builder
    }
    data class Pill(val text: String, val isVariable: Boolean)
}
class RoundedBackgroundSpan(context: Context, isVariable: Boolean) : ReplacementSpan() {
    private val backgroundColor: Int
    private val textColor: Int
    private val cornerRadius: Float = 20f
    private val paddingHorizontal: Float = 12f

    init {
        if (isVariable) {
            backgroundColor = ContextCompat.getColor(context, R.color.variable_pill_color)
            textColor = ContextCompat.getColor(context, R.color.white)
        } else {
            backgroundColor = ContextCompat.getColor(context, R.color.static_pill_color)
            textColor = ContextCompat.getColor(context, R.color.white)
        }
    }

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