// 文件: main/java/com/chaomixian/vflow/ui/repository/WorkflowRepoFragment.kt
package com.chaomixian.vflow.ui.repository

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.data.repository.api.RepositoryApiClient
import com.chaomixian.vflow.databinding.FragmentWorkflowRepoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 工作流仓库Fragment
 * 展示可下载的工作流列表
 */
class WorkflowRepoFragment : Fragment() {

    private var _binding: FragmentWorkflowRepoBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: WorkflowRepoAdapter
    private lateinit var workflowManager: WorkflowManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkflowRepoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        workflowManager = WorkflowManager(requireContext())

        setupRecyclerView()
        setupSwipeRefresh()
        loadWorkflows()
    }

    private fun setupRecyclerView() {
        adapter = WorkflowRepoAdapter(
            onDownloadClick = { repoWorkflow ->
                showDownloadConfirmDialog(repoWorkflow)
            }
        )

        binding.recyclerViewWorkflows.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WorkflowRepoFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadWorkflows()
        }
    }

    private fun loadWorkflows() {
        binding.swipeRefresh.isRefreshing = true
        binding.textError.visibility = View.GONE

        lifecycleScope.launch {
            val result = RepositoryApiClient.fetchWorkflowIndex()

            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { index ->
                if (index.workflows.isEmpty()) {
                    binding.textError.text = getString(R.string.text_no_workflows)
                    binding.textError.visibility = View.VISIBLE
                } else {
                    adapter.submitList(index.workflows)
                }
            }.onFailure { error ->
                val errorMsg = getString(R.string.text_load_failed, error.message ?: "")
                binding.textError.text = errorMsg
                binding.textError.visibility = View.VISIBLE
                Toast.makeText(requireContext(), getString(R.string.toast_load_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadConfirmDialog(repoWorkflow: com.chaomixian.vflow.data.repository.model.RepoWorkflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_download_workflow_title))
            .setMessage(getString(R.string.dialog_download_workflow_message, repoWorkflow.name, repoWorkflow.description))
            .setPositiveButton(getString(R.string.dialog_button_download)) { _, _ ->
                downloadWorkflow(repoWorkflow)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadWorkflow(repoWorkflow: com.chaomixian.vflow.data.repository.model.RepoWorkflow) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.toast_downloading, repoWorkflow.name), Toast.LENGTH_SHORT).show()

            val result = RepositoryApiClient.downloadWorkflow(repoWorkflow.download_url)

            result.onSuccess { workflow ->
                handleDownloadedWorkflow(workflow)
            }.onFailure { error ->
                Toast.makeText(requireContext(), getString(R.string.toast_download_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleDownloadedWorkflow(workflow: Workflow) {
        // 检查是否已存在相同ID的工作流
        val existingWorkflow = workflowManager.getWorkflow(workflow.id)

        if (existingWorkflow == null) {
            // 无冲突，直接保存
            workflowManager.saveWorkflow(workflow)
            Toast.makeText(requireContext(), getString(R.string.toast_download_workflow_success, workflow.name), Toast.LENGTH_SHORT).show()
        } else {
            // 存在冲突，显示对话框
            showConflictDialog(workflow, existingWorkflow)
        }
    }

    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_workflow_conflict_title))
            .setMessage(getString(R.string.dialog_workflow_conflict_message, existing.name, existing.id.substring(0, 8), toImport.name))
            .setPositiveButton(getString(R.string.dialog_button_keep_both)) { _, _ ->
                // 保留两者，导入的重命名
                val newWorkflow = toImport.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${toImport.name} ${getString(R.string.label_workflow_from_repo)}"
                )
                workflowManager.saveWorkflow(newWorkflow)
                Toast.makeText(requireContext(), getString(R.string.toast_workflow_downloaded_as_copy, toImport.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_button_replace)) { _, _ ->
                // 替换现有
                workflowManager.saveWorkflow(toImport)
                Toast.makeText(requireContext(), getString(R.string.toast_workflow_replaced, toImport.name), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.dialog_button_skip), null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = WorkflowRepoFragment()
    }
}
