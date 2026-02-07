package com.chaomixian.vflow.ui.workflow_list

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.permissions.PermissionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.view.isNotEmpty
import com.google.gson.Gson

/**
 * 显示文件夹内工作流的底部弹窗
 */
class FolderContentDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "FolderContentDialog"
        private const val ARG_FOLDER_ID = "folder_id"
        private const val ARG_FOLDER_NAME = "folder_name"

        fun newInstance(folderId: String, folderName: String): FolderContentDialogFragment {
            return FolderContentDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FOLDER_ID, folderId)
                    putString(ARG_FOLDER_NAME, folderName)
                }
            }
        }
    }

    private lateinit var workflowManager: WorkflowManager
    private lateinit var adapter: FolderWorkflowAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var folderId: String = ""
    private var folderName: String = ""
    private var onWorkflowChanged: (() -> Unit)? = null
    private var pendingExportWorkflow: Workflow? = null
    private val gson = Gson()

    // 延迟执行处理器
    private val delayedExecuteHandler = Handler(Looper.getMainLooper())

    // 导出单个工作流
    private val exportSingleLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportWorkflow?.let { workflow ->
                try {
                    val jsonString = gson.toJson(workflow)
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                    Toast.makeText(requireContext(), getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingExportWorkflow = null
    }

    fun setOnWorkflowChangedListener(callback: () -> Unit) {
        onWorkflowChanged = callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderId = it.getString(ARG_FOLDER_ID, "")
            folderName = it.getString(ARG_FOLDER_NAME, "")
        }
        workflowManager = WorkflowManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_folder_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_folder_workflows)
        val titleText = view.findViewById<TextView>(R.id.text_folder_title)
        val emptyText = view.findViewById<TextView>(R.id.text_empty)
        val backButton = view.findViewById<ImageButton>(R.id.button_back)

        titleText.text = folderName

        backButton.setOnClickListener {
            dismiss()
        }

        // 加载文件夹内的工作流（按 order 排序）
        val allWorkflows = workflowManager.getAllWorkflows()
        val workflows = allWorkflows
            .filter { it.folderId == folderId }
            .sortedBy { it.order }
            .toMutableList()

        if (workflows.isEmpty()) {
            emptyText.isVisible = true
            recyclerView.isVisible = false
        } else {
            emptyText.isVisible = false
            recyclerView.isVisible = true

            adapter = FolderWorkflowAdapter(
                workflows,
                workflowManager,
                onEdit = { workflow ->
                    val intent = android.content.Intent(requireContext(), com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity::class.java).apply {
                        putExtra(com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                    }
                    startActivity(intent)
                    dismiss()
                },
                onDelete = { workflow -> showDeleteConfirmation(workflow) },
                onMoveOutFolder = { workflow -> moveOutFolder(workflow) },
                onExecute = { workflow ->
                    if (WorkflowExecutor.isRunning(workflow.id)) {
                        WorkflowExecutor.stopExecution(workflow.id)
                    } else {
                        executeWorkflow(workflow)
                    }
                },
                onExecuteDelayed = { workflow, delayMs ->
                    scheduleDelayedExecution(workflow, delayMs)
                }
            )
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
            setupDragAndDrop(recyclerView)
        }
    }

    private fun setupDragAndDrop(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不处理滑动删除
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 保存排序
                saveOrder()
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // 将 itemTouchHelper 传给 adapter
        adapter.setItemTouchHelper(itemTouchHelper)
    }

    private fun saveOrder() {
        val workflows = adapter.getWorkflows().mapIndexed { index, workflow ->
            workflow.copy(order = index)
        }
        workflows.forEach { workflowManager.saveWorkflow(it) }
    }

    private fun showDeleteConfirmation(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                onWorkflowChanged?.invoke()
                loadData() // 只刷新列表，不关闭文件夹
            }
            .show()
    }

    private fun moveOutFolder(workflow: Workflow) {
        val updatedWorkflow = workflow.copy(folderId = null)
        workflowManager.saveWorkflow(updatedWorkflow)
        Toast.makeText(
            requireContext(),
            "已将 \"${workflow.name}\" 移出文件夹",
            Toast.LENGTH_SHORT
        ).show()
        onWorkflowChanged?.invoke()
        loadData()
    }

    private fun loadData() {
        val allWorkflows = workflowManager.getAllWorkflows()
        val workflows = allWorkflows
            .filter { it.folderId == folderId }
            .sortedBy { it.order }
        adapter.updateData(workflows)
    }

    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
        if (missingPermissions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_starting_workflow, workflow.name), Toast.LENGTH_SHORT).show()
            com.chaomixian.vflow.core.execution.WorkflowExecutor.execute(workflow, requireContext())
        } else {
            com.google.android.material.snackbar.Snackbar.make(
                requireView(),
                "缺少权限，无法执行",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 安排延迟执行工作流
     */
    private fun scheduleDelayedExecution(workflow: Workflow, delayMs: Long) {
        val delayText = when (delayMs) {
            5_000L -> getString(R.string.workflow_execute_delay_5s)
            15_000L -> getString(R.string.workflow_execute_delay_15s)
            60_000L -> getString(R.string.workflow_execute_delay_1min)
            else -> "${delayMs / 1000} 秒"
        }
        Toast.makeText(
            requireContext(),
            getString(R.string.workflow_execute_delayed, delayText, workflow.name),
            Toast.LENGTH_SHORT
        ).show()

        delayedExecuteHandler.postDelayed({
            executeWorkflow(workflow)
        }, delayMs)
    }

    /**
     * 文件夹内工作流的 Adapter
     */
    @SuppressLint("ClickableViewAccessibility")
    inner class FolderWorkflowAdapter(
        private var workflows: MutableList<Workflow>,
        private val workflowManager: WorkflowManager,
        private val onEdit: (Workflow) -> Unit,
        private val onDelete: (Workflow) -> Unit,
        private val onMoveOutFolder: (Workflow) -> Unit,
        private val onExecute: (Workflow) -> Unit,
        private val onExecuteDelayed: (Workflow, Long) -> Unit // 延迟执行回调
    ) : RecyclerView.Adapter<FolderWorkflowAdapter.ViewHolder>() {

        private var itemTouchHelper: ItemTouchHelper? = null

        fun setItemTouchHelper(helper: ItemTouchHelper) {
            this.itemTouchHelper = helper
        }

        fun getWorkflows(): List<Workflow> = workflows.toList()

        fun updateData(newWorkflows: List<Workflow>) {
            workflows.clear()
            workflows.addAll(newWorkflows)
            notifyDataSetChanged()
        }

        fun moveItem(fromPosition: Int, toPosition: Int) {
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    java.util.Collections.swap(workflows, i, i + 1)
                }
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    java.util.Collections.swap(workflows, i, i - 1)
                }
            }
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workflow = workflows[position]
            holder.bind(workflow)

            holder.clickableWrapper.setOnClickListener { onEdit(workflow) }

            // 长按拖拽排序
            holder.clickableWrapper.setOnLongClickListener {
                itemTouchHelper?.startDrag(holder)
                true
            }
        }

        override fun getItemCount() = workflows.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.text_view_workflow_name)
            val infoChipGroup: ChipGroup = itemView.findViewById(R.id.chip_group_info)
            val moreOptionsButton: ImageButton = itemView.findViewById(R.id.button_more_options)
            val executeButton: FloatingActionButton = itemView.findViewById(R.id.button_execute_workflow)
            val clickableWrapper: ConstraintLayout = itemView.findViewById(R.id.clickable_wrapper)
            val enabledSwitch: MaterialSwitch = itemView.findViewById(R.id.switch_workflow_enabled)
            val favoriteButton: ImageButton = itemView.findViewById(R.id.button_favorite)

            fun bind(workflow: Workflow) {
                val isManualTrigger = workflow.steps.firstOrNull()?.moduleId == ManualTriggerModule().id

                name.text = workflow.name
                infoChipGroup.removeAllViews()
                val inflater = LayoutInflater.from(itemView.context)

                val stepCount = workflow.steps.size - 1
                if (stepCount >= 0) {
                    val stepChip = inflater.inflate(R.layout.chip_permission, infoChipGroup, false) as Chip
                    stepChip.text = "${stepCount.coerceAtLeast(0)} 个步骤"
                    stepChip.setChipIconResource(R.drawable.ic_workflows)
                    infoChipGroup.addView(stepChip)
                }

                infoChipGroup.isVisible = infoChipGroup.isNotEmpty()

                // 收藏按钮
                favoriteButton.setImageResource(
                    if (workflow.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                )
                favoriteButton.setOnClickListener {
                    val updatedWorkflow = workflow.copy(isFavorite = !workflow.isFavorite)
                    workflowManager.saveWorkflow(updatedWorkflow)
                    val index = workflows.indexOfFirst { it.id == workflow.id }
                    if (index != -1) {
                        workflows[index] = updatedWorkflow
                        notifyItemChanged(index)
                    }
                }

                // 更多选项
                moreOptionsButton.setOnClickListener { view ->
                    val popup = android.widget.PopupMenu(view.context, view)
                    popup.menuInflater.inflate(R.menu.workflow_item_menu, popup.menu)
                    popup.menu.findItem(R.id.menu_add_shortcut).isVisible = isManualTrigger
                    popup.menu.findItem(R.id.menu_move_out_folder).isVisible = true

                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.menu_add_shortcut -> {
                                com.chaomixian.vflow.ui.common.ShortcutHelper.requestPinnedShortcut(view.context, workflow)
                                true
                            }
                            R.id.menu_duplicate -> {
                                workflowManager.duplicateWorkflow(workflow.id)
                                Toast.makeText(view.context, getString(R.string.toast_copied_as, workflow.name), Toast.LENGTH_SHORT).show()
                                loadData() // 刷新列表
                                true
                            }
                            R.id.menu_move_out_folder -> { onMoveOutFolder(workflow); true }
                            R.id.menu_export_single -> {
                                pendingExportWorkflow = workflow
                                exportSingleLauncher.launch("${workflow.name}.json")
                                true
                            }
                            R.id.menu_delete -> { onDelete(workflow); true }
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
                    executeButton.setOnClickListener { onExecute(workflow) }

                    // 长按执行按钮显示延迟执行菜单
                    executeButton.setOnLongClickListener { view ->
                        showDelayedExecuteMenu(view, workflow)
                        true
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
                    onExecuteDelayed(workflow, delayMs)
                    true
                }
                popup.show()
            }
        }
    }
}
