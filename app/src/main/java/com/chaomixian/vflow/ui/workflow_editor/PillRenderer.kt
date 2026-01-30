// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/PillRenderer.kt
// 描述: Pill视觉渲染器（统一渲染入口）
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.*
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.pill.PillTheme
import com.chaomixian.vflow.ui.workflow_editor.pill.PillVariableResolver
import kotlin.math.roundToInt

/**
 * Pill视觉渲染器（仅负责渲染）
 *
 * 职责：
 * - 统一的渲染入口（renderToSpannable, renderSinglePill）
 * - 视觉渲染（RoundedBackgroundSpan）
 * - 间距控制（在 ReplacementSpan 内部实现）
 *
 * 不再负责：
 * - 变量解析逻辑（已移到 PillVariableResolver）
 * - 颜色管理（已移到 PillTheme）
 */
object PillRenderer {

    /**
     * 渲染模式枚举
     * EDIT: 编辑模式，用于 EditText，保留原始变量引用
     * PREVIEW: 预览模式，用于 TextView，显示为友好名称
     */
    enum class RenderMode { EDIT, PREVIEW }

    // ========== 兼容层（已废弃，保留向后兼容） ==========

    /**
     * 获取变量引用的显示名称（兼容层）
     *
     * @deprecated 使用 PillVariableResolver.resolveVariable() 获取完整信息
     */
    @Deprecated("Use PillVariableResolver.resolveVariable() for complete information")
    fun getDisplayNameForVariableReference(variableReference: String, allSteps: List<ActionStep>, context: Context): String {
        val varInfo = com.chaomixian.vflow.core.execution.VariableInfo.fromReference(variableReference, allSteps)
        if (varInfo != null) {
            val propName = when {
                variableReference.startsWith("[[") -> {
                    val content = variableReference.removeSurrounding("[[", "]]")
                    val parts = content.split('.')
                    if (parts.size > 1) parts[1] else null
                }
                variableReference.startsWith("{{") -> {
                    val content = variableReference.removeSurrounding("{{", "}}")
                    val parts = content.split('.')
                    if (parts.size > 2) parts[2] else null
                }
                else -> null
            }

            return if (propName != null) {
                val propDisplay = varInfo.getPropertyDisplayName(context, propName)
                "${varInfo.sourceName} 的 $propDisplay"
            } else {
                varInfo.sourceName
            }
        }
        return variableReference
    }

    private fun truncate(text: String, maxLength: Int = 11): String {
        return if (text.length > maxLength) text.take(maxLength) + "..." else text
    }

    /**
     * 渲染 Pills（兼容层，用于 ActionStepAdapter）
     *
     * @deprecated 此方法仅供旧代码使用，新代码应使用 renderToSpannable()
     */
    @Deprecated("Use renderToSpannable() for new code")
    fun renderPills(
        context: Context,
        summary: CharSequence?,
        allSteps: List<ActionStep>,
        currentStep: ActionStep
    ): CharSequence? {
        if (summary !is android.text.Spanned) return summary
        val spannable = SpannableStringBuilder(summary)

        spannable.getSpans(0, spannable.length, com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan::class.java)
            .reversed().forEach { span ->
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val reference = spannable.substring(start, end).trim()

                val isVariable = reference.isMagicVariable() || reference.isNamedVariable()

                val color: Int
                val pillText: CharSequence

                if (isVariable) {
                    val resolvedInfo = PillVariableResolver.resolveVariable(context, reference, allSteps)
                    val displayName = resolvedInfo?.displayName ?: "变量"
                    pillText = truncate(displayName)
                    color = resolvedInfo?.color ?: PillTheme.getColor(context, R.color.variable_pill_color)
                } else {
                    pillText = truncate(reference)
                    color = PillTheme.getColor(context, R.color.static_pill_color)
                }

                spannable.replace(start, end, pillText)
                val newEnd = start + pillText.length

                spannable.setSpan(RoundedBackgroundSpan(context, color), start, newEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan(span.parameterId), start, newEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        return spannable
    }

    // ========== 核心渲染方法 ==========

    /**
     * 统一的富文本渲染方法（EDIT 和 PREVIEW 模式统一）
     *
     * 两种模式都：
     * - 使用相同的 padding 和间距大小
     * - 间距在 RoundedBackgroundSpan 内部实现，不添加空格字符
     *
     * 区别仅在于：
     * - EDIT：保留原始变量引用（如 {{step1.output}}）
     * - PREVIEW：替换为显示名称（如 "步骤1"）
     *
     * @param rawText 包含变量引用的原始文本
     * @param mode 渲染模式（EDIT 用于编辑器，PREVIEW 用于显示）
     * @param allSteps 工作流中的所有步骤
     * @param context Android 上下文
     * @return 渲染后的 SpannableStringBuilder
     */
    fun renderToSpannable(
        rawText: String,
        mode: RenderMode,
        allSteps: List<ActionStep>,
        context: Context
    ): SpannableStringBuilder {
        val spannable = SpannableStringBuilder()
        val matcher = com.chaomixian.vflow.core.execution.VariableResolver.VARIABLE_PATTERN.matcher(rawText)
        var lastEnd = 0

        while (matcher.find()) {
            // 添加变量引用之前的普通文本
            spannable.append(rawText.substring(lastEnd, matcher.start()))

            val variableRef = matcher.group(1)
            if (variableRef != null) {
                val resolvedInfo = PillVariableResolver.resolveVariable(context, variableRef, allSteps)
                val color = resolvedInfo?.color
                    ?: PillTheme.getColor(context, R.color.variable_pill_color)

                if (mode == RenderMode.EDIT) {
                    // ===== 编辑模式：保留原始变量引用 =====
                    renderPillWithSpacing(spannable, variableRef, resolvedInfo?.displayName ?: variableRef, color, context)
                } else {
                    // ===== 预览模式：替换为显示名称 =====
                    val displayName = resolvedInfo?.displayName ?: variableRef
                    renderPillWithSpacing(spannable, displayName, null, color, context)
                }
            }
            lastEnd = matcher.end()
        }

        // 添加剩余的普通文本
        if (lastEnd < rawText.length) {
            spannable.append(rawText.substring(lastEnd))
        }

        return spannable
    }

    /**
     * 渲染带间距的 Pill（内部辅助方法）
     *
     * 统一处理 pill 前后的间距（在 ReplacementSpan 内部实现，不使用空格）
     *
     * @param spannable 目标 SpannableStringBuilder
     * @param text 要渲染的文本（原始引用或显示名称）
     * @param displayText 可选的显示文本（用于 EDIT 模式）
     * @param color 背景颜色
     * @param context Android 上下文
     */
    private fun renderPillWithSpacing(
        spannable: SpannableStringBuilder,
        text: String,
        displayText: String?,
        color: Int,
        context: Context
    ) {
        val start = spannable.length
        spannable.append(text)
        val end = spannable.length

        spannable.setSpan(
            if (displayText != null) {
                RoundedBackgroundSpan(context, color, displayText)
            } else {
                RoundedBackgroundSpan(context, color)
            },
            start, end,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /**
     * 渲染单个 Pill（用于插入变量）
     *
     * 与 renderToSpannable() 的 EDIT 模式逻辑完全一致
     *
     * @param variableRef 变量引用（如 "{{step1.output}}"）
     * @param mode 渲染模式
     * @param allSteps 工作流中的所有步骤
     * @param context Android 上下文
     * @return 渲染后的单个 Pill SpannableStringBuilder
     */
    fun renderSinglePill(
        variableRef: String,
        mode: RenderMode,
        allSteps: List<ActionStep>,
        context: Context
    ): SpannableStringBuilder {
        val resolvedInfo = PillVariableResolver.resolveVariable(context, variableRef, allSteps)
        val color = resolvedInfo?.color
            ?: PillTheme.getColor(context, R.color.variable_pill_color)

        val spannable = SpannableStringBuilder()

        if (mode == RenderMode.EDIT) {
            // ===== 编辑模式：保留原始变量引用 =====
            renderPillWithSpacing(spannable, variableRef, resolvedInfo?.displayName ?: variableRef, color, context)
        } else {
            // ===== 预览模式：替换为显示名称 =====
            val displayName = resolvedInfo?.displayName ?: variableRef
            renderPillWithSpacing(spannable, displayName, null, color, context)
        }

        return spannable
    }

    /**
     * 在渲染富文本时，为每个药丸渲染 pill
     * @deprecated 使用 renderToSpannable() 替代
     */
    fun renderRichTextToSpannable(context: Context, rawText: String, allSteps: List<ActionStep>): SpannableStringBuilder {
        // 直接委托给新的统一方法，使用 PREVIEW 模式
        return renderToSpannable(rawText, RenderMode.PREVIEW, allSteps, context)
    }

    /**
     * Pill 背景 Span（圆角矩形背景）
     *
     * 功能：
     * 1. 绘制圆角矩形背景（带 padding）
     * 2. 支持自定义显示文本（用于 EDIT 模式，显示友好名称但保留原始引用）
     * 3. 在 Span 前后添加额外间距（不使用空格）
     *
     * 说明：
     * - internalPadding: 文本与背景边界的间距
     * - externalSpacing: pill 与相邻内容的间距
     * - 间距通过在 getSize() 中增加宽度实现，不添加实际空格字符
     *
     * @param context Android 上下文
     * @param backgroundColor 背景颜色
     * @param displayText 可选：要显示的文本（如果与原始文本不同）
     */
    class RoundedBackgroundSpan(
        context: Context,
        private val backgroundColor: Int,
        private val displayText: String? = null
    ) : ReplacementSpan() {
        private val textColor: Int = Color.WHITE
        private val cornerRadius: Float = 25f

        // 内部 padding：文本与背景边界的间距
        private val internalPaddingX: Float = 12f
        private val internalPaddingY: Float = 6f

        // 外部间距：pill 与相邻内容的间距
        private val externalSpacingX: Float = 8f

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            // 使用 displayText 计算宽度（如果提供），否则使用原始文本
            val textToMeasure = displayText ?: text.substring(start, end)
            val textWidth = paint.measureText(textToMeasure)

            if (fm != null) {
                val fmPaint = paint.fontMetricsInt
                val extra = internalPaddingY.roundToInt()
                fm.ascent = fmPaint.ascent - extra
                fm.descent = fmPaint.descent + extra
                fm.top = fmPaint.top - extra
                fm.bottom = fmPaint.bottom + extra
            }

            // 返回总宽度 = 前间距 + 文本宽度 + 左右内部 padding + 后间距
            return (externalSpacingX + textWidth + internalPaddingX * 2 + externalSpacingX).roundToInt()
        }

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            // 使用 displayText（如果提供），否则使用原始文本
            val textToDraw = displayText ?: text.substring(start, end)
            val textWidth = paint.measureText(textToDraw)

            // x 是 Span 的起始位置
            // 背景矩形：从 x + externalSpacingX 开始，跳过前间距
            val bgStart = x + externalSpacingX
            val bgEnd = bgStart + textWidth + internalPaddingX * 2

            val rectTop = y + paint.fontMetrics.ascent - internalPaddingY
            val rectBottom = y + paint.fontMetrics.descent + internalPaddingY
            val rect = RectF(bgStart, rectTop, bgEnd, rectBottom)

            // 绘制背景
            val originalColor = paint.color
            paint.color = backgroundColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            // 绘制文本，从 bgStart + internalPaddingX 开始
            paint.color = textColor
            canvas.drawText(textToDraw, 0, textToDraw.length, bgStart + internalPaddingX, y.toFloat(), paint)

            paint.color = originalColor
        }
    }
}