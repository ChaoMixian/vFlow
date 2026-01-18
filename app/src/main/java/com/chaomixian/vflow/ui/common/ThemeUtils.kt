package com.chaomixian.vflow.ui.common

import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.chaomixian.vflow.R

/**
 * 主题工具类
 * 提供统一的主题获取逻辑，供 Activity、Service、Manager 等使用
 * 支持 View 系统和 Compose 系统
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

    /**
     * 获取 Compose Material3 颜色方案
     * 支持：
     * 1. 动态取色（Material You）
     * 2. 深色模式自动切换
     * 3. 降级到默认配色
     *
     * @param darkTheme 是否使用深色主题（null 表示自动跟随系统）
     * @return ColorScheme 实例
     */
    @Composable
    fun getAppColorScheme(darkTheme: Boolean? = null): ColorScheme {
        val context = LocalContext.current
        val useDynamicColor = isDynamicColorEnabled(context)
        val isDarkTheme = darkTheme ?: isSystemInDarkTheme()

        return when {
            // Android 12+ 支持动态取色
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // 降级到默认配色
            isDarkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
}
