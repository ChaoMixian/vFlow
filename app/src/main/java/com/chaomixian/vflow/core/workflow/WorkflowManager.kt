package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class WorkflowManager(context: Context) {
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveWorkflow(workflow: Workflow) {
        val workflows = getAllWorkflows().toMutableList()
        val index = workflows.indexOfFirst { it.id == workflow.id }
        if (index != -1) {
            workflows[index] = workflow
        } else {
            workflows.add(workflow)
        }
        saveAll(workflows)
    }

    fun deleteWorkflow(id: String) {
        val workflows = getAllWorkflows().filter { it.id != id }
        saveAll(workflows)
    }

    fun getWorkflow(id: String): Workflow? {
        return getAllWorkflows().find { it.id == id }
    }

    fun getAllWorkflows(): List<Workflow> {
        val json = prefs.getString("workflow_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<Workflow>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun duplicateWorkflow(id: String) {
        val original = getWorkflow(id) ?: return
        val newName = "${original.name} (副本)"

        // 创建一个全新的工作流对象，使用新的ID
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(),
            name = newName
        )

        saveWorkflow(newWorkflow)
    }

    private fun saveAll(workflows: List<Workflow>) {
        val json = gson.toJson(workflows)
        prefs.edit().putString("workflow_list", json).apply()
    }
}