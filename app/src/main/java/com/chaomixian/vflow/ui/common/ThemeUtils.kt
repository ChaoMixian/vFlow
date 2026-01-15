package com.chaomixian.vflow.ui.common

import android.content.Context
import android.view.ContextThemeWrapper
import com.chaomixian.vflow.R

/**
 * 主题工具类
 * 提供统一的主题获取逻辑，供 Activity、Service、Manager 等使用
 */
object ThemeUtils {

    private const val PREFS_NAME = "vFlowPrefs"
    private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamicColorEnabled"

    /**
     * 获取主题资源 ID
     * @param context 上下文
     * @param transparent 是否使用透明主题（用于悬浮窗等场景）
     * @return 主题资源 ID
     */
    @JvmStatic
    fun getThemeResId(context: Context, transparent: Boolean = false): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false)

        return when {
            transparent && useDynamicColor -> R.style.Theme_vFlow_Transparent_Dynamic
            transparent && !useDynamicColor -> R.style.Theme_vFlow_Transparent_Default
            !transparent && useDynamicColor -> R.style.Theme_vFlow_Dynamic
            else -> R.style.Theme_vFlow
        }
    }

    /**
     * 创建带主题的 ContextThemeWrapper
     * @param context 原始上下文
     * @param transparent 是否使用透明主题
     * @return 包装了主题的 Context
     */
    @JvmStatic
    fun createThemedContext(context: Context, transparent: Boolean = false): ContextThemeWrapper {
        val themeResId = getThemeResId(context, transparent)
        return ContextThemeWrapper(context, themeResId)
    }

    /**
     * 检查是否启用了动态取色
     * @param context 上下文
     * @return 是否启用动态取色
     */
    @JvmStatic
    fun isDynamicColorEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false)
    }
}
