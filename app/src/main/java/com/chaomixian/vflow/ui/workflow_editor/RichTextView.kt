// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextView.kt
// 描述: [已修改] 在setRichText和insertVariablePill方法中为变量药丸前后添加了空格。

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import java.util.regex.Pattern

/**
 * 实现一个支持文本和“变量药丸”混合输入的自定义 EditText。
 */
class RichTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val variablePattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")

    /**
     * 将包含变量引用的纯文本转换为可显示的 Spannable。
     * [已修改] 在每个药丸前后添加空格。
     */
    fun setRichText(rawText: String, getPillDrawable: (String) -> Drawable) {
        val spannable = SpannableStringBuilder()
        val matcher = variablePattern.matcher(rawText)
        var lastEnd = 0

        while (matcher.find()) {
            spannable.append(rawText.substring(lastEnd, matcher.start()))

            val variableRef = matcher.group(1)
            if (variableRef != null) {
                val drawable = getPillDrawable(variableRef)
                // 在药丸前后添加空格
                spannable.append(" ")
                val start = spannable.length
                spannable.append(variableRef)
                val end = spannable.length
                spannable.setSpan(CenterAlignedImageSpan(drawable), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // 零宽字符用于光标定位
                spannable.append(" \u200B")
            }
            lastEnd = matcher.end()
        }

        if (lastEnd < rawText.length) {
            spannable.append(rawText.substring(lastEnd))
        }

        setText(spannable)
    }

    /**
     * 将当前编辑器中的 Spannable 内容转换回包含变量引用的纯文本字符串。
     * [已修改] 移除所有零宽字符和药丸前后的空格。
     */
    fun getRawText(): String {
        // 匹配 " {{...}} " 或 " [[...]] " 这样的模式，并将其替换回 " {{...}} "
        val textWithSpans = text.toString()
        val matcher = variablePattern.matcher(textWithSpans)
        val result = StringBuffer()
        while(matcher.find()) {
            // 将 " {{var}} " 替换为 "{{var}}"
            matcher.appendReplacement(result, matcher.group(0).trim())
        }
        matcher.appendTail(result)

        // 移除所有用于光标定位的零宽字符
        return result.toString().replace("\u200B", "")
    }


    /**
     * 在当前光标位置插入一个变量“药丸”。
     * 在插入的药丸前后添加空格。
     */
    fun insertVariablePill(variableReference: String, drawable: Drawable) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)

        val spannable = text as SpannableStringBuilder
        val span = CenterAlignedImageSpan(drawable)

        // 要插入的文本包含前后的空格和用于光标定位的零宽字符
        val textToInsert = " $variableReference \u200B"

        spannable.replace(start, end, textToInsert)

        // ImageSpan 只应用于变量引用本身，不包括空格
        val spanStart = start + 1
        val spanEnd = spanStart + variableReference.length
        spannable.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 将光标移动到整个插入内容的末尾
        setSelection(start + textToInsert.length)
    }
}

/**
 * 自定义 ImageSpan，使其在垂直方向上与文本居中对齐。
 */
class CenterAlignedImageSpan(drawable: Drawable) : ImageSpan(drawable) {
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
        val b = drawable
        val fm = paint.fontMetricsInt
        val transY = top + (bottom - top) / 2 - b.bounds.height() / 2

        canvas.save()
        canvas.translate(x, transY.toFloat())
        b.draw(canvas)
        canvas.restore()
    }
}