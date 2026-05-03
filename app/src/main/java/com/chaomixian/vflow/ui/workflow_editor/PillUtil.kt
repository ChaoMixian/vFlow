package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.pill.Pill as CorePill
import com.chaomixian.vflow.core.pill.PillFormatter
import com.chaomixian.vflow.core.pill.PillType
import com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan
import com.chaomixian.vflow.ui.workflow_editor.pill.PillTheme

object PillUtil {

    data class Pill(
        val text: String,
        val parameterId: String,
        val isModuleOption: Boolean = false,
    ) {
        fun toCorePill(): CorePill {
            return CorePill(
                text = text,
                parameterId = parameterId,
                type = if (isModuleOption) PillType.MODULE_OPTION else PillType.PARAMETER
            )
        }

        companion object {
            fun fromCorePill(corePill: CorePill): Pill {
                return Pill(
                    text = corePill.text,
                    parameterId = corePill.parameterId,
                    isModuleOption = corePill.type == PillType.MODULE_OPTION
                )
            }
        }
    }

    data class RichTextPill(
        val rawText: String,
        val onlyWhenComplex: Boolean = true
    )

    internal sealed interface SummaryPart {
        data class Inline(val content: CharSequence) : SummaryPart
        data class Rich(val pill: RichTextPill) : SummaryPart
    }

    class SummaryContent internal constructor(
        internal val parts: List<SummaryPart>,
        private val plainText: CharSequence
    ) : CharSequence by plainText

    fun createPillFromParam(
        paramValue: Any?,
        inputDef: InputDefinition?,
        isModuleOption: Boolean = false
    ): Pill {
        val corePill = PillFormatter.createPillFromParam(
            localizeEnumValue(paramValue, inputDef),
            inputDef,
            if (isModuleOption) PillType.MODULE_OPTION else PillType.PARAMETER
        )
        return Pill.fromCorePill(corePill)
    }

    fun richTextPreview(
        rawText: String?,
        onlyWhenComplex: Boolean = true
    ): RichTextPill? {
        val normalized = rawText?.takeIf { it.isNotEmpty() } ?: return null
        return RichTextPill(
            rawText = normalized,
            onlyWhenComplex = onlyWhenComplex
        )
    }

    fun buildSpannable(context: Context, vararg parts: Any?): CharSequence {
        val summaryParts = mutableListOf<SummaryPart>()
        val currentInline = SpannableStringBuilder()

        fun flushInline() {
            if (currentInline.isNotEmpty()) {
                summaryParts += SummaryPart.Inline(SpannableStringBuilder(currentInline))
                currentInline.clear()
            }
        }

        parts.forEach { part ->
            when (part) {
                null -> Unit
                is String -> currentInline.append(part)
                is Pill -> appendPill(currentInline, part)
                is CorePill -> appendPill(currentInline, Pill.fromCorePill(part))
                is RichTextPill -> {
                    flushInline()
                    summaryParts += SummaryPart.Rich(part)
                }
                is SummaryContent -> {
                    flushInline()
                    summaryParts += part.parts
                }
                is CharSequence -> currentInline.append(part)
                else -> Unit
            }
        }
        flushInline()

        if (summaryParts.none { it is SummaryPart.Rich }) {
            return summaryParts
                .filterIsInstance<SummaryPart.Inline>()
                .firstOrNull()
                ?.content
                ?: SpannableStringBuilder()
        }

        val plainText = SpannableStringBuilder().apply {
            summaryParts.forEach { part ->
                when (part) {
                    is SummaryPart.Inline -> append(part.content)
                    is SummaryPart.Rich -> {
                        if (isNotEmpty()) append('\n')
                        append(part.pill.rawText)
                    }
                }
            }
        }
        return SummaryContent(summaryParts, plainText)
    }

    internal fun splitSummaryContent(content: CharSequence?): List<Any> {
        return when (content) {
            null -> emptyList()
            is SummaryContent -> content.parts.map {
                when (it) {
                    is SummaryPart.Inline -> it.content
                    is SummaryPart.Rich -> it.pill
                }
            }
            else -> listOf(content)
        }
    }

    private fun localizeEnumValue(paramValue: Any?, inputDef: InputDefinition?): Any? {
        if (inputDef?.staticType != ParameterType.ENUM) return paramValue

        val rawValue = paramValue?.toString() ?: return null
        val normalizedValue = inputDef.normalizeEnumValue(rawValue, null) ?: rawValue
        val optionIndex = inputDef.options.indexOf(normalizedValue)
        if (optionIndex == -1) return rawValue

        val appContext = runCatching { LogManager.applicationContext }.getOrNull() ?: return normalizedValue
        val localizedContext = LocaleManager.applyLanguage(
            appContext,
            LocaleManager.getLanguage(appContext)
        )
        val localizedOptions = inputDef.getLocalizedOptions(localizedContext)
        return localizedOptions.getOrNull(optionIndex) ?: normalizedValue
    }

    fun createPillDrawable(context: Context, text: String): Drawable {
        val pillView = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, null)
        val textView = pillView.findViewById<TextView>(R.id.pill_text)
        textView.text = text

        pillView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        pillView.layout(0, 0, pillView.measuredWidth, pillView.measuredHeight)

        val bitmap = createBitmap(pillView.measuredWidth, pillView.measuredHeight)
        val canvas = Canvas(bitmap)
        pillView.draw(canvas)

        return bitmap.toDrawable(context.resources).apply {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }
    }

    fun getCategoryColor(category: String): Int {
        return PillTheme.getCategoryColor(category)
    }

    private fun appendPill(builder: SpannableStringBuilder, pill: Pill) {
        val start = builder.length
        builder.append(" ${pill.text} ")
        val end = builder.length
        builder.setSpan(ParameterPillSpan(pill.parameterId), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
