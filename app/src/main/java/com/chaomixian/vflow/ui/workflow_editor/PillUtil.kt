// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/PillUtil.kt
// 描述: 一个纯粹的、无状态的工具类，用于构建包含“药丸”结构的Spannable文本。

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

/**
 * 参数药丸 (Pill) UI 工具类。
 * 提供构建和处理摘要中药丸样式的 Spannable 的方法。
 * 这个类是无状态的，不依赖于工作流的上下文。
 */
object PillUtil {

    /**
     * 可点击参数药丸的 Span。
     * 这是一个自定义的ClickableSpan，用于在摘要文本中标记一个可点击的区域，
     * 并携带其关联的参数ID，以便上层UI能够响应点击事件。
     *
     * @param parameterId 关联的参数ID，用于告知回调哪个参数被点击了。
     */
    class ParameterPillSpan(val parameterId: String) : ClickableSpan() {
        // 点击事件由外部的OnTouchListener统一处理，这里为空实现。
        override fun onClick(widget: View) {}
        // 外观由PillRenderer中的RoundedBackgroundSpan处理，这里不需要额外操作。
        override fun updateDrawState(ds: TextPaint) {}
    }

    /**
     * 参数药丸的数据模型，用于在构建Spannable时传递结构化信息。
     *
     * @param text 药丸上显示的原始文本或变量引用。
     * @param parameterId 对应 InputDefinition 的 ID。
     * @param isModuleOption 是否为模块自身配置项 (例如If模块的操作符)。
     */
    data class Pill(
        val text: String,
        val parameterId: String,
        val isModuleOption: Boolean = false,
    )

    /**
     * 从模块参数创建 Pill 的标准方法。
     * 封装了变量检测和数字格式化逻辑。
     *
     * @param paramValue 步骤中存储的原始参数值。
     * @param inputDef 该参数的输入定义，用于获取默认值和ID。
     * @param isModuleOption 是否为模块的内置选项。
     * @return 一个配置好的 Pill 对象。
     */
    fun createPillFromParam(
        paramValue: Any?,
        inputDef: InputDefinition?,
        isModuleOption: Boolean = false
    ): Pill {
        val paramStr = paramValue?.toString()
        val text: String = if (paramStr.isMagicVariable() || paramStr.isNamedVariable()) {
            // 如果是变量，直接使用引用字符串作为文本
            paramStr!!
        } else {
            // 否则，格式化静态值
            val valueToFormat = paramValue ?: inputDef?.defaultValue
            when (valueToFormat) {
                is Number -> { // 格式化数字，整数不显示小数点
                    if (valueToFormat.toDouble() == valueToFormat.toLong().toDouble()) {
                        valueToFormat.toLong().toString()
                    } else {
                        String.format("%.2f", valueToFormat.toDouble())
                    }
                }
                else -> valueToFormat?.toString() ?: "..."
            }
        }
        return Pill(text, inputDef?.id ?: "", isModuleOption)
    }

    /**
     * 构建包含药丸“结构”的 Spannable 文本。
     * 此方法只负责创建Spannable和自定义的ParameterPillSpan，不负责具体的UI渲染。
     *
     * @param context Android上下文。
     * @param parts 包含String和Pill对象的序列。
     * @return 一个包含未渲染药丸的 CharSequence。
     */
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> {
                    val start = builder.length
                    // 药丸文本前后加空格，以便渲染时有间距
                    builder.append(" ${part.text} ")
                    val end = builder.length
                    // 附加 ParameterPillSpan，用于标记此区域为可点击的参数药丸
                    val span = ParameterPillSpan(part.parameterId)
                    builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return builder
    }

    /**
     * 创建一个用于在 EditText 中显示的“药丸”Drawable。
     * 它通过将一个预设的 XML 布局（magic_variable_pill.xml）绘制到 Bitmap 上来实现。
     * @param context 上下文。
     * @param text 药丸上显示的文本。
     * @return 一个包含文本的 Drawable，其边界已正确设置。
     */
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

    /**
     * 根据模块分类获取颜色资源ID。
     * @param category 模块的分类字符串。
     * @return 对应的颜色资源ID。
     */
    fun getCategoryColor(category: String): Int = when (category) {
        "触发器" -> R.color.category_trigger
        "界面交互" -> R.color.category_ui_interaction
        "逻辑控制" -> R.color.category_logic
        "数据" -> R.color.category_data
        "文件" -> R.color.category_file
        "网络" -> R.color.category_network
        "应用与系统" -> R.color.category_system
        "Shizuku" -> R.color.category_shizuku
        "用户模块" -> R.color.category_user_module
        else -> {
            // 动态颜色仅在 Android 12 (S) 及以上可用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                com.google.android.material.R.color.material_dynamic_neutral30
            } else {
                // 低版本回退到 colors.xml 中定义的静态颜色
                R.color.static_pill_color
            }
        }
    }
}