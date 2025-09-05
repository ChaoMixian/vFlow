// 文件：WorkflowListFragment.kt
// 描述：显示工作流列表，并提供创建、编辑、删除、导入/导出和执行工作流的功能。

package com.chaomixian.vflow.ui.workflow_list

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class WorkflowListFragment : Fragment() {
    // ... (其他属性保持不变)
    private lateinit var workflowManager: WorkflowManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkflowListAdapter
    // private lateinit var itemTouchHelper: ItemTouchHelper // 拖拽排序暂未完全实现或启用
    private var pendingWorkflow: Workflow? = null // 用于执行前权限请求的待处理工作流
    private var pendingExportWorkflow: Workflow? = null // 用于导出的待处理工作流
    private val gson = Gson()

    // 导入冲突处理相关状态
    private val importQueue: Queue<Workflow> = LinkedList()
    private enum class ConflictChoice { ASK, REPLACE_ALL, KEEP_ALL }
    private var conflictChoice = ConflictChoice.ASK

    // ActivityResultLauncher 用于处理权限请求回调
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { executeWorkflow(it) } // 权限获取成功后执行待处理工作流
        }
        pendingWorkflow = null
    }

    private val exportSingleLauncher = // ... (保持不变)
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { fileUri ->
                pendingExportWorkflow?.let { workflow ->
                    try {
                        val jsonString = gson.toJson(workflow)
                        requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                        Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
            pendingExportWorkflow = null
        }

    private val backupLauncher = // ... (保持不变)
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { fileUri ->
                try {
                    val allWorkflows = workflowManager.getAllWorkflows()
                    val jsonString = gson.toJson(allWorkflows)
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { it.write(jsonString.toByteArray()) }
                    Toast.makeText(requireContext(), "备份成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(requireContext(), "备份失败: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }

    private val importLauncher = // ... (保持不变)
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { fileUri ->
                try {
                    val jsonString = requireContext().contentResolver.openInputStream(fileUri)?.use {
                        BufferedReader(InputStreamReader(it)).readText()
                    } ?: throw Exception("无法读取文件")

                    val workflowsToImport = mutableListOf<Workflow>()
                    try {
                        val listType = object : TypeToken<List<Workflow>>() {}.type
                        val list: List<Workflow> = gson.fromJson(jsonString, listType)
                        if (list.any { it.id == null || it.name == null }) throw JsonSyntaxException("备份文件格式错误")
                        workflowsToImport.addAll(list)
                    } catch (e: JsonSyntaxException) {
                        val singleWorkflow: Workflow = gson.fromJson(jsonString, Workflow::class.java)
                        if (singleWorkflow.id == null || singleWorkflow.name == null) throw JsonSyntaxException("单个工作流文件格式错误")
                        workflowsToImport.add(singleWorkflow)
                    }

                    if (workflowsToImport.isNotEmpty()) {
                        startImportProcess(workflowsToImport)
                    } else {
                        Toast.makeText(requireContext(), "文件中没有可导入的工作流", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_LONG).show() }
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
        recyclerView = view.findViewById(R.id.recycler_view_workflows)
        setupRecyclerView()
        // setupDragAndDrop() // 拖拽排序功能暂缓
        view.findViewById<FloatingActionButton>(R.id.fab_add_workflow).setOnClickListener {
            startActivity(Intent(requireContext(), WorkflowEditorActivity::class.java))
        }

        // 订阅执行状态的 Flow
        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                // 当状态改变时，通知适配器刷新对应的项
                val workflows = (recyclerView.adapter as? WorkflowListAdapter)?.getWorkflows()
                val index = workflows?.indexOfFirst {
                    when(state) {
                        is ExecutionState.Running -> it.id == state.workflowId
                        is ExecutionState.Finished -> it.id == state.workflowId
                        is ExecutionState.Cancelled -> it.id == state.workflowId
                    }
                }
                if (index != null && index != -1) {
                    adapter.notifyItemChanged(index)
                }
            }
        }

        return view
    }

    /** Fragment 恢复时，重新加载工作流列表。 */
    override fun onResume() {
        super.onResume()
        loadWorkflows()
    }

    // --- 选项菜单处理 ---
    /** 创建选项菜单。 */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.workflow_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    /** 处理选项菜单项点击事件。 */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_backup_workflows -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                backupLauncher.launch("vflow_backup_${timestamp}.json") // 启动备份文件创建流程
                true
            }
            R.id.menu_import_workflows -> {
                importLauncher.launch(arrayOf("application/json")) // 启动导入文件选择流程
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 加载工作流并设置到 RecyclerView 的 Adapter。 */
    private fun loadWorkflows() {
        val workflows = workflowManager.getAllWorkflows()
        // 检查 adapter 是否已初始化
        if (::adapter.isInitialized) {
            adapter.updateData(workflows)
        } else {
            adapter = WorkflowListAdapter(
                workflows.toMutableList(), // 传递可变列表
                onEdit = { workflow ->
                    val intent = Intent(requireContext(), WorkflowEditorActivity::class.java).apply {
                        putExtra(WorkflowEditorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                    }
                    startActivity(intent)
                },
                onDelete = { workflow -> showDeleteConfirmationDialog(workflow) },
                onDuplicate = { workflow ->
                    workflowManager.duplicateWorkflow(workflow.id)
                    Toast.makeText(requireContext(), "已复制为 '${workflow.name} (副本)'", Toast.LENGTH_SHORT).show()
                    loadWorkflows()
                },
                onExport = { workflow ->
                    pendingExportWorkflow = workflow
                    exportSingleLauncher.launch("${workflow.name}.json")
                },
                onExecute = { workflow ->
                    if (WorkflowExecutor.isRunning(workflow.id)) {
                        WorkflowExecutor.stopExecution(workflow.id)
                    } else {
                        executeWorkflow(workflow)
                    }
                }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(requireContext(), workflow)
        if (missingPermissions.isEmpty()) {
            Toast.makeText(requireContext(), "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
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

    private fun startImportProcess(workflows: List<Workflow>) {
        importQueue.clear()
        importQueue.addAll(workflows)
        conflictChoice = ConflictChoice.ASK // 重置冲突处理选择
        processNextInImportQueue()
    }

    /** 处理导入队列中的下一个工作流。 */
    private fun processNextInImportQueue() {
        if (importQueue.isEmpty()) { // 队列为空，导入完成
            Toast.makeText(requireContext(), "导入完成", Toast.LENGTH_SHORT).show()
            loadWorkflows()
            return
        }

        val workflowToImport = importQueue.poll() ?: return
        val existingWorkflow = workflowManager.getWorkflow(workflowToImport.id)

        if (existingWorkflow == null) { // 无冲突，直接保存
            workflowManager.saveWorkflow(workflowToImport)
            processNextInImportQueue()
        } else { // 存在冲突
            when (conflictChoice) {
                ConflictChoice.REPLACE_ALL -> { handleReplace(workflowToImport); processNextInImportQueue() }
                ConflictChoice.KEEP_ALL -> { handleKeepBoth(workflowToImport); processNextInImportQueue() }
                ConflictChoice.ASK -> showConflictDialog(workflowToImport, existingWorkflow) // 询问用户
            }
        }
    }

    /** 显示导入冲突对话框。 */
    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_import_conflict, null)
        val rememberChoiceCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_remember_choice)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入冲突")
            .setMessage("已存在一个名为 '${existing.name}' 的工作流 (ID: ${existing.id.substring(0, 8)}...)。您想如何处理来自文件中的 '${toImport.name}'?")
            .setView(dialogView)
            .setPositiveButton("保留两者") { _, _ -> // 保留两者，导入的重命名
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.KEEP_ALL
                handleKeepBoth(toImport)
                processNextInImportQueue()
            }
            .setNegativeButton("替换") { _, _ -> // 替换现有
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.REPLACE_ALL
                handleReplace(toImport)
                processNextInImportQueue()
            }
            .setNeutralButton("跳过") { _,_ -> processNextInImportQueue() } // 跳过此条
            .setCancelable(false)
            .show()
    }

    /** 处理替换操作。 */
    private fun handleReplace(workflow: Workflow) {
        workflowManager.saveWorkflow(workflow)
        Toast.makeText(requireContext(), "'${workflow.name}' 已被替换", Toast.LENGTH_SHORT).show()
    }

    /** 处理保留两者操作 (重命名导入的工作流)。 */
    private fun handleKeepBoth(workflow: Workflow) {
        val newWorkflow = workflow.copy(
            id = UUID.randomUUID().toString(), // 生成新ID
            name = "${workflow.name} (导入)" // 修改名称
        )
        workflowManager.saveWorkflow(newWorkflow)
        Toast.makeText(requireContext(), "'${workflow.name}' 已作为副本导入", Toast.LENGTH_SHORT).show()
    }

    /** 显示删除确认对话框。 */
    private fun showDeleteConfirmationDialog(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                loadWorkflows() // 重新加载列表
            }
            .show()
    }
}