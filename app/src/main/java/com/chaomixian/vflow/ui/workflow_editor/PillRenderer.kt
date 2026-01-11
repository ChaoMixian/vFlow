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

    private fun findSourceInfo(context: Context, variableRef: String, allSteps: List<ActionStep>): SourceInfo? {
        if (!variableRef.isMagicVariable()) return null

        // 解析路径：{{stepId.outputId.prop1...}}
        val content = variableRef.removeSurrounding("{{", "}}")
        val parts = content.split('.')
        val sourceStepId = parts.getOrNull(0)
        val sourceOutputId = parts.getOrNull(1)

        if (sourceStepId == null || sourceOutputId == null) return null

        val sourceStep = allSteps.find { it.id == sourceStepId }
        val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) } ?: return null
        val sourceOutput = sourceModule.getOutputs(sourceStep).find { it.id == sourceOutputId } ?: return null

        val sourceColor = ContextCompat.getColor(context, PillUtil.getCategoryColor(sourceModule.metadata.category))

        // 尝试解析属性名 (例如 "width")
        var propDisplay: String? = null
        if (parts.size > 2) {
            val propName = parts[2]
            val type = VTypeRegistry.getType(sourceOutput.typeName)
            val propDef = type.properties.find { it.name == propName }
            propDisplay = propDef?.displayName ?: propName
        }

        return SourceInfo(outputName = sourceOutput.name, color = sourceColor, propertyName = propDisplay)
    }

    /**
     * 获取变量引用的显示名称。
     * 修复：现在支持解析属性（如 "图片 的 宽度"）。
     */
    fun getDisplayNameForVariableReference(variableReference: String, allSteps: List<ActionStep>): String {
        // 1. 处理命名变量 [[var]] 或 [[var.prop]]
        if (variableReference.isNamedVariable()) {
            val content = variableReference.removeSurrounding("[[", "]]")
            if (content.contains('.')) {
                val parts = content.split('.')
                return "${parts[0]} 的 ${parts[1]}" // 简单显示属性
            }
            return content
        }

        // 2. 处理魔法变量 {{step.out}} 或 {{step.out.prop}}
        if (variableReference.isMagicVariable()) {
            val content = variableReference.removeSurrounding("{{", "}}")
            val parts = content.split('.')
            val sourceStepId = parts.getOrNull(0)
            val sourceOutputId = parts.getOrNull(1)

            if (sourceStepId != null && sourceOutputId != null) {
                val sourceStep = allSteps.find { it.id == sourceStepId }
                if (sourceStep != null) {
                    val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId)
                    val outputDef = sourceModule?.getOutputs(sourceStep)?.find { it.id == sourceOutputId }
                    if (outputDef != null) {
                        var name = outputDef.name
                        // 如果有属性部分，追加显示名称
                        if (parts.size > 2) {
                            val propName = parts[2]
                            val type = VTypeRegistry.getType(outputDef.typeName)
                            val propDef = type.properties.find { it.name == propName }
                            val propDisplay = propDef?.displayName ?: propName
                            name += " 的 $propDisplay"
                        }
                        return name
                    }
                }
            }
            // 降级显示
            return if (parts.size > 2) "$sourceOutputId.${parts[2]}" else sourceOutputId ?: variableReference
        }
        return variableReference
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
                isNamedVariable -> {
                    val displayName = getDisplayNameForVariableReference(reference, allSteps)
                    val truncatedName = truncate(displayName)
                    pillText = " $truncatedName "
                    color = ContextCompat.getColor(context, PillUtil.getCategoryColor("数据"))
                }
                isVariable -> {
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

                val color = when {
                    variableRef.isMagicVariable() -> findSourceInfo(context, variableRef, allSteps)?.color ?: ContextCompat.getColor(context, R.color.variable_pill_color)
                    variableRef.isNamedVariable() -> ContextCompat.getColor(context, PillUtil.getCategoryColor("数据"))
                    else -> ContextCompat.getColor(context, R.color.static_pill_color)
                }

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