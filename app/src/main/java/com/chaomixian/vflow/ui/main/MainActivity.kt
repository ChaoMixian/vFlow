// 文件：main/java/com/chaomixian/vflow/ui/main/MainActivity.kt
// 描述：应用的主活动，承载底部导航和各个主页面 Fragment。
package com.chaomixian.vflow.ui.main

import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.CrashReport
import com.chaomixian.vflow.core.logging.CrashReportManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.opencv.OpenCVManager
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TriggerHandlerRegistry
import com.chaomixian.vflow.services.ExecutionNotificationManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.services.PermissionGuardianService
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.chaomixian.vflow.services.CoreManagementService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var uiShellReady = false
    private var navigationReady = false
    private var startupCompleted = false
    private var pendingCrashExportText: String? = null
    private val exportCrashReportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            try {
                val reportText = pendingCrashExportText ?: return@registerForActivityResult
                val targetUri = uri ?: return@registerForActivityResult
                val outputStream = contentResolver.openOutputStream(targetUri)
                    ?: throw IllegalStateException("Failed to open output stream")
                outputStream.use {
                    outputStream.write(reportText.toByteArray())
                }
                toast(R.string.settings_toast_logs_exported)
            } catch (e: Exception) {
                toast(getString(R.string.settings_toast_export_failed, e.message))
            } finally {
                pendingCrashExportText = null
            }
        }

    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 检查首次运行
        if (prefs.getBoolean("is_first_run", true)) {
            startActivity(Intent(this, com.chaomixian.vflow.ui.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }

        initializeUiShell()

        if (savedInstanceState == null) {
            val pendingCrashReport = CrashReportManager.getPendingCrashReport()
            if (pendingCrashReport != null) {
                maybeShowPendingCrashReport(pendingCrashReport) {
                    continueStartup(savedInstanceState)
                }
                return
            }
        }

        continueStartup(savedInstanceState)
    }

    private fun initializeUiShell() {
        if (uiShellReady) return
        uiShellReady = true

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    private fun initializeNavigation() {
        if (navigationReady) return
        navigationReady = true

        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val hasGraph = runCatching { navController.graph.id }.getOrNull() != null
        if (!hasGraph) {
            navController.setGraph(R.navigation.main_nav_graph, null)
        }

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_workflows,
                R.id.navigation_modules,
                R.id.navigation_repository,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    private fun continueStartup(savedInstanceState: Bundle?) {
        if (startupCompleted) return
        startupCompleted = true

        ModuleRegistry.initialize(applicationContext) // 初始化模块注册表
        ModuleManager.loadModules(this, true) // 初始化用户模块管理器
        TriggerHandlerRegistry.initialize() // 初始化触发器处理器注册表
        ExecutionNotificationManager.initialize(this) // 初始化通知管理器
        // 移除此处对 ExecutionLogger 的初始化，因为它已在 TriggerService 中完成
        LogManager.initialize(applicationContext)
        DebugLogger.initialize(applicationContext) // 初始化调试日志记录器

        // 初始化 OpenCV (在后台线程)
        lifecycleScope.launch(Dispatchers.IO) {
            OpenCVManager.initialize(applicationContext)
        }

        // 应用启动时，立即发起 Shizuku 预连接
        ShellManager.proactiveConnect(applicationContext)
        // 检查并自动启动 vFlow Core
        checkCoreAutoStart()
        // 检查并自动启动权限守护
        checkPermissionGuardianAutoStart()
        // 启动后台触发器服务
        startService(Intent(this, TriggerService::class.java))
        initializeNavigation()
    }

    /**
     * Activity 启动时，如果尚未应用边衬区，则应用它们。
     * 同时检查并应用启动设置。
     */
    override fun onStart() {
        super.onStart()
        if (uiShellReady && !insetsApplied) {
            val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
            val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            applyWindowInsets(appBarLayout, navView, navHostFragment.requireView())
            // insetsApplied 会在 applyWindowInsets 的回调中设置
        }
        if (startupCompleted) {
            // 每次返回主界面时，检查并应用 Shizuku 相关设置
            checkAndApplyStartupSettings()
        }
    }

    /**
     * 当Activity进入后台时，检查是否需要从最近任务中隐藏
     */
    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hideFromRecents = prefs.getBoolean("hideFromRecents", false)
        if (hideFromRecents) {
            // 从最近任务中移除当前Activity
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.appTasks.forEach { task ->
                if (task.taskInfo.baseActivity?.packageName == packageName) {
                    task.setExcludeFromRecents(true)
                }
            }
        }
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
                val shizukuActive = ShellManager.isShizukuActive(this@MainActivity)
                val rootAvailable = ShellManager.isRootAvailable()
                if (autoEnableAccessibility && (shizukuActive || rootAvailable)) {
                    // 自动启用无障碍服务，这里不显示 Toast 以避免打扰
                    ShellManager.ensureAccessibilityServiceRunning(this@MainActivity)
                }
                if (forceKeepAlive && shizukuActive) {
                    // 自动启动守护，仅支持 Shizuku
                    ShellManager.startWatcher(this@MainActivity)
                }
            }
        }
    }

    /**
     * 检查并自动启动 vFlow Core
     */
    private fun checkCoreAutoStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStartEnabled = prefs.getBoolean("core_auto_start_enabled", false)
        val savedMode = prefs.getString("preferred_core_launch_mode", null)

        DebugLogger.d("MainActivity", "checkCoreAutoStart: autoStartEnabled=$autoStartEnabled, savedMode=$savedMode")

        if (autoStartEnabled) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val isRunning = com.chaomixian.vflow.services.VFlowCoreBridge.ping()
                DebugLogger.d("MainActivity", "Core is running: $isRunning")

                if (!isRunning) {
                    DebugLogger.i("MainActivity", "自动启动 vFlow Core with EXTRA_AUTO_START=true")
                    val intent = Intent(this@MainActivity, CoreManagementService::class.java).apply {
                        action = CoreManagementService.ACTION_START_CORE
                        putExtra(CoreManagementService.EXTRA_AUTO_START, true)
                    }
                    startService(intent)
                } else {
                    DebugLogger.d("MainActivity", "Core 已经在运行，跳过自动启动")
                }
            }
        }
    }

    /**
     * 检查并自动启动权限守护服务
     */
    private fun checkPermissionGuardianAutoStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guardianEnabled = prefs.getBoolean("accessibilityGuardEnabled", false)

        DebugLogger.d("MainActivity", "checkPermissionGuardianAutoStart: guardianEnabled=$guardianEnabled")

        if (guardianEnabled) {
            DebugLogger.i("MainActivity", "权限守护已启用，自动启动权限守护服务")
            PermissionGuardianService.start(this)
        } else {
            DebugLogger.d("MainActivity", "权限守护未启用，跳过自动启动")
        }
    }

    private fun maybeShowPendingCrashReport(
        report: CrashReport,
        onComplete: () -> Unit
    ) {
        val summary = getString(
            R.string.crash_report_dialog_message,
            crashDateFormat.format(Date(report.timestamp)),
            report.threadName,
            report.exceptionType,
            report.exceptionMessage ?: getString(R.string.crash_report_message_unknown)
        )

        var openedDetails = false
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_report_dialog_title)
            .setMessage(summary)
            .setPositiveButton(R.string.crash_report_action_view) { _, _ ->
                openedDetails = true
                showCrashReportDetails(report, onComplete)
            }
            .setNeutralButton(R.string.common_delete) { _, _ ->
                CrashReportManager.clearPendingCrashReport()
                toast(R.string.crash_report_deleted)
                onComplete()
            }
            .setNegativeButton(R.string.crash_report_action_later) { _, _ ->
                onComplete()
            }
            .create()

        dialog.setOnDismissListener {
            if (!openedDetails) {
                onComplete()
            }
        }
        dialog.show()
    }

    private fun showCrashReportDetails(
        report: CrashReport,
        onComplete: () -> Unit
    ) {
        val reportText = CrashReportManager.formatReport(report)
        val textView = TextView(this).apply {
            text = reportText
            setTextIsSelectable(true)
            setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding)
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_report_detail_title)
            .setView(scrollView)
            .setPositiveButton(R.string.common_share) { _, _ ->
                shareCrashReport(reportText)
            }
            .setNeutralButton(R.string.common_export) { _, _ ->
                exportCrashReport(report, reportText)
            }
            .setNegativeButton(R.string.common_close, null)
            .create()

        dialog.setOnDismissListener {
            onComplete()
        }
        dialog.show()
    }

    private fun exportCrashReport(report: CrashReport, reportText: String) {
        pendingCrashExportText = reportText
        val fileName = "vflow-crash-${exportDateFormat.format(Date(report.timestamp))}.txt"
        exportCrashReportLauncher.launch(fileName)
    }

    private fun shareCrashReport(reportText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_share_subject))
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_report_share_title)))
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

        // 为底部导航栏底部添加系统导航栏高度的 padding
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // 为 Fragment 容器底部添加 BottomNavigationView 高度的 padding
        // 使用 ViewTreeObserver 监听布局变化，直到获取到准确的高度值
        val globalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 移除监听器，避免重复调用
                bottomNav.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 获取底部导航栏的准确高度（包含 padding）
                val bottomNavHeight = bottomNav.height

                // 只有高度有效时才应用 padding
                if (bottomNavHeight > 0) {
                    fragmentContainer.updatePadding(bottom = bottomNavHeight)
                    insetsApplied = true
                } else {
                    // 如果高度仍为 0，延迟重试
                    bottomNav.post {
                        fragmentContainer.updatePadding(bottom = bottomNav.height)
                        insetsApplied = true
                    }
                }
            }
        }
        bottomNav.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private val dialogPadding: Int by lazy {
        resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dialog_padding_material)
    }

    private val crashDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val exportDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}
