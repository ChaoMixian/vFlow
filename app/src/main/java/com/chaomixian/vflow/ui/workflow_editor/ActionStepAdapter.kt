// 文件: ActionStepAdapter.kt
// 描述: 工作流编辑器中步骤列表 (RecyclerView) 的适配器。
//      负责显示每个步骤的摘要、处理参数药丸的点击、步骤删除等。

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.Intent
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
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.color.MaterialColors
import java.util.*
import kotlin.math.roundToInt

/**
 * 可点击参数药丸的 Span。这是一个自定义的ClickableSpan，用于标记摘要中的可点击部分。
 * @param parameterId 关联的参数ID，用于告知回调哪个参数被点击了。
 * @param isVariable 是否为魔法变量。
 * @param isModuleOption 是否为模块自身配置选项（例如If模块的条件操作符）。
 */
private class ParameterPillSpan(
    val parameterId: String,
    val isVariable: Boolean,
    val isModuleOption: Boolean
) : ClickableSpan() {
    override fun onClick(widget: View) { /* 点击事件由外部的OnTouchListener统一处理 */ }
    override fun updateDrawState(ds: TextPaint) { /* 外观由下面的RoundedBackgroundSpan处理，这里不需要额外操作 */ }
}

/** 魔法变量来源信息的数据类。 */
private data class SourceInfo(val outputName: String, val color: Int)

/**
 * 工作流步骤列表的 RecyclerView.Adapter。
 */
class ActionStepAdapter(
    private val actionSteps: MutableList<ActionStep>,
    private val hideConnections: Boolean, // TODO: 连接线绘制暂未(不打算)实现
    private val onEditClick: (position: Int, inputId: String?) -> Unit, // 编辑回调
    private val onDeleteClick: (position: Int) -> Unit, // 删除回调
    private val onParameterPillClick: (position: Int, parameterId: String) -> Unit, // 参数药丸点击回调
    // 回调，允许列表项请求启动一个Activity并获取结果
    private val onStartActivityForResult: (position: Int, Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit
) : RecyclerView.Adapter<ActionStepAdapter.ActionStepViewHolder>() {

    /** 移动列表项 (用于拖拽排序)。 */
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

            // 设置缩进
            indentSpace.layoutParams.width = (step.indentationLevel * 24 * context.resources.displayMetrics.density).toInt()

            // 设置类别颜色条
            val categoryColor = ContextCompat.getColor(context, PillUtil.getCategoryColor(module.metadata.category))
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (4 * context.resources.displayMetrics.density)
                setColor(categoryColor)
            }
            categoryColorBar.background = drawable

            contentContainer.removeAllViews()

            // 优先使用模块自定义预览UI，并传递onStartActivityForResult回调
            val customPreview = module.uiProvider?.createPreview(
                context,
                contentContainer,
                step
            ) { intent, callback ->
                // 当自定义预览视图请求启动Activity时，调用Adapter的回调
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onStartActivityForResult(adapterPosition, intent, callback)
                }
            }

            if (customPreview != null) {
                contentContainer.addView(customPreview)
            } else {
                // 否则，使用模块生成的摘要文本
                val rawSummary = module.getSummary(context, step)
                val finalSummary = PillUtil.processSummarySpans(context, rawSummary, allSteps, step)
                // 为摘要添加步骤编号前缀
                val prefix = "#$position "
                val spannablePrefix = SpannableStringBuilder(prefix).apply {
                    setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val prefixColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
                    setSpan(ForegroundColorSpan(prefixColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val finalTitle = SpannableStringBuilder().append(spannablePrefix).append(finalSummary ?: module.metadata.name)
                val headerView = createHeaderRow(finalTitle) // 创建包含摘要的头部视图
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

            // 控制删除按钮的可见性 (触发器不可删，块结束/中间步骤根据配置)
            val behavior = module.blockBehavior
            val isDeletable = position != 0 && (behavior.isIndividuallyDeletable || behavior.type == BlockType.BLOCK_START || behavior.type == BlockType.NONE)
            deleteButton.visibility = if (isDeletable) View.VISIBLE else View.GONE
        }

        /** 创建步骤摘要的 TextView，并处理参数药丸的点击。 */
        private fun createHeaderRow(summary: CharSequence): View {
            val textView = TextView(context).apply {
                text = summary
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                movementMethod = LinkMovementMethod.getInstance() // 使ClickableSpan生效
                highlightColor = Color.TRANSPARENT // 去除点击高亮
                includeFontPadding = false
                setLineSpacing(0f, 1.4f) // 调整行间距，使药丸更好看
            }

            // 自定义 TouchListener 以区分药丸点击和列表项点击
            textView.setOnTouchListener { v, event ->
                val widget = v as TextView
                val text = widget.text
                if (text is Spanned && event.action == MotionEvent.ACTION_UP) {
                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                    val layout = widget.layout ?: return@setOnTouchListener false
                    val line = layout.getLineForVertical(y)
                    if (x < 0 || x > layout.getLineWidth(line)) {
                        itemView.performClick()
                        return@setOnTouchListener true
                    }
                    val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                    val links = text.getSpans(offset, offset, ParameterPillSpan::class.java)
                    if (links.isNotEmpty()) { // 点击到药丸
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onParameterPillClick(adapterPosition, links[0].parameterId)
                        }
                        true
                    } else { // 未点击到药丸，视为列表项点击
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
// ... (PillUtil 和 RoundedBackgroundSpan 的代码不变，这里省略以保持清晰)

/**
 * 参数药丸 (Pill) UI 工具类。
 * 提供构建和处理摘要中药丸样式的 Spannable 的方法。
 */
object PillUtil {
    /**
     * 从模块参数创建 Pill 的标准方法。
     * 封装了魔法变量检测和数字格式化逻辑。
     *
     * @param paramValue 步骤中存储的原始参数值。
     * @param inputDef 该参数的输入定义，用于获取默认值和ID。
     * @param isModuleOption 是否为模块的内置选项（如操作符）。
     * @return 一个配置好的 Pill 对象。
     */
    fun createPillFromParam(
        paramValue: Any?,
        inputDef: InputDefinition?,
        isModuleOption: Boolean = false
    ): Pill {
        val isVariable = (paramValue as? String)?.isMagicVariable() == true
        val text: String

        if (isVariable) {
            text = paramValue.toString()
        } else {
            val valueToFormat = paramValue ?: inputDef?.defaultValue
            text = when (valueToFormat) {
                is Number -> {
                    if (valueToFormat.toDouble() == valueToFormat.toLong().toDouble()) {
                        valueToFormat.toLong().toString()
                    } else {
                        String.format("%.2f", valueToFormat.toDouble())
                    }
                }
                else -> valueToFormat?.toString() ?: "..."
            }
        }
        return Pill(text, isVariable, inputDef?.id ?: "", isModuleOption)
    }

    /** 构建包含药丸的 Spannable 文本。 */
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> { // 药丸对象
                    val start = builder.length
                    builder.append(" ${part.text} ") // 药丸文本前后加空格
                    val end = builder.length
                    // 附加 ParameterPillSpan 以便后续处理和点击
                    val span = ParameterPillSpan(part.parameterId, part.isVariable, part.isModuleOption)
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return builder
    }

    /** 参数药丸的数据模型。 */
    data class Pill(
        val text: String, // 显示文本
        val isVariable: Boolean, // 是否为魔法变量
        val parameterId: String, // 对应 InputDefinition 的 ID
        val isModuleOption: Boolean = false // 是否为模块自身配置项 (如If模块的操作符)
    )

    /** 根据模块分类获取颜色资源ID。 */
    fun getCategoryColor(category: String): Int = when (category) {
        "触发器" -> R.color.category_trigger
        "界面交互" -> R.color.category_ui_interaction
        "逻辑控制" -> R.color.category_logic
        "数据" -> R.color.category_data
        "文件" -> R.color.category_file
        "应用与系统" -> R.color.category_system
        "Shizuku" -> R.color.category_shizuku
        else -> com.google.android.material.R.color.material_dynamic_neutral30
    }

    /** 查找魔法变量的来源步骤和输出信息。 */
    private fun findSourceInfo(context: Context, variableRef: String, allSteps: List<ActionStep>): SourceInfo? {
        if (!variableRef.isMagicVariable()) return null // 不是合法的魔法变量引用
        val (sourceStepId, sourceOutputId) = variableRef.removeSurrounding("{{", "}}").split('.').let { it.getOrNull(0) to it.getOrNull(1) }
        if (sourceStepId == null || sourceOutputId == null) return null

        val sourceStep = allSteps.find { it.id == sourceStepId }
        val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) } ?: return null
        val sourceOutput = sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId } ?: return null
        val sourceColor = ContextCompat.getColor(context, getCategoryColor(sourceModule.metadata.category))
        return SourceInfo(outputName = sourceOutput.name, color = sourceColor)
    }

    /** 处理模块摘要中的 ParameterPillSpan，为其应用 RoundedBackgroundSpan 样式。 */
    fun processSummarySpans(
        context: Context,
        summary: CharSequence?,
        allSteps: List<ActionStep>,
        currentStep: ActionStep // 当前步骤，用于获取模块选项颜色
    ): CharSequence? {
        if (summary !is Spanned) return summary // 非Spanned文本无需处理
        val spannable = SpannableStringBuilder(summary)

        // 从后向前替换，避免索引错乱
        spannable.getSpans<ParameterPillSpan>().reversed().forEach { span ->
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)

            val color: Int
            val pillText: CharSequence

            if (span.isVariable) { // 魔法变量药丸
                val reference = spannable.substring(start, end).trim()
                val sourceInfo = findSourceInfo(context, reference, allSteps)
                pillText = " ${sourceInfo?.outputName ?: "变量"} " // 显示来源输出名称或通用"变量"
                color = sourceInfo?.color ?: ContextCompat.getColor(context, R.color.variable_pill_color)
            } else if (span.isModuleOption) { // 模块配置选项药丸 (如If的操作符)
                pillText = spannable.subSequence(start, end)
                val currentModule = ModuleRegistry.getModule(currentStep.moduleId)
                color = currentModule?.let { ContextCompat.getColor(context, getCategoryColor(it.metadata.category)) }
                    ?: ContextCompat.getColor(context, R.color.static_pill_color) // 模块不存在则用静态颜色
            } else { // 普通静态值药丸
                pillText = spannable.subSequence(start, end)
                color = ContextCompat.getColor(context, R.color.static_pill_color)
            }

            spannable.replace(start, end, pillText) // 替换原始文本为药丸文本
            val newEnd = start + pillText.length

            // 应用圆角背景和新的可点击Span
            val backgroundSpan = RoundedBackgroundSpan(context, color)
            spannable.setSpan(backgroundSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val newClickableSpan = ParameterPillSpan(span.parameterId, span.isVariable, span.isModuleOption)
            spannable.setSpan(newClickableSpan, start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}

/**
 * 自定义 ReplacementSpan 实现圆角背景药丸效果。
 */
class RoundedBackgroundSpan(
    context: Context, // 未使用，但保留以备将来扩展
    private val backgroundColor: Int
) : ReplacementSpan() {
    private val textColor: Int = Color.WHITE // 药丸文字颜色固定为白色
    private val cornerRadius: Float = 25f // 圆角半径
    private val paddingHorizontal: Float = 12f // 水平内边距
    private val paddingVertical: Float = 6f   // 垂直内边距 (影响药丸高度)

    /** 计算Span的宽度和影响FontMetrics。 */
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val textWidth = paint.measureText(text, start, end)
        if (fm != null) { //调整FontMetrics以确保背景能完全包裹文本
            val fmPaint = paint.fontMetricsInt
            val extra = paddingVertical.roundToInt()
            fm.ascent = fmPaint.ascent - extra
            fm.descent = fmPaint.descent + extra
            fm.top = fmPaint.top - extra
            fm.bottom = fmPaint.bottom + extra
        }
        return (textWidth + paddingHorizontal * 2).roundToInt() // 返回总宽度
    }

    /** 绘制圆角背景和文本。 */
    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float, // Span的起始x坐标
        top: Int, // 文本顶行y坐标
        y: Int,   // 文本基线y坐标
        bottom: Int, // 文本底行y坐标
        paint: Paint
    ) {
        val textWidth = paint.measureText(text, start, end)
        // 计算背景矩形的坐标 (基于文本的ascent/descent和padding)
        val rectTop = y + paint.fontMetrics.ascent - paddingVertical
        val rectBottom = y + paint.fontMetrics.descent + paddingVertical
        val rect = android.graphics.RectF(x, rectTop, x + textWidth + paddingHorizontal * 2, rectBottom)

        val originalColor = paint.color // 保存原始画笔颜色
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint) // 绘制圆角背景

        paint.color = textColor
        canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paint) // 绘制文本

        paint.color = originalColor // 恢复原始画笔颜色
    }
}