package com.chaomixian.vflow.ui.common

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.chaomixian.vflow.R // 确保 R 包名正确

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 主题必须在 super.onCreate() 之前设置
        applyDynamicTheme()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    private fun applyDynamicTheme() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val useDynamicColor = prefs.getBoolean("dynamicColorEnabled", false)
        if (useDynamicColor) {
            setTheme(R.style.Theme_vFlow_Dynamic)
        } else {
            setTheme(R.style.Theme_vFlow)
        }
    }
}