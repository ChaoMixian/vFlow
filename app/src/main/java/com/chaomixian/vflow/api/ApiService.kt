package com.chaomixian.vflow.api

import android.content.Context
import com.chaomixian.vflow.api.auth.AuthManager
import com.chaomixian.vflow.api.mcp.McpServer
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.services.McpForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * MCP 服务管理器
 * 负责启动、停止和管理 MCP 服务器
 * 单例模式，在应用生命周期内持久化
 */
class ApiService private constructor(
    private val context: Context,
    private val workflowManager: WorkflowManager,
    private val moduleRegistry: Any?
) {

    private val authManager = AuthManager(context)
    private var mcpServer: McpServer? = null

    // 服务器状态
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState = _serverState.asStateFlow()

    // 服务器配置
    private val _serverConfig = MutableStateFlow(ServerConfig())
    val serverConfig = _serverConfig.asStateFlow()

    init {
        // 加载配置
        loadConfig()

        // 确保ModuleRegistry已初始化（如果MainActivity还没启动）
        try {
            val registryClass = Class.forName("com.chaomixian.vflow.core.module.ModuleRegistry")
            val initializeMethod = registryClass.getMethod("initialize", android.content.Context::class.java)
            initializeMethod.invoke(null, context)
        } catch (e: Exception) {
            // 忽略初始化失败，可能已经初始化过了
        }

        // 如果配置为自动启动，则启动服务器
        if (_serverConfig.value.enabled) {
            startServer()
        }
    }

    companion object {
        @Volatile
        private var instance: ApiService? = null

        fun getInstance(
            context: Context,
            workflowManager: WorkflowManager,
            moduleRegistry: Any? = null
        ): ApiService {
            return instance ?: synchronized(this) {
                instance ?: ApiService(
                    context.applicationContext,
                    workflowManager,
                    moduleRegistry
                ).also { instance = it }
            }
        }

        fun getInstanceOrNull(): ApiService? = instance
    }

    /**
     * 启动 MCP 服务器
     */
    fun startServer(): Boolean {
        // 持久化启用状态，应用被回收后可由前台服务/进程重启自动恢复
        if (!_serverConfig.value.enabled) {
            _serverConfig.value = _serverConfig.value.copy(enabled = true)
            saveConfig()
        }

        if (_serverState.value == ServerState.RUNNING) {
            ensureForegroundService()
            return true
        }

        try {
            // 创建 MCP 服务器
            val port = _serverConfig.value.port
            mcpServer = McpServer(port, authManager, context)

            // 启动服务器
            val started = mcpServer?.startServer() ?: false

            if (started) {
                _serverState.value = ServerState.RUNNING

                // 保存配置
                saveConfig()

                // 使用前台服务保活，避免 MIUI 等系统在后台冻结 MCP 端口
                ensureForegroundService()
            }

            return started

        } catch (e: IOException) {
            e.printStackTrace()
            _serverState.value = ServerState.ERROR
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            _serverState.value = ServerState.ERROR
            return false
        }
    }

    /**
     * 停止 MCP 服务器
     */
    fun stopServer() {
        if (_serverConfig.value.enabled) {
            _serverConfig.value = _serverConfig.value.copy(enabled = false)
            saveConfig()
        }
        mcpServer?.stopServer()
        mcpServer = null
        _serverState.value = ServerState.STOPPED
        stopForegroundService()
    }

    private fun ensureForegroundService() {
        try {
            McpForegroundService.start(context)
        } catch (e: Exception) {
            android.util.Log.w("ApiService", "无法启动 MCP 前台服务", e)
        }
    }

    private fun stopForegroundService() {
        try {
            McpForegroundService.stop(context)
        } catch (e: Exception) {
            android.util.Log.w("ApiService", "无法停止 MCP 前台服务", e)
        }
    }

    /**
     * 重启服务器
     */
    fun restartServer(): Boolean {
        stopServer()
        return startServer()
    }

    /**
     * 更新配置
     */
    fun updateConfig(config: ServerConfig) {
        _serverConfig.value = config
        saveConfig()

        // 如果服务器正在运行，需要重启
        if (_serverState.value == ServerState.RUNNING) {
            restartServer()
        }
    }

    /**
     * 获取服务器URL
     */
    fun getServerUrl(): String? {
        if (_serverState.value != ServerState.RUNNING) {
            return null
        }

        val config = _serverConfig.value
        val ipAddress = getLocalIpAddress()
        return "http://$ipAddress:${config.port}/mcp"
    }

    /**
     * 生成Token
     */
    fun generateToken(deviceId: String, deviceName: String?): String? {
        val tokenInfo = authManager.generateToken(deviceId, deviceName)
        return tokenInfo.token
    }

    /**
     * 撤销Token
     */
    fun revokeToken(token: String): Boolean {
        return authManager.revokeToken(token)
    }

    /**
     * 获取所有Token
     */
    fun getActiveTokens(): List<com.chaomixian.vflow.api.auth.TokenInfo> {
        authManager.cleanupExpiredTokens()
        return authManager.getTokens().values.toList()
    }

    /**
     * 获取认证统计
     */
    fun getAuthStats(): com.chaomixian.vflow.api.auth.AuthStats {
        return authManager.getStats()
    }

    /**
     * 加载配置
     */
    private fun loadConfig() {
        val prefs = context.getSharedPreferences("vflow_api", Context.MODE_PRIVATE)
        val config = ServerConfig(
            port = prefs.getInt("port", 8080),
            enabled = prefs.getBoolean("enabled", false)
        )
        _serverConfig.value = config
    }

    /**
     * 保存配置
     */
    private fun saveConfig() {
        val config = _serverConfig.value
        val prefs = context.getSharedPreferences("vflow_api", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("port", config.port)
            putBoolean("enabled", config.enabled)
            apply()
        }
    }

    /**
     * 获取本地IP地址
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces.toList()) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses.toList()) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        address.hostAddress?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stopServer()
    }

    /**
     * 服务器状态
     */
    enum class ServerState {
        STOPPED,
        RUNNING,
        ERROR
    }

    /**
     * 服务器配置
     */
    data class ServerConfig(
        val port: Int = 8080,
        val enabled: Boolean = false,
        val autoStart: Boolean = false
    )
}
