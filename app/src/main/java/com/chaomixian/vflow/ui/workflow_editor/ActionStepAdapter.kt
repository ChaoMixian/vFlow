// main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt

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
import android.text.style.CharacterStyle
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
import com.chaomixian.vflow.core.module.ConditionalOption
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.color.MaterialColors
import java.util.*
import kotlin.math.roundToInt

// --- 数据类与自定义 Span 定义 ---

/** 用于查找魔法变量来源信息的辅助数据类 */
private data class SourceInfo(
    val outputName: String,
    val color: Int,
    val conditionalOptions: List<ConditionalOption>?
)

/** 用于标记一个静态值的药丸(Pill) */
private class StaticPillSpan : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {}
}

/** 用于标记一个魔法变量来源的药丸(Pill) */
private class MagicVariableSourceSpan(val parameterId: String) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) {}
}

/** 用于标记一个可点击的条件选项药丸(Pill) */
private class ConditionalPillSpan(
    val parameterId: String,
    val options: List<ConditionalOption>
) : ClickableSpan() {
    // 点击事件由 Adapter 统一处理，此处为空实现
    override fun onClick(widget: View) {}
    // 覆盖此方法以防止系统给药丸文本添加下划线
    override fun updateDrawState(ds: TextPaint) {}
}


/**
 * 工作流编辑器中步骤列表的核心适配器。
 * 负责渲染每个动作步骤卡片、处理拖拽、点击等交互。
 */
class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val hideConnections: Boolean,
    private val onEditClick: (position: Int, inputId: String?) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onParameterPillClick: (position: Int, parameterId: String, options: List<ConditionalOption>) -> Unit
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

    override fun getItemCount(): Int = actionSteps.size

    inner class ActionStepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_action)
        private val indentSpace: Space = itemView.findViewById(R.id.indent_space)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.content_container)
        private val categoryColorBar: View = itemView.findViewById(R.id.category_color_bar)

        fun bind(step: ActionStep, position: Int, allSteps: List<ActionStep>) {
            val module = ModuleRegistry.getModule(step.moduleId) ?: return

            // 根据缩进级别设置左侧间距
            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            // 设置分类颜色条
            val categoryColor = ContextCompat.getColor(context, getCategoryColor(module.metadata.category))
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * context.resources.displayMetrics.density)
                setColor(categoryColor)
            }
            categoryColorBar.background = drawable

            // 动态构建摘要并添加到内容容器
            contentContainer.removeAllViews()
            val rawSummary = module.getSummary(context, step)
            val finalSummary = processSummarySpans(rawSummary, step, allSteps)

            // 拼接行号和最终摘要
            val prefix = "#$position "
            val spannablePrefix = SpannableStringBuilder(prefix).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                val prefixColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val finalTitle = SpannableStringBuilder().append(spannablePrefix).append(finalSummary ?: module.metadata.name)
            val headerView = createHeaderRow(finalTitle)
            contentContainer.addView(headerView)

            // 绑定点击事件
            deleteButton.setOnClickListener {
                if(adapterPosition != RecyclerView.NO_POSITION) onDeleteClick(adapterPosition)
            }
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClick(adapterPosition, null)
                }
            }

            // 根据模块行为决定是否显示删除按钮
            val behavior = module.blockBehavior
            val isDeletable = position != 0 && (behavior.isIndividuallyDeletable || behavior.type == BlockType.BLOCK_START || behavior.type == BlockType.NONE)
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        /**
         * 创建摘要标题行，并附加上解决点击冲突的触摸监听器。
         */
        private fun createHeaderRow(summary: CharSequence): View {
            val textView = TextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                movementMethod = LinkMovementMethod.getInstance() // 激活 ClickableSpan
                highlightColor = Color.TRANSPARENT // 避免点击药丸时出现背景色块

                // --- 核心修复：移除字体内部的额外边距，解决垂直对齐问题 ---
                includeFontPadding = false
            }

            // 核心交互：通过自定义触摸监听器，区分“点击药丸”和“点击卡片空白处”
            textView.setOnTouchListener { v, event ->
                val widget = v as TextView
                val text = widget.text
                if (text is Spanned && event.action == MotionEvent.ACTION_UP) {
                    // 计算触摸点在文本中的位置
                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                    val layout = widget.layout
                    val line = layout.getLineForVertical(y)
                    val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                    // 检查该位置是否有 ClickableSpan
                    val links = text.getSpans(offset, offset, ClickableSpan::class.java)
                    if (links.isNotEmpty()) {
                        // 如果点击在药丸上，不做任何事，让 LinkMovementMethod 自己处理
                        false
                    } else {
                        // 如果点击在空白处，手动触发整个卡片的点击事件
                        itemView.performClick()
                        true
                    }
                } else {
                    false
                }
            }
            return textView
        }

        private fun getCategoryColor(category: String): Int = when (category) {
            "设备" -> R.color.category_device
            "逻辑控制" -> R.color.category_logic
            "变量" -> R.color.category_variable
            "触发器" -> R.color.category_trigger
            else -> com.google.android.material.R.color.material_dynamic_neutral30
        }

        /**
         * 查找魔法变量的来源信息（模块名、颜色、条件选项等）。
         */
        private fun findSourceInfo(variableRef: String?, allSteps: List<ActionStep>): SourceInfo? {
            if (variableRef == null || !variableRef.startsWith("{{")) return null
            val (sourceStepId, sourceOutputId) = variableRef.removeSurrounding("{{", "}}").split('.').let { it.getOrNull(0) to it.getOrNull(1) }
            if (sourceStepId == null || sourceOutputId == null) return null
            val sourceStep = allSteps.find { it.id == sourceStepId }
            val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) } ?: return null
            val sourceOutput = sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId } ?: return null
            return SourceInfo(
                outputName = sourceOutput.name,
                color = ContextCompat.getColor(context, getCategoryColor(sourceModule.metadata.category)),
                conditionalOptions = sourceOutput.conditionalOptions
            )
        }

        /**
         * 核心渲染逻辑：处理摘要中的 Spanned 文本，将其中的占位符 Span 替换为最终带样式的药丸。
         */
        private fun processSummarySpans(summary: CharSequence?, step: ActionStep, allSteps: List<ActionStep>): CharSequence? {
            if (summary !is Spanned) return summary
            val spannable = SpannableStringBuilder(summary)
            // 从后往前遍历，防止索引错乱
            spannable.getSpans<CharacterStyle>().reversed().forEach { span ->
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                when (span) {
                    is MagicVariableSourceSpan -> {
                        val sourceInfo = findSourceInfo(step.parameters[span.parameterId]?.toString(), allSteps)
                        val pillText = " ${sourceInfo?.outputName ?: "变量"} "
                        spannable.replace(start, end, pillText)
                        val backgroundSpan = RoundedBackgroundSpan(context, sourceInfo?.color ?: Color.GRAY, false)
                        spannable.setSpan(backgroundSpan, start, start + pillText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    is ConditionalPillSpan -> {
                        val sourceInfo = findSourceInfo(step.parameters["condition"]?.toString(), allSteps)
                        // 如果魔法变量的源头没有定义条件选项（如普通布尔值），则隐藏此药丸
                        if (sourceInfo?.conditionalOptions.isNullOrEmpty()) {
                            spannable.replace(start, end, "")
                        } else {
                            val currentChoice = step.parameters[span.parameterId] as? String
                            val pillText = " ${currentChoice ?: sourceInfo?.conditionalOptions?.firstOrNull()?.displayName ?: "..."} "
                            spannable.replace(start, end, pillText)
                            val backgroundSpan = RoundedBackgroundSpan(context, ContextCompat.getColor(context, R.color.category_logic), true)
                            val clickableSpan = object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    if (adapterPosition != RecyclerView.NO_POSITION) {
                                        onParameterPillClick(adapterPosition, span.parameterId, sourceInfo!!.conditionalOptions!!)
                                    }
                                }
                                override fun updateDrawState(ds: TextPaint) {}
                            }
                            spannable.setSpan(backgroundSpan, start, start + pillText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(clickableSpan, start, start + pillText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    is StaticPillSpan -> {
                        val color = ContextCompat.getColor(context, R.color.static_pill_color)
                        val backgroundSpan = RoundedBackgroundSpan(context, color, false)
                        spannable.setSpan(backgroundSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                spannable.removeSpan(span)
            }
            return spannable
        }
    }
}

/**
 * 构建带药丸样式的 Spanned 文本的工具类。
 */
object PillUtil {
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val start = builder.length
                    builder.append(part.text)
                    val end = builder.length
                    val span = if (part.parameterId == "checkMode") {
                        ConditionalPillSpan(part.parameterId, part.options ?: emptyList())
                    } else if (part.isVariable && part.parameterId != null) {
                        MagicVariableSourceSpan(part.parameterId)
                    } else {
                        StaticPillSpan()
                    }
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return builder
    }
    data class Pill(val text: String, val isVariable: Boolean, val parameterId: String? = null, val options: List<ConditionalOption>? = null)
}

/**
 * 自定义 ReplacementSpan，用于绘制带圆角背景的药丸(Pill)。
 */
class RoundedBackgroundSpan(
    context: Context,
    private val backgroundColor: Int,
    private val isClickable: Boolean
) : ReplacementSpan() {
    private val textColor: Int = Color.WHITE
    private val cornerRadius: Float = 25f
    private val paddingHorizontal: Float = 12f

    // 核心修改：使用非对称的垂直内边距来实现视觉居中
    private val paddingTop: Float = 2f
    private val paddingBottom: Float = 4f

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // 让 FontMetrics 把垂直内边距也计算在内，确保行高足够
        if (fm != null) {
            fm.top = (fm.top - paddingTop).toInt()
            fm.bottom = (fm.bottom + paddingBottom).toInt()
        }
        return (paint.measureText(text, start, end) + paddingHorizontal * 2).roundToInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val width = paint.measureText(text, start, end)
        // 使用包含了垂直内边距的 top 和 bottom 来绘制矩形背景
        val rect = android.graphics.RectF(x, top.toFloat(), x + width + paddingHorizontal * 2, bottom.toFloat() - (paddingTop + paddingBottom) / 2)

        val originalColor = paint.color
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        paint.color = textColor
        // 如果可点击，绘制一个细微的下划线作为视觉提示
//        if (isClickable) {
//            val underlinePaint = Paint(paint).apply { alpha = 150; strokeWidth = 2f }
//            val lineY = rect.bottom - (paddingBottom / 2)
//            canvas.drawLine(rect.left + 6f, lineY, rect.right - 6f, lineY, underlinePaint)
//        }

        // 绘制文本，y 坐标是文本的 baseline，我们的 padding 方案已经使其视觉居中
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paint)

        // 恢复画笔原始颜色
        paint.color = originalColor
    }
}