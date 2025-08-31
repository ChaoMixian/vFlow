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

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireActivity().getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)

        // ... (动态颜色和隐藏连接线开关的代码不变) ...
        val dynamicColorSwitch = view.findViewById<MaterialSwitch>(R.id.switch_dynamic_color)
        dynamicColorSwitch.isChecked = prefs.getBoolean("dynamicColorEnabled", false)
        dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dynamicColorEnabled", isChecked).apply()
            requireActivity().recreate()
        }

        val hideConnectionsSwitch = view.findViewById<MaterialSwitch>(R.id.switch_hide_connections)
        hideConnectionsSwitch.isChecked = prefs.getBoolean("hideConnections", false)
        hideConnectionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hideConnections", isChecked).apply()
        }


        // --- 新增：权限管理器入口 ---
        view.findViewById<MaterialCardView>(R.id.card_permission_manager).setOnClickListener {
            val allPermissions = PermissionManager.getAllRegisteredPermissions()
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(allPermissions))
            }
            startActivity(intent)
        }

        return view
    }
}