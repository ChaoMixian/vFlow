// 文件：WorkflowListAdapter.kt
// 描述：用于在 RecyclerView 中显示工作流列表的适配器。

package com.chaomixian.vflow.ui.workflow_list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule

/**
 * 工作流列表的 RecyclerView.Adapter。
 * @param workflows 初始的工作流数据列表。
 * @param onEdit 点击编辑按钮或列表项时的回调。
 * @param onDelete 点击删除菜单项时的回调。
 * @param onDuplicate 点击复制菜单项时的回调。
 * @param onExport 点击导出菜单项时的回调。
 * @param onExecute 点击执行按钮时的回调。
 */
class WorkflowListAdapter(
    private var workflows: MutableList<Workflow>, // 改为 MutableList
    private val workflowManager: WorkflowManager, // 增加 WorkflowManager 依赖
    private val onEdit: (Workflow) -> Unit,
    private val onDelete: (Workflow) -> Unit,
    private val onDuplicate: (Workflow) -> Unit,
    private val onExport: (Workflow) -> Unit,
    private val onExecute: (Workflow) -> Unit
) : RecyclerView.Adapter<WorkflowListAdapter.WorkflowViewHolder>() {

    /** 获取当前工作流列表的副本。 */
    fun getWorkflows(): List<Workflow> {
        return workflows.toList()
    }

    fun updateData(newWorkflows: List<Workflow>) {
        workflows.clear()
        workflows.addAll(newWorkflows)
        notifyDataSetChanged()
    }

    /** 创建新的 ViewHolder 实例。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow, parent, false)
        return WorkflowViewHolder(view)
    }

    /** 将数据绑定到指定位置的 ViewHolder。 */
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        val workflow = workflows[position]
        holder.bind(workflow)

        // 整个卡片点击时触发编辑回调
        holder.clickableWrapper.setOnClickListener { onEdit(workflow) }

        // “更多选项”按钮的点击逻辑
        holder.moreOptionsButton.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.workflow_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_delete -> { onDelete(workflow); true }
                    R.id.menu_duplicate -> { onDuplicate(workflow); true }
                    R.id.menu_export_single -> { onExport(workflow); true }
                    else -> false
                }
            }
            popup.show()
        }

        // --- UI切换逻辑 ---
        val isManualTrigger = workflow.steps.firstOrNull()?.moduleId == ManualTriggerModule().id
        holder.executeButton.isVisible = isManualTrigger
        holder.enabledSwitch.isVisible = !isManualTrigger

        if(isManualTrigger) {
            // 更新执行按钮的状态
            if (WorkflowExecutor.isRunning(workflow.id)) {
                holder.executeButton.setImageResource(R.drawable.rounded_pause_24) // 设为停止图标
            } else {
                holder.executeButton.setImageResource(R.drawable.ic_play_arrow) // 设为播放图标
            }
            // onExecute 回调现在会处理启动或停止
            holder.executeButton.setOnClickListener { onExecute(workflow) }
        } else {
            // 设置开关状态并处理变化
            holder.enabledSwitch.setOnCheckedChangeListener(null) // 先移除监听器防止重复触发
            holder.enabledSwitch.isChecked = workflow.isEnabled
            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                val updatedWorkflow = workflow.copy(isEnabled = isChecked)
                workflowManager.saveWorkflow(updatedWorkflow)
                // 更新列表中的数据，以便UI保持同步
                workflows[position] = updatedWorkflow
            }
        }
    }

    /** 返回数据项的总数。 */
    override fun getItemCount() = workflows.size

    /**
     * ViewHolder 类，用于缓存工作流列表项的视图引用。
     */
    class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
        val description: TextView = itemView.findViewById(R.id.text_view_workflow_description)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val executeButton: ImageButton = itemView.findViewById(R.id.button_execute_workflow)
        val clickableWrapper: RelativeLayout = itemView.findViewById(R.id.clickable_wrapper) // 卡片整体的可点击区域
        val enabledSwitch: SwitchCompat = itemView.findViewById(R.id.switch_workflow_enabled) // 新增开关引用

        /** 将工作流数据显示到视图上。 */
        fun bind(workflow: Workflow) {
            name.text = workflow.name
            val stepCount = workflow.steps.size - 1 // 减去触发器步骤
            description.text = "包含 ${stepCount.coerceAtLeast(0)} 个步骤"
        }
    }
}