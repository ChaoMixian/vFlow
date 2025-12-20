// 文件: main/java/com/chaomixian/vflow/ui/main/fragments/HomeFragment.kt
package com.chaomixian.vflow.ui.main.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.logging.LogStatus
import com.chaomixian.vflow.core.module.ModuleRegistry
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

class HomeFragment : Fragment() {

    private lateinit var workflowManager: WorkflowManager
    private lateinit var totalWorkflowsText: TextView
    private lateinit var autoWorkflowsText: TextView

    // 权限健康检查卡片视图
    private lateinit var permissionHealthTitle: TextView
    private lateinit var permissionHealthDesc: TextView
    private lateinit var permissionHealthCard: MaterialCardView

    // 快速执行卡片视图
    private lateinit var quickExecuteCard: MaterialCardView
    private lateinit var quickExecuteContainer: LinearLayout
    private val quickExecuteViews = mutableMapOf<String, View>()
    private var pendingWorkflow: Workflow? = null

    // 最近日志卡片视图
    private lateinit var recentLogsCard: MaterialCardView
    private lateinit var recentLogsContainer: LinearLayout

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { executeWorkflow(it, checkPermissions = false) }
        }
        pendingWorkflow = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        workflowManager = WorkflowManager(requireContext())

        // 初始化视图
        totalWorkflowsText = view.findViewById(R.id.text_total_workflows)
        autoWorkflowsText = view.findViewById(R.id.text_auto_workflows)
        permissionHealthTitle = view.findViewById(R.id.text_permission_health_title)
        permissionHealthDesc = view.findViewById(R.id.text_permission_health_desc)
        permissionHealthCard = view.findViewById(R.id.card_permission_health)
        quickExecuteCard = view.findViewById(R.id.card_quick_execute)
        quickExecuteContainer = view.findViewById(R.id.container_quick_execute)
        recentLogsCard = view.findViewById(R.id.card_recent_logs)
        recentLogsContainer = view.findViewById(R.id.container_recent_logs)

        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                updateQuickExecuteButtonsState()
                updateRecentLogs() // 当有新日志时也刷新
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        updateStatistics()
        updatePermissionHealthCheck()
        updateQuickExecuteCard()
        updateRecentLogs()
    }

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

    /**
     * 实现权限健康检查的逻辑
     */
    private fun updatePermissionHealthCheck() {
        val allWorkflows = workflowManager.getAllWorkflows()
        val requiredPermissions = allWorkflows
            .flatMap { it.steps }
            .mapNotNull { step ->
                ModuleRegistry.getModule(step.moduleId)?.getRequiredPermissions(step) }
            .flatten()
            .distinct()

        val missingPermissions = requiredPermissions.filter { !PermissionManager.isGranted(requireContext(), it) }

        if (missingPermissions.isEmpty()) {
            permissionHealthDesc.text = "状态：良好，所有权限均已授予"
            permissionHealthDesc.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0))
        } else {
            permissionHealthDesc.text = "状态：存在 ${missingPermissions.size} 个缺失的权限"
            permissionHealthDesc.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, 0))
        }

        permissionHealthCard.setOnClickListener {
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(
                    PermissionActivity.EXTRA_PERMISSIONS,
                    ArrayList(PermissionManager.getAllRegisteredPermissions())
                )
            }
            startActivity(intent)
        }
    }

    /**
     * 更新最近日志卡片的逻辑
     */
    private fun updateRecentLogs() {
        val logs = LogManager.getRecentLogs(5) // 获取最近5条日志
        recentLogsContainer.removeAllViews()

        if (logs.isEmpty()) {
            recentLogsCard.isVisible = false
            return
        }

        recentLogsCard.isVisible = true
        val inflater = LayoutInflater.from(context)

        logs.forEachIndexed { index, log ->
            val itemView = inflater.inflate(R.layout.item_log_entry, recentLogsContainer, false)
            val iconView = itemView.findViewById<ImageView>(R.id.log_status_icon)
            val nameView = itemView.findViewById<TextView>(R.id.log_workflow_name)
            val messageView = itemView.findViewById<TextView>(R.id.log_message)
            val timestampView = itemView.findViewById<TextView>(R.id.log_timestamp)

            nameView.text = log.workflowName
            messageView.text = log.message
            timestampView.text = DateUtils.getRelativeTimeSpanString(
                log.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            when (log.status) {
                LogStatus.SUCCESS -> {
                    iconView.setImageResource(R.drawable.ic_log_success)
                    iconView.setColorFilter(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0))
                }
                LogStatus.FAILURE, LogStatus.CANCELLED -> {
                    iconView.setImageResource(R.drawable.ic_log_failure)
                    iconView.setColorFilter(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, 0))
                }
            }
            recentLogsContainer.addView(itemView)

            if (index < logs.size - 1) {
                val divider = MaterialDivider(requireContext())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val marginStart = (64 * resources.displayMetrics.density).toInt()
                val marginEnd = (20 * resources.displayMetrics.density).toInt()
                lp.setMargins(marginStart, 0, marginEnd, 0)
                divider.layoutParams = lp
                recentLogsContainer.addView(divider)
            }
        }
    }

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
                val itemView = inflater.inflate(R.layout.item_quick_execute, quickExecuteContainer, false)
                itemView.findViewById<TextView>(R.id.quick_execute_name).text = workflow.name
                itemView.setOnClickListener { handleQuickExecuteClick(workflow) }
                quickExecuteContainer.addView(itemView)
                quickExecuteViews[workflow.id] = itemView

                if (index < favoritedManualWorkflows.size - 1) {
                    val divider = MaterialDivider(requireContext())
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val marginStart = (64 * resources.displayMetrics.density).toInt()
                    val marginEnd = (20 * resources.displayMetrics.density).toInt()
                    lp.setMargins(marginStart, 0, marginEnd, 0)
                    divider.layoutParams = lp
                    quickExecuteContainer.addView(divider)
                }
            }
            updateQuickExecuteButtonsState()
        }
    }

    private fun executeWorkflow(workflow: Workflow, checkPermissions: Boolean = true) {
        if (checkPermissions) {
            val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
            if (missingPermissions.isNotEmpty()) {
                pendingWorkflow = workflow
                val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                    putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(missingPermissions))
                    putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
                }
                permissionLauncher.launch(intent)
                return
            }
        }
        Toast.makeText(context, "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
        WorkflowExecutor.execute(workflow, requireContext())
    }

    private fun handleQuickExecuteClick(workflow: Workflow) {
        if (WorkflowExecutor.isRunning(workflow.id)) {
            WorkflowExecutor.stopExecution(workflow.id)
            Toast.makeText(context, "已停止: ${workflow.name}", Toast.LENGTH_SHORT).show()
        } else {
            executeWorkflow(workflow)
        }
    }

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