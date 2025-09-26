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
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShizukuManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * “设置” Fragment。
 * 提供应用相关的设置选项，如动态颜色、隐藏连接线和权限管理入口。
 */
class SettingsFragment : Fragment() {
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
            prefs.edit().putBoolean("dynamicColorEnabled", isChecked).apply()
            requireActivity().recreate() // 重新创建Activity以应用主题更改
        }

        // 进度通知开关逻辑
        val progressNotificationSwitch = view.findViewById<MaterialSwitch>(R.id.switch_progress_notification)
        progressNotificationSwitch.isChecked = prefs.getBoolean("progressNotificationEnabled", true) // 默认开启
        progressNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("progressNotificationEnabled", isChecked).apply()
        }

        // 强制保活开关逻辑
        val forceKeepAliveSwitch = view.findViewById<MaterialSwitch>(R.id.switch_force_keep_alive)
        if (ShizukuManager.isShizukuActive(requireContext())) {
            forceKeepAliveSwitch.isEnabled = true
            forceKeepAliveSwitch.isChecked = prefs.getBoolean("forceKeepAliveEnabled", false)
            forceKeepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("forceKeepAliveEnabled", isChecked).apply()
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
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ChaoMixian/vFlow"))
            startActivity(browserIntent)
            dialog.dismiss() // 点击后关闭对话框
        }

        dialog.show()
    }
}