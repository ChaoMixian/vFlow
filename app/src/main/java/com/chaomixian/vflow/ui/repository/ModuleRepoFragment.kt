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
                    binding.textError.text = getString(R.string.text_no_modules)
                    binding.textError.visibility = View.VISIBLE
                } else {
                    adapter.submitList(index.modules)
                }
            }.onFailure { error ->
                val errorMsg = getString(R.string.text_load_failed, error.message ?: "")
                binding.textError.text = errorMsg
                binding.textError.visibility = View.VISIBLE
                Toast.makeText(requireContext(), getString(R.string.toast_load_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadConfirmDialog(repoModule: com.chaomixian.vflow.data.repository.model.RepoModule) {
        val permissionsText = if (repoModule.permissions.isEmpty()) {
            getString(R.string.label_no_permissions)
        } else {
            repoModule.permissions.joinToString(", ")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_download_module_title))
            .setMessage(getString(R.string.dialog_download_module_message,
                repoModule.name,
                repoModule.description,
                repoModule.version,
                repoModule.category,
                permissionsText))
            .setPositiveButton(getString(R.string.dialog_button_download)) { _, _ ->
                downloadModule(repoModule)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadModule(repoModule: com.chaomixian.vflow.data.repository.model.RepoModule) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.toast_downloading, repoModule.name), Toast.LENGTH_SHORT).show()

            val result = RepositoryApiClient.downloadModule(repoModule.download_url)

            result.onSuccess { bytes ->
                installModule(repoModule, bytes)
            }.onFailure { error ->
                Toast.makeText(requireContext(), getString(R.string.toast_download_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
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
                        Toast.makeText(requireContext(), getString(R.string.toast_install_success, session.manifest.name), Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    tempFile.delete()
                    Toast.makeText(requireContext(), getString(R.string.toast_install_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_install_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
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
            .setTitle(getString(R.string.dialog_module_conflict_title))
            .setMessage(getString(R.string.dialog_module_conflict_message, session.manifest.id, repoModule.name))
            .setPositiveButton(getString(R.string.dialog_button_overwrite)) { _, _ ->
                // 替换现有模块
                ModuleManager.commitInstall(session)
                tempFile.delete()
                Toast.makeText(requireContext(), getString(R.string.toast_module_replaced, repoModule.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_button_skip), null)
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
