// 文件：SettingsFragment.kt
// 描述：主界面中的“设置”屏幕。
package com.chaomixian.vflow.ui.main.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShizukuManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * “设置” Fragment。
 * 提供应用相关的设置选项，如动态颜色、隐藏连接线和权限管理入口。
 */
class SettingsFragment : Fragment() {

    // 新增一个 ActivityResultLauncher 用于处理文件导出
    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { fileUri ->
                try {
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(DebugLogger.getLogs().toByteArray())
                    }
                    Toast.makeText(requireContext(), "日志导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
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

        // 强制保活开关逻辑
        val forceKeepAliveSwitch = view.findViewById<MaterialSwitch>(R.id.switch_force_keep_alive)
        if (ShizukuManager.isShizukuActive(requireContext())) {
            forceKeepAliveSwitch.isEnabled = true
            forceKeepAliveSwitch.isChecked = prefs.getBoolean("forceKeepAliveEnabled", false)
            forceKeepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("forceKeepAliveEnabled", isChecked) }
                if (isChecked) {
                    ShizukuManager.startWatcher(requireContext())
                    Toast.makeText(requireContext(), "Shizuku 守护已开启", Toast.LENGTH_SHORT).show()
                } else {
                    ShizukuManager.stopWatcher(requireContext())
                    Toast.makeText(requireContext(), "Shizuku 守护已关闭", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            forceKeepAliveSwitch.isEnabled = false
            forceKeepAliveSwitch.isChecked = false
            forceKeepAliveSwitch.text = "${getString(R.string.settings_switch_force_keep_alive)} (Shizuku未激活)"
        }

        // --- 新增：自动开启无障碍服务开关逻辑 ---
        val autoEnableAccessibilitySwitch = view.findViewById<MaterialSwitch>(R.id.switch_auto_enable_accessibility)
        if (ShizukuManager.isShizukuActive(requireContext())) {
            autoEnableAccessibilitySwitch.isEnabled = true
            autoEnableAccessibilitySwitch.isChecked = prefs.getBoolean("autoEnableAccessibility", false)
            autoEnableAccessibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("autoEnableAccessibility", isChecked) }
                lifecycleScope.launch {
                    val success = if (isChecked) {
                        ShizukuManager.enableAccessibilityService(requireContext())
                    } else {
                        ShizukuManager.disableAccessibilityService(requireContext())
                    }
                    // 在主线程显示 Toast
                    launch(Dispatchers.Main) {
                        if (success) {
                            val status = if (isChecked) "开启" else "关闭"
                            Toast.makeText(requireContext(), "自动无障碍服务已$status", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "操作失败，请检查Shizuku状态", Toast.LENGTH_SHORT).show()
                            // 操作失败时，将开关恢复原状
                            autoEnableAccessibilitySwitch.isChecked = !isChecked
                            prefs.edit { putBoolean("autoEnableAccessibility", !isChecked) }
                        }
                    }
                }
            }
        } else {
            autoEnableAccessibilitySwitch.isEnabled = false
            autoEnableAccessibilitySwitch.isChecked = false
            autoEnableAccessibilitySwitch.text = "自动开启无障碍服务 (Shizuku未激活)"
        }

        // --- 新增调试功能逻辑 ---
        val loggingSwitch = view.findViewById<MaterialSwitch>(R.id.switch_enable_logging)
        val exportButton = view.findViewById<Button>(R.id.button_export_logs)
        val clearButton = view.findViewById<Button>(R.id.button_clear_logs)

        loggingSwitch.isChecked = DebugLogger.isLoggingEnabled()
        exportButton.isEnabled = DebugLogger.isLoggingEnabled()
        clearButton.isEnabled = DebugLogger.isLoggingEnabled()

        loggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            DebugLogger.setLoggingEnabled(isChecked, requireContext())
            exportButton.isEnabled = isChecked
            clearButton.isEnabled = isChecked
            if (isChecked) {
                Toast.makeText(requireContext(), "调试日志已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "调试日志已关闭并清空", Toast.LENGTH_SHORT).show()
            }
        }

        exportButton.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "vflow_log_${timestamp}.txt"
            exportLogsLauncher.launch(fileName)
        }

        clearButton.setOnClickListener {
            DebugLogger.clearLogs()
            Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show()
        }


        // 权限管理器入口卡片点击逻辑
        view.findViewById<MaterialCardView>(R.id.card_permission_manager).setOnClickListener {
            val allPermissions = PermissionManager.getAllRegisteredPermissions()
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                // 将所有已注册的权限传递给权限管理Activity
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(allPermissions))
            }
            startActivity(intent)
        }

        // “关于”卡片的点击逻辑
        view.findViewById<MaterialCardView>(R.id.card_about).setOnClickListener {
            showAboutDialog()
        }

        return view
    }

    /**
     * 显示“关于”对话框的方法
     */
    private fun showAboutDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val versionTextView = dialogView.findViewById<TextView>(R.id.text_version)
        val githubButton = dialogView.findViewById<Button>(R.id.button_github)

        // 动态获取版本名
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionTextView.text = getString(R.string.about_version_label, pInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            versionTextView.visibility = View.GONE
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
}