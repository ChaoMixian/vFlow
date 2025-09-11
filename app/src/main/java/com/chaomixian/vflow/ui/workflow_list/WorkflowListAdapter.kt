// 文件: ui/workflow_list/WorkflowListAdapter.kt

package com.chaomixian.vflow.ui.workflow_list

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Collections
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

/**
 * 工作流列表的 RecyclerView.Adapter。
 * @param workflows 初始的工作流数据列表。
 * @param onEdit 点击编辑按钮或列表项时的回调。
 * @param onDelete 点击删除菜单项时的回调。
 * @param onDuplicate 点击复制菜单项时的回调。
 * @param onExport 点击导出菜单项时的回调。
 * @param onExecute 点击执行按钮时的回调。
 * @param itemTouchHelper 用于启动拖拽的 ItemTouchHelper 实例。
 */
class WorkflowListAdapter(
    private var workflows: MutableList<Workflow>,
    private val workflowManager: WorkflowManager,
    private val onEdit: (Workflow) -> Unit,
    private val onDelete: (Workflow) -> Unit,
    private val onDuplicate: (Workflow) -> Unit,
    private val onExport: (Workflow) -> Unit,
    private val onExecute: (Workflow) -> Unit,
    private val itemTouchHelper: ItemTouchHelper
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

    // 用于处理拖动排序
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(workflows, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(workflows, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    // 保存当前列表顺序
    fun saveOrder() {
        workflowManager.saveAllWorkflows(workflows)
    }


    /** 创建新的 ViewHolder 实例。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkflowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow, parent, false)
        return WorkflowViewHolder(view)
    }

    /** 将数据绑定到指定位置的 ViewHolder。 */
    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: WorkflowViewHolder, position: Int) {
        val workflow = workflows[position]
        val isManualTrigger = workflow.steps.firstOrNull()?.moduleId == ManualTriggerModule().id
        val missingPermissions = if (isManualTrigger) emptyList() else PermissionManager.getMissingPermissions(holder.itemView.context, workflow)

        holder.bind(workflow, missingPermissions.isNotEmpty())

        holder.clickableWrapper.setOnClickListener { onEdit(workflow) }

        // 长按卡片以启动拖动
        holder.clickableWrapper.setOnLongClickListener {
            itemTouchHelper.startDrag(holder)
            true
        }

        // 收藏按钮逻辑
        holder.favoriteButton.setImageResource(
            if (workflow.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
        )
        holder.favoriteButton.setOnClickListener {
            val updatedWorkflow = workflow.copy(isFavorite = !workflow.isFavorite)
            workflowManager.saveWorkflow(updatedWorkflow)
            workflows[position] = updatedWorkflow
            notifyItemChanged(position)
        }


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

        holder.executeButton.isVisible = isManualTrigger
        holder.enabledSwitch.isVisible = !isManualTrigger

        if (isManualTrigger) {
            // 更新执行按钮的状态
            if (WorkflowExecutor.isRunning(workflow.id)) {
                holder.executeButton.setImageResource(R.drawable.rounded_pause_24)
            } else {
                holder.executeButton.setImageResource(R.drawable.ic_play_arrow)
            }
            // onExecute 回调现在会处理启动或停止
            holder.executeButton.setOnClickListener { onExecute(workflow) }
        } else {
            holder.enabledSwitch.setOnCheckedChangeListener(null)
            holder.enabledSwitch.isChecked = workflow.isEnabled
            // 如果权限缺失，则禁用开关
            holder.enabledSwitch.isEnabled = missingPermissions.isEmpty()

            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                val updatedWorkflow = workflow.copy(
                    isEnabled = isChecked,
                    // 用户手动操作开关时，应重置“因权限丢失而禁用”的标记
                    wasEnabledBeforePermissionsLost = false
                )
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
        val infoChipGroup: ChipGroup = itemView.findViewById(R.id.chip_group_info)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val executeButton: FloatingActionButton = itemView.findViewById(R.id.button_execute_workflow)
        val clickableWrapper: ConstraintLayout = itemView.findViewById(R.id.clickable_wrapper)
        val enabledSwitch: MaterialSwitch = itemView.findViewById(R.id.switch_workflow_enabled)
        val favoriteButton: ImageButton = itemView.findViewById(R.id.button_favorite)

        fun bind(workflow: Workflow, hasMissingPermissions: Boolean) {
            val context = itemView.context
            name.text = workflow.name
            infoChipGroup.removeAllViews()
            val inflater = LayoutInflater.from(context)

            // 如果权限缺失，显示一个醒目的提示Chip
            if (hasMissingPermissions) {
                val permissionChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
                permissionChip.text = "缺少权限"
                permissionChip.setChipIconResource(R.drawable.ic_shield)
                // 设置为警告色 (使用主题属性)
                permissionChip.chipBackgroundColor = ColorStateList.valueOf(
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorErrorContainer, 0)
                )
                val onColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnErrorContainer, 0)
                permissionChip.chipIconTint = ColorStateList.valueOf(onColor)
                permissionChip.setTextColor(onColor)
                infoChipGroup.addView(permissionChip)
            }


            val stepCount = workflow.steps.size - 1
            if (stepCount >= 0) {
                val stepChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
                stepChip.text = "${stepCount.coerceAtLeast(0)} 个步骤"
                stepChip.setChipIconResource(R.drawable.ic_workflows)
                infoChipGroup.addView(stepChip)
            }

            // 2. 添加权限Chips
            val requiredPermissions = workflow.steps
                .mapNotNull { ModuleRegistry.getModule(it.moduleId)?.requiredPermissions }
                .flatten()
                .distinct()

            if (requiredPermissions.isNotEmpty()) {
                for (permission in requiredPermissions) {
                    val permissionChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
                    permissionChip.text = permission.name
                    permissionChip.setChipIconResource(R.drawable.ic_shield)
                    infoChipGroup.addView(permissionChip)
                }
            }

            // 如果没有任何Chip，则隐藏ChipGroup
            infoChipGroup.isVisible = infoChipGroup.childCount > 0
        }
    }
}