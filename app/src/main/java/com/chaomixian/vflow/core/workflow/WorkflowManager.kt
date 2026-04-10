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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.UUID

internal fun normalizeJsonElementValue(element: JsonElement?): Any? {
    if (element == null || element.isJsonNull) return null

    return when {
        element.isJsonObject -> {
            LinkedHashMap<String, Any?>().apply {
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    put(key, normalizeJsonElementValue(value))
                }
            }
        }
        element.isJsonArray -> element.asJsonArray.map { item ->
            normalizeJsonElementValue(item)
        }
        element.isJsonPrimitive -> {
            val primitive = element.asJsonPrimitive
            when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asNumber
                primitive.isString -> primitive.asString
                else -> null
            }
        }
        else -> null
    }
}

internal fun normalizedObjectMap(value: Any?): Map<String, Any?>? {
    val map = value as? Map<*, *> ?: return null
    return LinkedHashMap<String, Any?>().apply {
        map.forEach { (key, nestedValue) ->
            key?.toString()?.let { put(it, nestedValue) }
        }
    }
}

internal fun normalizedObjectMapList(value: Any?): List<Map<String, Any?>>? {
    val list = value as? List<*> ?: return null
    return list.mapNotNull(::normalizedObjectMap)
}

class WorkflowManager(val context: Context) {
    private val prefs = context.getSharedPreferences("vflow_workflows", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(VObject::class.java, VObjectGsonAdapter())
        .create()

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
        val record = element.asJsonObjectOrNull()
            ?: throw IllegalStateException("Workflow record is not a JSON object")
        val legacyTriggerConfigs = buildList {
            record.getMapList("triggerConfigs")?.let { addAll(it) }
            record.getMap("triggerConfig")?.let { add(it) }
        }
        val normalizedContent = WorkflowNormalizer.normalize(
            triggers = record.getActionSteps("triggers"),
            steps = record.getActionSteps("steps"),
            legacyTriggerConfigs = legacyTriggerConfigs
        )

        return Workflow(
            id = record.getString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = record.getString("name")?.takeIf { it.isNotBlank() } ?: "未命名工作流",
            triggers = normalizedContent.triggers,
            steps = normalizedContent.steps,
            isEnabled = record.getBoolean("isEnabled") ?: true,
            isFavorite = record.getBoolean("isFavorite") ?: false,
            wasEnabledBeforePermissionsLost = record.getBoolean("wasEnabledBeforePermissionsLost") ?: false,
            folderId = record.getString("folderId"),
            order = record.getInt("order") ?: 0,
            shortcutName = record.getString("shortcutName"),
            shortcutIconRes = record.getString("shortcutIconRes"),
            modifiedAt = record.getLong("modifiedAt")?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            version = record.getString("version")?.takeIf { it.isNotBlank() } ?: "1.0.0",
            vFlowLevel = record.getInt("vFlowLevel")?.takeIf { it > 0 } ?: 1,
            description = record.getString("description") ?: "",
            author = record.getString("author") ?: "",
            homepage = record.getString("homepage") ?: "",
            tags = record.getStringList("tags") ?: emptyList(),
            maxExecutionTime = record.getInt("maxExecutionTime")
        )
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.getString(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString
    }

    private fun JsonObject.getBoolean(name: String): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asBoolean }.getOrNull()
    }

    private fun JsonObject.getInt(name: String): Int? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.getLong(name: String): Long? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asLong }.getOrNull()
    }

    private fun JsonObject.getStringList(name: String): List<String>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray.mapNotNull { item ->
            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) item.asString else null
        }
    }

    private fun JsonObject.getActionSteps(name: String): List<ActionStep>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) return null
        val type = object : TypeToken<List<ActionStep>>() {}.type
        return gson.fromJson<List<ActionStep>>(element, type)
    }

    private fun JsonObject.getMap(name: String): Map<String, Any?>? {
        val element = get(name) ?: return null
        if (!element.isJsonObject) return null
        return normalizedObjectMap(normalizeJsonElementValue(element))
    }

    private fun JsonObject.getMapList(name: String): List<Map<String, Any?>>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) return null
        return normalizedObjectMapList(normalizeJsonElementValue(element))
    }
}
