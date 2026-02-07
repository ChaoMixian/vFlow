package com.chaomixian.vflow.ui.workflow_list

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import com.google.android.material.color.MaterialColors
import androidx.core.view.isNotEmpty

/**
 * 支持工作流和文件夹混合列表的 RecyclerView.Adapter。
 */
class WorkflowListAdapter(
    private var items: MutableList<WorkflowListItem>,
    private val workflowManager: WorkflowManager,
    private val folderId: String? = null, // 如果指定了 folderId，则只显示该文件夹内的工作流
    private val onEditWorkflow: (Workflow) -> Unit,
    private val onDeleteWorkflow: (Workflow) -> Unit,
    private val onDuplicateWorkflow: (Workflow) -> Unit,
    private val onExportWorkflow: (Workflow) -> Unit,
    private val onExecuteWorkflow: (Workflow) -> Unit,
    private val onExecuteWorkflowDelayed: (Workflow, Long) -> Unit, // 延迟执行回调，delayMs 为延迟毫秒数
    private val onAddShortcut: (Workflow) -> Unit,
    private val onFolderClick: (String) -> Unit, // 文件夹点击事件
    private val onFolderRename: (String) -> Unit, // 文件夹重命名事件
    private val onFolderDelete: (String) -> Unit, // 文件夹删除事件
    private val onFolderExport: (String) -> Unit, // 文件夹导出事件
    private val itemTouchHelper: ItemTouchHelper?,
    private val onMoveToFolder: ((Workflow, String?) -> Unit)? = null // 拖拽移动到文件夹的回调
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_WORKFLOW = 0
        private const val VIEW_TYPE_FOLDER = 1
    }

    fun getItems(): List<WorkflowListItem> = items.toList()

    fun updateData(newItems: List<WorkflowListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun saveOrder() {
        // 只保存工作流的顺序
        val workflows = items.filterIsInstance<WorkflowListItem.WorkflowItem>().map { it.workflow }
        workflowManager.saveAllWorkflows(workflows)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WorkflowListItem.WorkflowItem -> VIEW_TYPE_WORKFLOW
            is WorkflowListItem.FolderItem -> VIEW_TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_FOLDER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
                FolderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow, parent, false)
                WorkflowViewHolder(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WorkflowListItem.WorkflowItem -> (holder as WorkflowViewHolder).bind(item.workflow, itemTouchHelper, onMoveToFolder)
            is WorkflowListItem.FolderItem -> (holder as FolderViewHolder).bind(item.folder, item.workflowCount, onFolderClick, onFolderRename, onFolderDelete, onFolderExport)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = items.size

    /**
     * 工作流 ViewHolder
     */
    inner class WorkflowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
        val infoChipGroup: ChipGroup = itemView.findViewById(R.id.chip_group_info)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val executeButton: FloatingActionButton = itemView.findViewById(R.id.button_execute_workflow)
        val clickableWrapper: ConstraintLayout = itemView.findViewById(R.id.clickable_wrapper)
        val enabledSwitch: MaterialSwitch = itemView.findViewById(R.id.switch_workflow_enabled)
        val favoriteButton: ImageButton = itemView.findViewById(R.id.button_favorite)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            workflow: Workflow,
            itemTouchHelper: ItemTouchHelper?,
            onMoveToFolder: ((Workflow, String?) -> Unit)?
        ) {
            val isManualTrigger = workflow.steps.firstOrNull()?.moduleId == ManualTriggerModule().id
            val missingPermissions = PermissionManager.getMissingPermissions(itemView.context, workflow)

            name.text = workflow.name
            infoChipGroup.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)

            // 如果权限缺失，显示提示Chip
            if (missingPermissions.isNotEmpty()) {
                val permissionChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
                permissionChip.text = "缺少权限"
                permissionChip.setChipIconResource(R.drawable.ic_shield)
                permissionChip.chipBackgroundColor = ColorStateList.valueOf(
                    MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorErrorContainer, 0)
                )
                val onColor = MaterialColors.getColor(itemView.context, com.google.android.material.R.attr.colorOnErrorContainer, 0)
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

            val requiredPermissions = workflow.steps
                .mapNotNull { step ->
                    ModuleRegistry.getModule(step.moduleId)?.getRequiredPermissions(step)
                }
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

            infoChipGroup.isVisible = infoChipGroup.isNotEmpty()

            // 点击编辑
            clickableWrapper.setOnClickListener { onEditWorkflow(workflow) }

            // 长按启动拖拽（如果指定了移动到文件夹的回调）
            if (onMoveToFolder != null) {
                clickableWrapper.setOnLongClickListener {
                    itemTouchHelper?.startDrag(this)
                    true
                }
            }

            // 收藏按钮
            favoriteButton.setImageResource(
                if (workflow.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            )
            favoriteButton.setOnClickListener {
                val updatedWorkflow = workflow.copy(isFavorite = !workflow.isFavorite)
                workflowManager.saveWorkflow(updatedWorkflow)
                val index = items.indexOfFirst {
                    it is WorkflowListItem.WorkflowItem && it.workflow.id == workflow.id
                }
                if (index != -1) {
                    items[index] = WorkflowListItem.WorkflowItem(updatedWorkflow)
                    notifyItemChanged(index)
                }
            }

            // 更多选项
            moreOptionsButton.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.workflow_item_menu, popup.menu)

                val addShortcutItem = popup.menu.findItem(R.id.menu_add_shortcut)
                addShortcutItem.isVisible = isManualTrigger

                // 在主列表中，工作流不在文件夹里，不需要显示"移出文件夹"
                popup.menu.findItem(R.id.menu_move_out_folder).isVisible = false

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_add_shortcut -> { onAddShortcut(workflow); true }
                        R.id.menu_delete -> { onDeleteWorkflow(workflow); true }
                        R.id.menu_duplicate -> { onDuplicateWorkflow(workflow); true }
                        R.id.menu_export_single -> { onExportWorkflow(workflow); true }
                        else -> false
                    }
                }
                popup.show()
            }

            executeButton.isVisible = isManualTrigger
            enabledSwitch.isVisible = !isManualTrigger

            if (isManualTrigger) {
                executeButton.setImageResource(
                    if (WorkflowExecutor.isRunning(workflow.id)) R.drawable.rounded_pause_24 else R.drawable.ic_play_arrow
                )
                executeButton.setOnClickListener { onExecuteWorkflow(workflow) }

                // 长按执行按钮显示延迟执行菜单
                executeButton.setOnLongClickListener { view ->
                    showDelayedExecuteMenu(view, workflow)
                    true
                }
            } else {
                enabledSwitch.setOnCheckedChangeListener(null)
                enabledSwitch.isChecked = workflow.isEnabled
                enabledSwitch.isEnabled = missingPermissions.isEmpty()

                enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                    val updatedWorkflow = workflow.copy(
                        isEnabled = isChecked,
                        wasEnabledBeforePermissionsLost = false
                    )
                    workflowManager.saveWorkflow(updatedWorkflow)
                    val index = items.indexOfFirst {
                        it is WorkflowListItem.WorkflowItem && it.workflow.id == workflow.id
                    }
                    if (index != -1) {
                        items[index] = WorkflowListItem.WorkflowItem(updatedWorkflow)
                    }
                }
            }
        }

        /**
         * 显示延迟执行菜单
         */
        private fun showDelayedExecuteMenu(anchorView: View, workflow: Workflow) {
            val popup = PopupMenu(anchorView.context, anchorView)
            popup.menuInflater.inflate(R.menu.workflow_execute_delayed_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                val delayMs = when (menuItem.itemId) {
                    R.id.menu_execute_in_5s -> 5_000L      // 5秒
                    R.id.menu_execute_in_15s -> 15_000L     // 15秒
                    R.id.menu_execute_in_1min -> 60_000L    // 1分钟
                    else -> return@setOnMenuItemClickListener false
                }
                onExecuteWorkflowDelayed(workflow, delayMs)
                true
            }
            popup.show()
        }
    }

    /**
     * 文件夹 ViewHolder
     */
    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_view_folder_name)
        val workflowCount: TextView = itemView.findViewById(R.id.text_view_workflow_count)
        val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
        val clickableWrapper: ConstraintLayout = itemView.findViewById(R.id.clickable_wrapper)
        val iconFolder: ImageView = itemView.findViewById(R.id.icon_folder)

        fun bind(
            folder: com.chaomixian.vflow.core.workflow.model.WorkflowFolder,
            count: Int,
            onFolderClick: (String) -> Unit,
            onFolderRename: (String) -> Unit,
            onFolderDelete: (String) -> Unit,
            onFolderExport: (String) -> Unit
        ) {
            name.text = folder.name
            workflowCount.text = "$count 个工作流"

            clickableWrapper.setOnClickListener { onFolderClick(folder.id) }

            moreOptionsButton.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.folder_item_menu, popup.menu)

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_rename -> { onFolderRename(folder.id); true }
                        R.id.menu_delete -> { onFolderDelete(folder.id); true }
                        R.id.menu_export -> { onFolderExport(folder.id); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}
