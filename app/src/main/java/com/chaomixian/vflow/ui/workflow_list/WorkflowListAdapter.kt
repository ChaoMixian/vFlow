package com.chaomixian.vflow.ui.workflow_list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow

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
            is WorkflowListItem.WorkflowItem -> {
                val workflowHolder = holder as WorkflowViewHolder
                workflowHolder.clickableWrapper.setOnClickListener { onEditWorkflow(item.workflow) }

                // 长按启动拖拽（如果指定了移动到文件夹的回调）
                if (onMoveToFolder != null) {
                    workflowHolder.clickableWrapper.setOnLongClickListener {
                        itemTouchHelper?.startDrag(holder)
                        true
                    }
                }

                workflowHolder.bind(
                    workflow = item.workflow,
                    workflowManager = workflowManager,
                    workflowListRef = items,
                    callbacks = WorkflowViewHolder.WorkflowCallbacks(
                        showAddToTile = true,
                        showMoveOutFolder = false,
                        onAddShortcut = onAddShortcut,
                        onAddToTile = { workflow ->
                            val dialog = TileSelectionDialog.newInstance(workflow.id, workflow.name)
                            dialog.show((holder.itemView.context as androidx.fragment.app.FragmentActivity).supportFragmentManager, TileSelectionDialog.TAG)
                        },
                        onDuplicate = onDuplicateWorkflow,
                        onExport = onExportWorkflow,
                        onDelete = onDeleteWorkflow,
                        onExecute = onExecuteWorkflow,
                        onExecuteDelayed = onExecuteWorkflowDelayed,
                        notifyItemChanged = { index -> this@WorkflowListAdapter.notifyItemChanged(index) }
                    )
                )
            }
            is WorkflowListItem.FolderItem -> {
                val folderHolder = holder as FolderViewHolder
                folderHolder.bind(item.folder, item.workflowCount, onFolderClick, onFolderRename, onFolderDelete, onFolderExport)
            }
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
