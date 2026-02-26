package com.chaomixian.vflow.api.server

import com.chaomixian.vflow.api.auth.AuthManager
import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.handler.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * API Web服务器
 * 基于NanoHTTPD实现
 */
class ApiServer(
    private val port: Int,
    private val authManager: AuthManager,
    private val dependencies: ApiDependencies
) : NanoHTTPD(port) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val rateLimiter = RateLimiter()

    // API Handlers
    private lateinit var authHandler: AuthHandler
    private lateinit var workflowHandler: WorkflowHandler
    private lateinit var executionHandler: ExecutionHandler
    private lateinit var moduleHandler: ModuleHandler
    private lateinit var folderHandler: FolderHandler
    private lateinit var importExportHandler: ImportExportHandler
    private lateinit var systemHandler: SystemHandler

    var isServerRunning = false
        private set

    /**
     * 初始化Handlers
     */
    private fun initHandlers() {
        authHandler = AuthHandler(authManager, rateLimiter, gson)
        workflowHandler = WorkflowHandler(dependencies, rateLimiter, gson)
        executionHandler = ExecutionHandler(dependencies, rateLimiter, gson)
        moduleHandler = ModuleHandler(dependencies, rateLimiter, gson)
        folderHandler = FolderHandler(dependencies, rateLimiter, gson)
        importExportHandler = ImportExportHandler(dependencies, rateLimiter, gson)
        systemHandler = SystemHandler(dependencies, rateLimiter, gson)
    }

    /**
     * 启动服务器
     */
    @Throws(IOException::class)
    fun startServer(): Boolean {
        if (isServerRunning) {
            return true
        }

        initHandlers()
        start()
        isServerRunning = true

        // 启动定期清理任务
        startCleanupTasks()

        return true
    }

    /**
     * 停止服务器
     */
    fun stopServer() {
        if (!isServerRunning) {
            return
        }

        stop()
        isServerRunning = false
    }

    /**
     * 启动定期清理任务
     */
    private fun startCleanupTasks() {
        scope.launch {
            // 每分钟清理一次过期Token
            while (isServerRunning) {
                kotlinx.coroutines.delay(60000)
                authManager.cleanupExpiredTokens()
                rateLimiter.cleanup()
            }
        }
    }

    /**
     * 处理HTTP请求
     */
    override fun serve(session: IHTTPSession): Response {
        // 处理OPTIONS请求（CORS预检）
        if (session.method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, "application/json", "")
            return addCorsHeaders(response)
        }

        return try {
            val uri = session.uri
            val method = session.method

            // 添加CORS头
            val response = when {
                // 认证端点（不需要Token）
                uri.startsWith("/api/v1/auth") -> handleAuthRequest(session)

                // 系统健康检查（不需要Token）
                uri == "/api/v1/system/health" -> systemHandler.handleHealthCheck(session)

                // 其他端点需要认证
                else -> handleAuthenticatedRequest(session)
            }

            // 添加CORS头
            addCorsHeaders(response)

        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "code" to 9001,
                    "message" to "Internal server error: ${e.message}",
                    "data" to null
                ))
            )
        }
    }

    /**
     * 处理认证请求
     */
    private fun handleAuthRequest(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/api/v1/auth/token" && method == Method.POST -> {
                authHandler.handleGenerateToken(session)
            }
            uri == "/api/v1/auth/refresh" && method == Method.POST -> {
                authHandler.handleRefreshToken(session)
            }
            uri == "/api/v1/auth/verify" && method == Method.GET -> {
                authHandler.handleVerifyToken(session)
            }
            uri == "/api/v1/auth/revoke" && method == Method.POST -> {
                authHandler.handleRevokeToken(session)
            }
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                gson.toJson(mapOf(
                    "code" to 404,
                    "message" to "Endpoint not found",
                    "data" to null
                ))
            )
        }
    }

    /**
     * 处理需要认证的请求
     */
    private fun handleAuthenticatedRequest(session: IHTTPSession): Response {
        // 验证Token
        val token = extractToken(session)
        if (token == null) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf(
                    "code" to 6001,
                    "message" to "Missing authentication token",
                    "data" to null
                ))
            )
        }

        val tokenInfo = authManager.verifyToken(token)
        if (tokenInfo == null) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf(
                    "code" to 6002,
                    "message" to "Invalid or expired token",
                    "data" to null
                ))
            )
        }

        // 路由到相应的Handler
        val uri = session.uri
        return when {
            // 执行工作流端点（需要优先匹配）
            uri.matches(Regex("/api/v1/workflows/[^/]+/execute")) -> executionHandler.handle(session, tokenInfo)
            uri.startsWith("/api/v1/workflows") -> workflowHandler.handle(session, tokenInfo)
            uri.startsWith("/api/v1/executions") -> executionHandler.handle(session, tokenInfo)
            uri.startsWith("/api/v1/modules") -> moduleHandler.handle(session, tokenInfo)
            uri.startsWith("/api/v1/folders") -> folderHandler.handle(session, tokenInfo)
            uri.contains("/import") || uri.contains("/export") -> importExportHandler.handle(session, tokenInfo)
            uri.startsWith("/api/v1/system") -> systemHandler.handle(session, tokenInfo)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                gson.toJson(mapOf(
                    "code" to 404,
                    "message" to "Endpoint not found",
                    "data" to null
                ))
            )
        }
    }

    /**
     * 从请求中提取Token
     */
    private fun extractToken(session: IHTTPSession): String? {
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }
        return null
    }

    /**
     * 添加CORS头
     */
    private fun addCorsHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        response.addHeader("Access-Control-Max-Age", "86400")
        return response
    }
}

/**
 * API依赖项
 */
data class ApiDependencies(
    val context: android.content.Context,
    val workflowManager: com.chaomixian.vflow.core.workflow.WorkflowManager,
    val moduleRegistry: Any?,
    val executionManager: ExecutionManager,
    val folderManager: com.chaomixian.vflow.core.workflow.FolderManager? = null
) {
    companion object {
        private var startupTime: Long = System.currentTimeMillis()

        fun setStartupTime(time: Long) {
            startupTime = time
        }

        fun getStartupTime(): Long = startupTime
    }
}
