package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 文件：WorkflowManager.kt
// 描述：管理工作流的持久化存储和检索。

/**
 * 工作流管理器。
 * 负责工作流的保存、删除、检索和复制等操作。
 * 使用 SharedPreferences 和 Gson 进行数据持久化。
 */
class WorkflowManager(context: Context) {
    // SharedPreferences实例，用于存储工作流数据
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    // Gson实例，用于JSON序列化和反序列化
    private val gson = Gson()

    /**
     * 保存单个工作流。
     * 如果工作流已存在（ID相同），则更新；否则添加为新的工作流。
     * @param workflow 要保存的工作流对象。
     */
    fun saveWorkflow(workflow: Workflow) {
        val workflows = getAllWorkflows().toMutableList()
        val index = workflows.indexOfFirst { it.id == workflow.id } // 查找现有工作流的索引
        if (index != -1) {
            workflows[index] = workflow // 更新现有工作流
        } else {
            workflows.add(workflow) // 添加新工作流
        }
        saveAll(workflows) // 保存所有工作流到持久化存储
    }

    /**
     * 根据ID删除一个工作流。
     * @param id 要删除的工作流的ID。
     */
    fun deleteWorkflow(id: String) {
        val workflows = getAllWorkflows().filter { it.id != id } // 过滤掉指定ID的工作流
        saveAll(workflows)
    }

    /**
     * 根据ID检索一个工作流。
     * @param id 要检索的工作流的ID。
     * @return 找到的工作流对象，如果不存在则返回 null。
     */
    fun getWorkflow(id: String): Workflow? {
        return getAllWorkflows().find { it.id == id } // 查找具有匹配ID的工作流
    }

    /**
     * 检索所有已保存的工作流。
     * @return 包含所有工作流的列表；如果没有任何工作流，则返回空列表。
     */
    fun getAllWorkflows(): List<Workflow> {
        val json = prefs.getString("workflow_list", null) // 从SharedPreferences读取JSON字符串
        return if (json != null) {
            val type = object : TypeToken<List<Workflow>>() {}.type // 定义列表类型
            gson.fromJson(json, type) ?: emptyList() // JSON反序列化，失败则返回空列表
        } else {
            emptyList() // 没有数据则返回空列表
        }
    }

    /**
     * 复制一个现有的工作流。
     * 新的工作流将使用新的ID，并在名称后附加"(副本)"。
     * @param id 要复制的原始工作流的ID。
     */
    fun duplicateWorkflow(id: String) {
        val original = getWorkflow(id) ?: return // 获取原始工作流，不存在则直接返回
        val newName = "${original.name} (副本)" // 为复制的工作流生成新名称

        // 创建一个新的工作流副本，使用新的UUID和名称
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(), // 生成新的唯一ID
            name = newName
        )

        saveWorkflow(newWorkflow) // 保存复制后的工作流
    }

    /**
     * 将所有工作流保存到 SharedPreferences。
     * @param workflows 要保存的工作流列表。
     */
    private fun saveAll(workflows: List<Workflow>) {
        val json = gson.toJson(workflows) // 将工作流列表序列化为JSON字符串
        prefs.edit().putString("workflow_list", json).apply() // 保存JSON字符串
    }
}