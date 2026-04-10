package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.chaomixian.vflow.core.workflow.WorkflowNormalizer
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.util.UUID

internal fun parseImportedActionStep(stepMap: Map<*, *>, errorLabel: String): ActionStep {
    val moduleId = stepMap["moduleId"] as? String
        ?: throw IllegalArgumentException("Invalid $errorLabel format: missing moduleId")
    val rawParameters = stepMap["parameters"]
    val parameters = when (rawParameters) {
        null -> emptyMap()
        is Map<*, *> -> normalizeImportedJsonObject(rawParameters)
        else -> throw IllegalArgumentException("Invalid $errorLabel format: parameters must be an object")
    }

    return ActionStep(
        moduleId = moduleId,
        parameters = parameters,
        indentationLevel = (stepMap["indentationLevel"] as? Number)?.toInt() ?: 0,
        id = stepMap["id"] as? String ?: UUID.randomUUID().toString()
    )
}

/**
 * 导入导出Handler
 */
class ImportExportHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        return when {
            // 批量导出
            uri == "/api/v1/workflows/export-batch" && method == NanoHTTPD.Method.POST -> {
                handleBatchExport(session, tokenInfo)
            }
            // 批量导入
            uri == "/api/v1/workflows/import-batch" && method == NanoHTTPD.Method.POST -> {
                handleBatchImport(session, tokenInfo)
            }
            // 导入单个工作流
            uri == "/api/v1/workflows/import" && method == NanoHTTPD.Method.POST -> {
                handleImportWorkflow(session, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    private fun handleBatchExport(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val request = parseRequestBody(session, BatchExportRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        // 导出指定的工作流
        val workflows = deps.workflowManager.getAllWorkflows()
            .filter { request.workflowIds.contains(it.id) }
            .map { wf ->
                mapOf(
                    "workflowId" to wf.id,
                    "name" to wf.name,
                    "workflow" to wf
                )
            }

        return successResponse(mapOf(
            "workflows" to workflows,
            "exportedAt" to System.currentTimeMillis(),
            "format" to (request.format ?: "json")
        ))
    }

    private fun handleBatchImport(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        // TODO: 处理文件上传
        return successResponse(BatchImportResponse(
            imported = emptyList(),
            skipped = emptyList(),
            errors = emptyList(),
            total = 0,
            importedCount = 0,
            skippedCount = 0,
            errorCount = 0
        ))
    }

    private fun handleImportWorkflow(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val request = parseRequestBody(session, ImportWorkflowDataRequest::class.java)

        if (request == null || request.workflow == null) {
            return errorResponse(400, "Invalid request body, workflow data required")
        }

        try {
            val workflowData = request.workflow
            val newId = UUID.randomUUID().toString()

            // 构建步骤列表
            val triggers: List<ActionStep> = workflowData.triggers?.map { stepMap ->
                parseImportedActionStep(stepMap, "trigger")
            } ?: emptyList()

            val steps: List<ActionStep> = workflowData.steps?.map { stepMap ->
                parseImportedActionStep(stepMap, "step")
            } ?: emptyList()

            val normalizedContent = WorkflowNormalizer.normalize(
                triggers = triggers,
                steps = steps,
                legacyTriggerConfigs = buildList {
                    workflowData.triggerConfigs?.let { addAll(it) }
                    workflowData.triggerConfig?.let { add(it) }
                }
            )

            // 创建新工作流
            val newWorkflow = Workflow(
                id = newId,
                name = workflowData.name ?: "Imported Workflow",
                description = workflowData.description ?: "",
                triggers = normalizedContent.triggers,
                steps = normalizedContent.steps,
                isEnabled = false,
                isFavorite = workflowData.isFavorite ?: false,
                folderId = request.folderId,
                order = 0,
                tags = workflowData.tags ?: emptyList(),
                version = workflowData.version ?: "1.0.0",
                modifiedAt = System.currentTimeMillis()
            )

            deps.workflowManager.saveWorkflow(newWorkflow)

            return successResponse(ImportWorkflowResponse(
                imported = listOf(ImportedWorkflow(newId, newWorkflow.name)),
                skipped = emptyList(),
                errors = emptyList(),
                total = 1
            ))
        } catch (e: Exception) {
            return errorResponse(8001, "Invalid file format: ${e.message}")
        }
    }
}

/**
 * 批量导出请求
 */
data class BatchExportRequest(
    val workflowIds: List<String>,
    val format: String? = "json",
    val includeSteps: Boolean? = true
)

/**
 * 导入工作流数据请求
 */
data class ImportWorkflowDataRequest(
    val workflow: WorkflowImportData?,
    val override: Boolean? = false,
    val folderId: String? = null
)

/**
 * 工作流导入数据
 */
data class WorkflowImportData(
    val name: String?,
    val description: String?,
    val triggers: List<Map<String, Any?>>?,
    val steps: List<Map<String, Any?>>?,
    val triggerConfig: Map<String, Any?>? = null,
    val triggerConfigs: List<Map<String, Any?>>? = null,
    val isEnabled: Boolean?,
    val isFavorite: Boolean?,
    val tags: List<String>?,
    val version: String?
)
