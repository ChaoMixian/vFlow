// 文件: main/java/com/chaomixian/vflow/ui/repository/WorkflowRepoAdapter.kt
package com.chaomixian.vflow.ui.repository

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.data.repository.model.RepoWorkflow
import com.chaomixian.vflow.databinding.ItemWorkflowRepoBinding

/**
 * 工作流仓库列表Adapter
 */
class WorkflowRepoAdapter(
    private val onDownloadClick: (RepoWorkflow) -> Unit
) : ListAdapter<RepoWorkflow, WorkflowRepoAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkflowRepoBinding.inflate(
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
        private val binding: ItemWorkflowRepoBinding,
        private val onDownloadClick: (RepoWorkflow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(workflow: RepoWorkflow) {
            binding.apply {
                textWorkflowName.text = workflow.name
                textWorkflowDescription.text = workflow.description
                textWorkflowAuthor.text = "作者: ${workflow.author}"
                textWorkflowVersion.text = "v${workflow.version}"
                textWorkflowID.text = "ID: ${workflow.id}"

                // 显示标签
                if (workflow.tags.isNotEmpty()) {
                    textWorkflowTags.text = "Tags: ${workflow.tags.joinToString(", ")}"
                    textWorkflowTags.visibility = android.view.View.VISIBLE
                } else {
                    textWorkflowTags.visibility = android.view.View.GONE
                }

                // 下载按钮
                buttonDownload.setOnClickListener {
                    onDownloadClick(workflow)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RepoWorkflow>() {
        override fun areItemsTheSame(oldItem: RepoWorkflow, newItem: RepoWorkflow): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RepoWorkflow, newItem: RepoWorkflow): Boolean {
            return oldItem == newItem
        }
    }
}
