package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 执行Handler
 */
class ExecutionHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        // 提取执行ID - 支持 /executions/{id}, /executions/{id}/logs, /executions/{id}/stop
        val executionId = if (uri.matches(Regex("/api/v1/executions/[^/]+(/.*)?$"))) {
            getPathParameter(uri, "/api/v1/executions/")
        } else null

        return when {
            // 执行工作流
            uri.matches(Regex("/api/v1/workflows/[^/]+/execute")) && method == NanoHTTPD.Method.POST -> {
                val workflowId = getPathParameter(uri, "/api/v1/workflows/", 0)
                handleExecuteWorkflow(workflowId!!, session, tokenInfo)
            }
            // 获取执行状态
            executionId != null && method == NanoHTTPD.Method.GET && !uri.endsWith("/logs") -> {
                handleGetExecution(executionId, tokenInfo)
            }
            // 停止执行
            executionId != null && uri.endsWith("/stop") && method == NanoHTTPD.Method.POST -> {
                handleStopExecution(executionId, tokenInfo)
            }
            // 获取执行日志
            executionId != null && uri.endsWith("/logs") && method == NanoHTTPD.Method.GET -> {
                handleGetExecutionLogs(executionId, session, tokenInfo)
            }
            // 列出执行记录
            uri == "/api/v1/executions" && method == NanoHTTPD.Method.GET -> {
                handleListExecutions(session, tokenInfo)
            }
            // 删除执行历史
            uri == "/api/v1/executions" && method == NanoHTTPD.Method.DELETE -> {
                handleDeleteExecutionHistory(session, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    private fun handleExecuteWorkflow(workflowId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.EXECUTE)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val request = parseRequestBody(session, ExecuteWorkflowRequest::class.java)
            ?: ExecuteWorkflowRequest()

        try {
            val detail = deps.executionManager.executeWorkflow(
                workflowId = workflowId,
                inputVariables = request.inputVariables,
                async = request.async ?: true,
                timeout = request.timeout
            )

            return successResponse(ExecutionResponse(
                executionId = detail.executionId,
                workflowId = detail.workflowId,
                status = detail.status,
                startedAt = detail.startedAt
            ))
        } catch (e: Exception) {
            return errorResponse(1003, "Workflow execution failed: ${e.message}")
        }
    }

    private fun handleGetExecution(executionId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val detail = deps.executionManager.getExecutionDetail(executionId)
        if (detail == null) {
            return errorResponse(5001, "Execution not found")
        }

        return successResponse(detail)
    }

    private fun handleStopExecution(executionId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val stopped = deps.executionManager.stopExecution(executionId)
        if (!stopped) {
            return errorResponse(5002, "Execution already stopped or not found")
        }

        return successResponse(mapOf(
            "executionId" to executionId,
            "stoppedAt" to System.currentTimeMillis(),
            "status" to "cancelled"
        ))
    }

    private fun handleGetExecutionLogs(executionId: String, session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val params = parseQueryParams(session)
        val level = params["level"]?.let {
            try { LogLevel.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        val stepIndex = params["stepIndex"]?.toIntOrNull()
        val limit = params["limit"]?.toIntOrNull() ?: 100
        val offset = params["offset"]?.toIntOrNull() ?: 0

        val logs = deps.executionManager.getExecutionLogs(executionId, level, stepIndex, limit, offset)
        return successResponse(logs)
    }

    private fun handleListExecutions(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val params = parseQueryParams(session)
        val workflowId = params["workflowId"]
        val status = params["status"]?.let {
            try { ExecutionStatus.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        val limit = params["limit"]?.toIntOrNull() ?: 20
        val offset = params["offset"]?.toIntOrNull() ?: 0

        val executions = deps.executionManager.listExecutions(workflowId, status, limit, offset)
        return successResponse(executions)
    }

    private fun handleDeleteExecutionHistory(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val rateLimitResult = runBlocking {
            checkRateLimit(tokenInfo.token, RateLimiter.RequestType.MODIFY)
        }
        if (rateLimitResult != null) {
            return rateLimitResponse(rateLimitResult)
        }

        val params = parseQueryParams(session)
        val executionId = params["executionId"]
        val workflowId = params["workflowId"]
        val olderThan = params["olderThan"]?.toLongOrNull()

        val deletedCount = deps.executionManager.deleteExecutionHistory(executionId, workflowId, olderThan)
        return successResponse(mapOf("deletedCount" to deletedCount))
    }
}
