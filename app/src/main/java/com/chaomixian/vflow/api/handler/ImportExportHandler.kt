package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.util.UUID

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
            val steps: List<ActionStep> = workflowData.steps?.map { stepMap ->
                val stepMapTyped = stepMap as? Map<String, Any?>
                    ?: throw IllegalArgumentException("Invalid step format")
                ActionStep(
                    moduleId = stepMapTyped["moduleId"] as? String ?: "",
                    parameters = (stepMapTyped["parameters"] as? Map<String, Any?>) ?: emptyMap(),
                    indentationLevel = (stepMapTyped["indentationLevel"] as? Number)?.toInt() ?: 0,
                    id = stepMapTyped["id"] as? String ?: UUID.randomUUID().toString()
                )
            } ?: emptyList()

            // 创建新工作流
            val newWorkflow = Workflow(
                id = newId,
                name = workflowData.name ?: "Imported Workflow",
                description = workflowData.description ?: "",
                steps = steps,
                isEnabled = false,
                isFavorite = workflowData.isFavorite ?: false,
                folderId = request.folderId,
                order = 0,
                tags = workflowData.tags ?: emptyList(),
                version = workflowData.version ?: "1.0.0",
                triggerConfig = workflowData.triggerConfig,
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
    val steps: List<Map<String, Any?>>?,
    val isEnabled: Boolean?,
    val isFavorite: Boolean?,
    val tags: List<String>?,
    val version: String?,
    val triggerConfig: Map<String, Any?>?
)
