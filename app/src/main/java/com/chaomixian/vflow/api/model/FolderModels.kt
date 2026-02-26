package com.chaomixian.vflow.api.model

/**
 * 文件夹
 */
data class Folder(
    val id: String,
    val name: String,
    val parentId: String?,
    val order: Int,
    val workflowCount: Int,
    val subfolderCount: Int,
    val createdAt: Long,
    val modifiedAt: Long
)

/**
 * 文件夹详情（包含子项）
 */
data class FolderDetail(
    val id: String,
    val name: String,
    val parentId: String?,
    val order: Int,
    val workflowCount: Int,
    val subfolderCount: Int,
    val workflows: List<FolderWorkflowItem>,
    val subfolders: List<FolderSubfolderItem>,
    val createdAt: Long,
    val modifiedAt: Long
)

/**
 * 文件夹中的工作流项
 */
data class FolderWorkflowItem(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
    val order: Int
)

/**
 * 子文件夹项
 */
data class FolderSubfolderItem(
    val id: String,
    val name: String,
    val order: Int
)

/**
 * 创建文件夹请求
 */
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,
    val order: Int = 0
)

/**
 * 更新文件夹请求
 */
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: String? = null,
    val order: Int? = null
)
