// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/pill/ParameterPillSpan.kt
// 描述: 可点击参数药丸的Span，用于在文本中标记可点击的Pill区域
package com.chaomixian.vflow.ui.workflow_editor.pill

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

/**
 * 可点击参数药丸的 Span
 *
 * 这是一个自定义的ClickableSpan，用于在摘要文本中标记一个可点击的区域，
 * 并携带其关联的参数ID，以便上层UI能够响应点击事件。
 *
 * 设计理念：
 * - onClick: 空实现。点击事件由外部的OnTouchListener统一处理，不在Span内部处理。
 * - updateDrawState: 空实现。外观由PillRenderer中的RoundedBackgroundSpan处理，这里不需要额外操作。
 *
 * 这个Span的职责仅仅是标记文本区域和携带parameterId元数据。
 *
 * @property parameterId 关联的参数ID，用于告知回调哪个参数被点击了
 */
class ParameterPillSpan(val parameterId: String) : ClickableSpan() {
    /**
     * 点击事件（空实现）
     *
     * 实际的点击处理由ActionStepAdapter中的OnTouchListener统一处理。
     * 这样可以在一个地方处理所有Pill的点击事件，便于统一管理。
     */
    override fun onClick(widget: View) {}

    /**
     * 更新绘制状态（空实现）
     *
     * Pill的视觉外观（背景色、圆角等）由PillRenderer.RoundedBackgroundSpan处理。
     * 这个Span不参与视觉渲染，只负责标记区域和携带元数据。
     */
    override fun updateDrawState(ds: TextPaint) {}
}
