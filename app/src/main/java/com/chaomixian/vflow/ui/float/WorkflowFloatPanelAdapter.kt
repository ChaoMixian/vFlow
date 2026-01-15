// 文件：WorkflowFloatPanelAdapter.kt
// 描述：工作流悬浮面板的 RecyclerView 适配器

package com.chaomixian.vflow.ui.float

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 工作流悬浮面板适配器
 *
 * @param workflows 工作流列表
 * @param onExecute 执行工作流回调
 * @param onStop 停止工作流回调
 */
class WorkflowFloatPanelAdapter(
    private var workflows: List<Workflow>,
    private val onExecute: (Workflow) -> Unit,
    private val onStop: (Workflow) -> Unit
) : RecyclerView.Adapter<WorkflowFloatPanelAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val workflowIcon: ImageView = view.findViewById(R.id.workflow_icon)
        val workflowName: TextView = view.findViewById(R.id.workflow_name)
        val workflowStatus: TextView = view.findViewById(R.id.workflow_status)
        val btnExecute: ImageView = view.findViewById(R.id.btn_execute)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workflow_float_panel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workflow = workflows[position]

        holder.workflowName.text = workflow.name

        // 更新执行状态
        updateExecutionStatus(holder, workflow)

        // 设置执行按钮点击事件
        holder.btnExecute.setOnClickListener {
            if (WorkflowExecutor.isRunning(workflow.id)) {
                onStop(workflow)
            } else {
                // 检查权限
                val missingPermissions = PermissionManager.getMissingPermissions(
                    holder.itemView.context,
                    workflow
                )

                if (missingPermissions.isEmpty()) {
                    onExecute(workflow)
                } else {
                    // 缺少权限，不执行
                    android.widget.Toast.makeText(
                        holder.itemView.context,
                        "缺少必要权限，请在主界面授予权限后再执行",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun getItemCount(): Int = workflows.size

    /**
     * 更新执行状态显示
     */
    private fun updateExecutionStatus(holder: ViewHolder, workflow: Workflow) {
        val isRunning = WorkflowExecutor.isRunning(workflow.id)

        // 检查缺少的权限
        val missingPermissions = PermissionManager.getMissingPermissions(
            holder.itemView.context,
            workflow
        )

        when {
            isRunning -> {
                // 正在运行
                holder.workflowStatus.text = "运行中..."
                holder.workflowStatus.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.category_system)
                )
                holder.btnExecute.setImageResource(R.drawable.rounded_stop_circle_24)
            }
            missingPermissions.isNotEmpty() -> {
                // 缺少权限
                holder.workflowStatus.text = "需要权限"
                holder.workflowStatus.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
                holder.btnExecute.setImageResource(R.drawable.rounded_play_arrow_24)
            }
            else -> {
                // 准备就绪
                holder.workflowStatus.text = "准备就绪"
                holder.workflowStatus.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.category_trigger)
                )
                holder.btnExecute.setImageResource(R.drawable.rounded_play_arrow_24)
            }
        }
    }
}
