package com.chaomixian.vflow.core.workflow

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ReceiveShareTriggerModule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 文件：WorkflowManager.kt
// 描述：管理工作流的持久化存储和检索。

/**
 * 工作流管理器。
 */
class WorkflowManager(private val context: Context) { // [修改] 将 context 设为私有属性
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    private val gson = Gson()

    // [新增] 定义一个广播动作常量，用于通知服务数据已更新
    companion object {
        const val ACTION_WORKFLOWS_UPDATED = "com.chaomixian.vflow.WORKFLOWS_UPDATED"
    }

    /**
     * 保存单个工作流。
     */
    fun saveWorkflow(workflow: Workflow) {
        val workflows = getAllWorkflows().toMutableList()
        val index = workflows.indexOfFirst { it.id == workflow.id }

        val firstStep = workflow.steps.firstOrNull()
        var config: Map<String, Any?>? = null
        if (firstStep != null && firstStep.moduleId != ManualTriggerModule().id) {
            config = firstStep.parameters + ("type" to firstStep.moduleId)
        }
        val workflowToSave = workflow.copy(triggerConfig = config)

        if (index != -1) {
            workflows[index] = workflowToSave
        } else {
            workflows.add(workflowToSave)
        }
        saveAllWorkflows(workflows)
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
        val workflows = getAllWorkflows().filter { it.id != id }
        saveAllWorkflows(workflows)
    }

    fun getWorkflow(id: String): Workflow? {
        return getAllWorkflows().find { it.id == id }
    }

    fun getAllWorkflows(): List<Workflow> {
        val json = prefs.getString("workflow_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<Workflow>>() {}.type
            try {
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun duplicateWorkflow(id: String) {
        val original = getWorkflow(id) ?: return
        val newName = "${original.name} (副本)"
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(),
            name = newName
        )
        saveWorkflow(newWorkflow)
    }

    /**
     * 将所有工作流保存到 SharedPreferences，并发送通知。
     */
    fun saveAllWorkflows(workflows: List<Workflow>) {
        val json = gson.toJson(workflows)
        prefs.edit().putString("workflow_list", json).apply()
        // [新增] 在保存后，立即发送一个本地广播
        notifyWorkflowsUpdated()
    }

    /**
     * [新增] 发送“工作流已更新”广播的辅助方法。
     */
    private fun notifyWorkflowsUpdated() {
        val intent = Intent(ACTION_WORKFLOWS_UPDATED)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}