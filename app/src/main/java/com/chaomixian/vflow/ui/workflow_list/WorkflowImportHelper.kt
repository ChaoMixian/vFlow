// 文件: main/java/com/chaomixian/vflow/ui/workflow_list/WorkflowImportHelper.kt
package com.chaomixian.vflow.ui.workflow_list

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
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
     * 1. 单个工作流对象
     * 2. 工作流数组
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
                else -> {
                    // 旧的格式或单个工作流
                    importWorkflows(jsonString)
                }
            }
        } catch (e: Exception) {
            // 尝试作为工作流列表解析
            importWorkflows(jsonString)
        }
    }

    private fun importWorkflows(jsonString: String) {
        val workflowsToImport = mutableListOf<Workflow>()
        try {
            val listType = object : TypeToken<List<Workflow>>() {}.type
            val list: List<Workflow> = gson.fromJson(jsonString, listType)
            workflowsToImport.addAll(list)
        } catch (e: JsonSyntaxException) {
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

            // 重置 folderId 为新文件夹的 ID
            val updatedWorkflows = workflows.map { workflow ->
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

            // 更新工作流的 folderId
            val updatedWorkflows = workflows.map { workflow ->
                workflow.copy(folderId = newFolderId)
            }

            startImportProcess(updatedWorkflows)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun startImportProcess(workflows: List<Workflow>) {
        ImportQueueProcessor(context, workflowManager, onImportCompleted).startImport(workflows)
    }
}
