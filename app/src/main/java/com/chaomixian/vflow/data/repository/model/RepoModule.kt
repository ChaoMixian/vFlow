// 文件: main/java/com/chaomixian/vflow/data/repository/model/RepoModule.kt
package com.chaomixian.vflow.data.repository.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 仓库中的模块元数据
 */
@Parcelize
data class RepoModule(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val category: String,
    val homepage: String = "",
    val permissions: List<String> = emptyList(),
    val inputs: List<ModuleInput> = emptyList(),
    val outputs: List<ModuleOutput> = emptyList(),
    val filename: String,
    val download_url: String
) : Parcelable

/**
 * 模块输入参数定义
 */
@Parcelize
data class ModuleInput(
    val id: String,
    val name: String,
    val type: String,
    val defaultValue: String? = null
) : Parcelable

/**
 * 模块输出参数定义
 */
@Parcelize
data class ModuleOutput(
    val id: String,
    val name: String,
    val type: String
) : Parcelable

/**
 * 模块仓库索引数据
 */
data class RepoModuleIndex(
    val version: String,
    val last_updated: String,
    val total_count: Int,
    val modules: List<RepoModule>
)
