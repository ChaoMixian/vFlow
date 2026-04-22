package com.chaomixian.vflow.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.R
import com.chaomixian.vflow.api.ApiService
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellDiagnostic
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.services.UiInspectorService
import com.chaomixian.vflow.ui.changelog.ChangelogActivity
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.ui.viewmodel.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun SettingsRoute(
    extraBottomContentPadding: androidx.compose.ui.unit.Dp,
    activity: MainActivity,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = remember(context) { context.applicationContext }
    val apiService = remember(appContext) {
        val workflowManager = WorkflowManager(appContext)
        ApiService.getInstance(appContext, workflowManager)
    }
    val uiState = settingsViewModel.uiState.collectAsState().value

    val exportLogsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { fileUri ->
                try {
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(DebugLogger.getLogs().toByteArray())
                    }
                    context.toast(R.string.settings_toast_logs_exported)
                } catch (error: Exception) {
                    context.toast(
                        context.getString(R.string.settings_toast_export_failed, error.message)
                    )
                }
            }
        }

    DisposableEffect(lifecycleOwner, context, apiService) {
        settingsViewModel.refresh(context, refreshUpdateInfo = true)
        settingsViewModel.setApiRunning(apiService.serverState.value)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    settingsViewModel.setApiRunning(apiService.serverState.value)
                }

                Lifecycle.Event.ON_RESUME -> {
                    settingsViewModel.refresh(context)
                    settingsViewModel.setApiRunning(apiService.serverState.value)
                }

                else -> Unit
            }
        }

        val job = activity.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                apiService.serverState.collect { serverState ->
                    settingsViewModel.setApiRunning(serverState)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            job.cancel()
        }
    }

    SettingsScreen(
        uiState = uiState,
        extraBottomContentPadding = extraBottomContentPadding,
        modifier = modifier,
        actions = SettingsScreenActions(
            onSetDynamicColorEnabled = { enabled ->
                settingsViewModel.setDynamicColorEnabled(context, enabled)
                activity.recreate()
            },
            onSetColorfulWorkflowCardsEnabled = { enabled ->
                settingsViewModel.setColorfulWorkflowCardsEnabled(context, enabled)
                activity.recreate()
            },
            onSetLiquidGlassNavBarEnabled = { enabled ->
                settingsViewModel.setLiquidGlassNavBarEnabled(context, enabled)
                activity.applyLiquidGlassNavBarEnabled(enabled)
            },
            onSetAppScale = { scale ->
                val currentScale = AppearanceManager.getAppScale(context)
                val clampedScale = AppearanceManager.clampAppScale(scale)
                if (abs(clampedScale - currentScale) >= 0.001f) {
                    settingsViewModel.setAppScale(context, clampedScale)
                    activity.recreate()
                }
            },
            onOpenLanguageDialog = {
                showLanguageDialog(context = context, activity = activity)
            },
            onOpenModuleConfig = {
                context.startActivity(Intent(context, ModuleConfigActivity::class.java))
            },
            onOpenModelConfig = {
                context.startActivity(ModelConfigActivity.createIntent(context))
            },
            onSetAutoCheckUpdatesEnabled = { enabled ->
                settingsViewModel.setAutoCheckUpdatesEnabled(context, enabled)
            },
            onSetAllowShowOnLockScreen = { enabled ->
                settingsViewModel.setAllowShowOnLockScreen(context, enabled)
            },
            onSetApiEnabled = { enabled ->
                if (enabled) {
                    val started = apiService.startServer()
                    context.toast(
                        if (started) {
                            R.string.api_settings_toast_started
                        } else {
                            R.string.api_settings_toast_start_failed
                        }
                    )
                } else {
                    apiService.stopServer()
                    context.toast(R.string.api_settings_toast_stopped)
                }
            },
            onOpenApiSettings = {
                context.startActivity(Intent(context, ApiSettingsActivity::class.java))
            },
            onSetProgressNotificationEnabled = { enabled ->
                settingsViewModel.setProgressNotificationEnabled(context, enabled)
            },
            onSetBackgroundServiceNotificationEnabled = { enabled ->
                settingsViewModel.setBackgroundServiceNotificationEnabled(context, enabled)
                context.startService(
                    Intent(context, TriggerService::class.java).apply {
                        action = TriggerService.ACTION_UPDATE_NOTIFICATION
                    }
                )
            },
            onSetForceKeepAliveEnabled = { enabled ->
                settingsViewModel.setForceKeepAliveEnabled(context, enabled)
                if (ShellManager.isShizukuActive(context)) {
                    if (enabled) {
                        ShellManager.startWatcher(context)
                        context.toast(R.string.settings_toast_shizuku_watcher_started)
                    } else {
                        ShellManager.stopWatcher(context)
                        context.toast(R.string.settings_toast_shizuku_watcher_stopped)
                    }
                } else {
                    context.toast(
                        if (enabled) {
                            R.string.settings_toast_force_keep_alive_enabled
                        } else {
                            R.string.settings_toast_force_keep_alive_disabled
                        }
                    )
                }
            },
            onSetAutoEnableAccessibility = { enabled ->
                val canUseShell = ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()
                if (!canUseShell) {
                    context.toast(R.string.settings_toast_operation_failed)
                    settingsViewModel.refresh(context)
                } else {
                    activity.lifecycleScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            if (enabled) {
                                ShellManager.enableAccessibilityService(context)
                            } else {
                                ShellManager.disableAccessibilityService(context)
                            }
                        }
                        if (success) {
                            settingsViewModel.setAutoEnableAccessibility(context, enabled)
                            context.toast(
                                if (enabled) {
                                    R.string.settings_toast_auto_accessibility_enabled
                                } else {
                                    R.string.settings_toast_auto_accessibility_disabled
                                }
                            )
                        } else {
                            context.toast(R.string.settings_toast_operation_failed)
                            settingsViewModel.refresh(context)
                        }
                    }
                }
            },
            onSetEnableTypeFilter = { enabled ->
                settingsViewModel.setEnableTypeFilter(context, enabled)
            },
            onSetHideFromRecents = { enabled ->
                settingsViewModel.setHideFromRecents(context, enabled)
                context.toast(
                    if (enabled) {
                        R.string.settings_toast_hide_from_recents_enabled
                    } else {
                        R.string.settings_toast_hide_from_recents_disabled
                    }
                )
            },
            onSetDefaultShellMode = { mode ->
                settingsViewModel.setDefaultShellMode(context, mode)
            },
            onOpenPermissionManager = {
                val allPermissions = PermissionManager.getAllRegisteredPermissions()
                context.startActivity(
                    Intent(context, PermissionActivity::class.java).apply {
                        putParcelableArrayListExtra(
                            PermissionActivity.EXTRA_PERMISSIONS,
                            ArrayList(allPermissions)
                        )
                    }
                )
            },
            onOpenPermissionGuardian = {
                context.startActivity(Intent(context, PermissionGuardianActivity::class.java))
            },
            onSetLoggingEnabled = { enabled ->
                settingsViewModel.setLoggingEnabled(context, enabled)
                context.toast(
                    if (enabled) {
                        R.string.settings_toast_logging_enabled
                    } else {
                        R.string.settings_toast_logging_disabled
                    }
                )
            },
            onOpenCrashReports = {
                context.startActivity(Intent(context, CrashReportsActivity::class.java))
            },
            onExportLogs = {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                exportLogsLauncher.launch("vflow_log_${timestamp}.txt")
            },
            onClearLogs = {
                DebugLogger.clearLogs()
                context.toast(R.string.settings_toast_logs_cleared)
            },
            onRunDiagnostic = {
                runDiagnostic(context = context, scope = activity.lifecycleScope)
            },
            onOpenKeyTester = {
                context.startActivity(Intent(context, KeyTesterActivity::class.java))
            },
            onOpenCoreManagement = {
                context.startActivity(Intent(context, CoreManagementActivity::class.java))
            },
            onStartUiInspector = {
                context.startService(Intent(context, UiInspectorService::class.java))
            },
            onOpenAbout = {
                showAboutDialog(context = context)
            },
            onOpenUpdatePage = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/ChaoMixian/vFlow/releases".toUri()
                    )
                )
            }
        )
    )
}

private fun runDiagnostic(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (!ShellManager.isShizukuActive(context)) {
        context.toast(R.string.settings_toast_shizuku_not_active)
        return
    }

    context.toast(R.string.settings_toast_diagnostic_running)
    scope.launch(Dispatchers.IO) {
        ShellDiagnostic.diagnose(context)
        ShellDiagnostic.runKeyEventDiagnostic(context)
        withContext(Dispatchers.Main) {
            context.toast(R.string.settings_toast_diagnostic_complete)
        }
    }
}

private fun showAboutDialog(context: android.content.Context) {
    var iconClickCount = 0
    var lastClickTime = 0L
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about, null)
    val versionTextView = dialogView.findViewById<TextView>(R.id.text_version)
    val githubButton = dialogView.findViewById<Button>(R.id.button_github)
    val appIcon = dialogView.findViewById<android.widget.ImageView>(R.id.app_icon)

    try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        versionTextView.text = context.getString(R.string.about_version_label, packageInfo.versionName)
    } catch (_: PackageManager.NameNotFoundException) {
        versionTextView.visibility = View.GONE
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(dialogView)
        .create()

    appIcon.setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 2000) {
            iconClickCount++
            lastClickTime = currentTime
            if (iconClickCount >= 5) {
                context.startActivity(Intent(context, ChangelogActivity::class.java))
                iconClickCount = 0
            }
        } else {
            iconClickCount = 1
            lastClickTime = currentTime
        }
    }

    githubButton.setOnClickListener {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/ChaoMixian/vFlow".toUri()))
        dialog.dismiss()
    }

    dialog.show()
}

private fun showLanguageDialog(
    context: android.content.Context,
    activity: MainActivity,
) {
    val currentLanguage = LocaleManager.getLanguage(context)
    val languages = LocaleManager.SUPPORTED_LANGUAGES.keys.toList()
    val languageNames = LocaleManager.SUPPORTED_LANGUAGES.values.toList()
    val checkedItem = languages.indexOf(currentLanguage)

    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.settings_language_dialog_title)
        .setSingleChoiceItems(languageNames.toTypedArray(), checkedItem) { dialog, which ->
            val selectedLanguage = languages[which]
            if (selectedLanguage != currentLanguage) {
                LocaleManager.setLanguage(context, selectedLanguage)
                showRestartDialog(context, activity)
            }
            dialog.dismiss()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showRestartDialog(
    context: android.content.Context,
    activity: MainActivity,
) {
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.settings_toast_language_changed)
        .setMessage(R.string.settings_toast_restart_needed)
        .setPositiveButton(R.string.settings_button_restart) { _, _ ->
            activity.safeRestart()
        }
        .setNegativeButton(R.string.settings_button_later, null)
        .setCancelable(false)
        .show()
}
