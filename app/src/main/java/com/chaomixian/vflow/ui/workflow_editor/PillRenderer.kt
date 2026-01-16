// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/PillRenderer.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.*
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlin.math.roundToInt

object PillRenderer {
    private data class SourceInfo(val outputName: String, val color: Int, val propertyName: String? = null)

    // 截断辅助函数
    private fun truncate(text: String, maxLength: Int = 11): String {
        return if (text.length > maxLength) {
            text.take(maxLength) + "..."
        } else {
            text
        }
    }

    /**
     * 查找变量信息（颜色、名称、属性等）
     * 使用统一的 VariableInfo 类，消除重复逻辑
     */
    private fun findSourceInfo(context: Context, variableRef: String, allSteps: List<ActionStep>): SourceInfo? {
        val varInfo = com.chaomixian.vflow.core.execution.VariableInfo.fromReference(variableRef, allSteps)
            ?: return null

        // 获取模块颜色
        val sourceStep = if (varInfo.sourceStepId != null) {
            allSteps.find { it.id == varInfo.sourceStepId }
        } else null
        val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) }
        val color = if (sourceModule != null) {
            ContextCompat.getColor(context, PillUtil.getCategoryColor(sourceModule.metadata.category))
        } else {
            ContextCompat.getColor(context, R.color.variable_pill_color)
        }

        // 解析属性（如果有）
        val propertyName = when {
            variableRef.isNamedVariable() -> {
                val content = variableRef.removeSurrounding("[[", "]]")
                val parts = content.split('.')
                if (parts.size > 1) varInfo.getPropertyDisplayName(parts[1]) else null
            }
            variableRef.isMagicVariable() -> {
                val content = variableRef.removeSurrounding("{{", "}}")
                val parts = content.split('.')
                if (parts.size > 2) varInfo.getPropertyDisplayName(parts[2]) else null
            }
            else -> null
        }

        return SourceInfo(outputName = varInfo.sourceName, color = color, propertyName = propertyName)
    }

    /**
     * 获取变量引用的显示名称。
     * 使用简单的 split('.') 解析
     */
    fun getDisplayNameForVariableReference(variableReference: String, allSteps: List<ActionStep>): String {
        val varInfo = com.chaomixian.vflow.core.execution.VariableInfo.fromReference(variableReference, allSteps)
            ?: return variableReference

        val propName = when {
            variableReference.isNamedVariable() -> {
                val content = variableReference.removeSurrounding("[[", "]]")
                val parts = content.split('.')
                if (parts.size > 1) parts[1] else null
            }
            variableReference.isMagicVariable() -> {
                val content = variableReference.removeSurrounding("{{", "}}")
                val parts = content.split('.')
                if (parts.size > 2) parts[2] else null
            }
            else -> null
        }

        return if (propName != null) {
            val propDisplay = varInfo.getPropertyDisplayName(propName)
            "${varInfo.sourceName} 的 $propDisplay"
        } else {
            varInfo.sourceName
        }
    }

    fun renderPills(
        context: Context,
        summary: CharSequence?,
        allSteps: List<ActionStep>,
        currentStep: ActionStep
    ): CharSequence? {
        if (summary !is Spanned) return summary
        val spannable = SpannableStringBuilder(summary)

        spannable.getSpans(0, spannable.length, PillUtil.ParameterPillSpan::class.java).reversed().forEach { span ->
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val reference = spannable.substring(start, end).trim() // 获取不带前后空格的原始引用

            val isVariable = reference.isMagicVariable()
            val isNamedVariable = reference.isNamedVariable()
            val module = ModuleRegistry.getModule(currentStep.moduleId)
            val inputDef = module?.getDynamicInputs(currentStep, allSteps)?.find { it.id == span.parameterId }
            val isModuleOption = inputDef?.staticType == ParameterType.ENUM && !inputDef.acceptsMagicVariable

            val color: Int
            val pillText: CharSequence

            when {
                isNamedVariable || isVariable -> {
                    // 统一处理：命名变量和魔法变量都使用 findSourceInfo
                    val sourceInfo = findSourceInfo(context, reference, allSteps)
                    val baseName = sourceInfo?.outputName ?: "变量"
                    val displayName = if (sourceInfo?.propertyName != null) "$baseName 的 ${sourceInfo.propertyName}" else baseName
                    val truncatedName = truncate(displayName)

                    pillText = " $truncatedName "
                    color = sourceInfo?.color ?: ContextCompat.getColor(context, R.color.variable_pill_color)
                }
                isModuleOption -> {
                    // 对于选项，reference 即为显示值，也需要截断
                    val truncatedText = truncate(reference)
                    pillText = " $truncatedText "
                    color = module?.let { ContextCompat.getColor(context, PillUtil.getCategoryColor(it.metadata.category)) }
                        ?: ContextCompat.getColor(context, R.color.static_pill_color)
                }
                else -> {
                    // 其他静态值
                    val truncatedText = truncate(reference)
                    pillText = " $truncatedText "
                    color = ContextCompat.getColor(context, R.color.static_pill_color)
                }
            }

            spannable.replace(start, end, pillText)
            val newEnd = start + pillText.length

            spannable.setSpan(RoundedBackgroundSpan(context, color), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(PillUtil.ParameterPillSpan(span.parameterId), start, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    /**
     * 在渲染富文本时，为每个药丸前后添加空格。
     */
    fun renderRichTextToSpannable(context: Context, rawText: String, allSteps: List<ActionStep>): SpannableStringBuilder {
        val spannable = SpannableStringBuilder()
        val matcher = VariableResolver.VARIABLE_PATTERN.matcher(rawText)
        var lastEnd = 0

        while (matcher.find()) {
            spannable.append(rawText.substring(lastEnd, matcher.start()))

            val variableRef = matcher.group(1)
            if (variableRef != null) {
                // 使用统一的名称解析逻辑
                val displayName = getDisplayNameForVariableReference(variableRef, allSteps)
                val truncatedName = truncate(displayName)

                // 统一获取颜色：命名变量和魔法变量都使用 findSourceInfo
                val color = findSourceInfo(context, variableRef, allSteps)?.color
                    ?: ContextCompat.getColor(context, R.color.variable_pill_color)

                // 紧凑模式渲染，前后留白由 insertVariablePill 或 render 逻辑控制，这里为富文本预览添加小间距
                spannable.append(" ")
                val start = spannable.length
                val pillTextWithPadding = " $truncatedName "
                spannable.append(pillTextWithPadding)
                val end = spannable.length
                spannable.setSpan(RoundedBackgroundSpan(context, color, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // 添加后置空格
                spannable.append(" ")
            }
            lastEnd = matcher.end()
        }
        if (lastEnd < rawText.length) {
            spannable.append(rawText.substring(lastEnd))
        }
        return spannable
    }

    /**
     * RoundedBackgroundSpan 现在有一个新参数 isCompact，
     * 用于调整富文本预览中的药丸内边距，使其更紧凑。
     */
    class RoundedBackgroundSpan(
        context: Context,
        private val backgroundColor: Int,
        isCompact: Boolean = false
    ) : ReplacementSpan() {
        private val textColor: Int = Color.WHITE
        private val cornerRadius: Float = 25f
        // 根据 isCompact 调整内边距
        private val paddingHorizontal: Float = if (isCompact) 8f else 12f
        private val paddingVertical: Float = if (isCompact) 4f else 6f

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
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

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val textWidth = paint.measureText(text, start, end)
            val rectTop = y + paint.fontMetrics.ascent - paddingVertical
            val rectBottom = y + paint.fontMetrics.descent + paddingVertical
            val rect = RectF(x, rectTop, x + textWidth + paddingHorizontal * 2, rectBottom)

            val originalColor = paint.color
            paint.color = backgroundColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            paint.color = textColor
            canvas.drawText(text, start, end, x + paddingHorizontal, y.toFloat(), paint)

            paint.color = originalColor
        }
    }
}