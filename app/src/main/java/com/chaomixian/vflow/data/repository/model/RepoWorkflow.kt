// 文件: main/java/com/chaomixian/vflow/data/repository/model/RepoWorkflow.kt
package com.chaomixian.vflow.data.repository.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 仓库中的工作流元数据
 */
@Parcelize
data class RepoWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val vFlowLevel: Int,
    val homepage: String = "",
    val tags: List<String> = emptyList(),
    val updated_at: String = "",
    val filename: String,
    val download_url: String
) : Parcelable

/**
 * 仓库索引数据
 */
data class RepoIndex(
    val version: String,
    val last_updated: String,
    val total_count: Int,
    val workflows: List<RepoWorkflow>
)
