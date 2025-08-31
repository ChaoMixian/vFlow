package com.chaomixian.vflow.ui.workflow_list

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.UUID

class WorkflowListFragment : Fragment() {
    private lateinit var workflowManager: WorkflowManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkflowListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var pendingWorkflow: Workflow? = null
    private var pendingExportWorkflow: Workflow? = null
    private val gson = Gson()

    // --- 导入冲突处理所需的状态 ---
    private val importQueue: Queue<Workflow> = LinkedList()
    private enum class ConflictChoice { ASK, REPLACE_ALL, KEEP_ALL }
    private var conflictChoice = ConflictChoice.ASK

    // --- ActivityResult Launchers ---
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWorkflow?.let { executeWorkflow(it) }
        }
        pendingWorkflow = null
    }

    private val exportSingleLauncher = registerForActivityResult(
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

    private val backupLauncher = registerForActivityResult(
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

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                val jsonString = requireContext().contentResolver.openInputStream(fileUri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: throw Exception("无法读取文件")

                val workflowsToImport = mutableListOf<Workflow>()
                // 智能识别：优先尝试解析为列表（备份文件），如果失败，再尝试解析为单个对象
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // 启用选项菜单
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workflows, container, false)
        workflowManager = WorkflowManager(requireContext())
        recyclerView = view.findViewById(R.id.recycler_view_workflows)
        setupRecyclerView()
        setupDragAndDrop()
        view.findViewById<FloatingActionButton>(R.id.fab_add_workflow).setOnClickListener {
            startActivity(Intent(requireContext(), WorkflowEditorActivity::class.java))
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        loadWorkflows()
    }

    // --- 菜单处理 ---
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.workflow_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
    // --- 菜单处理结束 ---


    private fun loadWorkflows() {
        val workflows = workflowManager.getAllWorkflows().toMutableList()
        adapter = WorkflowListAdapter(
            workflows,
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
            onExecute = { workflow -> executeWorkflow(workflow) }
        )
        recyclerView.adapter = adapter
    }

    // --- 导入与冲突处理逻辑 ---
    private fun startImportProcess(workflows: List<Workflow>) {
        importQueue.clear()
        importQueue.addAll(workflows)
        conflictChoice = ConflictChoice.ASK
        processNextInImportQueue()
    }

    private fun processNextInImportQueue() {
        if (importQueue.isEmpty()) {
            Toast.makeText(requireContext(), "导入完成", Toast.LENGTH_SHORT).show()
            loadWorkflows()
            return
        }

        val workflowToImport = importQueue.poll() ?: return
        val existingWorkflow = workflowManager.getWorkflow(workflowToImport.id)

        if (existingWorkflow == null) {
            workflowManager.saveWorkflow(workflowToImport)
            processNextInImportQueue()
        } else {
            when (conflictChoice) {
                ConflictChoice.REPLACE_ALL -> {
                    handleReplace(workflowToImport)
                    processNextInImportQueue()
                }
                ConflictChoice.KEEP_ALL -> {
                    handleKeepBoth(workflowToImport)
                    processNextInImportQueue()
                }
                ConflictChoice.ASK -> showConflictDialog(workflowToImport, existingWorkflow)
            }
        }
    }

    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_import_conflict, null)
        val rememberChoiceCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_remember_choice)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入冲突")
            .setMessage("已存在一个名为 '${existing.name}' 的工作流 (ID: ${existing.id.substring(0, 8)}...)。您想如何处理来自文件中的 '${toImport.name}'?")
            .setView(dialogView)
            .setPositiveButton("保留两者") { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.KEEP_ALL
                handleKeepBoth(toImport)
                processNextInImportQueue()
            }
            .setNegativeButton("替换") { _, _ ->
                if (rememberChoiceCheckbox.isChecked) conflictChoice = ConflictChoice.REPLACE_ALL
                handleReplace(toImport)
                processNextInImportQueue()
            }
            .setNeutralButton("跳过", { _,_ ->
                processNextInImportQueue()
            })
            .setCancelable(false)
            .show()
    }

    private fun handleReplace(workflow: Workflow) {
        workflowManager.saveWorkflow(workflow)
        Toast.makeText(requireContext(), "'${workflow.name}' 已被替换", Toast.LENGTH_SHORT).show()
    }

    private fun handleKeepBoth(workflow: Workflow) {
        val newWorkflow = workflow.copy(
            id = UUID.randomUUID().toString(),
            name = "${workflow.name} (导入)"
        )
        workflowManager.saveWorkflow(newWorkflow)
        Toast.makeText(requireContext(), "'${workflow.name}' 已作为副本导入", Toast.LENGTH_SHORT).show()
    }
    // --- 导入逻辑结束 ---

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

    private fun showDeleteConfirmationDialog(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                loadWorkflows()
            }
            .show()
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.notifyItemMoved(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}