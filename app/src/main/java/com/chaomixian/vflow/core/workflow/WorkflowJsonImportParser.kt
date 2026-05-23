package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.chaomixian.vflow.core.workflow.model.WorkflowReentryBehavior
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

class WorkflowJsonImportParser(
    private val gson: Gson = Gson()
) {
    data class ParsedImport(
        val workflows: List<Workflow>,
        val folders: List<WorkflowFolder> = emptyList()
    )

    fun parse(jsonString: String): ParsedImport {
        val root = JsonParser.parseString(jsonString)
        return when {
            root.isJsonArray -> ParsedImport(
                workflows = parseWorkflowList(root)
            )
            root.isJsonObject -> parseObject(root.asJsonObject)
            else -> ParsedImport(emptyList())
        }
    }

    private fun parseObject(data: JsonObject): ParsedImport {
        return when {
            data.has("folders") && data.has("workflows") -> ParsedImport(
                workflows = parseWorkflowList(data.get("workflows")),
                folders = parseFolderList(data.get("folders"))
            )
            data.has("folder") && data.has("workflows") -> ParsedImport(
                workflows = parseWorkflowList(data.get("workflows")),
                folders = listOf(parseFolder(data.get("folder")))
            )
            data.has("workflows") -> ParsedImport(
                workflows = parseWorkflowList(data.get("workflows"))
            )
            else -> ParsedImport(
                workflows = listOf(parseWorkflow(data))
            )
        }
    }

    private fun parseWorkflowList(rawValue: JsonElement?): List<Workflow> {
        if (rawValue == null || !rawValue.isJsonArray) {
            return emptyList()
        }
        return rawValue.asJsonArray.mapNotNull { item ->
            item.asJsonObjectOrNull()?.let(::parseWorkflowObject)
        }
    }

    private fun parseFolderList(rawValue: JsonElement?): List<WorkflowFolder> {
        if (rawValue == null || !rawValue.isJsonArray) {
            return emptyList()
        }
        return rawValue.asJsonArray.mapNotNull { item ->
            item.asJsonObjectOrNull()?.let(::parseFolderObject)
        }
    }

    private fun parseWorkflow(rawValue: JsonElement): Workflow {
        val workflowObject = rawValue.asJsonObjectOrNull()
            ?: return sanitizeWorkflow(Workflow(id = UUID.randomUUID().toString(), name = "未命名工作流"))
        return parseWorkflowObject(workflowObject)
    }

    private fun parseFolder(rawValue: JsonElement): WorkflowFolder {
        val folderObject = rawValue.asJsonObjectOrNull()
            ?: return WorkflowFolder(name = "")
        return parseFolderObject(folderObject)
    }

    private fun parseWorkflowObject(data: JsonObject): Workflow {
        val meta = data.getObject("_meta")
        val legacyTriggerConfigs = buildList {
            data.getMapList("triggerConfigs")?.let { addAll(it) }
            data.getMap("triggerConfig")?.let { add(it) }
        }
        val normalizedContent = WorkflowNormalizer.normalize(
            triggers = data.getActionSteps("triggers"),
            steps = data.getActionSteps("steps"),
            legacyTriggerConfigs = legacyTriggerConfigs
        )

        val workflow = Workflow(
            id = data.getString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = data.getString("name")
                ?.takeIf { it.isNotBlank() }
                ?: meta?.getString("name")
                ?.takeIf { it.isNotBlank() }
                ?: "未命名工作流",
            triggers = normalizedContent.triggers,
            steps = normalizedContent.steps,
            isEnabled = data.getBoolean("isEnabled") ?: true,
            isFavorite = data.getBoolean("isFavorite") ?: false,
            wasEnabledBeforePermissionsLost = data.getBoolean("wasEnabledBeforePermissionsLost") ?: false,
            folderId = data.getString("folderId"),
            order = data.getInt("order") ?: 0,
            shortcutName = data.getString("shortcutName"),
            shortcutIconRes = data.getString("shortcutIconRes"),
            cardIconRes = WorkflowVisuals.normalizeIconResName(data.getString("cardIconRes")),
            cardThemeColor = WorkflowVisuals.normalizeThemeColorHex(data.getString("cardThemeColor")),
            modifiedAt = data.getLong("modifiedAt")?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            version = data.getString("version")
                ?.takeIf { it.isNotBlank() }
                ?: meta?.getString("version")
                ?.takeIf { it.isNotBlank() }
                ?: "1.0.0",
            vFlowLevel = data.getInt("vFlowLevel")
                ?.takeIf { it > 0 }
                ?: meta?.getInt("vFlowLevel")
                ?.takeIf { it > 0 }
                ?: 1,
            description = data.getString("description") ?: meta?.getString("description") ?: "",
            author = data.getString("author") ?: meta?.getString("author") ?: "",
            homepage = data.getString("homepage") ?: meta?.getString("homepage") ?: "",
            tags = data.getStringList("tags") ?: meta?.getStringList("tags") ?: emptyList(),
            maxExecutionTime = data.getInt("maxExecutionTime"),
            reentryBehavior = WorkflowReentryBehavior.fromStoredValue(data.getString("reentryBehavior"))
        )
        return sanitizeWorkflow(workflow)
    }

    private fun parseFolderObject(data: JsonObject): WorkflowFolder {
        return WorkflowFolder(
            id = data.getString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = data.getString("name") ?: "",
            parentId = data.getString("parentId"),
            order = data.getInt("order") ?: 0,
            createdAt = data.getLong("createdAt")?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            modifiedAt = data.getLong("modifiedAt")?.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    private fun sanitizeWorkflow(workflow: Workflow): Workflow {
        val description: String? = workflow.description
        val author: String? = workflow.author
        val homepage: String? = workflow.homepage
        val tags: List<String>? = workflow.tags
        val triggers: List<com.chaomixian.vflow.core.workflow.model.ActionStep>? = workflow.triggers
        val steps: List<com.chaomixian.vflow.core.workflow.model.ActionStep>? = workflow.steps
        val reentryBehavior: WorkflowReentryBehavior? = workflow.reentryBehavior

        return workflow.copy(
            id = workflow.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = workflow.name.takeIf { it.isNotBlank() } ?: "未命名工作流",
            triggers = triggers ?: emptyList(),
            steps = steps ?: emptyList(),
            folderId = workflow.folderId?.takeIf { it.isNotBlank() },
            cardIconRes = WorkflowVisuals.normalizeIconResName(workflow.cardIconRes),
            cardThemeColor = WorkflowVisuals.normalizeThemeColorHex(workflow.cardThemeColor),
            modifiedAt = workflow.modifiedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            version = workflow.version.takeIf { it.isNotBlank() } ?: "1.0.0",
            vFlowLevel = workflow.vFlowLevel.takeIf { it > 0 } ?: 1,
            description = description?.takeIf { it.isNotBlank() } ?: "",
            author = author?.takeIf { it.isNotBlank() } ?: "",
            homepage = homepage?.takeIf { it.isNotBlank() } ?: "",
            tags = tags ?: emptyList(),
            reentryBehavior = WorkflowReentryBehavior.fromStoredValue(
                reentryBehavior?.storedValue
            )
        )
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.getObject(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
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

    private fun JsonObject.getActionSteps(name: String): List<com.chaomixian.vflow.core.workflow.model.ActionStep>? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val moduleId = obj.getString("moduleId") ?: return@mapNotNull null
            val parameters = obj.getMap("parameters") ?: emptyMap()
            com.chaomixian.vflow.core.workflow.model.ActionStep(
                moduleId = moduleId,
                parameters = parameters,
                isDisabled = obj.getBoolean("isDisabled") ?: false,
                indentationLevel = obj.getInt("indentationLevel") ?: 0,
                id = obj.getString("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            )
        }
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
