package com.chaomixian.vflow.ui.main.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

// 文件：HomeFragment.kt
// 描述：主界面中的“首页”屏幕。

/**
 * “首页” Fragment。
 * 显示统计信息和应用状态。
 */
class HomeFragment : Fragment() {

    private lateinit var workflowManager: WorkflowManager
    private lateinit var totalWorkflowsText: TextView
    private lateinit var autoWorkflowsText: TextView
    private lateinit var serviceStatusTitle: TextView
    private lateinit var serviceStatusDesc: TextView
    private lateinit var serviceStatusCard: MaterialCardView

    /** 创建并返回 Fragment 的视图。 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 fragment_home 布局
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        workflowManager = WorkflowManager(requireContext())

        // 初始化视图
        totalWorkflowsText = view.findViewById(R.id.text_total_workflows)
        autoWorkflowsText = view.findViewById(R.id.text_auto_workflows)
        serviceStatusTitle = view.findViewById(R.id.text_service_status_title)
        serviceStatusDesc = view.findViewById(R.id.text_service_status_desc)
        serviceStatusCard = view.findViewById(R.id.card_service_status)

        return view
    }

    /** Fragment 可见时刷新数据 */
    override fun onResume() {
        super.onResume()
        updateStatistics()
        updateServiceStatus()
    }

    /** 更新统计信息 */
    private fun updateStatistics() {
        val allWorkflows = workflowManager.getAllWorkflows()
        val totalCount = allWorkflows.size

        val autoWorkflows = allWorkflows.filter {
            it.steps.firstOrNull()?.moduleId != ManualTriggerModule().id
        }
        val autoCount = autoWorkflows.size
        val enabledAutoCount = autoWorkflows.count { it.isEnabled }

        totalWorkflowsText.text = totalCount.toString()
        autoWorkflowsText.text = "$enabledAutoCount / $autoCount"
    }

    /** 更新服务状态卡片 */
    private fun updateServiceStatus() {
        val isServiceEnabled = PermissionManager.isAccessibilityServiceEnabledInSettings(requireContext())
        if (isServiceEnabled) {
            serviceStatusDesc.text = "运行中"
            serviceStatusDesc.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0))
            serviceStatusCard.setOnClickListener(null)
            serviceStatusCard.isClickable = false
        } else {
            serviceStatusDesc.text = "已停止，点击开启"
            serviceStatusDesc.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, 0))
            serviceStatusCard.isClickable = true
            serviceStatusCard.setOnClickListener {
                // 跳转到无障碍设置页面
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
    }
}