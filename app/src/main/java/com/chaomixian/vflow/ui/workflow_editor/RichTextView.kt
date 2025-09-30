// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextView.kt
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

    // 正则表达式，用于匹配两种变量引用格式
    private val variablePattern = Pattern.compile("(\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\])")

    /**
     * 将包含变量引用（如 {{...}} 或 [[...]]）的纯文本转换为可显示的 Spannable。
     * @param rawText 包含变量引用的字符串。
     * @param getPillDrawable 一个函数，根据变量引用字符串返回一个用于显示的 Drawable。
     */
    fun setRichText(rawText: String, getPillDrawable: (String) -> Drawable) {
        val spannable = SpannableStringBuilder()
        val matcher = variablePattern.matcher(rawText)
        var lastEnd = 0

        while (matcher.find()) {
            // 添加变量引用之前的纯文本部分
            spannable.append(rawText.substring(lastEnd, matcher.start()))

            // 将变量引用渲染成“药丸”
            val variableRef = matcher.group(1)
            if (variableRef != null) {
                val drawable = getPillDrawable(variableRef)
                val start = spannable.length
                // 使用变量引用本身作为占位符文本
                spannable.append(variableRef)
                val end = spannable.length
                // 使用自定义的 ImageSpan 来实现垂直居中
                spannable.setSpan(CenterAlignedImageSpan(drawable), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                // [修复] 在每个 pill 后面附加一个零宽字符，以便光标可以定位
                spannable.append("\u200B")
            }
            lastEnd = matcher.end()
        }

        // 添加最后一个变量引用之后的纯文本部分
        if (lastEnd < rawText.length) {
            spannable.append(rawText.substring(lastEnd))
        }

        setText(spannable)
    }

    /**
     * 将当前编辑器中的 Spannable 内容转换回包含变量引用的纯文本字符串。
     * @return 包含变量引用的原始字符串。
     */
    fun getRawText(): String {
        // [修复] 在返回原始文本之前，移除所有用于光标定位的零宽字符
        return text.toString().replace("\u200B", "")
    }

    /**
     * 在当前光标位置插入一个变量“药丸”。
     * @param variableReference 变量的引用字符串，例如 "{{step_id.output_id}}"。
     * @param drawable 用于渲染该变量的“药丸”Drawable。
     */
    fun insertVariablePill(variableReference: String, drawable: Drawable) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)

        val spannable = text as SpannableStringBuilder

        val span = CenterAlignedImageSpan(drawable)

        // [修复] 要插入的文本包含变量引用和一个零宽字符
        val textToInsert = "$variableReference\u200B"

        spannable.replace(start, end, textToInsert)

        // 仅对变量引用部分应用 ImageSpan
        spannable.setSpan(span, start, start + variableReference.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // [修复] 将光标移动到零宽字符之后，从而完美地定位在 "药丸" 之后
        setSelection(start + textToInsert.length)
    }

    // [修复] 移除有问题的 onSelectionChanged 覆盖，恢复安卓原生、流畅的光标行为。
    // 用户现在可以像在普通文本框中一样自由移动光标。
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