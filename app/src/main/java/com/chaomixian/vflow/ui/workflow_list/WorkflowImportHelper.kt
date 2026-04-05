// 文件: main/java/com/chaomixian/vflow/ui/workflow_list/WorkflowImportHelper.kt
package com.chaomixian.vflow.ui.workflow_list

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.FolderManager
import com.chaomixian.vflow.core.workflow.WorkflowJsonImportParser
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.google.gson.Gson
import java.util.UUID

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
    private val parser = WorkflowJsonImportParser(gson)

    /**
     * 从 JSON 字符串导入工作流
     * 支持格式：
     * 1. 单个工作流对象（带或不带 _meta）
     * 2. 工作流数组（带或不带 _meta）
     * 3. 文件夹导出格式：{"folder": {...}, "workflows": [...]}
     * 4. 完整备份格式：{"folders": [...], "workflows": [...]}
     */
    fun importFromJson(jsonString: String): Boolean {
        try {
            val parsedImport = parser.parse(jsonString)
            if (parsedImport.workflows.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.toast_no_workflow_in_file), Toast.LENGTH_SHORT).show()
                return false
            }

            val importedFolderIds = importFolders(parsedImport.folders)
            val workflows = parsedImport.workflows.map { workflow ->
                val mappedFolderId = workflow.folderId?.let(importedFolderIds::get)
                applyWorkflowDefaults(workflow.copy(folderId = mappedFolderId))
            }

            startImportProcess(workflows)
            return true
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun importFolders(folders: List<WorkflowFolder>): Map<String, String> {
        if (folders.isEmpty()) {
            return emptyMap()
        }

        val existingNames = folderManager.getAllFolders().mapTo(mutableSetOf()) { it.name }
        val importedFolderIds = mutableMapOf<String, String>()

        val foldersWithNewIds = folders.map { folder ->
            val resolvedName = generateUniqueFolderName(folder.name, existingNames)
            val newId = UUID.randomUUID().toString()
            existingNames += resolvedName
            importedFolderIds[folder.id] = newId
            folder.copy(
                id = newId,
                name = resolvedName
            )
        }

        foldersWithNewIds.forEachIndexed { index, folder ->
            val originalFolder = folders[index]
            folderManager.saveFolder(
                folder.copy(parentId = originalFolder.parentId?.let(importedFolderIds::get))
            )
        }

        return importedFolderIds
    }

    /**
     * 确保工作流元数据字段有默认值
     */
    private fun applyWorkflowDefaults(wf: Workflow): Workflow {
        return wf.copy(
            id = wf.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = wf.name?.takeIf { it.isNotBlank() } ?: "未命名工作流",
            version = wf.version?.takeIf { it.isNotEmpty() } ?: "1.0.0",
            vFlowLevel = if (wf.vFlowLevel == 0) 1 else wf.vFlowLevel,
            description = wf.description ?: "",
            author = wf.author ?: "",
            homepage = wf.homepage ?: "",
            tags = wf.tags ?: emptyList(),
            modifiedAt = if (wf.modifiedAt == 0L) System.currentTimeMillis() else wf.modifiedAt,
            folderId = wf.folderId?.takeIf { it.isNotBlank() }
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

    private fun generateUniqueFolderName(baseName: String, existingNames: MutableSet<String>): String {
        if (baseName !in existingNames) {
            return baseName
        }

        var index = 1
        var candidate = "$baseName (导入)"
        while (candidate in existingNames) {
            index++
            candidate = "$baseName (导入$index)"
        }
        return candidate
    }
}
