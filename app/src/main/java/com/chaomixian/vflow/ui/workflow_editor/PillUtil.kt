// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/PillUtil.kt
// 描述: Pill工具类（兼容层）- 委托给新的架构
//
// @deprecated 此类为兼容层保留，新代码应使用：
// - com.chaomixian.vflow.core.pill.PillFormatter (创建Pill)
// - com.chaomixian.vflow.ui.workflow_editor.pill.AndroidPillBuilder (构建Spannable)
// - com.chaomixian.vflow.ui.workflow_editor.pill.PillTheme (颜色管理)
// - com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan (点击标记)

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.pill.Pill as CorePill
import com.chaomixian.vflow.core.pill.PillFormatter
import com.chaomixian.vflow.core.pill.PillType
import com.chaomixian.vflow.ui.workflow_editor.pill.AndroidPillBuilder
import com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan
import com.chaomixian.vflow.ui.workflow_editor.pill.PillTheme
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

/**
 * 可点击参数药丸的 Span（兼容别名）
 *
 * @deprecated 使用 com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan 替代
 */
@Deprecated(
    "Use ParameterPillSpan from pill package instead",
    ReplaceWith("ParameterPillSpan", "com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan")
)
typealias ParameterPillSpan = com.chaomixian.vflow.ui.workflow_editor.pill.ParameterPillSpan

/**
 * 参数药丸 (Pill) UI 工具类（兼容层）
 *
 * @deprecated 此类保留用于向后兼容。新代码应使用：
 * - PillFormatter.createPillFromParam() 替代 createPillFromParam()
 * - AndroidPillBuilder().build() 替代 buildSpannable()
 * - PillTheme.getCategoryColor() 替代 getCategoryColor()
 * - ParameterPillSpan 替代 PillUtil.ParameterPillSpan
 */
@Deprecated(
    "Use PillFormatter from core.pill package instead",
    ReplaceWith("PillFormatter", "com.chaomixian.vflow.core.pill.PillFormatter")
)
object PillUtil {

    /**
     * 参数药丸的数据模型（兼容层）
     *
     * @deprecated 使用 com.chaomixian.vflow.core.pill.Pill 替代
     */
    @Deprecated(
        "Use Pill from core.pill package instead",
        ReplaceWith("CorePill", "com.chaomixian.vflow.core.pill.Pill")
    )
    data class Pill(
        val text: String,
        val parameterId: String,
        val isModuleOption: Boolean = false,
    ) {
        /**
         * 转换为新的核心Pill对象
         */
        fun toCorePill(): CorePill {
            return CorePill(
                text = text,
                parameterId = parameterId,
                type = if (isModuleOption) PillType.MODULE_OPTION else PillType.PARAMETER
            )
        }

        companion object {
            /**
             * 从核心Pill对象转换为旧的Pill对象
             */
            fun fromCorePill(corePill: CorePill): Pill {
                return Pill(
                    text = corePill.text,
                    parameterId = corePill.parameterId,
                    isModuleOption = corePill.type == PillType.MODULE_OPTION
                )
            }
        }
    }

    /**
     * 从模块参数创建 Pill（兼容方法）
     *
     * @deprecated 使用 PillFormatter.createPillFromParam() 替代
     */
    @Deprecated(
        "Use PillFormatter.createPillFromParam() instead",
        ReplaceWith("PillFormatter.createPillFromParam(paramValue, inputDef, if (isModuleOption) PillType.MODULE_OPTION else PillType.PARAMETER)")
    )
    fun createPillFromParam(
        paramValue: Any?,
        inputDef: InputDefinition?,
        isModuleOption: Boolean = false
    ): Pill {
        val corePill = PillFormatter.createPillFromParam(
            paramValue,
            inputDef,
            if (isModuleOption) PillType.MODULE_OPTION else PillType.PARAMETER
        )
        return Pill.fromCorePill(corePill)
    }

    /**
     * 构建包含药丸"结构"的 Spannable 文本（兼容方法）
     *
     * @deprecated 使用 AndroidPillBuilder(context).build() 替代
     */
    @Deprecated(
        "Use AndroidPillBuilder(context).build() instead",
        ReplaceWith("AndroidPillBuilder(context).build(*parts)")
    )
    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        // 将旧的Pill对象转换为核心Pill对象
        val convertedParts = parts.map { part ->
            when (part) {
                is Pill -> part.toCorePill()
                else -> part
            }
        }.toTypedArray()

        @Suppress("UNCHECKED_CAST")
        return AndroidPillBuilder(context).build(*convertedParts) as CharSequence
    }

    /**
     * 创建一个用于在 EditText 中显示的"药丸"Drawable。
     *
     * 注意：此方法暂时保留，未来可能会移到专门的UI工具类中。
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
     * 根据模块分类获取颜色资源ID（兼容方法）
     *
     * @deprecated 使用 PillTheme.getCategoryColor() 替代
     */
    @Deprecated(
        "Use PillTheme.getCategoryColor() instead",
        ReplaceWith("PillTheme.getCategoryColor(category)")
    )
    fun getCategoryColor(category: String): Int {
        return PillTheme.getCategoryColor(category)
    }
}