package com.chaomixian.vflow.ui.common

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.chaomixian.vflow.R

// 文件：BaseActivity.kt
// 描述：应用中所有 Activity 的基类，提供通用功能如动态主题应用。

/**
 * Activity 的基类。
 * 主要负责在 Activity 创建时应用动态主题，并设置窗口布局以适配系统边衬区。
 */
abstract class BaseActivity : AppCompatActivity() {

    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用动态主题（必须在 super.onCreate() 和 setContentView() 之前调用）
        applyDynamicTheme()
        // 配置窗口以允许内容绘制到系统栏区域后面，实现沉浸式效果
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    /** 根据用户偏好设置应用动态主题或默认主题。 */
    private fun applyDynamicTheme() {
        val themeResId = ThemeUtils.getThemeResId(this)
        setTheme(themeResId)
    }
}