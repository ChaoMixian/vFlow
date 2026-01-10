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
import com.chaomixian.vflow.data.repository.api.RepositoryApiClient
import com.chaomixian.vflow.databinding.FragmentWorkflowRepoBinding
import kotlinx.coroutines.launch

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
        loadWorkflows()
    }

    private fun setupRecyclerView() {
        adapter = WorkflowRepoAdapter(
            onDownloadClick = { repoWorkflow ->
                downloadWorkflow(repoWorkflow)
            }
        )

        binding.recyclerViewWorkflows.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WorkflowRepoFragment.adapter
        }
    }

    private fun loadWorkflows() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textError.visibility = View.GONE

        lifecycleScope.launch {
            val result = RepositoryApiClient.fetchIndex()

            binding.progressBar.visibility = View.GONE

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

    private fun downloadWorkflow(repoWorkflow: com.chaomixian.vflow.data.repository.model.RepoWorkflow) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "正在下载: ${repoWorkflow.name}", Toast.LENGTH_SHORT).show()

            val result = RepositoryApiClient.downloadWorkflow(repoWorkflow.download_url)

            result.onSuccess { workflow ->
                // 保存工作流
                workflowManager.saveWorkflow(workflow)
                Toast.makeText(requireContext(), "下载成功: ${workflow.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "下载失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = WorkflowRepoFragment()
    }
}
