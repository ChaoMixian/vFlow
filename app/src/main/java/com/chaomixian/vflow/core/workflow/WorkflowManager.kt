// 文件: main/java/com/chaomixian/vflow/core/workflow/WorkflowManager.kt
// 描述: 管理工作流的持久化，并通过 TriggerServiceProxy 直接通知服务层。

package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.serialization.VObjectGsonAdapter
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ReceiveShareTriggerModule
import com.chaomixian.vflow.services.TriggerServiceProxy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.util.*

class WorkflowManager(val context: Context) {
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(VObject::class.java, VObjectGsonAdapter())
        .create()

    /**
     * 保存单个工作流。
     */
    fun saveWorkflow(workflow: Workflow) {
        val workflows = getAllWorkflows().toMutableList()
        val index = workflows.indexOfFirst { it.id == workflow.id }
        val oldWorkflow = if (index != -1) workflows[index] else null

        // 重新计算 triggerConfig
        val firstStep = workflow.steps.firstOrNull()
        var config: Map<String, Any?>? = null
        if (firstStep != null && firstStep.moduleId != ManualTriggerModule().id) {
            config = firstStep.parameters + ("type" to firstStep.moduleId)
        }

        // 确保元数据字段有默认值
        val workflowToSave = workflow.copy(
            triggerConfig = config,
            modifiedAt = System.currentTimeMillis(),
            version = workflow.version?.takeIf { it.isNotEmpty() } ?: "1.0.0",
            vFlowLevel = if (workflow.vFlowLevel == 0) 1 else workflow.vFlowLevel,
            description = workflow.description ?: "",
            author = workflow.author ?: "",
            homepage = workflow.homepage ?: "",
            tags = workflow.tags ?: emptyList(),
            maxExecutionTime = workflow.maxExecutionTime
        )

        if (index != -1) {
            workflows[index] = workflowToSave
        } else {
            workflows.add(workflowToSave)
        }

        // 保存到磁盘
        val json = gson.toJson(workflows)
        prefs.edit().putString("workflow_list", json).apply()

        // 直接通过代理精确通知 TriggerService
        TriggerServiceProxy.notifyWorkflowChanged(context, workflowToSave, oldWorkflow)
    }

    fun findShareableWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.triggerConfig?.get("type") == ReceiveShareTriggerModule().id
        }
    }

    fun findAppStartTriggerWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.triggerConfig?.get("type") == AppStartTriggerModule().id
        }
    }

    fun findKeyEventTriggerWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.triggerConfig?.get("type") == KeyEventTriggerModule().id
        }
    }

    /**
     * 根据ID删除一个工作流。
     */
    fun deleteWorkflow(id: String) {
        val workflows = getAllWorkflows().toMutableList()
        val workflowToRemove = workflows.find { it.id == id }
        if (workflowToRemove != null) {
            workflows.remove(workflowToRemove)
            val json = gson.toJson(workflows)
            prefs.edit().putString("workflow_list", json).apply()

            // 通知服务工作流已被删除
            TriggerServiceProxy.notifyWorkflowRemoved(context, workflowToRemove)
        }
    }

    fun getWorkflow(id: String): Workflow? {
        return getAllWorkflows().find { it.id == id }
    }

    fun getAllWorkflows(): List<Workflow> {
        val json = prefs.getString("workflow_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<Workflow>>() {}.type
            try {
                val workflows: List<Workflow> = gson.fromJson(json, type) ?: emptyList()
                // 确保所有工作流的元数据字段都有默认值
                workflows.map { wf ->
                    if (wf.version.isNullOrEmpty() || wf.description.isNullOrEmpty() || wf.author.isNullOrEmpty() ||
                        wf.homepage.isNullOrEmpty() || wf.tags.isNullOrEmpty() || wf.vFlowLevel == 0) {
                        wf.copy(
                            version = wf.version?.takeIf { it.isNotEmpty() } ?: "1.0.0",
                            vFlowLevel = if (wf.vFlowLevel == 0) 1 else wf.vFlowLevel,
                            description = wf.description ?: "",
                            author = wf.author ?: "",
                            homepage = wf.homepage ?: "",
                            tags = wf.tags ?: emptyList(),
                            modifiedAt = if (wf.modifiedAt == 0L) System.currentTimeMillis() else wf.modifiedAt,
                            maxExecutionTime = wf.maxExecutionTime
                        )
                    } else {
                        wf
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun clearAllWorkflows() {
        prefs.edit().remove("workflow_list").apply()
    }

    fun duplicateWorkflow(id: String) {
        val original = getWorkflow(id) ?: return
        val newName = "${original.name} (副本)"
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            isEnabled = false // 复制的工作流默认为禁用状态
        )
        saveWorkflow(newWorkflow)
    }

    /**
     * 将所有工作流保存到 SharedPreferences (主要用于拖拽排序后)。
     * 注意：保留不在新列表中的工作流（如文件夹中的工作流）。
     */
    fun saveAllWorkflows(newWorkflows: List<Workflow>) {
        // 先获取现有数据
        val existingWorkflows = getAllWorkflows().associateBy { it.id }
        val newWorkflowIds = newWorkflows.map { it.id }.toSet()

        // 合并数据：使用新列表中的工作流，但保留现有数据中不在新列表中的工作流（文件夹中的）
        val mergedWorkflows = newWorkflows.map { newWf ->
            val existing = existingWorkflows[newWf.id]
            if (existing != null) {
                // 保留 folderId 等字段，只更新排序相关字段
                newWf.copy(folderId = existing.folderId)
            } else {
                newWf
            }
        } + existingWorkflows.values.filter { it.id !in newWorkflowIds }

        val json = gson.toJson(mergedWorkflows)
        prefs.edit().putString("workflow_list", json).apply()
        // 排序操作不应触发重新加载，所以这里不通知服务
    }
}