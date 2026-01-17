// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/pill/PillTheme.kt
// 描述: Pill主题管理器，负责颜色和主题相关的逻辑
package com.chaomixian.vflow.ui.workflow_editor.pill

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R

/**
 * Pill主题管理器
 *
 * 负责管理Pill的颜色和主题相关的逻辑。
 * 提取自旧的PillUtil，职责更加单一明确。
 */
object PillTheme {

    /**
     * 获取模块分类对应的颜色资源ID
     *
     * 根据模块的category返回对应的颜色资源ID。
     * 对于未知的category，根据Android版本返回动态颜色或静态颜色。
     *
     * @param category 模块的分类字符串（如"触发器"、"逻辑控制"等）
     * @return 对应的颜色资源ID
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

    /**
     * 获取上下文中的实际颜色值
     *
     * 将颜色资源ID转换为实际的Int颜色值。
     *
     * @param context Android上下文
     * @param colorRes 颜色资源ID
     * @return 实际的颜色Int值
     */
    fun getColor(context: Context, colorRes: Int): Int {
        return ContextCompat.getColor(context, colorRes)
    }
}
