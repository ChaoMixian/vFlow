// 文件：SettingsFragment.kt
// 描述：主界面中的“设置”屏幕。
package com.chaomixian.vflow.ui.main.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.card.MaterialCardView
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

        // 隐藏连接线开关逻辑
        val hideConnectionsSwitch = view.findViewById<MaterialSwitch>(R.id.switch_hide_connections)
        hideConnectionsSwitch.isChecked = prefs.getBoolean("hideConnections", false)
        hideConnectionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hideConnections", isChecked).apply()
        }

        // 进度通知开关逻辑
        val progressNotificationSwitch = view.findViewById<MaterialSwitch>(R.id.switch_progress_notification)
        progressNotificationSwitch.isChecked = prefs.getBoolean("progressNotificationEnabled", true) // 默认开启
        progressNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("progressNotificationEnabled", isChecked).apply()
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

        return view
    }
}