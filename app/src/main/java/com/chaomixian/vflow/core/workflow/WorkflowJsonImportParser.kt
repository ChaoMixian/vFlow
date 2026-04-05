package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowFolder
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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
        val listType = object : TypeToken<List<Workflow>>() {}.type
        val workflows = gson.fromJson<List<Workflow>>(rawValue, listType).orEmpty()
        return workflows.map(::sanitizeWorkflow)
    }

    private fun parseFolderList(rawValue: JsonElement?): List<WorkflowFolder> {
        val listType = object : TypeToken<List<WorkflowFolder>>() {}.type
        return gson.fromJson<List<WorkflowFolder>>(rawValue, listType).orEmpty()
    }

    private fun parseWorkflow(rawValue: JsonElement): Workflow {
        val workflow = gson.fromJson(rawValue, Workflow::class.java)
        return sanitizeWorkflow(workflow)
    }

    private fun parseFolder(rawValue: JsonElement): WorkflowFolder {
        return gson.fromJson(rawValue, WorkflowFolder::class.java)
    }

    private fun sanitizeWorkflow(workflow: Workflow): Workflow {
        return workflow.copy(
            id = workflow.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = workflow.name?.takeIf { it.isNotBlank() } ?: "未命名工作流",
            triggers = workflow.triggers ?: emptyList(),
            steps = workflow.steps ?: emptyList(),
            folderId = workflow.folderId?.takeIf { it.isNotBlank() },
            modifiedAt = workflow.modifiedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            version = workflow.version?.takeIf { it.isNotBlank() } ?: "1.0.0",
            vFlowLevel = workflow.vFlowLevel.takeIf { it > 0 } ?: 1,
            description = workflow.description ?: "",
            author = workflow.author ?: "",
            homepage = workflow.homepage ?: "",
            tags = workflow.tags ?: emptyList()
        )
    }
}
