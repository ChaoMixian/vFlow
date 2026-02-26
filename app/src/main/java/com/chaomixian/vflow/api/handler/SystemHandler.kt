package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 系统Handler
 */
class SystemHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        return when {
            // 健康检查（不需要认证）
            uri == "/api/v1/system/health" && method == NanoHTTPD.Method.GET -> {
                handleHealthCheck(session)
            }
            // 获取设备信息
            uri == "/api/v1/system/info" && method == NanoHTTPD.Method.GET -> {
                handleGetSystemInfo(tokenInfo)
            }
            // 获取统计信息
            uri == "/api/v1/system/stats" && method == NanoHTTPD.Method.GET -> {
                handleGetSystemStats(session, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    fun handleHealthCheck(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return successResponse(HealthCheckResponse(
            status = "healthy",
            version = "1.0.0",
            timestamp = System.currentTimeMillis(),
            uptime = System.currentTimeMillis() - ApiDependencies.getStartupTime()
        ))
    }

    private fun handleGetSystemInfo(tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val context = deps.context
        // TODO: 获取实际权限状态
        val packageManager = context.packageManager
        val packageInfo = try {
            packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        return successResponse(SystemInfoResponse(
            device = DeviceInfo(
                brand = android.os.Build.BRAND,
                model = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE_OR_CODENAME,
                apiLevel = android.os.Build.VERSION.SDK_INT
            ),
            permissions = listOf(
                PermissionStatus("ACCESSIBILITY_SERVICE", true, "无障碍服务权限"),
                PermissionStatus("WRITE_EXTERNAL_STORAGE", true, "存储权限"),
                PermissionStatus("SYSTEM_ALERT_WINDOW", false, "悬浮窗权限")
            ),
            capabilities = Capabilities(
                hasRoot = false,
                hasShizuku = true,
                hasCoreService = true,
                supportedFeatures = listOf(
                    "opencv_image_matching",
                    "ml_kit_ocr",
                    "lua_scripting",
                    "javascript_scripting"
                )
            ),
            server = ServerInfo(
                version = "1.0.0",
                startTime = ApiDependencies.getStartupTime(),
                uptime = System.currentTimeMillis() - ApiDependencies.getStartupTime()
            )
        ))
    }

    private fun handleGetSystemStats(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val workflows = deps.workflowManager.getAllWorkflows()
        val executionStats = deps.executionManager.getStats()

        // 计算存储使用情况
        val prefs = deps.context.getSharedPreferences("vflow_workflows", android.content.Context.MODE_PRIVATE)
        val storageUsed = prefs.getString("workflow_list", null)?.toByteArray()?.size ?: 0
        val storageTotal = 100 * 1024 * 1024 // 100MB 限制

        // 获取热门工作流
        val workflowExecutionCounts = workflows.map { wf ->
            TopWorkflow(
                workflowId = wf.id,
                name = wf.name,
                executionCount = 0 // TODO: 从执行记录中统计
            )
        }.sortedByDescending { it.executionCount }.take(5)

        return successResponse(SystemStatsResponse(
            workflowCount = workflows.size,
            enabledWorkflowCount = workflows.count { it.isEnabled },
            folderCount = 0, // TODO: 获取文件夹数量
            totalExecutions = executionStats.totalExecutions.toLong(),
            todayExecutions = executionStats.todayExecutions.toLong(),
            successfulExecutions = executionStats.successfulExecutions.toLong(),
            failedExecutions = executionStats.failedExecutions.toLong(),
            successRate = if (executionStats.totalExecutions > 0) {
                executionStats.successfulExecutions.toDouble() / executionStats.totalExecutions
            } else 0.0,
            averageExecutionTime = 0, // TODO: 计算平均执行时间
            storageUsage = StorageUsage(
                usedBytes = storageUsed.toLong(),
                totalBytes = storageTotal.toLong(),
                used = "${storageUsed / 1024}KB",
                total = "100MB",
                percentage = (storageUsed * 100 / storageTotal)
            ),
            memoryUsage = StorageUsage(
                usedBytes = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()),
                totalBytes = Runtime.getRuntime().maxMemory(),
                used = "${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024}MB",
                total = "${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB",
                percentage = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) * 100 / Runtime.getRuntime().maxMemory()).toInt()
            ),
            topWorkflows = workflowExecutionCounts
        ))
    }
}
