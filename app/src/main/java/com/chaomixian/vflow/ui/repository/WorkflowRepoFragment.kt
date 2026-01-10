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
            val result = RepositoryApiClient.fetchIndex()

            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { index ->
                if (index.workflows.isEmpty()) {
                    binding.textError.text = "暂无工作流"
                    binding.textError.visibility = View.VISIBLE
                } else {
                    adapter.submitList(index.workflows)
                }
            }.onFailure { error ->
                binding.textError.text = "加载失败: ${error.message}"
                binding.textError.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "加载失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadConfirmDialog(repoWorkflow: com.chaomixian.vflow.data.repository.model.RepoWorkflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("下载工作流")
            .setMessage("确定要下载 '${repoWorkflow.name}' 吗？\n\n${repoWorkflow.description}")
            .setPositiveButton("下载") { _, _ ->
                downloadWorkflow(repoWorkflow)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadWorkflow(repoWorkflow: com.chaomixian.vflow.data.repository.model.RepoWorkflow) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "正在下载: ${repoWorkflow.name}", Toast.LENGTH_SHORT).show()

            val result = RepositoryApiClient.downloadWorkflow(repoWorkflow.download_url)

            result.onSuccess { workflow ->
                handleDownloadedWorkflow(workflow)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "下载失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleDownloadedWorkflow(workflow: Workflow) {
        // 检查是否已存在相同ID的工作流
        val existingWorkflow = workflowManager.getWorkflow(workflow.id)

        if (existingWorkflow == null) {
            // 无冲突，直接保存
            workflowManager.saveWorkflow(workflow)
            Toast.makeText(requireContext(), "下载成功: ${workflow.name}", Toast.LENGTH_SHORT).show()
        } else {
            // 存在冲突，显示对话框
            showConflictDialog(workflow, existingWorkflow)
        }
    }

    private fun showConflictDialog(toImport: Workflow, existing: Workflow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("工作流冲突")
            .setMessage("已存在一个名为 '${existing.name}' 的工作流 (ID: ${existing.id.substring(0, 8)}...)。您想如何处理来自仓库的 '${toImport.name}'?")
            .setPositiveButton("保留两者") { _, _ ->
                // 保留两者，导入的重命名
                val newWorkflow = toImport.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${toImport.name} (仓库)"
                )
                workflowManager.saveWorkflow(newWorkflow)
                Toast.makeText(requireContext(), "'${toImport.name}' 已作为副本下载", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("替换") { _, _ ->
                // 替换现有
                workflowManager.saveWorkflow(toImport)
                Toast.makeText(requireContext(), "'${toImport.name}' 已被替换", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("跳过", null)
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
