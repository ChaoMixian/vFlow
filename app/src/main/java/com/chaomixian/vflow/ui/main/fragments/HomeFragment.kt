// 文件: main/java/com/chaomixian/vflow/ui/main/fragments/HomeFragment.kt
package com.chaomixian.vflow.ui.main.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider
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
    private var pendingWorkflow: Workflow? = null // [新增] 用于权限请求后待执行的工作流

    // [新增] 用于接收权限请求页面返回结果的 Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 如果权限授予成功，执行之前被挂起的工作流
            pendingWorkflow?.let { executeWorkflow(it, checkPermissions = false) }
        }
        // 清理待处理的工作流
        pendingWorkflow = null
    }


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

    /**
     * 更新快速执行卡片，以列表形式添加项目和分隔线
     */
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
            favoritedManualWorkflows.forEachIndexed { index, workflow ->
                // 1. 添加列表项
                val itemView = inflater.inflate(R.layout.item_quick_execute, quickExecuteContainer, false)
                itemView.findViewById<TextView>(R.id.quick_execute_name).text = workflow.name

                itemView.setOnClickListener {
                    handleQuickExecuteClick(workflow)
                }

                quickExecuteContainer.addView(itemView)
                quickExecuteViews[workflow.id] = itemView

                // 2. 如果不是最后一项，则添加分隔线
                if (index < favoritedManualWorkflows.size - 1) {
                    val divider = MaterialDivider(requireContext())
                    // 为分隔线同时设置左右边距
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val marginStart = (64 * resources.displayMetrics.density).toInt() // 左边距，与图标和文本的间距对齐
                    val marginEnd = (20 * resources.displayMetrics.density).toInt()   // 右边距
                    lp.setMargins(marginStart, 0, marginEnd, 0)
                    divider.layoutParams = lp
                    quickExecuteContainer.addView(divider)
                }
            }
            updateQuickExecuteButtonsState()
        }
    }


    /**
     * [修改] 封装统一的执行逻辑，增加权限检查
     */
    private fun executeWorkflow(workflow: Workflow, checkPermissions: Boolean = true) {
        if (checkPermissions) {
            val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
            if (missingPermissions.isNotEmpty()) {
                // 如果有缺失的权限，则暂存工作流并启动权限请求页面
                pendingWorkflow = workflow
                val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                    putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(missingPermissions))
                    putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
                }
                permissionLauncher.launch(intent)
                return // 终止当前执行流程，等待权限回调
            }
        }

        // 如果没有缺失的权限，或明确跳过检查，则直接执行
        Toast.makeText(context, "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
        WorkflowExecutor.execute(workflow, requireContext())
    }


    /** 处理快速执行按钮的点击事件 */
    private fun handleQuickExecuteClick(workflow: Workflow) {
        if (WorkflowExecutor.isRunning(workflow.id)) {
            WorkflowExecutor.stopExecution(workflow.id)
            Toast.makeText(context, "已停止: ${workflow.name}", Toast.LENGTH_SHORT).show()
        } else {
            // [修改] 调用封装好的执行方法，该方法会处理权限检查
            executeWorkflow(workflow)
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