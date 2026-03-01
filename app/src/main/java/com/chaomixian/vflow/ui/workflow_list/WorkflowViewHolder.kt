package com.chaomixian.vflow.ui.workflow_list

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
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
import com.google.android.material.color.MaterialColors
import androidx.core.view.isNotEmpty

/**
 * 工作流列表项的 ViewHolder，可在主列表和文件夹弹窗中复用
 */
class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
    val infoChipGroup: ChipGroup = itemView.findViewById(R.id.chip_group_info)
    val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
    val executeButton: FloatingActionButton = itemView.findViewById(R.id.button_execute_workflow)
    val clickableWrapper: ConstraintLayout = itemView.findViewById(R.id.clickable_wrapper)
    val enabledSwitch: MaterialSwitch = itemView.findViewById(R.id.switch_workflow_enabled)
    val favoriteButton: ImageButton = itemView.findViewById(R.id.button_favorite)

    /**
     * 绑定工作流数据到视图
     * @param workflow 要显示的工作流
     * @param workflowManager 工作流管理器
     * @param workflowListRef 工作流列表的引用，用于更新列表项（支持 MutableList 或 MutableList<WorkflowListItem>）
     * @param callbacks 回调接口集合
     */
    @SuppressLint("ClickableViewAccessibility")
    fun bind(
        workflow: Workflow,
        workflowManager: WorkflowManager,
        workflowListRef: Any, // MutableList<Workflow> 或 MutableList<WorkflowListItem>
        callbacks: WorkflowCallbacks
    ) {
        val isManualTrigger = workflow.steps.firstOrNull()?.moduleId == ManualTriggerModule().id
        val missingPermissions = PermissionManager.getMissingPermissions(itemView.context, workflow)

        // 基本信息
        name.text = workflow.name
        infoChipGroup.removeAllViews()
        val inflater = LayoutInflater.from(itemView.context)

        // 显示权限缺失提示
        if (missingPermissions.isNotEmpty()) {
            val permissionChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
            permissionChip.text = "缺少权限"
            permissionChip.setChipIconResource(R.drawable.rounded_security_24)
            permissionChip.chipBackgroundColor = ColorStateList.valueOf(
                MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorErrorContainer, 0)
            )
            val onColor = MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnErrorContainer, 0)
            permissionChip.chipIconTint = ColorStateList.valueOf(onColor)
            permissionChip.setTextColor(onColor)
            infoChipGroup.addView(permissionChip)
        }

        // 显示步骤数
        val stepCount = workflow.steps.size - 1
        if (stepCount >= 0) {
            val stepChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
            stepChip.text = "${stepCount.coerceAtLeast(0)} 个步骤"
            stepChip.setChipIconResource(R.drawable.rounded_dashboard_fill_24)
            infoChipGroup.addView(stepChip)
        }

        // 显示所需权限
        val requiredPermissions = workflow.steps
            .mapNotNull { step ->
                ModuleRegistry.getModule(step.moduleId)?.getRequiredPermissions(step)
            }
            .flatten()
            .distinct()

        for (permission in requiredPermissions) {
            val permissionChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
            permissionChip.text = permission.name
            permissionChip.setChipIconResource(R.drawable.rounded_security_24)
            infoChipGroup.addView(permissionChip)
        }

        infoChipGroup.isVisible = infoChipGroup.isNotEmpty()

        // 收藏按钮
        favoriteButton.setImageResource(
            if (workflow.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
        )
        favoriteButton.setOnClickListener {
            val updatedWorkflow = workflow.copy(isFavorite = !workflow.isFavorite)
            workflowManager.saveWorkflow(updatedWorkflow)
            updateWorkflowInList(workflowListRef, updatedWorkflow, callbacks.notifyItemChanged)
        }

        // 更多选项菜单
        moreOptionsButton.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.workflow_item_menu, popup.menu)

            // 根据上下文显示不同的菜单项
            popup.menu.findItem(R.id.menu_add_shortcut).isVisible = isManualTrigger && callbacks.showAddToTile
            popup.menu.findItem(R.id.menu_add_to_tile).isVisible = isManualTrigger && callbacks.showAddToTile
            popup.menu.findItem(R.id.menu_move_out_folder).isVisible = callbacks.showMoveOutFolder

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_add_shortcut -> {
                        if (callbacks.showAddToTile) {
                            callbacks.onAddShortcut?.invoke(workflow)
                        } else {
                            com.chaomixian.vflow.ui.common.ShortcutHelper.requestPinnedShortcut(view.context, workflow)
                        }
                        true
                    }
                    R.id.menu_copy_id -> {
                        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Workflow ID", workflow.id)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(view.context, "工作流ID已复制", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_duplicate -> {
                        workflowManager.duplicateWorkflow(workflow.id)
                        Toast.makeText(view.context, "工作流已复制", Toast.LENGTH_SHORT).show()
                        callbacks.onDuplicate?.invoke(workflow)
                        true
                    }
                    R.id.menu_move_out_folder -> {
                        callbacks.onMoveOutFolder?.invoke(workflow)
                        true
                    }
                    R.id.menu_export_single -> {
                        callbacks.onExport?.invoke(workflow)
                        true
                    }
                    R.id.menu_add_to_tile -> {
                        callbacks.onAddToTile?.invoke(workflow)
                        true
                    }
                    R.id.menu_delete -> {
                        callbacks.onDelete?.invoke(workflow)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // 执行按钮 vs 开关
        executeButton.isVisible = isManualTrigger
        enabledSwitch.isVisible = !isManualTrigger

        if (isManualTrigger) {
            executeButton.setImageResource(
                if (WorkflowExecutor.isRunning(workflow.id)) R.drawable.rounded_pause_24 else R.drawable.ic_play_arrow
            )
            executeButton.setOnClickListener { callbacks.onExecute?.invoke(workflow) }

            // 长按显示延迟执行菜单
            executeButton.setOnLongClickListener { view ->
                showDelayedExecuteMenu(view, workflow, callbacks.onExecuteDelayed)
                true
            }
        } else {
            // 开关状态
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = workflow.isEnabled
            enabledSwitch.isEnabled = missingPermissions.isEmpty()

            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                val updatedWorkflow = workflow.copy(
                    isEnabled = isChecked,
                    wasEnabledBeforePermissionsLost = false
                )
                workflowManager.saveWorkflow(updatedWorkflow)
                updateWorkflowInList(workflowListRef, updatedWorkflow)
            }
        }
    }

    /**
     * 更新列表中的工作流项
     */
    @Suppress("UNCHECKED_CAST")
    private fun updateWorkflowInList(
        list: Any,
        updatedWorkflow: Workflow,
        onChange: ((Int) -> Unit)? = null
    ) {
        try {
            when {
                // 尝试作为 MutableList<Workflow> 处理
                list is MutableList<*> && list.firstOrNull() is Workflow -> {
                    val workflowList = list as MutableList<Workflow>
                    val index = workflowList.indexOfFirst { it.id == updatedWorkflow.id }
                    if (index != -1) {
                        workflowList[index] = updatedWorkflow
                        onChange?.invoke(index)
                    }
                }
                // 尝试作为 MutableList<WorkflowListItem> 处理
                list is MutableList<*> && list.firstOrNull() is WorkflowListItem -> {
                    val itemList = list as MutableList<WorkflowListItem>
                    val index = itemList.indexOfFirst {
                        it is WorkflowListItem.WorkflowItem && it.workflow.id == updatedWorkflow.id
                    }
                    if (index != -1) {
                        itemList[index] = WorkflowListItem.WorkflowItem(updatedWorkflow)
                        onChange?.invoke(index)
                    }
                }
            }
        } catch (e: Exception) {
            // 类型转换失败，忽略
        }
    }

    /**
     * 显示延迟执行菜单
     */
    private fun showDelayedExecuteMenu(
        anchorView: View,
        workflow: Workflow,
        onExecuteDelayed: ((Workflow, Long) -> Unit)?
    ) {
        val popup = PopupMenu(anchorView.context, anchorView)
        popup.menuInflater.inflate(R.menu.workflow_execute_delayed_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            val delayMs = when (menuItem.itemId) {
                R.id.menu_execute_in_5s -> 5_000L
                R.id.menu_execute_in_15s -> 15_000L
                R.id.menu_execute_in_1min -> 60_000L
                else -> return@setOnMenuItemClickListener false
            }
            onExecuteDelayed?.invoke(workflow, delayMs)
            true
        }
        popup.show()
    }

    /**
     * 工作流项回调接口
     */
    data class WorkflowCallbacks(
        val showAddToTile: Boolean = true,         // 是否显示"添加到快捷方式"和"添加到控制中心"
        val showMoveOutFolder: Boolean = false,   // 是否显示"移出文件夹"
        val onAddShortcut: ((Workflow) -> Unit)? = null,
        val onAddToTile: ((Workflow) -> Unit)? = null,
        val onDuplicate: ((Workflow) -> Unit)? = null,
        val onMoveOutFolder: ((Workflow) -> Unit)? = null,
        val onExport: ((Workflow) -> Unit)? = null,
        val onDelete: ((Workflow) -> Unit)? = null,
        val onExecute: ((Workflow) -> Unit)? = null,
        val onExecuteDelayed: ((Workflow, Long) -> Unit)? = null,
        val notifyItemChanged: ((Int) -> Unit)? = null
    )
}
