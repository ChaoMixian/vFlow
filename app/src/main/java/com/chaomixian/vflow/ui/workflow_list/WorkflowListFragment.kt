package com.chaomixian.vflow.ui.workflow_list

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.ui.workflow_editor.WorkflowEditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Collections

class WorkflowListFragment : Fragment() {
    private lateinit var workflowManager: WorkflowManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkflowListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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

    private fun setupRecyclerView() {
        // 使用 GridLayoutManager，每行2个
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        // Adapter 在 onResume 中加载数据时创建
    }

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
            onDelete = { workflow ->
                showDeleteConfirmationDialog(workflow)
            },
            onDuplicate = { workflow ->
                workflowManager.duplicateWorkflow(workflow.id)
                Toast.makeText(requireContext(), "已复制为 '${workflow.name} (副本)'", Toast.LENGTH_SHORT).show()
                loadWorkflows() // 重新加载以刷新列表
            },
            onExport = { workflow ->
                // TODO: 实现导出逻辑 (需要文件选择器)
                Toast.makeText(requireContext(), "导出功能待实现", Toast.LENGTH_SHORT).show()
            },
            onExecute = { workflow ->
                // TODO: 实现执行逻辑
                Toast.makeText(requireContext(), "正在执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.adapter = adapter
    }

    private fun showDeleteConfirmationDialog(workflow: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, workflow.name))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                workflowManager.deleteWorkflow(workflow.id)
                loadWorkflows() // 删除后刷新列表
            }
            .show()
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                val workflows = workflowManager.getAllWorkflows().toMutableList()
                Collections.swap(workflows, fromPosition, toPosition)
                // Here you should ideally save the new order in WorkflowManager
                // For now, we just update the adapter
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}