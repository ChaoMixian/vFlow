// 文件: main/java/com/chaomixian/vflow/ui/main/fragments/SettingsFragment.kt
package com.chaomixian.vflow.ui.main.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.chaomixian.vflow.core.locale.toast
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.data.update.UpdateChecker
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.services.ShellDiagnostic
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.ui.changelog.ChangelogActivity
import com.chaomixian.vflow.ui.settings.KeyTesterActivity
import com.chaomixian.vflow.core.locale.LocaleManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * “设置” Fragment。
 * 提供应用相关的设置选项，如动态颜色和权限管理入口。
 */
class SettingsFragment : Fragment() {

    private var iconClickCount = 0  // 图标点击计数器
    private var lastClickTime = 0L
    private var updateInfo: com.chaomixian.vflow.data.update.UpdateInfo? = null

    // 新增一个 ActivityResultLauncher 用于处理文件导出
    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { fileUri ->
                try {
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(DebugLogger.getLogs().toByteArray())
                    }
                    requireContext().toast(R.string.settings_toast_logs_exported)
                } catch (e: Exception) {
                    requireContext().toast(getString(R.string.settings_toast_export_failed, e.message))
                }
            }
        }

    /** 创建并返回 Fragment 的视图。 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireActivity().getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)

        // 检查更新
        checkForUpdates(view)

        // 动态颜色开关逻辑
        val dynamicColorSwitch = view.findViewById<MaterialSwitch>(R.id.switch_dynamic_color)
        dynamicColorSwitch.isChecked = prefs.getBoolean("dynamicColorEnabled", false)
        dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("dynamicColorEnabled", isChecked) }
            requireActivity().recreate() // 重新创建Activity以应用主题更改
        }

        // 进度通知开关逻辑
        val progressNotificationSwitch = view.findViewById<MaterialSwitch>(R.id.switch_progress_notification)
        progressNotificationSwitch.isChecked = prefs.getBoolean("progressNotificationEnabled", true) // 默认开启
        progressNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("progressNotificationEnabled", isChecked) }
        }

        // 后台服务通知开关逻辑
        val bgServiceNotificationSwitch = view.findViewById<MaterialSwitch>(R.id.switch_background_service_notification)
        bgServiceNotificationSwitch.isChecked = prefs.getBoolean("backgroundServiceNotificationEnabled", true)
        bgServiceNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("backgroundServiceNotificationEnabled", isChecked) }
            // 通知服务更新状态
            val intent = Intent(requireContext(), TriggerService::class.java).apply {
                action = TriggerService.ACTION_UPDATE_NOTIFICATION
            }
            requireContext().startService(intent)
        }

        // 强制保活开关逻辑
        val forceKeepAliveSwitch = view.findViewById<MaterialSwitch>(R.id.switch_force_keep_alive)
        if (ShellManager.isShizukuActive(requireContext())) {
            forceKeepAliveSwitch.isEnabled = true
            forceKeepAliveSwitch.isChecked = prefs.getBoolean("forceKeepAliveEnabled", false)
            forceKeepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("forceKeepAliveEnabled", isChecked) }
                if (isChecked) {
                    ShellManager.startWatcher(requireContext())
                    requireContext().toast(R.string.settings_toast_shizuku_watcher_started)
                } else {
                    ShellManager.stopWatcher(requireContext())
                    requireContext().toast(R.string.settings_toast_shizuku_watcher_stopped)
                }
            }
        } else {
            forceKeepAliveSwitch.isEnabled = false
            forceKeepAliveSwitch.isChecked = false
            forceKeepAliveSwitch.text = getString(R.string.settings_switch_keep_alive_unavailable, getString(R.string.settings_switch_force_keep_alive))
        }

        // 自动开启无障碍服务开关逻辑
        val autoEnableAccessibilitySwitch = view.findViewById<MaterialSwitch>(R.id.switch_auto_enable_accessibility)

        // 使用 ShellManager 检查 Shizuku 或 Root 是否可用
        val canUseShell = ShellManager.isShizukuActive(requireContext()) || ShellManager.isRootAvailable()

        if (canUseShell) {
            autoEnableAccessibilitySwitch.isEnabled = true
            autoEnableAccessibilitySwitch.isChecked = prefs.getBoolean("autoEnableAccessibility", false)
            autoEnableAccessibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("autoEnableAccessibility", isChecked) }
                lifecycleScope.launch {
                    val success = if (isChecked) {
                        ShellManager.enableAccessibilityService(requireContext())
                    } else {
                        ShellManager.disableAccessibilityService(requireContext())
                    }
                    // 在主线程显示 Toast
                    launch(Dispatchers.Main) {
                        if (success) {
                            val toastRes = if (isChecked) R.string.settings_toast_auto_accessibility_enabled else R.string.settings_toast_auto_accessibility_disabled
                            requireContext().toast(toastRes)
                        } else {
                            requireContext().toast(R.string.settings_toast_operation_failed)
                            autoEnableAccessibilitySwitch.isChecked = !isChecked
                            prefs.edit { putBoolean("autoEnableAccessibility", !isChecked) }
                        }
                    }
                }
            }
        } else {
            autoEnableAccessibilitySwitch.isEnabled = false
            autoEnableAccessibilitySwitch.isChecked = false
            autoEnableAccessibilitySwitch.text = getString(R.string.settings_switch_auto_accessibility_unavailable)
        }

        // 权限与 Shell 设置
        val shellModeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_shell_mode)
        val defaultMode = prefs.getString("default_shell_mode", "shizuku")
        if (defaultMode == "root") {
            shellModeToggle.check(R.id.btn_shell_root)
        } else {
            shellModeToggle.check(R.id.btn_shell_shizuku)
        }

        shellModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = if (checkedId == R.id.btn_shell_root) "root" else "shizuku"
                prefs.edit { putString("default_shell_mode", mode) }
            }
        }

        // 权限管理器入口
        view.findViewById<View>(R.id.btn_open_permission_manager).setOnClickListener {
            val allPermissions = PermissionManager.getAllRegisteredPermissions()
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(allPermissions))
            }
            startActivity(intent)
        }

        // --- 调试功能逻辑 ---
        val loggingSwitch = view.findViewById<MaterialSwitch>(R.id.switch_enable_logging)
        val exportButton = view.findViewById<Button>(R.id.button_export_logs)
        val clearButton = view.findViewById<Button>(R.id.button_clear_logs)
        val diagnoseButton = view.findViewById<Button>(R.id.button_run_diagnostic)
        val keyTesterButton = view.findViewById<Button>(R.id.button_key_tester)

        // 初始化状态
        val isLoggingEnabled = DebugLogger.isLoggingEnabled()
        loggingSwitch.isChecked = isLoggingEnabled
        exportButton.isEnabled = isLoggingEnabled
        clearButton.isEnabled = isLoggingEnabled
        diagnoseButton.isEnabled = isLoggingEnabled
        // keyTesterButton 可以保持开启，因为它主要依赖 Shizuku

        loggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            DebugLogger.setLoggingEnabled(isChecked, requireContext())
            exportButton.isEnabled = isChecked
            clearButton.isEnabled = isChecked
            diagnoseButton.isEnabled = isChecked
            if (isChecked) {
                requireContext().toast(R.string.settings_toast_logging_enabled)
            } else {
                requireContext().toast(R.string.settings_toast_logging_disabled)
            }
        }

        exportButton.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "vflow_log_${timestamp}.txt"
            exportLogsLauncher.launch(fileName)
        }

        clearButton.setOnClickListener {
            DebugLogger.clearLogs()
            requireContext().toast(R.string.settings_toast_logs_cleared)
        }

        // 运行全面诊断
        diagnoseButton.setOnClickListener {
            if (!ShellManager.isShizukuActive(requireContext())) {
                requireContext().toast(R.string.settings_toast_shizuku_not_active)
                return@setOnClickListener
            }

            requireContext().toast(R.string.settings_toast_diagnostic_running)
            lifecycleScope.launch(Dispatchers.IO) {
                ShellDiagnostic.diagnose(requireContext())
                ShellDiagnostic.runKeyEventDiagnostic(requireContext())
                launch(Dispatchers.Main) {
                    requireContext().toast(R.string.settings_toast_diagnostic_complete)
                }
            }
        }

        // 启动按键测试器
        keyTesterButton.setOnClickListener {
            val intent = Intent(requireContext(), KeyTesterActivity::class.java)
            startActivity(intent)
        }

        // 语言设置
        view.findViewById<View>(R.id.btn_language_settings).setOnClickListener {
            showLanguageDialog()
        }

        // 启动Core管理
        val CoreManagementButton = view.findViewById<Button>(R.id.button_core_management)
        CoreManagementButton.setOnClickListener {
            val intent = Intent(requireContext(), com.chaomixian.vflow.ui.settings.CoreManagementActivity::class.java)
            startActivity(intent)
        }

        view.findViewById<MaterialCardView>(R.id.card_about).setOnClickListener {
            showAboutDialog()
        }

        return view
    }

    /**
     * 显示"关于"对话框的方法
     */
    private fun showAboutDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val versionTextView = dialogView.findViewById<TextView>(R.id.text_version)
        val githubButton = dialogView.findViewById<Button>(R.id.button_github)
        val appIcon = dialogView.findViewById<android.widget.ImageView>(R.id.app_icon)

        // 动态获取版本名
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionTextView.text = getString(R.string.about_version_label, pInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            versionTextView.visibility = View.GONE
        }

        // 图标点击彩蛋 - 连续点击5次打开版本时间线
        appIcon.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 2000) {  // 2秒内的点击才计数
                iconClickCount++
                lastClickTime = currentTime  // 更新最后点击时间
                if (iconClickCount >= 5) {
                    // 打开版本时间线
                    val intent = Intent(requireContext(), ChangelogActivity::class.java)
                    startActivity(intent)
                    iconClickCount = 0  // 重置计数
                }
            } else {
                iconClickCount = 1  // 超过时间间隔，重新计数
                lastClickTime = currentTime  // 更新最后点击时间
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        githubButton.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW,
                "https://github.com/ChaoMixian/vFlow".toUri())
            startActivity(browserIntent)
            dialog.dismiss() // 点击后关闭对话框
        }

        dialog.show()
    }

    /**
     * 显示语言选择对话框
     */
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
                    // 切换语言
                    LocaleManager.setLanguage(requireContext(), selectedLanguage)

                    // 显示重启提示
                    showRestartDialog()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    /**
     * 显示重启应用对话框
     */
    private fun showRestartDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_toast_language_changed)
            .setMessage(R.string.settings_toast_restart_needed)
            .setPositiveButton(R.string.settings_button_restart) { _, _ ->
                // 重启应用
                val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                requireActivity().finish()
            }
            .setNegativeButton(R.string.settings_button_later, null)
            .setCancelable(false)
            .show()
    }

    /**
     * 检查更新
     */
    private fun checkForUpdates(view: View) {
        lifecycleScope.launch {
            try {
                val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                val currentVersion = pInfo.versionName ?: "1.0.0"

                val result = UpdateChecker.checkUpdate(currentVersion)

                result.onSuccess { info ->
                    if (info.hasUpdate) {
                        updateInfo = info
                        showUpdateCard(view, info)
                    }
                }
            } catch (e: Exception) {
                // 忽略更新检查失败
            }
        }
    }

    /**
     * 显示更新卡片
     */
    private fun showUpdateCard(view: View, info: com.chaomixian.vflow.data.update.UpdateInfo) {
        val updateCard = view.findViewById<MaterialCardView>(R.id.card_update_available)
        updateCard.visibility = View.VISIBLE

        val textUpdateVersion = view.findViewById<TextView>(R.id.text_update_version)
        val buttonUpdate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_update)

        textUpdateVersion.text = getString(R.string.settings_update_available, info.latestVersion)

        buttonUpdate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                "https://github.com/ChaoMixian/vFlow/releases".toUri())
            startActivity(intent)
        }
    }
}