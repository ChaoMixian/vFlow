// 文件：WorkflowListFragment.kt
// 描述：显示工作流列表，并提供创建、编辑、删除、导入/导出和执行工作流的功能。支持文件夹功能。

package com.chaomixian.vflow.ui.workflow_list

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.ShortcutHelper
import com.chaomixian.vflow.ui.float.WorkflowsFloatPanelService
import com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class WorkflowListFragment : Fragment() {
    private lateinit var workflowManager: WorkflowManager
    private lateinit var folderManager: FolderManager
    private lateinit var importHelper: WorkflowImportHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkflowListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var pendingWorkflow: Workflow? = null
    private var pendingExportFolderId: String? = null
    private val gson = Gson()

    // 排序状态：false = 默认排序，true = 按名称排序
    private var isSortedByName = false

    // 延迟执行处理器
    private val delayedExecuteHandler = Handler(Looper.getMainLooper())

    // 导入冲突处理相关状态
    private val importQueue: Queue<Workflow> = LinkedList()
    private enum class ConflictChoice { ASK, REPLACE_ALL, KEEP_ALL }
    private var conflictChoice = ConflictChoice.ASK

    // ActivityResultLauncher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { executeWorkflow(it) }
        }
        pendingWorkflow = null
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (checkOverlayPermission()) {
            showFavoriteWorkflowsFloat()
        } else {
            Toast.makeText(requireContext(), getString(R.string.toast_overlay_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    // 单个工作流导出
    private val exportSingleLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportWorkflow?.let { workflow ->
                try {
                    val exportData = createWorkflowExportData(workflow)
                    val jsonString = gson.toJson(exportData)
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                    Toast.makeText(requireContext(), getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingExportWorkflow = null
    }

    /**
     * 创建带元数据的工作流导出数据
     */
    private fun createWorkflowExportData(workflow: Workflow): Map<String, Any?> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val updatedAt = dateFormat.format(Date(workflow.modifiedAt))

        val meta = mapOf(
            "id" to workflow.id,
            "name" to workflow.name,
            "version" to workflow.version,
            "vFlowLevel" to workflow.vFlowLevel,
            "description" to workflow.description,
            "author" to workflow.author,
            "homepage" to workflow.homepage,
            "tags" to workflow.tags,
            "updated_at" to updatedAt,
            "modified_at" to workflow.modifiedAt
        )

        val workflowMap = mapOf(
            "id" to workflow.id,
            "name" to workflow.name,
            "steps" to workflow.steps,
            "isEnabled" to workflow.isEnabled,
            "triggerConfig" to workflow.triggerConfig,
            "isFavorite" to workflow.isFavorite,
            "wasEnabledBeforePermissionsLost" to workflow.wasEnabledBeforePermissionsLost,
            "folderId" to workflow.folderId,
            "order" to workflow.order,
            "shortcutName" to workflow.shortcutName,
            "shortcutIconRes" to workflow.shortcutIconRes,
            "modifiedAt" to workflow.modifiedAt,
            "version" to workflow.version,
            "vFlowLevel" to workflow.vFlowLevel,
            "description" to workflow.description,
            "author" to workflow.author,
            "homepage" to workflow.homepage,
            "tags" to workflow.tags
        )

        return mapOf("_meta" to meta) + workflowMap
    }
    private var pendingExportWorkflow: Workflow? = null

    // 文件夹导出
    private val exportFolderLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            pendingExportFolderId?.let { folderId ->
                try {
                    val folder = folderManager.getFolder(folderId)
                    val workflows = workflowManager.getAllWorkflows().filter { it.folderId == folderId }
                    if (folder != null) {
                        // 导出为包含文件夹信息的格式，每个工作流带 _meta
                        val workflowsWithMeta = workflows.map { createWorkflowExportData(it) }
                        val exportData = mapOf(
                            "folder" to folder,
                            "workflows" to workflowsWithMeta
                        )
                        val jsonString = gson.toJson(exportData)
                        requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                        Toast.makeText(requireContext(), getString(R.string.toast_folder_export_success), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingExportFolderId = null
    }

    // 备份所有工作流
    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            try {
                // 导出所有工作流（包括文件夹信息），每个工作流带 _meta
                val allWorkflows = workflowManager.getAllWorkflows()
                val allFolders = folderManager.getAllFolders()
                val workflowsWithMeta = allWorkflows.map { createWorkflowExportData(it) }
                val backupData = mapOf(
                    "workflows" to workflowsWithMeta,
                    "folders" to allFolders
                )
                val jsonString = gson.toJson(backupData)
                requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                Toast.makeText(requireContext(), getString(R.string.toast_backup_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_backup_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导入
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                val jsonString = requireContext().contentResolver.openInputStream(fileUri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: throw Exception(getString(R.string.error_cannot_read_file))

                // 使用统一的导入工具类
                importHelper.importFromJson(jsonString)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Fragment 创建时调用，启用选项菜单。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    /** 创建并返回 Fragment 的视图。 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workflows, container, false)
        workflowManager = WorkflowManager(requireContext())
        folderManager = FolderManager(requireContext())
        importHelper = WorkflowImportHelper(
            requireContext(),
            workflowManager,
            folderManager
        ) { loadData() }
        recyclerView = view.findViewById(R.id.recycler_view_workflows)
        setupRecyclerView()
        setupDragAndDrop()
        view.findViewById<FloatingActionButton>(R.id.fab_add_workflow).setOnClickListener {
            startActivity(Intent(requireContext(), WorkflowEditorActivity::class.java))
        }

        // 订阅执行状态的 Flow
        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                val items = adapter.getItems()
                val index = items.indexOfFirst { item ->
                    when (item) {
                        is WorkflowListItem.WorkflowItem -> {
                            when (state) {
                                is ExecutionState.Running -> item.workflow.id == state.workflowId
                                is ExecutionState.Finished -> item.workflow.id == state.workflowId
                                is ExecutionState.Cancelled -> item.workflow.id == state.workflowId
                                is ExecutionState.Failure -> item.workflow.id == state.workflowId
                            }
                        }
                        else -> false
                    }
                }
                if (index != -1) {
                    adapter.notifyItemChanged(index)
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        checkAndReEnableWorkflows()
        loadData()
    }

    // 选项菜单处理
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.workflow_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sort_by_name -> {
                isSortedByName = !isSortedByName
                loadData()
                true
            }
            R.id.menu_create_folder -> {
                showCreateFolderDialog()
                true
            }
            R.id.menu_favorite_float -> {
                handleFavoriteFloatClick()
                true
            }
            R.id.menu_backup_workflows -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                backupLauncher.launch("vflow_backup_${timestamp}.json")
                true
            }
            R.id.menu_import_workflows -> {
                importLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleFavoriteFloatClick() {
        if (checkOverlayPermission()) {
            showFavoriteWorkflowsFloat()
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_overlay_permission_title))
                .setMessage(getString(R.string.dialog_overlay_permission_message))
                .setPositiveButton(getString(R.string.dialog_button_go_to_settings)) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showFavoriteWorkflowsFloat() {
        val intent = Intent(requireContext(), WorkflowsFloatPanelService::class.java).apply {
            action = WorkflowsFloatPanelService.ACTION_SHOW
        }
        requireContext().startService(intent)
    }

    private fun checkAndReEnableWorkflows() {
        val allWorkflows = workflowManager.getAllWorkflows()
        allWorkflows.forEach { workflow ->
            if (workflow.wasEnabledBeforePermissionsLost && !workflow.isEnabled) {
                val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
                if (missingPermissions.isEmpty()) {
                    DebugLogger.i("WorkflowListFragment", "工作流 '${workflow.name}' 的权限已授予，正在自动重新启用。")
                    val updatedWorkflow = workflow.copy(isEnabled = true, wasEnabledBeforePermissionsLost = false)
                    workflowManager.saveWorkflow(updatedWorkflow)
                }
            }
        }
    }

    private fun loadData() {
        val workflows = workflowManager.getAllWorkflows()
        val folders = folderManager.getAllFolders()

        // 构建混合列表：文件夹在前，工作流在后（不在文件夹中的）
        val items = mutableListOf<WorkflowListItem>()

        // 根据排序状态决定排序方式
        val sortedFolders = if (isSortedByName) {
            folders.sortedBy { it.name }
        } else {
            folders
        }

        // 添加文件夹（带有工作流数量）
        sortedFolders.forEach { folder ->
            val count = workflows.count { it.folderId == folder.id }
            items.add(WorkflowListItem.FolderItem(folder, count))
        }

        // 过滤出不在文件夹中的工作流
        val rootWorkflows = workflows.filter { it.folderId == null }

        // 根据排序状态决定排序方式
        val sortedWorkflows = if (isSortedByName) {
            rootWorkflows.sortedBy { it.name }
        } else {
            rootWorkflows
        }

        sortedWorkflows.forEach { workflow ->
            items.add(WorkflowListItem.WorkflowItem(workflow))
        }

        if (::adapter.isInitialized) {
            adapter.updateData(items)
        } else {
            adapter = WorkflowListAdapter(
                items.toMutableList(),
                workflowManager,
                onEditWorkflow = { workflow ->
                    val intent = Intent(requireContext(), WorkflowEditorActivity::class.java).apply {
                        putExtra(WorkflowEditorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                    }
                    startActivity(intent)
                },
                onDeleteWorkflow = { workflow -> showDeleteWorkflowConfirmationDialog(workflow) },
                onDuplicateWorkflow = { workflow ->
                    workflowManager.duplicateWorkflow(workflow.id)
                    Toast.makeText(requireContext(), getString(R.string.toast_copied_as, workflow.name), Toast.LENGTH_SHORT).show()
                    loadData()
                },
                onExportWorkflow = { workflow ->
                    pendingExportWorkflow = workflow
                    exportSingleLauncher.launch("${workflow.name}.json")
                },
                onExecuteWorkflow = { workflow ->
                    if (WorkflowExecutor.isRunning(workflow.id)) {
                        WorkflowExecutor.stopExecution(workflow.id)
                    } else {
                        executeWorkflow(workflow)
                    }
                },
                onExecuteWorkflowDelayed = { workflow, delayMs ->
                    scheduleDelayedExecution(workflow, delayMs)
                },
                onAddShortcut = { workflow ->
                    ShortcutHelper.requestPinnedShortcut(requireContext(), workflow)
                },
                onFolderClick = { folderId ->
                    val folder = folderManager.getFolder(folderId)
                    folder?.let {
                        // 打开文件夹内容弹窗
                        val dialog = FolderContentDialogFragment.newInstance(folderId, folder.name)
                        dialog.setOnWorkflowChangedListener {
                            loadData()
                        }
                        dialog.show(childFragmentManager, FolderContentDialogFragment.TAG)
                    }
                },
                onFolderRename = { folderId -> showRenameFolderDialog(folderId) },
                onFolderDelete = { folderId -> showDeleteFolderConfirmationDialog(folderId) },
                onFolderExport = { folderId ->
                    pendingExportFolderId = folderId
                    val folder = folderManager.getFolder(folderId)
                    exportFolderLauncher.launch("${folder?.name ?: "folder"}.json")
                },
                itemTouchHelper = itemTouchHelper,
                onMoveToFolder = null // 暂不实现拖拽移动
            )
            recyclerView.adapter = adapter
        }
        ShortcutHelper.updateShortcuts(requireContext())
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            private var pendingDropFolderId: String? = null

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                val items = adapter.getItems()

                // 如果拖动的是文件夹，禁止移动
                if (fromPosition < items.size && items[fromPosition] is WorkflowListItem.FolderItem) {
                    return false
                }

                // 如果目标是文件夹，记录下来（不实际移动列表项）
                if (toPosition < items.size && items[toPosition] is WorkflowListItem.FolderItem) {
                    pendingDropFolderId = (items[toPosition] as WorkflowListItem.FolderItem).folder.id
                    // 返回 false 取消移动，但允许在 clearView 时处理放置
                    return false
                }

                // 普通工作流之间的移动
                pendingDropFolderId = null
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不处理滑动删除
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                // 检查是否放置到了文件夹上
                pendingDropFolderId?.let { folderId ->
                    val fromPosition = viewHolder.adapterPosition
                    if (fromPosition >= 0 && fromPosition < adapter.getItems().size) {
                        val item = adapter.getItems()[fromPosition]
                        if (item is WorkflowListItem.WorkflowItem) {
                            handleDropToFolder(item.workflow, folderId)
                        }
                    }
                }

                pendingDropFolderId = null
                adapter.saveOrder()
                ShortcutHelper.updateShortcuts(requireContext())
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    /**
     * 处理将工作流放置到文件夹（松手时执行）
     */
    private fun handleDropToFolder(workflow: Workflow, folderId: String) {
        val folder = folderManager.getFolder(folderId) ?: return

        // 更新工作流的 folderId
        val updatedWorkflow = workflow.copy(folderId = folderId)
        workflowManager.saveWorkflow(updatedWorkflow)

        Toast.makeText(
            requireContext(),
            "已将 \"${workflow.name}\" 移动到 \"${folder.name}\"",
            Toast.LENGTH_SHORT
        ).show()

        loadData()
    }

    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
        if (missingPermissions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_starting_workflow, workflow.name), Toast.LENGTH_SHORT).show()
            WorkflowExecutor.execute(workflow, requireContext())
        } else {
            pendingWorkflow = workflow
            val intent = Intent(requireContext(), PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(missingPermissions))
                putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
            }
            permissionLauncher.launch(intent)
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

    private fun showCreateFolderDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_create)
            .setView(editText)
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val folder = WorkflowFolder(name = name)
                    folderManager.saveFolder(folder)
                    Toast.makeText(requireContext(), getString(R.string.toast_folder_created), Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(requireContext(), "文件夹名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showRenameFolderDialog(folderId: String) {
        val folder = folderManager.getFolder(folderId) ?: return
        val editText = EditText(requireContext()).apply {
            setText(folder.name)
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_rename)
            .setView(editText)
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    folderManager.saveFolder(folder.copy(name = name))
                    Toast.makeText(requireContext(), getString(R.string.toast_folder_renamed), Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showDeleteFolderConfirmationDialog(folderId: String) {
        val folder = folderManager.getFolder(folderId) ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_folder_delete_title)
            .setMessage(getString(R.string.dialog_folder_delete_message, folder.name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                // 将文件夹内的工作流移出
                val workflows = workflowManager.getAllWorkflows()
                workflows.filter { it.folderId == folderId }.forEach { workflow ->
                    workflowManager.saveWorkflow(workflow.copy(folderId = null))
                }
                // 删除文件夹
                folderManager.deleteFolder(folderId)
                Toast.makeText(requireContext(), getString(R.string.toast_folder_deleted), Toast.LENGTH_SHORT).show()
                loadData()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showDeleteWorkflowConfirmationDialog(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                loadData()
            }
            .show()
    }

    private fun startImportProcess(workflows: List<Workflow>) {
        importQueue.clear()
        importQueue.addAll(workflows)
        conflictChoice = ConflictChoice.ASK
        processNextInImportQueue()
    }

    private fun processNextInImportQueue() {
        if (importQueue.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_import_completed), Toast.LENGTH_SHORT).show()
            loadData()
            return
        }

        val workflowToImport = importQueue.poll() ?: return
        val existingWorkflow = workflowManager.getWorkflow(workflowToImport.id)

        if (existingWorkflow == null) {
            workflowManager.saveWorkflow(workflowToImport)
            processNextInImportQueue()
        } else {
            when (conflictChoice) {
                ConflictChoice.REPLACE_ALL -> { handleReplace(workflowToImport); processNextInImportQueue() }
                ConflictChoice.KEEP_ALL -> { handleKeepBoth(workflowToImport); processNextInImportQueue() }
                ConflictChoice.ASK -> showConflictDialog(workflowToImport, existingWorkflow)
            }
        }
    }

    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_import_conflict, null)
        val rememberChoiceCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_remember_choice)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_import_conflict_title))
            .setMessage(getString(R.string.dialog_import_conflict_message, existing.name, existing.id.substring(0, 8), toImport.name))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_keep_both)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.KEEP_ALL
                handleKeepBoth(toImport)
                processNextInImportQueue()
            }
            .setNegativeButton(getString(R.string.dialog_button_replace)) { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.REPLACE_ALL
                handleReplace(toImport)
                processNextInImportQueue()
            }
            .setNeutralButton(getString(R.string.dialog_button_skip)) { _, _ -> processNextInImportQueue() }
            .setCancelable(false)
            .show()
    }

    private fun handleReplace(workflow: Workflow) {
        workflowManager.saveWorkflow(workflow)
        Toast.makeText(requireContext(), getString(R.string.toast_workflow_replaced, workflow.name), Toast.LENGTH_SHORT).show()
    }

    private fun handleKeepBoth(workflow: Workflow) {
        // 确保元数据字段有默认值，避免 copy 时 NPE
        val workflowWithDefaults = workflow.copy(
            version = workflow.version ?: "1.0.0",
            vFlowLevel = if (workflow.vFlowLevel == 0) 1 else workflow.vFlowLevel,
            description = workflow.description ?: "",
            author = workflow.author ?: "",
            homepage = workflow.homepage ?: "",
            tags = workflow.tags ?: emptyList(),
            modifiedAt = if (workflow.modifiedAt == 0L) System.currentTimeMillis() else workflow.modifiedAt
        )
        val newWorkflow = workflowWithDefaults.copy(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.toast_workflow_imported_name, workflow.name)
        )
        workflowManager.saveWorkflow(newWorkflow)
        Toast.makeText(requireContext(), getString(R.string.toast_workflow_imported_as_copy, workflow.name), Toast.LENGTH_SHORT).show()
    }
}
