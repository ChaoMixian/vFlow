package com.chaomixian.vflow.api

import android.content.Context
import com.chaomixian.vflow.api.auth.AuthManager
import com.chaomixian.vflow.api.server.ApiDependencies
import com.chaomixian.vflow.api.server.ApiServer
import com.chaomixian.vflow.api.server.ExecutionManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.FolderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * API服务管理器
 * 负责启动、停止和管理API服务器
 * 单例模式，在应用生命周期内持久化
 */
class ApiService private constructor(
    private val context: Context,
    private val workflowManager: WorkflowManager,
    private val moduleRegistry: Any?
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val authManager = AuthManager(context)
    private var apiServer: ApiServer? = null
    private var executionManager: ExecutionManager? = null

    // 服务器状态
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState = _serverState.asStateFlow()

    // 服务器配置
    private val _serverConfig = MutableStateFlow(ServerConfig())
    val serverConfig = _serverConfig.asStateFlow()

    init {
        // 加载配置
        loadConfig()

        // 设置启动时间
        ApiDependencies.setStartupTime(System.currentTimeMillis())

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
     * 启动API服务器
     */
    fun startServer(): Boolean {
        if (_serverState.value == ServerState.RUNNING) {
            return true
        }

        try {
            // 创建ExecutionManager和FolderManager
            executionManager = ExecutionManager(workflowManager)
            val folderManager = FolderManager(context)

            // 创建依赖项
            val dependencies = ApiDependencies(
                context = context,
                workflowManager = workflowManager,
                moduleRegistry = moduleRegistry,
                executionManager = executionManager!!,
                folderManager = folderManager
            )

            // 创建服务器
            val port = _serverConfig.value.port
            apiServer = ApiServer(port, authManager, dependencies)

            // 启动服务器
            val started = apiServer?.startServer() ?: false

            if (started) {
                _serverState.value = ServerState.RUNNING

                // 保存配置
                saveConfig()

                // 启动清理任务
                startCleanupTasks()
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
     * 停止API服务器
     */
    fun stopServer() {
        apiServer?.stopServer()
        apiServer = null
        executionManager = null
        _serverState.value = ServerState.STOPPED
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
        return "http://$ipAddress:${config.port}/api/v1"
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
     * 启动清理任务
     */
    private fun startCleanupTasks() {
        scope.launch {
            while (_serverState.value == ServerState.RUNNING) {
                kotlinx.coroutines.delay(300000) // 5分钟
                executionManager?.cleanupOldExecutions()
            }
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
                        return address.hostAddress
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
