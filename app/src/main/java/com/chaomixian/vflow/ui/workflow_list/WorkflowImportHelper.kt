// 文件: main/java/com/chaomixian/vflow/ui/workflow_list/WorkflowImportHelper.kt
package com.chaomixian.vflow.ui.workflow_list

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * 工作流导入工具类
 * 用于从 JSON 字符串导入工作流，支持多种格式
 */
class WorkflowImportHelper(
    private val context: Context,
    private val workflowManager: WorkflowManager,
    private val folderManager: FolderManager,
    private val onImportCompleted: () -> Unit
) {
    private val gson = Gson()

    /**
     * 从 JSON 字符串导入工作流
     * 支持格式：
     * 1. 单个工作流对象（带或不带 _meta）
     * 2. 工作流数组（带或不带 _meta）
     * 3. 文件夹导出格式：{"folder": {...}, "workflows": [...]}
     * 4. 完整备份格式：{"folders": [...], "workflows": [...]}
     */
    fun importFromJson(jsonString: String) {
        try {
            val backupType = object : TypeToken<Map<String, Any>>() {}.type
            val backupData: Map<String, Any> = gson.fromJson(jsonString, backupType)

            when {
                // 完整备份格式：folders + workflows
                backupData.containsKey("folders") && backupData.containsKey("workflows") -> {
                    importBackupWithFolders(backupData)
                }
                // 文件夹导出格式：folder (单数) + workflows
                backupData.containsKey("folder") && backupData.containsKey("workflows") -> {
                    importFolderExport(backupData)
                }
                // 带 _meta 的单个工作流
                backupData.containsKey("_meta") -> {
                    val workflow = parseWorkflowWithMeta(backupData)
                    startImportProcess(listOf(workflow))
                }
                // 带有 workflows 键的数组格式（可能有 _meta）
                backupData.containsKey("workflows") -> {
                    val workflows = parseWorkflowsWithMeta(backupData)
                    startImportProcess(workflows)
                }
                else -> {
                    // 旧的格式或工作流列表
                    importLegacyWorkflows(jsonString)
                }
            }
        } catch (e: Exception) {
            // 尝试作为旧格式工作流列表解析
            importLegacyWorkflows(jsonString)
        }
    }

    /**
     * 解析带 _meta 的单个工作流
     */
    private fun parseWorkflowWithMeta(data: Map<String, Any>): Workflow {
        // Gson 会自动忽略 _meta 字段，因为它不在 Workflow 类中
        return gson.fromJson(gson.toJson(data), Workflow::class.java)
    }

    /**
     * 解析带 _meta 的工作流数组
     */
    private fun parseWorkflowsWithMeta(backupData: Map<String, Any>): List<Workflow> {
        val workflowsJson = gson.toJson(backupData["workflows"])
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val workflowMaps: List<Map<String, Any>> = gson.fromJson(workflowsJson, listType)
        return workflowMaps.map { workflowMap ->
            gson.fromJson(gson.toJson(workflowMap), Workflow::class.java)
        }
    }

    /**
     * 解析旧格式的工作流列表
     */
    private fun importLegacyWorkflows(jsonString: String) {
        val workflowsToImport = mutableListOf<Workflow>()
        try {
            // 尝试作为工作流列表解析
            val listType = object : TypeToken<List<Workflow>>() {}.type
            val list: List<Workflow> = gson.fromJson(jsonString, listType)
            workflowsToImport.addAll(list)
        } catch (e: Exception) {
            // 尝试作为单个工作流解析
            val singleWorkflow: Workflow = gson.fromJson(jsonString, Workflow::class.java)
            workflowsToImport.add(singleWorkflow)
        }

        if (workflowsToImport.isNotEmpty()) {
            startImportProcess(workflowsToImport)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_no_workflow_in_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun importBackupWithFolders(backupData: Map<String, Any>) {
        try {
            // 导入文件夹
            val foldersJson = gson.toJson(backupData["folders"])
            val folderListType = object : TypeToken<List<WorkflowFolder>>() {}.type
            val folders: List<WorkflowFolder> = gson.fromJson(foldersJson, folderListType)

            folders.forEach { folder ->
                // 检查是否已存在同名文件夹
                val existingFolder = folderManager.getAllFolders().find { it.name == folder.name }
                if (existingFolder != null) {
                    // 重命名导入的文件夹
                    folderManager.saveFolder(folder.copy(name = "${folder.name} (导入)"))
                } else {
                    folderManager.saveFolder(folder)
                }
            }

            // 导入工作流
            val workflowsJson = gson.toJson(backupData["workflows"])
            val workflowListType = object : TypeToken<List<Workflow>>() {}.type
            val workflows: List<Workflow> = gson.fromJson(workflowsJson, workflowListType)

            // 先确保元数据字段有默认值，再更新 folderId
            val workflowsWithDefaults = workflows.map { wf ->
                applyWorkflowDefaults(wf)
            }

            // 重置 folderId 为新文件夹的 ID
            val updatedWorkflows = workflowsWithDefaults.map { workflow ->
                val originalFolderName = folders.find { it.id == workflow.folderId }?.name
                if (originalFolderName != null) {
                    val newFolder = folderManager.getAllFolders().find { it.name == "${originalFolderName} (导入)" || it.name == originalFolderName }
                    if (newFolder != null) {
                        workflow.copy(folderId = newFolder.id)
                    } else {
                        workflow.copy(folderId = null)
                    }
                } else {
                    workflow.copy(folderId = null)
                }
            }

            startImportProcess(updatedWorkflows)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 导入文件夹导出格式：{"folder": {...}, "workflows": [...]}
     */
    private fun importFolderExport(backupData: Map<String, Any>) {
        try {
            // 导入文件夹
            val folderJson = gson.toJson(backupData["folder"])
            val folder: WorkflowFolder = gson.fromJson(folderJson, WorkflowFolder::class.java)

            // 检查是否已存在同名文件夹
            val existingFolder = folderManager.getAllFolders().find { it.name == folder.name }
            if (existingFolder != null) {
                folderManager.saveFolder(folder.copy(name = "${folder.name} (导入)"))
            } else {
                folderManager.saveFolder(folder)
            }

            // 获取新文件夹的 ID（可能是原名或重命名后的）
            val newFolder = folderManager.getAllFolders().find { it.name == folder.name || it.name == "${folder.name} (导入)" }
            val newFolderId = newFolder?.id

            // 导入工作流
            val workflowsJson = gson.toJson(backupData["workflows"])
            val workflowListType = object : TypeToken<List<Workflow>>() {}.type
            val workflows: List<Workflow> = gson.fromJson(workflowsJson, workflowListType)

            // 先确保元数据字段有默认值，再更新 folderId
            val workflowsWithDefaults = workflows.map { wf ->
                applyWorkflowDefaults(wf)
            }

            // 更新工作流的 folderId
            val updatedWorkflows = workflowsWithDefaults.map { workflow ->
                workflow.copy(folderId = newFolderId)
            }

            startImportProcess(updatedWorkflows)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 确保工作流元数据字段有默认值
     */
    private fun applyWorkflowDefaults(wf: Workflow): Workflow {
        return wf.copy(
            version = wf.version?.takeIf { it.isNotEmpty() } ?: "1.0.0",
            vFlowLevel = if (wf.vFlowLevel == 0) 1 else wf.vFlowLevel,
            description = wf.description ?: "",
            author = wf.author ?: "",
            homepage = wf.homepage ?: "",
            tags = wf.tags ?: emptyList(),
            modifiedAt = if (wf.modifiedAt == 0L) System.currentTimeMillis() else wf.modifiedAt
        )
    }

    private fun startImportProcess(workflows: List<Workflow>) {
        // 确保所有工作流的元数据字段有默认值
        val workflowsWithDefaults = workflows.map { wf ->
            wf.copy(
                version = wf.version?.takeIf { it.isNotEmpty() } ?: "1.0.0",
                vFlowLevel = if (wf.vFlowLevel == 0) 1 else wf.vFlowLevel,
                description = wf.description ?: "",
                author = wf.author ?: "",
                homepage = wf.homepage ?: "",
                tags = wf.tags ?: emptyList(),
                modifiedAt = if (wf.modifiedAt == 0L) System.currentTimeMillis() else wf.modifiedAt
            )
        }
        ImportQueueProcessor(context, workflowManager, onImportCompleted).startImport(workflowsWithDefaults)
    }
}
