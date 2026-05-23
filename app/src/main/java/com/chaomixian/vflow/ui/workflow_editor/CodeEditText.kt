package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.ColorUtils
import kotlin.math.max

class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    private var lineNumberPaint: Paint? = null
    private val gutterEndPadding = dp(8)
    private val gutterStartPadding = dp(2)
    private var appliedGutterWidth = 0

    init {
        typeface = Typeface.MONOSPACE
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        gravity = android.view.Gravity.TOP
        setHorizontallyScrolling(false)
        lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
        }
        updateLineNumberPaint()
        updateGutterWidth()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        updateLineNumberPaint()
        updateGutterWidth()
    }

    override fun setTextColor(color: Int) {
        super.setTextColor(color)
        updateLineNumberPaint()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        updateGutterWidth()
    }

    override fun onDraw(canvas: Canvas) {
        updateGutterWidth()
        drawLineNumbers(canvas)
        super.onDraw(canvas)
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val layout = layout ?: return
        val paint = lineNumberPaint ?: return
        val firstVisibleLine = layout.getLineForVertical(scrollY)
        val lastVisibleLine = layout.getLineForVertical(scrollY + height)
        val x = (paddingLeft - gutterEndPadding).toFloat()

        for (layoutLineIndex in firstVisibleLine..lastVisibleLine) {
            val lineStart = layout.getLineStart(layoutLineIndex)
            if (lineStart > 0 && text?.getOrNull(lineStart - 1) != '\n') continue

            val sourceLineNumber = countSourceLinesBefore(lineStart) + 1
            val baseline = layout.getLineBaseline(layoutLineIndex) + totalPaddingTop
            canvas.drawText(sourceLineNumber.toString(), x, baseline.toFloat(), paint)
        }
    }

    private fun updateLineNumberPaint() {
        val paint = lineNumberPaint ?: return
        paint.textSize = textSize * 0.9f
        paint.typeface = Typeface.MONOSPACE
        paint.color = ColorUtils.setAlphaComponent(currentTextColor.takeIf { it != 0 } ?: Color.GRAY, 120)
    }

    private fun updateGutterWidth() {
        val paint = lineNumberPaint ?: return
        val digits = max(2, sourceLineCount().toString().length)
        val numberWidth = paint.measureText("9".repeat(digits)).toInt()
        val gutterWidth = gutterStartPadding + numberWidth + gutterEndPadding
        if (gutterWidth == appliedGutterWidth) return

        appliedGutterWidth = gutterWidth
        setPadding(gutterWidth, paddingTop, paddingRight, paddingBottom)
    }

    private fun sourceLineCount(): Int {
        return (text?.count { it == '\n' } ?: 0) + 1
    }

    private fun countSourceLinesBefore(offset: Int): Int {
        val editable = text ?: return 0
        var count = 0
        val end = offset.coerceAtMost(editable.length)
        for (i in 0 until end) {
            if (editable[i] == '\n') count++
        }
        return count
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
