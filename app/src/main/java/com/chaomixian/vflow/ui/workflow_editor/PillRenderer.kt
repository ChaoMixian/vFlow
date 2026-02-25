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

                // 检查是否为纯变量引用
                val isPureVariable = reference.isMagicVariable() || reference.isNamedVariable()
                // 检查是否包含变量引用（混合文本）
                val hasVariableRef = com.chaomixian.vflow.core.execution.VariableResolver.hasVariableReference(reference)

                val color: Int
                val pillText: CharSequence

                when {
                    // 纯变量引用：替换为显示名称
                    isPureVariable -> {
                        val resolvedInfo = PillVariableResolver.resolveVariable(context, reference, allSteps)
                        val displayName = resolvedInfo?.displayName ?: "变量"
                        pillText = truncate(displayName)
                        color = resolvedInfo?.color ?: PillTheme.getColor(context, R.color.variable_pill_color)
                    }
                    // 包含变量引用的混合文本：保持原样，让 RoundedBackgroundSpan 渲染嵌套Pill
                    hasVariableRef -> {
                        pillText = reference // 保持原始文本
                        color = PillTheme.getColor(context, R.color.static_pill_color)
                    }
                    // 静态文本：直接显示
                    else -> {
                        pillText = truncate(reference)
                        color = PillTheme.getColor(context, R.color.static_pill_color)
                    }
                }

                spannable.replace(start, end, pillText)
                val newEnd = start + pillText.length

                spannable.setSpan(RoundedBackgroundSpan(context, color, null, allSteps), start, newEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                    renderPillWithSpacing(spannable, variableRef, resolvedInfo?.displayName ?: variableRef, color, context, allSteps)
                } else {
                    // ===== 预览模式：替换为显示名称 =====
                    val displayName = resolvedInfo?.displayName ?: variableRef
                    renderPillWithSpacing(spannable, displayName, null, color, context, allSteps)
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
     * @param allSteps 所有步骤，用于渲染嵌套Pill
     */
    private fun renderPillWithSpacing(
        spannable: SpannableStringBuilder,
        text: String,
        displayText: String?,
        color: Int,
        context: Context,
        allSteps: List<ActionStep>? = null
    ) {
        val start = spannable.length
        spannable.append(text)
        val end = spannable.length

        spannable.setSpan(
            if (displayText != null) {
                RoundedBackgroundSpan(context, color, displayText, allSteps)
            } else {
                RoundedBackgroundSpan(context, color, null, allSteps)
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
            renderPillWithSpacing(spannable, variableRef, resolvedInfo?.displayName ?: variableRef, color, context, allSteps)
        } else {
            // ===== 预览模式：替换为显示名称 =====
            val displayName = resolvedInfo?.displayName ?: variableRef
            renderPillWithSpacing(spannable, displayName, null, color, context, allSteps)
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
     * 3. 支持嵌套Pill渲染：当文本包含变量引用时，自动渲染内嵌的小Pill
     * 4. 在 Span 前后添加额外间距（不使用空格）
     *
     * 说明：
     * - internalPadding: 文本与背景边界的间距
     * - externalSpacing: pill 与相邻内容的间距
     * - 间距通过在 getSize() 中增加宽度实现，不添加实际空格字符
     *
     * @param context Android 上下文
     * @param backgroundColor 背景颜色
     * @param displayText 可选：要显示的文本（如果与原始文本不同）
     * @param allSteps 可选：所有步骤，用于解析嵌套的变量引用
     */
    class RoundedBackgroundSpan(
        private val context: Context,
        private val backgroundColor: Int,
        private val displayText: String? = null,
        private val allSteps: List<ActionStep>? = null
    ) : ReplacementSpan() {
        private val textColor: Int = Color.WHITE
        private val cornerRadius: Float = 25f

        // 内部 padding：文本与背景边界的间距
        private val internalPaddingX: Float = 12f
        private val internalPaddingY: Float = 6f

        // 外部间距：pill 与相邻内容的间距
        private val externalSpacingX: Float = 8f

        // 嵌套Pill样式
        private val nestedPillColor: Int = PillTheme.getColor(context, R.color.variable_pill_color)
        private val nestedPillRadius: Float = 14f
        private val nestedPillPaddingX: Float = 12f
        private val nestedPillPaddingY: Float = 2f
        private val nestedPillTextScale: Float = 0.90f

        /**
         * 变量片段信息（用于缓存解析结果）
         */
        private data class VariableSegment(
            val start: Int,
            val end: Int,
            val variableRef: String,
            val displayName: String,
            val color: Int
        ) {
            // 缓存显示名称的宽度（避免重复计算）
            var cachedDisplayWidth: Float = 0f
        }

        /**
         * 解析文本中的所有变量引用
         */
        private fun parseVariableSegments(text: String): List<VariableSegment> {
            if (allSteps == null) return emptyList()

            val segments = mutableListOf<VariableSegment>()
            val matcher = com.chaomixian.vflow.core.execution.VariableResolver.VARIABLE_PATTERN.matcher(text)

            while (matcher.find()) {
                val variableRef = matcher.group(1) ?: continue
                val resolvedInfo = PillVariableResolver.resolveVariable(context, variableRef, allSteps)
                val displayName = resolvedInfo?.displayName ?: variableRef
                val color = resolvedInfo?.color ?: nestedPillColor

                segments.add(VariableSegment(
                    start = matcher.start(),
                    end = matcher.end(),
                    variableRef = variableRef,
                    displayName = displayName,
                    color = color
                ))
            }

            return segments
        }

        /**
         * 测量显示名称的宽度（使用缩放后的字体）
         */
        private fun measureDisplayWidth(paint: Paint, displayName: String): Float {
            val originalTextSize = paint.textSize
            paint.textSize = originalTextSize * nestedPillTextScale
            val width = paint.measureText(displayName)
            paint.textSize = originalTextSize
            return width
        }

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            // 使用 displayText 计算宽度（如果提供），否则使用原始文本
            val textToMeasure = displayText ?: text.substring(start, end)

            // 如果包含变量引用，需要计算解析后的实际宽度
            val textWidth = if (allSteps != null &&
                com.chaomixian.vflow.core.execution.VariableResolver.hasVariableReference(textToMeasure.toString())) {
                calculateTextWidthWithVariables(paint, textToMeasure.toString())
            } else {
                paint.measureText(textToMeasure)
            }

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

        /**
         * 计算包含变量引用的文本实际宽度（使用显示名称）
         */
        private fun calculateTextWidthWithVariables(paint: Paint, text: String): Float {
            val segments = parseVariableSegments(text)
            if (segments.isEmpty()) return paint.measureText(text)

            var totalWidth = 0f
            var lastEnd = 0

            for (segment in segments) {
                // 添加变量引用之前的普通文本宽度
                if (segment.start > lastEnd) {
                    val beforeText = text.substring(lastEnd, segment.start)
                    totalWidth += paint.measureText(beforeText)
                }

                // 缓存显示名称宽度
                segment.cachedDisplayWidth = measureDisplayWidth(paint, segment.displayName)

                // 小pill占据的总宽度 = 前间距 + 背景宽度(文字+内部padding) + 后间距
                totalWidth += nestedPillPaddingX + segment.cachedDisplayWidth + nestedPillPaddingX + nestedPillPaddingX * 0.5f

                lastEnd = segment.end
            }

            // 添加剩余的普通文本宽度
            if (lastEnd < text.length) {
                val remainingText = text.substring(lastEnd)
                totalWidth += paint.measureText(remainingText)
            }

            return totalWidth
        }

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            // 使用 displayText（如果提供），否则使用原始文本
            val textToDraw = displayText ?: text.substring(start, end)

            // 检查是否包含变量引用
            val hasVariableRef = allSteps != null &&
                com.chaomixian.vflow.core.execution.VariableResolver.hasVariableReference(textToDraw.toString())

            // 计算文本宽度（如果包含变量引用，使用解析后的实际宽度）
            val textWidth = if (hasVariableRef) {
                calculateTextWidthWithVariables(paint, textToDraw.toString())
            } else {
                paint.measureText(textToDraw)
            }

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

            // 绘制嵌套Pill（如果存在变量引用）
            if (hasVariableRef) {
                drawNestedPills(canvas, textToDraw.toString(), bgStart + internalPaddingX, y, paint)
            }

            // 绘制文本
            paint.color = textColor
            if (hasVariableRef) {
                // 如果包含变量引用，分段绘制（使用显示名称）
                drawTextWithVariableResolution(canvas, textToDraw.toString(), bgStart + internalPaddingX, y.toFloat(), paint)
            } else {
                // 普通文本，直接绘制
                canvas.drawText(textToDraw, 0, textToDraw.length, bgStart + internalPaddingX, y.toFloat(), paint)
            }

            paint.color = originalColor
        }

        /**
         * 绘制文本，将变量引用替换为显示名称
         */
        private fun drawTextWithVariableResolution(canvas: Canvas, text: String, startX: Float, y: Float, paint: Paint) {
            val segments = parseVariableSegments(text)
            if (segments.isEmpty()) {
                canvas.drawText(text, startX, y, paint)
                return
            }

            var currentX = startX
            var lastEnd = 0

            for (segment in segments) {
                // 绘制变量引用之前的普通文本
                if (segment.start > lastEnd) {
                    val beforeText = text.substring(lastEnd, segment.start)
                    canvas.drawText(beforeText, currentX, y, paint)
                    currentX += paint.measureText(beforeText)
                }

                // 添加小pill的前间距
                currentX += nestedPillPaddingX

                // 记录背景起始位置
                val bgStartX = currentX

                // 使用缩放后的字体绘制显示名称
                val originalTextSize = paint.textSize
                paint.textSize = originalTextSize * nestedPillTextScale

                // 使用缓存的显示名称宽度
                val displayWidth = segment.cachedDisplayWidth.takeIf { it > 0f }
                    ?: paint.measureText(segment.displayName)

                // 文字在背景内居中
                val textStartX = bgStartX + nestedPillPaddingX / 2
                canvas.drawText(segment.displayName, textStartX, y, paint)
                paint.textSize = originalTextSize

                // 更新位置（背景宽度 + 后间距）
                currentX += displayWidth + nestedPillPaddingX + nestedPillPaddingX * 0.5f

                lastEnd = segment.end
            }

            // 绘制剩余的普通文本
            if (lastEnd < text.length) {
                val remainingText = text.substring(lastEnd)
                canvas.drawText(remainingText, currentX, y, paint)
            }
        }

        /**
         * 绘制嵌套的小Pill背景
         */
        private fun drawNestedPills(canvas: Canvas, text: String, textStartX: Float, baselineY: Int, paint: Paint) {
            val segments = parseVariableSegments(text)
            if (segments.isEmpty()) return

            val fontMetrics = paint.fontMetrics
            val scaledAscent = fontMetrics.ascent * nestedPillTextScale
            val scaledDescent = fontMetrics.descent * nestedPillTextScale
            val pillTop = baselineY + scaledAscent - nestedPillPaddingY
            val pillBottom = baselineY + scaledDescent + nestedPillPaddingY

            // 使用与 drawTextWithVariableResolution 相同的位置计算逻辑
            var currentX = textStartX
            var lastEnd = 0

            for (segment in segments) {
                // 计算变量引用之前的普通文本宽度
                if (segment.start > lastEnd) {
                    val beforeText = text.substring(lastEnd, segment.start)
                    currentX += paint.measureText(beforeText)
                }

                // 添加小pill的前间距
                currentX += nestedPillPaddingX

                // 记录背景起始位置
                val bgStartX = currentX

                // 使用缓存的显示名称宽度
                val displayWidth = segment.cachedDisplayWidth.takeIf { it > 0f }
                    ?: measureDisplayWidth(paint, segment.displayName)

                // 小Pill的位置：背景宽度 = displayWidth + nestedPillPaddingX
                val pillStart = bgStartX
                val pillEnd = bgStartX + displayWidth + nestedPillPaddingX
                val pillRect = RectF(pillStart, pillTop, pillEnd, pillBottom)

                // 绘制小Pill背景
                val originalColor = paint.color
                paint.color = segment.color
                canvas.drawRoundRect(pillRect, nestedPillRadius, nestedPillRadius, paint)
                paint.color = originalColor

                // 更新位置（背景宽度 + 后间距）
                currentX += displayWidth + nestedPillPaddingX + nestedPillPaddingX * 0.5f

                lastEnd = segment.end
            }
        }
    }
}