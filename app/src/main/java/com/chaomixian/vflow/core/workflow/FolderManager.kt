package com.chaomixian.vflow.core.workflow

import android.content.Context
import android.content.SharedPreferences
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class FolderManager(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vflow_folders", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * 保存或更新文件夹
     */
    fun saveFolder(folder: WorkflowFolder) {
        val folders = getAllFolders().toMutableList()
        val index = folders.indexOfFirst { it.id == folder.id }
        if (index != -1) {
            folders[index] = folder
        } else {
            folders.add(folder)
        }
        saveAllFolders(folders)
    }

    /**
     * 获取所有文件夹
     */
    fun getAllFolders(): List<WorkflowFolder> {
        val json = prefs.getString("folder_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WorkflowFolder>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 根据 ID 获取文件夹
     */
    fun getFolder(id: String): WorkflowFolder? {
        return getAllFolders().find { it.id == id }
    }

    /**
     * 删除文件夹
     */
    fun deleteFolder(id: String) {
        val folders = getAllFolders().toMutableList()
        folders.removeAll { it.id == id }
        saveAllFolders(folders)
    }

    /**
     * 保存所有文件夹
     */
    private fun saveAllFolders(folders: List<WorkflowFolder>) {
        val json = gson.toJson(folders)
        prefs.edit().putString("folder_list", json).apply()
    }

    /**
     * 获取文件夹内的工作流数量
     */
    fun getWorkflowCountInFolder(folderId: String, workflowManager: WorkflowManager): Int {
        return workflowManager.getAllWorkflows().count { it.folderId == folderId }
    }
}
