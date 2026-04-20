package com.chaomixian.vflow.ui.main.fragments

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.chaomixian.vflow.ui.common.InsetAwareComposeContainer
import com.chaomixian.vflow.ui.common.VFlowTheme
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.ui.screen.settings.SettingsScreen
import com.chaomixian.vflow.ui.screen.settings.SettingsScreenActions
import com.chaomixian.vflow.ui.settings.ApiSettingsActivity
import com.chaomixian.vflow.ui.settings.CoreManagementActivity
import com.chaomixian.vflow.ui.settings.CrashReportsActivity
import com.chaomixian.vflow.ui.settings.KeyTesterActivity
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.chaomixian.vflow.ui.settings.PermissionGuardianActivity
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

class SettingsFragment : Fragment() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var apiService: ApiService
    private var iconClickCount = 0
    private var lastClickTime = 0L

    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { fileUri ->
                try {
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(DebugLogger.getLogs().toByteArray())
                    }
                    requireContext().toast(R.string.settings_toast_logs_exported)
                } catch (error: Exception) {
                    requireContext().toast(
                        getString(R.string.settings_toast_export_failed, error.message)
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = requireContext().applicationContext
        val workflowManager = WorkflowManager(appContext)
        apiService = ApiService.getInstance(appContext, workflowManager)
        settingsViewModel.refresh(requireContext(), refreshUpdateInfo = true)
        settingsViewModel.setApiRunning(apiService.serverState.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeContainer = InsetAwareComposeContainer(requireContext())
        return composeContainer.apply {
            composeView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            composeView.setContent {
                val uiState = settingsViewModel.uiState.collectAsState().value
                val density = LocalDensity.current

                VFlowTheme {
                    SettingsScreen(
                        uiState = uiState,
                        extraBottomContentPadding = with(density) { contentBottomInsetPx.toDp() },
                        actions = SettingsScreenActions(
                            onSetDynamicColorEnabled = { enabled ->
                                settingsViewModel.setDynamicColorEnabled(requireContext(), enabled)
                                activity?.recreate()
                            },
                            onSetColorfulWorkflowCardsEnabled = { enabled ->
                                settingsViewModel.setColorfulWorkflowCardsEnabled(
                                    requireContext(),
                                    enabled
                                )
                                activity?.recreate()
                            },
                            onSetLiquidGlassNavBarEnabled = { enabled ->
                                settingsViewModel.setLiquidGlassNavBarEnabled(
                                    requireContext(),
                                    enabled
                                )
                                (activity as? MainActivity)?.applyLiquidGlassNavBarEnabled(enabled)
                            },
                            onSetAppScale = { scale ->
                                val currentScale = AppearanceManager.getAppScale(requireContext())
                                val clampedScale = AppearanceManager.clampAppScale(scale)
                                if (abs(clampedScale - currentScale) >= 0.001f) {
                                    settingsViewModel.setAppScale(requireContext(), clampedScale)
                                    activity?.recreate()
                                }
                            },
                            onOpenLanguageDialog = ::showLanguageDialog,
                            onOpenModuleConfig = {
                                startActivity(Intent(requireContext(), ModuleConfigActivity::class.java))
                            },
                            onSetAllowShowOnLockScreen = { enabled ->
                                settingsViewModel.setAllowShowOnLockScreen(requireContext(), enabled)
                            },
                            onSetApiEnabled = ::setApiEnabled,
                            onOpenApiSettings = {
                                startActivity(Intent(requireContext(), ApiSettingsActivity::class.java))
                            },
                            onSetProgressNotificationEnabled = { enabled ->
                                settingsViewModel.setProgressNotificationEnabled(
                                    requireContext(),
                                    enabled
                                )
                            },
                            onSetBackgroundServiceNotificationEnabled = { enabled ->
                                settingsViewModel.setBackgroundServiceNotificationEnabled(
                                    requireContext(),
                                    enabled
                                )
                                requireContext().startService(
                                    Intent(requireContext(), TriggerService::class.java).apply {
                                        action = TriggerService.ACTION_UPDATE_NOTIFICATION
                                    }
                                )
                            },
                            onSetForceKeepAliveEnabled = ::setForceKeepAliveEnabled,
                            onSetAutoEnableAccessibility = ::setAutoEnableAccessibility,
                            onSetEnableTypeFilter = { enabled ->
                                settingsViewModel.setEnableTypeFilter(requireContext(), enabled)
                            },
                            onSetHideFromRecents = { enabled ->
                                settingsViewModel.setHideFromRecents(requireContext(), enabled)
                                requireContext().toast(
                                    if (enabled) {
                                        R.string.settings_toast_hide_from_recents_enabled
                                    } else {
                                        R.string.settings_toast_hide_from_recents_disabled
                                    }
                                )
                            },
                            onSetDefaultShellMode = { mode ->
                                settingsViewModel.setDefaultShellMode(requireContext(), mode)
                            },
                            onOpenPermissionManager = ::openPermissionManager,
                            onOpenPermissionGuardian = {
                                startActivity(
                                    Intent(requireContext(), PermissionGuardianActivity::class.java)
                                )
                            },
                            onSetLoggingEnabled = { enabled ->
                                settingsViewModel.setLoggingEnabled(requireContext(), enabled)
                                requireContext().toast(
                                    if (enabled) {
                                        R.string.settings_toast_logging_enabled
                                    } else {
                                        R.string.settings_toast_logging_disabled
                                    }
                                )
                            },
                            onOpenCrashReports = {
                                startActivity(Intent(requireContext(), CrashReportsActivity::class.java))
                            },
                            onExportLogs = ::exportLogs,
                            onClearLogs = {
                                DebugLogger.clearLogs()
                                requireContext().toast(R.string.settings_toast_logs_cleared)
                            },
                            onRunDiagnostic = ::runDiagnostic,
                            onOpenKeyTester = {
                                startActivity(Intent(requireContext(), KeyTesterActivity::class.java))
                            },
                            onOpenCoreManagement = {
                                startActivity(
                                    Intent(requireContext(), CoreManagementActivity::class.java)
                                )
                            },
                            onStartUiInspector = {
                                requireContext().startService(
                                    Intent(requireContext(), UiInspectorService::class.java)
                                )
                            },
                            onOpenAbout = ::showAboutDialog,
                            onOpenUpdatePage = {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/ChaoMixian/vFlow/releases".toUri()
                                    )
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                apiService.serverState.collect { serverState ->
                    settingsViewModel.setApiRunning(serverState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.refresh(requireContext())
        settingsViewModel.setApiRunning(apiService.serverState.value)
    }

    private fun openPermissionManager() {
        val allPermissions = PermissionManager.getAllRegisteredPermissions()
        val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
            putParcelableArrayListExtra(
                PermissionActivity.EXTRA_PERMISSIONS,
                ArrayList(allPermissions)
            )
        }
        startActivity(intent)
    }

    private fun setApiEnabled(enabled: Boolean) {
        if (enabled) {
            val started = apiService.startServer()
            requireContext().toast(
                if (started) {
                    R.string.api_settings_toast_started
                } else {
                    R.string.api_settings_toast_start_failed
                }
            )
        } else {
            apiService.stopServer()
            requireContext().toast(R.string.api_settings_toast_stopped)
        }
    }

    private fun setForceKeepAliveEnabled(enabled: Boolean) {
        settingsViewModel.setForceKeepAliveEnabled(requireContext(), enabled)
        if (ShellManager.isShizukuActive(requireContext())) {
            if (enabled) {
                ShellManager.startWatcher(requireContext())
                requireContext().toast(R.string.settings_toast_shizuku_watcher_started)
            } else {
                ShellManager.stopWatcher(requireContext())
                requireContext().toast(R.string.settings_toast_shizuku_watcher_stopped)
            }
            return
        }

        requireContext().toast(
            if (enabled) {
                R.string.settings_toast_force_keep_alive_enabled
            } else {
                R.string.settings_toast_force_keep_alive_disabled
            }
        )
    }

    private fun setAutoEnableAccessibility(enabled: Boolean) {
        val canUseShell =
            ShellManager.isShizukuActive(requireContext()) || ShellManager.isRootAvailable()
        if (!canUseShell) {
            requireContext().toast(R.string.settings_toast_operation_failed)
            settingsViewModel.refresh(requireContext())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (enabled) {
                    ShellManager.enableAccessibilityService(requireContext())
                } else {
                    ShellManager.disableAccessibilityService(requireContext())
                }
            }

            if (success) {
                settingsViewModel.setAutoEnableAccessibility(requireContext(), enabled)
                requireContext().toast(
                    if (enabled) {
                        R.string.settings_toast_auto_accessibility_enabled
                    } else {
                        R.string.settings_toast_auto_accessibility_disabled
                    }
                )
            } else {
                requireContext().toast(R.string.settings_toast_operation_failed)
                settingsViewModel.refresh(requireContext())
            }
        }
    }

    private fun exportLogs() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        exportLogsLauncher.launch("vflow_log_${timestamp}.txt")
    }

    private fun runDiagnostic() {
        if (!ShellManager.isShizukuActive(requireContext())) {
            requireContext().toast(R.string.settings_toast_shizuku_not_active)
            return
        }

        requireContext().toast(R.string.settings_toast_diagnostic_running)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            ShellDiagnostic.diagnose(requireContext())
            ShellDiagnostic.runKeyEventDiagnostic(requireContext())
            withContext(Dispatchers.Main) {
                requireContext().toast(R.string.settings_toast_diagnostic_complete)
            }
        }
    }

    private fun showAboutDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val versionTextView = dialogView.findViewById<TextView>(R.id.text_version)
        val githubButton = dialogView.findViewById<Button>(R.id.button_github)
        val appIcon = dialogView.findViewById<android.widget.ImageView>(R.id.app_icon)

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            )
            versionTextView.text = getString(R.string.about_version_label, packageInfo.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            versionTextView.visibility = View.GONE
        }

        appIcon.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 2000) {
                iconClickCount++
                lastClickTime = currentTime
                if (iconClickCount >= 5) {
                    startActivity(Intent(requireContext(), ChangelogActivity::class.java))
                    iconClickCount = 0
                }
            } else {
                iconClickCount = 1
                lastClickTime = currentTime
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        githubButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/ChaoMixian/vFlow".toUri()))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLanguageDialog() {
        val currentLanguage = LocaleManager.getLanguage(requireContext())
        val languages = LocaleManager.SUPPORTED_LANGUAGES.keys.toList()
        val languageNames = LocaleManager.SUPPORTED_LANGUAGES.values.toList()
        val checkedItem = languages.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language_dialog_title)
            .setSingleChoiceItems(languageNames.toTypedArray(), checkedItem) { dialog, which ->
                val selectedLanguage = languages[which]
                if (selectedLanguage != currentLanguage) {
                    LocaleManager.setLanguage(requireContext(), selectedLanguage)
                    showRestartDialog()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showRestartDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_toast_language_changed)
            .setMessage(R.string.settings_toast_restart_needed)
            .setPositiveButton(R.string.settings_button_restart) { _, _ ->
                (activity as? MainActivity)?.safeRestart()
            }
            .setNegativeButton(R.string.settings_button_later, null)
            .setCancelable(false)
            .show()
    }
}
