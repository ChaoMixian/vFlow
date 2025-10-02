// 文件路径: main/java/com/chaomixian/vflow/services/ShizukuManager.kt
package com.chaomixian.vflow.services

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shizuku 管理工具类 (精简优化版)。
 * 负责管理与 Shizuku 服务的连接，并提供核心的命令执行功能。
 * 新增了 proactiveConnect() 方法用于应用启动时预连接，以优化首次执行速度。
 */
object ShizukuManager {
    private const val TAG = "vFlowShizukuManager"
    private const val BIND_TIMEOUT_MS = 3000L
    private const val MAX_RETRY_COUNT = 3

    @Volatile
    private var shellService: IShizukuUserService? = null
    // [核心修改] 使用 Mutex 替代 AtomicBoolean 来保护连接过程
    private val connectionMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 主动预连接 Shizuku 服务。
     * 在应用启动时调用此方法，可以在后台异步建立连接，
     * 以便在用户首次执行相关模块时获得更快的响应速度。
     */
    fun proactiveConnect(context: Context) {
        // 如果服务已连接并且 binder 存活，则无需操作
        if (shellService?.asBinder()?.isBinderAlive == true) {
            DebugLogger.d(TAG, "Shizuku 服务已连接，跳过预连接。")
            return
        }

        DebugLogger.d(TAG, "发起 Shizuku 服务预连接...")
        // 启动一个后台协程来执行连接，不阻塞主线程
        scope.launch {
            if (isShizukuActive(context)) {
                // 调用 getService 来触发带重试的连接逻辑
                getService(context)
                DebugLogger.d(TAG, "Shizuku 预连接尝试完成。")
            } else {
                DebugLogger.d(TAG, "Shizuku 未激活，跳过预连接。")
            }
        }
    }

    /**
     * 检查 Shizuku 是否可用且已授权。
     */
    fun isShizukuActive(context: Context): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动 Shizuku 守护任务
     */
    fun startWatcher(context: Context) {
        scope.launch {
            val service = getService(context)
            if (service == null) {
                DebugLogger.e(TAG, "无法启动守护任务：Shizuku 服务连接失败。")
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    service.startWatcher(context.packageName, TriggerService::class.java.name)
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "启动守护任务时出错。", e)
            }
        }
    }

    /**
     * 停止 Shizuku 守护任务
     */
    fun stopWatcher(context: Context) {
        scope.launch {
            val service = getService(context)
            if (service == null) {
                // 如果服务未连接，也无需停止
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    service.stopWatcher()
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "停止守护任务时出错。", e)
            }
        }
    }


    /**
     * 执行一条 Shell 命令。
     */
    suspend fun execShellCommand(context: Context, command: String): String {
        val service = getService(context)
        if (service == null) {
            val status = if (!isShizukuActive(context)) "Shizuku 未激活或未授权" else "服务连接失败"
            return "Error: $status"
        }
        return try {
            withContext(Dispatchers.IO) {
                service.exec(command)
            }
        } catch (e: CancellationException) {
            // 捕获协程取消异常。
            // 这是一个正常的操作流程（例如在重新加载触发器时），不应被记录为错误。
            // 将异常重新抛出，让协程框架正常处理取消逻辑。
            DebugLogger.d(TAG, "Shizuku command execution was cancelled as expected.")
            throw e
        } catch (e: Exception) {
            // 其他所有类型的异常仍然被视为执行失败。
            DebugLogger.e(TAG, "执行命令失败，连接可能已丢失。", e)
            shellService = null
            "Error: ${e.message}"
        }
    }

    /**
     * 通过 Shizuku 开启无障碍服务。
     * @return 返回操作是否成功。
     */
    suspend fun enableAccessibilityService(context: Context): Boolean {
        val serviceName = "${context.packageName}/${AccessibilityService::class.java.name}"
        // 1. 读取当前已启用的服务列表
        val currentServices = execShellCommand(context, "settings get secure enabled_accessibility_services")
        if (currentServices.startsWith("Error:")) {
            DebugLogger.e(TAG, "读取无障碍服务列表失败: $currentServices")
            return false
        }

        // 2. 检查服务是否已在列表中
        if (currentServices.split(':').any { it.equals(serviceName, ignoreCase = true) }) {
            DebugLogger.d(TAG, "无障碍服务已经启用。")
            return true
        }

        // 3. 将我们的服务添加到列表中
        val newServices = if (currentServices.isBlank() || currentServices == "null") {
            serviceName
        } else {
            "$currentServices:$serviceName"
        }

        // 4. 写回新的服务列表
        val result = execShellCommand(context, "settings put secure enabled_accessibility_services '$newServices'")
        if (result.startsWith("Error:")) {
            DebugLogger.e(TAG, "写入无障碍服务列表失败: $result")
            return false
        }

        // 5. 确保无障碍总开关是打开的
        execShellCommand(context, "settings put secure accessibility_enabled 1")
        DebugLogger.d(TAG, "已通过 Shizuku 尝试启用无障碍服务。")
        return true
    }

    /**
     * 通过 Shizuku 关闭无障碍服务。
     * @return 返回操作是否成功。
     */
    suspend fun disableAccessibilityService(context: Context): Boolean {
        val serviceName = "${context.packageName}/${AccessibilityService::class.java.name}"
        // 1. 读取当前服务列表
        val currentServices = execShellCommand(context, "settings get secure enabled_accessibility_services")
        if (currentServices.startsWith("Error:") || currentServices == "null" || currentServices.isBlank()) {
            return true // 列表为空，无需操作
        }

        // 2. 从列表中移除我们的服务
        val serviceList = currentServices.split(':').toMutableList()
        val removed = serviceList.removeAll { it.equals(serviceName, ignoreCase = true) }

        if (!removed) {
            return true // 服务本就不在列表中，无需操作
        }

        // 3. 写回新的服务列表
        val newServices = serviceList.joinToString(":")
        val result = execShellCommand(context, "settings put secure enabled_accessibility_services '$newServices'")
        if (result.startsWith("Error:")) {
            DebugLogger.e(TAG, "移除无障碍服务失败: $result")
            return false
        }
        DebugLogger.d(TAG, "已通过 Shizuku 尝试禁用无障碍服务。")
        return true
    }


    /**
     * 获取服务实例，使用 Mutex 解决并发问题。
     */
    private suspend fun getService(context: Context): IShizukuUserService? {
        // 在尝试加锁前，先进行一次快速检查，提高性能
        if (shellService?.asBinder()?.isBinderAlive == true) {
            return shellService
        }

        // 使用互斥锁确保只有一个协程可以执行连接操作
        connectionMutex.withLock {
            // 获取锁后，再次检查，因为在等待锁的过程中，可能已有其他协程完成了连接
            if (shellService?.asBinder()?.isBinderAlive == true) {
                return shellService
            }

            // 在锁内执行带重试的连接逻辑
            for (attempt in 1..MAX_RETRY_COUNT) {
                try {
                    shellService = connect(context)
                    // 连接成功后，直接返回
                    return shellService
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "第 $attempt 次连接 Shizuku 服务失败", e)
                    if (attempt < MAX_RETRY_COUNT) {
                        delay(500L * attempt)
                    }
                }
            }
            // 所有重试失败后，返回 null
            return null
        }
    }

    /**
     * 简化 connect 函数，移除并发控制逻辑。
     * 它现在只负责执行单次的绑定尝试。
     */
    private suspend fun connect(context: Context): IShizukuUserService {
        if (!isShizukuActive(context)) {
            throw RuntimeException("Shizuku 未激活或未授权。")
        }

        return withTimeout(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val userServiceArgs = Shizuku.UserServiceArgs(ComponentName(context, ShizukuUserService::class.java))
                    .daemon(false)
                    .processNameSuffix(":vflow_shizuku")
                    .version(1)

                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (service != null && service.isBinderAlive && continuation.isActive) {
                            DebugLogger.d(TAG, "Shizuku 服务已连接。")
                            val shell = IShizukuUserService.Stub.asInterface(service)
                            shellService = shell
                            continuation.resume(shell)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        shellService = null
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException("Shizuku 服务连接意外断开。"))
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        Shizuku.unbindUserService(userServiceArgs, connection, true)
                    } catch (e: Exception) {
                        // 忽略解绑时的异常
                    }
                }

                // 确保绑定操作在主线程执行
                scope.launch(Dispatchers.Main) {
                    Shizuku.bindUserService(userServiceArgs, connection)
                }
            }
        }
    }
}