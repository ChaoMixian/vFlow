// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/pill/AndroidPillBuilder.kt
// 描述: Android平台的Pill构建器实现
package com.chaomixian.vflow.ui.workflow_editor.pill

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import com.chaomixian.vflow.core.pill.Pill
import com.chaomixian.vflow.core.pill.PillBuilder

/**
 * Android平台的Pill构建器实现
 *
 * 实现PillBuilder接口，返回Android的Spannable对象。
 * 这是核心模块与Android UI层之间的桥梁。
 *
 * @param context Android上下文，用于访问资源
 */
class AndroidPillBuilder(private val context: Context) : PillBuilder {

    /**
     * 构建包含Pill的Android Spannable文本
     *
     * 此方法将混合的文本片段和Pill对象转换为Android的Spannable。
     * 每个Pill会被标记一个ParameterPillSpan，用于后续的点击处理和视觉渲染。
     *
     * @param parts 文本片段和Pill的混合序列
     * @return Android Spannable对象
     */
    override fun build(vararg parts: Any): Any {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val start = builder.length
                    // Pill文本前后加空格，以便渲染时撑开Pill
                    builder.append(" ${part.text} ")
                    val end = builder.length
                    // 附加 ParameterPillSpan，用于标记此区域为可点击的参数药丸
                    val span = ParameterPillSpan(part.parameterId)
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    // 忽略不支持的类型
                }
            }
        }
        return builder
    }
}
