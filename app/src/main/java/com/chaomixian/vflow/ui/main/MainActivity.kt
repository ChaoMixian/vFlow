package com.chaomixian.vflow.ui.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : BaseActivity() {

    companion object {
        const val PREFS_NAME = "vFlowPrefs"
        const val LOG_PREFS_NAME = "vFlowLogPrefs"
    }

    private var insetsApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleRegistry.initialize()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_workflows, R.id.navigation_settings)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onStart() {
        super.onStart()
        if (!insetsApplied) {
            val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
            val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            applyWindowInsets(appBarLayout, navView, navHostFragment.requireView())
            insetsApplied = true
        }
    }

    private fun applyWindowInsets(appBar: AppBarLayout, bottomNav: BottomNavigationView, fragmentContainer: View) {
        // 1. 处理顶部状态栏，给 AppBar 增加上边距
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        // 2. 处理底部系统导航栏，给 BottomNavigationView 增加下边距
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // 3. 【通用解决方案】给 Fragment 容器增加下边距，防止内容被 BottomNavigationView 遮挡
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { view, insets ->
            // 获取底部导航栏的高度，该高度已包含系统导航栏的边距
            val bottomNavHeight = bottomNav.height

            // 总的底部内边距仅为底部导航栏的高度
            view.updatePadding(bottom = bottomNavHeight)

            insets
        }
    }
}