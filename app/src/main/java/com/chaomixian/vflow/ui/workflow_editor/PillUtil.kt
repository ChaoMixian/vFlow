// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/PillUtil.kt
// 描述: Pill工具类 - 编辑器内统一的 Pill 构建入口

package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

object PillUtil {

    /**
     * 编辑器摘要层使用的 Pill 数据模型。
     * 保留 isModuleOption 这个高层意图，避免业务层直接感知底层 PillType。
     */
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

    fun buildSpannable(context: Context, vararg parts: Any): CharSequence {
        // 暂时保留 context 参数以保持现有调用面稳定，避免大规模迁移摘要构建代码。
        val builder = SpannableStringBuilder()
        parts.forEach { part ->
            when (part) {
                is String -> builder.append(part)
                is Pill -> appendPill(builder, part)
                is CorePill -> appendPill(builder, Pill.fromCorePill(part))
                is CharSequence -> builder.append(part)
                else -> {
                    // 保持旧行为：忽略不支持的类型
                }
            }
        }
        return builder
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
