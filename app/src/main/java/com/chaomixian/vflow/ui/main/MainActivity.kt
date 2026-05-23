package com.chaomixian.vflow.ui.main

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.CrashReport
import com.chaomixian.vflow.core.logging.CrashReportManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.opencv.OpenCVManager
import com.chaomixian.vflow.core.telemetry.TelemetryManager
import com.chaomixian.vflow.core.workflow.WorkflowPermissionRecovery
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TriggerHandlerRegistry
import com.chaomixian.vflow.services.ExecutionNotificationManager
import com.chaomixian.vflow.services.PermissionGuardianService
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.services.VoiceTriggerService
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.onboarding.ConsentUpdateActivity
import com.chaomixian.vflow.ui.onboarding.OnboardingActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.chaomixian.vflow.services.CoreManagementService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用的主 Activity。
 * 负责启动流程和主界面 Compose 外壳。
 */
class MainActivity : BaseActivity() {
    companion object {
        const val PREFS_NAME = "vFlowPrefs"
        const val LOG_PREFS_NAME = "vFlowLogPrefs"
        const val KEY_IS_FIRST_RUN = "is_first_run"
        const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        private const val EXTRA_INITIAL_MAIN_TAB_TAG = "initial_main_tab_tag"
        private const val STATE_CURRENT_MAIN_TAB_TAG = "current_main_tab_tag"

        fun createAppLaunchIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        }
    }

    private var uiShellReady = false
    private var startupCompleted = false
    private var contentReady by mutableStateOf(false)
    private var liquidGlassNavBarEnabled by mutableStateOf(false)
    private var pendingCrashExportText: String? = null
    private var currentMainTabTag: String? = null
    private var initialMainTab: MainTopLevelTab = MainTopLevelTab.HOME
    private var initialWorkflowSortMode: WorkflowSortMode = WorkflowSortMode.Default
    private var initialWorkflowLayoutMode: WorkflowLayoutMode = WorkflowLayoutMode.List
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
    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldFinishDuplicateLauncherLaunch()) {
            finish()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        liquidGlassNavBarEnabled = AppearanceManager.isLiquidGlassNavBarEnabled(this)
        initialMainTab = resolveInitialMainTab(savedInstanceState)
        initialWorkflowSortMode = resolveInitialWorkflowSortMode()
        initialWorkflowLayoutMode = resolveInitialWorkflowLayoutMode()
        currentMainTabTag = null

        val isFirstRun = prefs.getBoolean(KEY_IS_FIRST_RUN, true)
        val disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)

        if (isFirstRun) {
            startActivity(
                OnboardingActivity.createIntent(
                    context = this,
                    skipDisclaimerPage = disclaimerAccepted
                )
            )
            finish()
            return
        }

        if (!disclaimerAccepted) {
            startActivity(Intent(this, ConsentUpdateActivity::class.java))
            finish()
            return
        }

        initializeUiShell()

        if (savedInstanceState == null) {
            val pendingCrashReport = CrashReportManager.getPendingCrashReport()
            if (pendingCrashReport != null) {
                maybeShowPendingCrashReport(pendingCrashReport) {
                    continueStartup()
                }
                return
            }
        }

        continueStartup()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (TelemetryManager.isEnabled(this)) {
            TelemetryManager.onKillProcess(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_CURRENT_MAIN_TAB_TAG,
            currentMainTabTag ?: initialMainTab.fragmentTag
        )
        super.onSaveInstanceState(outState)
    }

    @androidx.compose.material3.ExperimentalMaterial3Api
    private fun initializeUiShell() {
        if (uiShellReady) return
        uiShellReady = true
        window.isNavigationBarContrastEnforced = false
        setContent {
            MainActivityContent(
                isReady = contentReady,
                liquidGlassNavBarEnabled = liquidGlassNavBarEnabled,
                activity = this,
                initialTab = initialMainTab,
                initialWorkflowSortMode = initialWorkflowSortMode,
                initialWorkflowLayoutMode = initialWorkflowLayoutMode,
                onBackPressedAtRoot = { finish() },
                onPrimaryTabChanged = ::setPrimaryMainTab,
            )
        }
    }

    private fun shouldFinishDuplicateLauncherLaunch(): Boolean {
        if (isTaskRoot) {
            return false
        }

        val launchIntent = intent ?: return false
        return launchIntent.action == Intent.ACTION_MAIN &&
            launchIntent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun continueStartup() {
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
        checkCoreAutoStart()
        checkPermissionGuardianAutoStart()
        startService(Intent(this, TriggerService::class.java))
        contentReady = true
    }

    /**
     * Activity 进入前台时，重新分发窗口边衬区并检查启动设置。
     */
    override fun onStart() {
        super.onStart()
        VoiceTriggerService.startIfEligible(this)
        if (startupCompleted) {
            checkAndApplyStartupSettings()
            lifecycleScope.launch(Dispatchers.IO) {
                WorkflowPermissionRecovery.recoverEligibleWorkflows(applicationContext)
            }
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
                    ShellManager.ensureAccessibilityServiceRunning(this@MainActivity)
                }
                if (forceKeepAlive && shizukuActive) {
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

    internal fun setPrimaryMainTab(tab: MainTopLevelTab) {
        if (currentMainTabTag == tab.fragmentTag) {
            return
        }
        currentMainTabTag = tab.fragmentTag
    }

    fun applyLiquidGlassNavBarEnabled(enabled: Boolean) {
        liquidGlassNavBarEnabled = enabled
    }

    fun safeRestart() {
        val nextTab = currentMainTabTag ?: initialMainTab.fragmentTag
        val restartIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_INITIAL_MAIN_TAB_TAG, nextTab)
        }
        startActivity(restartIntent)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun resolveInitialMainTab(savedInstanceState: Bundle?): MainTopLevelTab {
        val requestedTag = savedInstanceState?.getString(STATE_CURRENT_MAIN_TAB_TAG)
            ?: intent.getStringExtra(EXTRA_INITIAL_MAIN_TAB_TAG)
        return MainTopLevelTab.entries.firstOrNull { it.fragmentTag == requestedTag } ?: MainTopLevelTab.HOME
    }

    private fun resolveInitialWorkflowSortMode(): WorkflowSortMode {
        val storedValue = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("workflow_sort_mode", WorkflowSortMode.Default.name)
        return WorkflowSortMode.entries.firstOrNull { it.name == storedValue } ?: WorkflowSortMode.Default
    }

    private fun resolveInitialWorkflowLayoutMode(): WorkflowLayoutMode {
        val storedValue = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("workflow_layout_mode", WorkflowLayoutMode.List.name)
        return WorkflowLayoutMode.entries.firstOrNull { it.name == storedValue } ?: WorkflowLayoutMode.List
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

    private val dialogPadding: Int by lazy {
        resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dialog_padding_material)
    }

    private val crashDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val exportDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}
