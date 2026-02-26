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
 * 工作流Handler
 */
class WorkflowHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        // 提取工作流ID（如果存在）
        // 支持格式: /api/v1/workflows/{id}, /api/v1/workflows/{id}/duplicate, /api/v1/workflows/{id}/export
        val workflowId = if (uri.startsWith("/api/v1/workflows/") && uri != "/api/v1/workflows") {
            val pathPart = uri.substringAfter("/api/v1/workflows/")
            pathPart.substringBefore("/").takeIf { it.isNotEmpty() }
        } else null

        // 检查是否是magic-variables请求
        val isMagicVariables = workflowId != null && uri.endsWith("/magic-variables")

        // 检查是否是启用/禁用请求
        val isEnable = workflowId != null && uri.endsWith("/enable")
        val isDisable = workflowId != null && uri.endsWith("/disable")

        return when {
            // 列出工作流
            uri == "/api/v1/workflows" && method == NanoHTTPD.Method.GET -> {
                handleListWorkflows(session, tokenInfo)
            }
            // 获取工作流详情
            workflowId != null && method == NanoHTTPD.Method.GET && !uri.endsWith("/export") && !isMagicVariables -> {
                handleGetWorkflow(workflowId, tokenInfo)
            }
            // 创建工作流
            uri == "/api/v1/workflows" && method == NanoHTTPD.Method.POST -> {
                handleCreateWorkflow(session, tokenInfo)
            }
            // 更新工作流
            workflowId != null && method == NanoHTTPD.Method.PUT -> {
                handleUpdateWorkflow(workflowId, session, tokenInfo)
            }
            // 删除工作流
            workflowId != null && method == NanoHTTPD.Method.DELETE -> {
                handleDeleteWorkflow(workflowId, tokenInfo)
            }
            // 复制工作流
            workflowId != null && uri.endsWith("/duplicate") && method == NanoHTTPD.Method.POST -> {
                handleDuplicateWorkflow(workflowId, session, tokenInfo)
            }
            // 启用工作流
            isEnable && method == NanoHTTPD.Method.POST -> {
                handleEnableWorkflow(workflowId!!, tokenInfo)
            }
            // 禁用工作流
            isDisable && method == NanoHTTPD.Method.POST -> {
                handleDisableWorkflow(workflowId!!, tokenInfo)
            }
            // 批量操作
            uri == "/api/v1/workflows/batch" && method == NanoHTTPD.Method.POST -> {
                handleBatchOperation(session, tokenInfo)
            }
            // 导出工作流
            workflowId != null && uri.endsWith("/export") && method == NanoHTTPD.Method.GET -> {
                handleExportWorkflow(workflowId, tokenInfo)
            }
            // 获取魔法变量
            isMagicVariables && method == NanoHTTPD.Method.GET -> {
                handleGetMagicVariables(workflowId!!, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    private fun handleListWorkflows(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val params = parseQueryParams(session)

        // 解析筛选参数
        val search = params["search"]
        val folderId = params["folderId"]
        val tags = params["tags"]?.split(",")?.filter { it.isNotBlank() }
        val includeDisabled = params["includeDisabled"]?.toBooleanStrictOrNull() ?: true
        val sortBy = params["sortBy"] ?: "order"
        val order = params["order"] ?: "asc"
        val limit = params["limit"]?.toIntOrNull() ?: 50
        val offset = params["offset"]?.toIntOrNull() ?: 0

        var workflows = deps.workflowManager.getAllWorkflows()

        // 按名称或描述搜索
        if (!search.isNullOrBlank()) {
            workflows = workflows.filter {
                it.name.contains(search, ignoreCase = true) ||
                (it.description?.contains(search, ignoreCase = true) ?: false)
            }
        }

        // 按文件夹筛选
        if (!folderId.isNullOrBlank()) {
            workflows = workflows.filter { it.folderId == folderId }
        }

        // 按标签筛选
        if (!tags.isNullOrEmpty()) {
            workflows = workflows.filter { wf ->
                val wfTags = wf.tags ?: emptyList()
                tags.any { tag -> wfTags.contains(tag) }
            }
        }

        // 筛选启用状态
        if (!includeDisabled) {
            workflows = workflows.filter { it.isEnabled }
        }

        // 排序
        workflows = when (sortBy) {
            "name" -> if (order == "desc") workflows.sortedByDescending { it.name } else workflows.sortedBy { it.name }
            "modifiedAt" -> if (order == "desc") workflows.sortedByDescending { it.modifiedAt } else workflows.sortedBy { it.modifiedAt }
            "order" -> if (order == "desc") workflows.sortedByDescending { it.order } else workflows.sortedBy { it.order }
            else -> workflows.sortedBy { it.order }
        }

        val total = workflows.size

        // 分页
        workflows = workflows.drop(offset).take(limit)

        val summaries = workflows.map { wf ->
            WorkflowSummary(
                id = wf.id,
                name = wf.name,
                description = wf.description ?: "",
                isEnabled = wf.isEnabled,
                isFavorite = wf.isFavorite,
                folderId = wf.folderId,
                order = wf.order,
                stepCount = wf.steps.size,
                modifiedAt = wf.modifiedAt,
                tags = wf.tags ?: emptyList(),
                version = wf.version ?: "1.0.0",
                triggerConfig = wf.triggerConfig
            )
        }

        return successResponse(mapOf(
            "workflows" to summaries,
            "total" to total,
            "limit" to limit,
            "offset" to offset
        ))
    }

    private fun handleGetWorkflow(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val workflow = deps.workflowManager.getWorkflow(workflowId)
        if (workflow == null) {
            return errorResponse(1001, "Workflow not found")
        }

        return successResponse(workflow)
    }

    private fun handleCreateWorkflow(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        // 尝试解析简单请求格式
        val request = parseRequestBody(session, SimpleCreateWorkflowRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        // 转换步骤，处理可能的null
        val steps = request.steps?.map { it.toActionStep() } ?: emptyList()

        // 创建新工作流
        val newWorkflow = Workflow(
            id = UUID.randomUUID().toString(),
            name = request.name,
            steps = steps,
            isEnabled = request.isEnabled ?: false,
            description = request.description ?: "",
            folderId = request.folderId,
            order = 0,
            tags = request.tags ?: emptyList(),
            version = "1.0.0",
            triggerConfig = request.triggerConfig,
            modifiedAt = System.currentTimeMillis()
        )

        deps.workflowManager.saveWorkflow(newWorkflow)

        return successResponse(mapOf("id" to newWorkflow.id, "createdAt" to System.currentTimeMillis()))
    }

    private fun handleUpdateWorkflow(workflowId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val request = parseRequestBody(session, UpdateWorkflowRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        return successResponse(mapOf("id" to workflowId, "updatedAt" to System.currentTimeMillis()))
    }

    private fun handleDeleteWorkflow(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        deps.workflowManager.deleteWorkflow(workflowId)
        return successResponse(mapOf("deleted" to true, "deletedAt" to System.currentTimeMillis()))
    }

    private fun handleDuplicateWorkflow(workflowId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val request = parseRequestBody(session, DuplicateWorkflowRequest::class.java)
        val original = deps.workflowManager.getWorkflow(workflowId)
        if (original == null) {
            return errorResponse(1001, "Workflow not found")
        }

        val newName = request?.newName ?: "${original.name} (副本)"
        val newWorkflow = original.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            isEnabled = false
        )

        deps.workflowManager.saveWorkflow(newWorkflow)

        return successResponse(mapOf(
            "newWorkflowId" to newWorkflow.id,
            "name" to newName,
            "createdAt" to System.currentTimeMillis()
        ))
    }

    /**
     * 启用工作流
     */
    private fun handleEnableWorkflow(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val workflow = deps.workflowManager.getWorkflow(workflowId)
        if (workflow == null) {
            return errorResponse(1001, "Workflow not found")
        }

        val updatedWorkflow = workflow.copy(
            isEnabled = true,
            modifiedAt = System.currentTimeMillis()
        )
        deps.workflowManager.saveWorkflow(updatedWorkflow)

        return successResponse(mapOf(
            "id" to workflowId,
            "isEnabled" to true,
            "updatedAt" to System.currentTimeMillis()
        ))
    }

    /**
     * 禁用工作流
     */
    private fun handleDisableWorkflow(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val workflow = deps.workflowManager.getWorkflow(workflowId)
        if (workflow == null) {
            return errorResponse(1001, "Workflow not found")
        }

        val updatedWorkflow = workflow.copy(
            isEnabled = false,
            modifiedAt = System.currentTimeMillis()
        )
        deps.workflowManager.saveWorkflow(updatedWorkflow)

        return successResponse(mapOf(
            "id" to workflowId,
            "isEnabled" to false,
            "updatedAt" to System.currentTimeMillis()
        ))
    }

    private fun handleBatchOperation(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val request = parseRequestBody(session, BatchWorkflowRequest::class.java)
        if (request == null) {
            return errorResponse(400, "Invalid request body")
        }

        return successResponse(BatchOperationResponse(
            succeeded = request.workflowIds,
            failed = emptyList(),
            skipped = emptyList()
        ))
    }

    private fun handleExportWorkflow(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val workflow = deps.workflowManager.getWorkflow(workflowId)
        if (workflow == null) {
            return errorResponse(1001, "Workflow not found")
        }

        return successResponse(mapOf(
            "workflow" to workflow,
            "exportedAt" to System.currentTimeMillis(),
            "format" to "json"
        ))
    }

    /**
     * 获取工作流的魔法变量
     */
    private fun handleGetMagicVariables(workflowId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val workflow = deps.workflowManager.getWorkflow(workflowId)
        if (workflow == null) {
            return errorResponse(1001, "Workflow not found")
        }

        // 从工作流步骤中提取输出变量
        val magicVariables = mutableListOf<Map<String, Any?>>()
        val systemVariables = listOf(
            mapOf(
                "key" to "{{trigger.data}}",
                "label" to "触发器数据",
                "type" to "any",
                "description" to "触发器传入的原始数据"
            ),
            mapOf(
                "key" to "{{current_time}}",
                "label" to "当前时间",
                "type" to "number",
                "description" to "当前时间戳（毫秒）"
            ),
            mapOf(
                "key" to "{{device_info}}",
                "label" to "设备信息",
                "type" to "dictionary",
                "description" to "设备相关信息"
            )
        )

        // 从每个步骤的输出中提取变量
        workflow.steps.forEachIndexed { index, step ->
            val stepId = step.id ?: "step-$index"
            val stepName = step.moduleId

            // 为每个可能的输出添加变量引用
            // 这里简化处理，实际应该从模块定义获取输出
            magicVariables.add(mapOf(
                "key" to "{{$stepId.result}}",
                "label" to "$stepName - 结果",
                "type" to "any",
                "stepId" to stepId,
                "stepName" to stepName,
                "outputId" to "result",
                "outputName" to "结果",
                "category" to "Steps"
            ))
        }

        return successResponse(mapOf(
            "magicVariables" to magicVariables,
            "systemVariables" to systemVariables
        ))
    }
}
