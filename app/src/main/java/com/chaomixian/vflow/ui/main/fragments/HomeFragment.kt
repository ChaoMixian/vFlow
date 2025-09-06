package com.chaomixian.vflow.ui.main.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    private lateinit var quickExecuteCard: MaterialCardView
    private lateinit var quickExecuteContainer: LinearLayout

    private val quickExecuteViews = mutableMapOf<String, View>() // 存储快速执行按钮视图，Key为workflowId

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
        quickExecuteCard = view.findViewById(R.id.card_quick_execute)
        quickExecuteContainer = view.findViewById(R.id.container_quick_execute)

        // 订阅执行状态的 Flow，用于实时更新UI
        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                updateQuickExecuteButtonsState()
            }
        }

        return view
    }

    /** Fragment 可见时刷新数据 */
    override fun onResume() {
        super.onResume()
        updateStatistics()
        updateServiceStatus()
        updateQuickExecuteCard()
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

    /** 更新快速执行卡片 */
    private fun updateQuickExecuteCard() {
        val favoritedManualWorkflows = workflowManager.getAllWorkflows()
            .filter { it.isFavorite && it.steps.firstOrNull()?.moduleId == ManualTriggerModule().id }

        quickExecuteContainer.removeAllViews()
        quickExecuteViews.clear()

        if (favoritedManualWorkflows.isEmpty()) {
            quickExecuteCard.isVisible = false
        } else {
            quickExecuteCard.isVisible = true
            val inflater = LayoutInflater.from(context)
            favoritedManualWorkflows.forEach { workflow ->
                val itemView = inflater.inflate(R.layout.item_quick_execute, quickExecuteContainer, false)
                itemView.findViewById<TextView>(R.id.quick_execute_name).text = workflow.name

                itemView.setOnClickListener {
                    handleQuickExecuteClick(workflow)
                }

                quickExecuteContainer.addView(itemView)
                quickExecuteViews[workflow.id] = itemView
            }
            updateQuickExecuteButtonsState()
        }
    }

    /** 处理快速执行按钮的点击事件 */
    private fun handleQuickExecuteClick(workflow: Workflow) {
        if (WorkflowExecutor.isRunning(workflow.id)) {
            WorkflowExecutor.stopExecution(workflow.id)
            Toast.makeText(context, "已停止: ${workflow.name}", Toast.LENGTH_SHORT).show()
        } else {
            // 注意：这里的快速执行暂不检查权限，为简化逻辑
            WorkflowExecutor.execute(workflow, requireContext())
            Toast.makeText(context, "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 根据工作流运行状态更新所有快速执行按钮的图标 */
    private fun updateQuickExecuteButtonsState() {
        quickExecuteViews.forEach { (workflowId, view) ->
            val iconView = view.findViewById<ImageView>(R.id.quick_execute_icon)
            if (WorkflowExecutor.isRunning(workflowId)) {
                iconView.setImageResource(R.drawable.rounded_pause_24)
            } else {
                iconView.setImageResource(R.drawable.ic_play_arrow)
            }
        }
    }
}