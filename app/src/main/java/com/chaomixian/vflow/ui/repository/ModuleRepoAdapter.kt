// 文件: main/java/com/chaomixian/vflow/ui/repository/ModuleRepoAdapter.kt
package com.chaomixian.vflow.ui.repository

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.data.repository.model.RepoModule
import com.chaomixian.vflow.databinding.ItemModuleRepoBinding

/**
 * 模块仓库列表Adapter
 */
class ModuleRepoAdapter(
    private val onDownloadClick: (RepoModule) -> Unit
) : ListAdapter<RepoModule, ModuleRepoAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModuleRepoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onDownloadClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemModuleRepoBinding,
        private val onDownloadClick: (RepoModule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(module: RepoModule) {
            binding.apply {
                textModuleName.text = module.name
                textModuleDescription.text = module.description
                textModuleAuthor.text = "作者: ${module.author}"
                textModuleVersion.text = "v${module.version}"
                textModuleID.text = "ID: ${module.id}"
                textModuleCategory.text = module.category

                // 显示权限
                if (module.permissions.isNotEmpty()) {
                    textModulePermissions.text = "权限: ${module.permissions.size} 项"
                    textModulePermissions.visibility = android.view.View.VISIBLE
                } else {
                    textModulePermissions.visibility = android.view.View.GONE
                }

                // 下载按钮
                buttonDownload.setOnClickListener {
                    onDownloadClick(module)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RepoModule>() {
        override fun areItemsTheSame(oldItem: RepoModule, newItem: RepoModule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RepoModule, newItem: RepoModule): Boolean {
            return oldItem == newItem
        }
    }
}
