// 文件: main/java/com/chaomixian/vflow/ui/repository/ModuleRepoFragment.kt
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
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.data.repository.api.RepositoryApiClient
import com.chaomixian.vflow.databinding.FragmentModuleRepoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 模块仓库Fragment
 * 展示可下载的模块列表
 */
class ModuleRepoFragment : Fragment() {

    private var _binding: FragmentModuleRepoBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ModuleRepoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModuleRepoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        loadModules()
    }

    private fun setupRecyclerView() {
        adapter = ModuleRepoAdapter(
            onDownloadClick = { repoModule ->
                showDownloadConfirmDialog(repoModule)
            }
        )

        binding.recyclerViewModules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ModuleRepoFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadModules()
        }
    }

    private fun loadModules() {
        binding.swipeRefresh.isRefreshing = true
        binding.textError.visibility = View.GONE

        lifecycleScope.launch {
            val result = RepositoryApiClient.fetchModuleIndex()

            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { index ->
                if (index.modules.isEmpty()) {
                    binding.textError.text = "暂无模块"
                    binding.textError.visibility = View.VISIBLE
                } else {
                    adapter.submitList(index.modules)
                }
            }.onFailure { error ->
                binding.textError.text = "加载失败: ${error.message}"
                binding.textError.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "加载失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadConfirmDialog(repoModule: com.chaomixian.vflow.data.repository.model.RepoModule) {
        val permissionsText = if (repoModule.permissions.isEmpty()) {
            "无"
        } else {
            repoModule.permissions.joinToString(", ")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("下载模块")
            .setMessage("确定要下载 '${repoModule.name}' 吗？\n\n" +
                    "描述: ${repoModule.description}\n\n" +
                    "版本: v${repoModule.version}\n" +
                    "分类: ${repoModule.category}\n" +
                    "权限: $permissionsText")
            .setPositiveButton("下载") { _, _ ->
                downloadModule(repoModule)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadModule(repoModule: com.chaomixian.vflow.data.repository.model.RepoModule) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "正在下载: ${repoModule.name}", Toast.LENGTH_SHORT).show()

            val result = RepositoryApiClient.downloadModule(repoModule.download_url)

            result.onSuccess { bytes ->
                installModule(repoModule, bytes)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "下载失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installModule(
        repoModule: com.chaomixian.vflow.data.repository.model.RepoModule,
        bytes: ByteArray
    ) {
        lifecycleScope.launch {
            try {
                // 保存到临时文件
                val tempFile = File(requireContext().cacheDir, "temp_module.zip")
                FileOutputStream(tempFile).use { output ->
                    output.write(bytes)
                }

                // 使用ModuleManager安装
                val installResult = ModuleManager.prepareInstall(
                    requireContext(),
                    android.net.Uri.fromFile(tempFile)
                )

                installResult.onSuccess { session ->
                    // 检查是否已存在
                    val existingModule = checkModuleExists(session.manifest.id)

                    if (existingModule) {
                        showConflictDialog(repoModule, session, tempFile)
                    } else {
                        // 直接安装
                        ModuleManager.commitInstall(session)
                        tempFile.delete()
                        Toast.makeText(requireContext(), "安装成功: ${session.manifest.name}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    tempFile.delete()
                    Toast.makeText(requireContext(), "安装失败: ${error.message}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkModuleExists(moduleId: String): Boolean {
        val modulesDir = com.chaomixian.vflow.core.utils.StorageManager.modulesDir
        return File(modulesDir, moduleId).exists()
    }

    private fun showConflictDialog(
        repoModule: com.chaomixian.vflow.data.repository.model.RepoModule,
        session: ModuleManager.InstallSession,
        tempFile: File
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("模块冲突")
            .setMessage("已存在一个ID为 '${session.manifest.id}' 的模块。您想如何处理来自仓库的 '${repoModule.name}'?")
            .setPositiveButton("替换") { _, _ ->
                // 替换现有模块
                ModuleManager.commitInstall(session)
                tempFile.delete()
                Toast.makeText(requireContext(), "'${repoModule.name}' 已替换", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("跳过", null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ModuleRepoFragment()
    }
}
