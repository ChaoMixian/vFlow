// 文件：main/java/com/chaomixian/vflow/ui/main/MainActivity.kt
// 描述：应用的主活动，承载底部导航和各个主页面 Fragment。
package com.chaomixian.vflow.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TriggerHandlerRegistry
import com.chaomixian.vflow.services.ExecutionNotificationManager
import com.chaomixian.vflow.services.ShizukuManager
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * 应用的主 Activity。
 * 初始化模块注册表，设置Toolbar、底部导航栏和 NavHostFragment。
 * 处理窗口边衬区 (WindowInsets) 以适配系统栏。
 */
class MainActivity : BaseActivity() {

    // SharedPreferences 文件名常量
    companion object {
        const val PREFS_NAME = "vFlowPrefs" // 应用主要偏好设置
        const val LOG_PREFS_NAME = "vFlowLogPrefs" // 日志相关偏好设置
    }

    private var insetsApplied = false // 标记边衬区是否已应用

    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleRegistry.initialize() // 初始化模块注册表
        TriggerHandlerRegistry.initialize() // 初始化触发器处理器注册表
        ExecutionNotificationManager.initialize(this) // 初始化通知管理器
        // 移除此处对 ExecutionLogger 的初始化，因为它已在 TriggerService 中完成
        LogManager.initialize(applicationContext)
        DebugLogger.initialize(applicationContext) // 初始化调试日志记录器
        // 应用启动时，立即发起 Shizuku 预连接
        ShizukuManager.proactiveConnect(applicationContext)
        // 启动后台触发器服务
        startService(Intent(this, TriggerService::class.java))


        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 配置 AppBar，定义顶级导航目标
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_workflows, R.id.navigation_settings)
        )
        setupActionBarWithNavController(navController, appBarConfiguration) // 将 Toolbar 与 NavController 集成
        navView.setupWithNavController(navController) // 将 BottomNavigationView 与 NavController 集成
    }

    /**
     * Activity 启动时，如果尚未应用边衬区，则应用它们。
     * 同时检查并应用启动设置。
     */
    override fun onStart() {
        super.onStart()
        if (!insetsApplied) {
            val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
            val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            applyWindowInsets(appBarLayout, navView, navHostFragment.requireView())
            insetsApplied = true
        }
        // [新增] 每次返回主界面时，检查并应用 Shizuku 相关设置
        checkAndApplyStartupSettings()
    }

    /**
     * 检查并应用 Shizuku 相关的启动设置
     */
    private fun checkAndApplyStartupSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoEnableAccessibility = prefs.getBoolean("autoEnableAccessibility", false)
        val forceKeepAlive = prefs.getBoolean("forceKeepAliveEnabled", false)

        if (autoEnableAccessibility || forceKeepAlive) {
            lifecycleScope.launch {
                if (ShizukuManager.isShizukuActive(this@MainActivity)) {
                    if (autoEnableAccessibility) {
                        // 自动启用无障碍服务，这里不显示 Toast 以避免打扰
                        ShizukuManager.enableAccessibilityService(this@MainActivity)
                    }
                    if (forceKeepAlive) {
                        // 自动启动守护
                        ShizukuManager.startWatcher(this@MainActivity)
                    }
                }
            }
        }
    }


    /**
     * 应用窗口边衬区到 AppBar、底部导航和 Fragment 容器。
     * @param appBar AppBarLayout 视图。
     * @param bottomNav BottomNavigationView 视图。
     * @param fragmentContainer Fragment 容器视图。
     */
    private fun applyWindowInsets(appBar: AppBarLayout, bottomNav: BottomNavigationView, fragmentContainer: View) {
        // 1. 为 AppBar 顶部添加状态栏高度的 padding
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        // 2. 为底部导航栏底部添加系统导航栏高度的 padding
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // 3. 为 Fragment 容器底部添加 BottomNavigationView 高度的 padding
        //    使用 doOnPreDraw 可以确保我们在 bottomNav 视图被测量并准备好绘制之前执行代码，
        //    这样就能获取到它正确的高度，解决了时序问题。
        bottomNav.doOnPreDraw {
            // 在这里，it 指的是 bottomNav 视图，it.height 是它测量后的实际高度。
            fragmentContainer.updatePadding(bottom = it.height)
        }
    }
}