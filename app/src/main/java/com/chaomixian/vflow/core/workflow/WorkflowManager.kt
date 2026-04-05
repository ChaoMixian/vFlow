package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.serialization.VObjectGsonAdapter
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ReceiveShareTriggerModule
import com.chaomixian.vflow.services.TriggerServiceProxy
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.UUID

class WorkflowManager(val context: Context) {
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(VObject::class.java, VObjectGsonAdapter())
        .create()

    private data class WorkflowStorageRecord(
        val id: String? = null,
        val name: String? = null,
        val triggers: List<ActionStep>? = null,
        val steps: List<ActionStep>? = null,
        val isEnabled: Boolean? = null,
        val isFavorite: Boolean? = null,
        val wasEnabledBeforePermissionsLost: Boolean? = null,
        val folderId: String? = null,
        val order: Int? = null,
        val shortcutName: String? = null,
        val shortcutIconRes: String? = null,
        val modifiedAt: Long? = null,
        val version: String? = null,
        val vFlowLevel: Int? = null,
        val description: String? = null,
        val author: String? = null,
        val homepage: String? = null,
        val tags: List<String>? = null,
        val maxExecutionTime: Int? = null,
        val triggerConfig: Map<String, Any?>? = null,
        val triggerConfigs: List<Map<String, Any?>>? = null
    )

    fun saveWorkflow(workflow: Workflow) {
        val workflows = getAllWorkflows().toMutableList()
        val index = workflows.indexOfFirst { it.id == workflow.id }
        val oldWorkflow = if (index != -1) workflows[index] else null
        val normalizedWorkflow = normalizeWorkflow(workflow)

        val workflowToSave = normalizedWorkflow.copy(
            modifiedAt = System.currentTimeMillis(),
            version = normalizedWorkflow.version.ifBlank { "1.0.0" },
            vFlowLevel = normalizedWorkflow.vFlowLevel.takeIf { it > 0 } ?: 1,
            description = normalizedWorkflow.description,
            author = normalizedWorkflow.author,
            homepage = normalizedWorkflow.homepage,
            tags = normalizedWorkflow.tags,
            triggers = normalizedWorkflow.triggers,
            steps = normalizedWorkflow.steps,
            maxExecutionTime = normalizedWorkflow.maxExecutionTime
        )

        if (index != -1) {
            workflows[index] = workflowToSave
        } else {
            workflows.add(workflowToSave)
        }

        prefs.edit().putString("workflow_list", gson.toJson(workflows)).apply()
        TriggerServiceProxy.notifyWorkflowChanged(context, workflowToSave, oldWorkflow)
    }

    fun findShareableWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.hasTriggerType(ReceiveShareTriggerModule().id)
        }
    }

    fun findAppStartTriggerWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.hasTriggerType(AppStartTriggerModule().id)
        }
    }

    fun findKeyEventTriggerWorkflows(): List<Workflow> {
        return getAllWorkflows().filter {
            it.isEnabled && it.hasTriggerType(KeyEventTriggerModule().id)
        }
    }

    fun deleteWorkflow(id: String) {
        val workflows = getAllWorkflows().toMutableList()
        val workflowToRemove = workflows.find { it.id == id }
        if (workflowToRemove != null) {
            workflows.remove(workflowToRemove)
            prefs.edit().putString("workflow_list", gson.toJson(workflows)).apply()
            TriggerServiceProxy.notifyWorkflowRemoved(context, workflowToRemove)
        }
    }

    fun getWorkflow(id: String): Workflow? {
        return getAllWorkflows().find { it.id == id }
    }

    fun getAllWorkflows(): List<Workflow> {
        val json = prefs.getString("workflow_list", null) ?: return emptyList()
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonArray) {
                DebugLogger.w("WorkflowManager", "workflow_list is not a JSON array")
                return emptyList()
            }

            var skippedCount = 0
            val workflows = root.asJsonArray.mapIndexedNotNull { index, element ->
                try {
                    parseWorkflowRecord(element)
                } catch (e: Exception) {
                    skippedCount++
                    DebugLogger.w("WorkflowManager", "Failed to parse workflow record at index $index", e)
                    null
                }
            }

            if (skippedCount > 0) {
                DebugLogger.w("WorkflowManager", "Skipped $skippedCount invalid workflow record(s) while loading")
            }

            workflows
        } catch (e: Exception) {
            DebugLogger.e("WorkflowManager", "Failed to load workflow_list", e)
            emptyList()
        }
    }

    fun clearAllWorkflows() {
        prefs.edit().remove("workflow_list").apply()
    }

    fun duplicateWorkflow(id: String) {
        val original = getWorkflow(id) ?: return
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (副本)",
            isEnabled = false
        )
        saveWorkflow(newWorkflow)
    }

    fun saveAllWorkflows(newWorkflows: List<Workflow>) {
        val existingWorkflows = getAllWorkflows().associateBy { it.id }
        val normalizedNewWorkflows = newWorkflows.map(::normalizeWorkflow)
        val newWorkflowIds = normalizedNewWorkflows.map { it.id }.toSet()

        val mergedWorkflows = normalizedNewWorkflows.map { newWorkflow ->
            val existing = existingWorkflows[newWorkflow.id]
            if (existing != null) {
                newWorkflow.copy(folderId = existing.folderId)
            } else {
                newWorkflow
            }
        } + existingWorkflows.values.filter { it.id !in newWorkflowIds }

        prefs.edit().putString("workflow_list", gson.toJson(mergedWorkflows)).apply()
    }

    private fun normalizeWorkflow(workflow: Workflow): Workflow {
        val normalizedContent = WorkflowNormalizer.normalize(
            triggers = workflow.triggers,
            steps = workflow.steps
        )

        return workflow.copy(
            triggers = normalizedContent.triggers,
            steps = normalizedContent.steps
        )
    }

    private fun parseWorkflowRecord(element: JsonElement): Workflow {
        val record = gson.fromJson(element, WorkflowStorageRecord::class.java)
        return record?.toWorkflow() ?: throw IllegalStateException("Workflow record is null")
    }

    private fun WorkflowStorageRecord.toWorkflow(): Workflow {
        val legacyTriggerConfigs = buildList {
            triggerConfigs?.let { addAll(it) }
            triggerConfig?.let { add(it) }
        }
        val normalizedContent = WorkflowNormalizer.normalize(
            triggers = triggers,
            steps = steps,
            legacyTriggerConfigs = legacyTriggerConfigs
        )

        return Workflow(
            id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() } ?: "未命名工作流",
            triggers = normalizedContent.triggers,
            steps = normalizedContent.steps,
            isEnabled = isEnabled ?: true,
            isFavorite = isFavorite ?: false,
            wasEnabledBeforePermissionsLost = wasEnabledBeforePermissionsLost ?: false,
            folderId = folderId,
            order = order ?: 0,
            shortcutName = shortcutName,
            shortcutIconRes = shortcutIconRes,
            modifiedAt = modifiedAt?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            version = version?.takeIf { it.isNotBlank() } ?: "1.0.0",
            vFlowLevel = vFlowLevel?.takeIf { it > 0 } ?: 1,
            description = description ?: "",
            author = author ?: "",
            homepage = homepage ?: "",
            tags = tags ?: emptyList(),
            maxExecutionTime = maxExecutionTime
        )
    }
}
