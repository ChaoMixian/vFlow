// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.color.MaterialColors
import java.util.*
import kotlin.math.roundToInt

/**
 * 用于标记一个可点击参数的 Span。
 * @param parameterId 对应 InputDefinition 的 ID。
 * @param isVariable 标记这个参数的原始值是否是魔法变量。
 * @param isModuleOption 标记这个参数是否是模块自身的配置选项。
 */
private class ParameterPillSpan(
    val parameterId: String,
    val isVariable: Boolean,
    val isModuleOption: Boolean
) : ClickableSpan() {
    override fun onClick(widget: View) {}
    override fun updateDrawState(ds: TextPaint) {}
}

/**
 * 用于查找魔法变量来源信息的辅助数据类
 * @param outputName 变量的可读名称
 * @param color 来源模块的分类颜色
 */
private data class SourceInfo(
    val outputName: String,
    val color: Int
)

/**
 * 工作流编辑器中步骤列表的核心适配器。
 */
class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val hideConnections: Boolean,
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onParameterPillClick: (position: Int, parameterId: String) -> Unit
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
        return ActionStepViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionStepViewHolder, position: Int) {
        val step = actionSteps[position]
        holder.bind(step, position, actionSteps)
    }

    override fun getItemCount() = actionSteps.size

    inner class ActionStepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container)
        private val categoryColorBar: View = itemView.findViewById(R.id.category_color_bar)

        fun bind(step: ActionStep, position: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            val categoryColor = ContextCompat.getColor(context, PillUtil.getCategoryColor(module.metadata.category))
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * context.resources.displayMetrics.density)
                setColor(categoryColor)
            }
            categoryColorBar.background = drawable

            contentContainer.removeAllViews()

            val customPreview = module.uiProvider?.createPreview(context, contentContainer, step)
            if (customPreview != null) {
                contentContainer.addView(customPreview)
            } else {
                val rawSummary = module.getSummary(context, step)
                val finalSummary = PillUtil.processSummarySpans(context, rawSummary, allSteps, step)
                val prefix = "#$position "
                val spannablePrefix = SpannableStringBuilder(prefix).apply {
                    setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val prefixColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                    setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val finalTitle = SpannableStringBuilder().append(spannablePrefix).append(finalSummary ?: module.metadata.name)
                val headerView = createHeaderRow(finalTitle)
                contentContainer.addView(headerView)
            }

            deleteButton.setOnClickListener {
                if(adapterPosition != RecyclerView.NO_POSITION) onDeleteClick(adapterPosition)
            }
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }

            val behavior = module.blockBehavior
            val isDeletable = position != 0 && (behavior.isIndividuallyDeletable || behavior.type == BlockType.BLOCK_START || behavior.type == BlockType.NONE)
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        private fun createHeaderRow(summary: CharSequence): View {
            val textView = TextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = Color.TRANSPARENT
                includeFontPadding = false
            }

            textView.setLineSpacing(0f, 1.4f)

            textView.setOnTouchListener { v, event ->
                val widget = v as TextView
                val text = widget.text
                if (text is Spanned && event.action == MotionEvent.ACTION_UP) {
                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                    val layout = widget.layout
                    val line = layout.getLineForVertical(y)
                    if (x < 0 || x > layout.getLineWidth(line)) {
                        itemView.performClick()
                        return@setOnTouchListener true
                    }
                    val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                    val links = text.getSpans(offset, offset, ParameterPillSpan::class.java)
                    if (links.isNotEmpty()) {
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onParameterPillClick(adapterPosition, links[0].parameterId)
                        }
                        true
                    } else {
                        itemView.performClick()
                        true
                    }
                } else {
                    false
                }
            }
            return textView
        }
    }
}

object PillUtil {
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val start = builder.length
                    builder.append(" ${part.text} ")
                    val end = builder.length
                    val span = ParameterPillSpan(part.parameterId, part.isVariable, part.isModuleOption)
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return builder
    }

    data class Pill(
        val text: String,
        val isVariable: Boolean,
        val parameterId: String,
        val isModuleOption: Boolean = false
    )

    fun getCategoryColor(category: String): Int = when (category) {
        "设备" -> R.color.category_device
        "逻辑控制" -> R.color.category_logic
        "变量" -> R.color.category_variable
        "触发器" -> R.color.category_trigger
        "其他" -> com.google.android.material.R.color.material_dynamic_neutral30
        else -> com.google.android.material.R.color.material_dynamic_neutral30
    }

    private fun findSourceInfo(context: Context, variableRef: String, allSteps: List<ActionStep>): SourceInfo? {
        if (!variableRef.startsWith("{{")) return null
        val (sourceStepId, sourceOutputId) = variableRef.removeSurrounding("{{", "}}").split('.').let { it.getOrNull(0) to it.getOrNull(1) }
        if (sourceStepId == null || sourceOutputId == null) return null
        val sourceStep = allSteps.find { it.id == sourceStepId }
        val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) } ?: return null
        val sourceOutput = sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId } ?: return null
        val sourceColor = ContextCompat.getColor(context, getCategoryColor(sourceModule.metadata.category))
        return SourceInfo(
            outputName = sourceOutput.name,
            color = sourceColor
        )
    }

    fun processSummarySpans(
        context: Context,
        summary: CharSequence?,
        allSteps: List<ActionStep>,
        currentStep: ActionStep
    ): CharSequence? {
        if (summary !is Spanned) return summary
        val spannable = SpannableStringBuilder(summary)

        spannable.getSpans<ParameterPillSpan>().reversed().forEach { span ->
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)

            val color: Int
            val pillText: CharSequence

            if (span.isVariable) {
                val reference = spannable.substring(start, end).trim()
                val sourceInfo = findSourceInfo(context, reference, allSteps)
                pillText = " ${sourceInfo?.outputName ?: "变量"} "
                color = sourceInfo?.color ?: ContextCompat.getColor(context, R.color.variable_pill_color)
            } else if (span.isModuleOption) {
                pillText = spannable.subSequence(start, end)
                val currentModule = ModuleRegistry.getModule(currentStep.moduleId)
                color = if (currentModule != null) {
                    ContextCompat.getColor(context, getCategoryColor(currentModule.metadata.category))
                } else {
                    ContextCompat.getColor(context, R.color.static_pill_color)
                }
            } else {
                pillText = spannable.subSequence(start, end)
                color = ContextCompat.getColor(context, R.color.static_pill_color)
            }

            spannable.replace(start, end, pillText)
            val newEnd = start + pillText.length

            val backgroundSpan = RoundedBackgroundSpan(context, color)
            spannable.setSpan(backgroundSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val newClickableSpan = ParameterPillSpan(span.parameterId, span.isVariable, span.isModuleOption)
            spannable.setSpan(newClickableSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}

class RoundedBackgroundSpan(
    context: Context,
    private val backgroundColor: Int
) : ReplacementSpan() {
    private val textColor: Int = Color.WHITE
    private val cornerRadius: Float = 25f
    private val paddingHorizontal: Float = 12f
    private val paddingVertical: Float = 6f

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val textWidth = paint.measureText(text, start, end)
        if (fm != null) {
            val fmPaint = paint.fontMetricsInt
            val extra = paddingVertical.roundToInt()
            fm.ascent = fmPaint.ascent - extra
            fm.descent = fmPaint.descent + extra
            fm.top = fmPaint.top - extra
            fm.bottom = fmPaint.bottom + extra
        }
        return (textWidth + paddingHorizontal * 2).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textWidth = paint.measureText(text, start, end)
        val rectTop = y + paint.fontMetrics.ascent - paddingVertical
        val rectBottom = y + paint.fontMetrics.descent + paddingVertical
        val rect = android.graphics.RectF(x, rectTop, x + textWidth + paddingHorizontal * 2, rectBottom)
        val originalColor = paint.color
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paint)
        paint.color = originalColor
    }
}