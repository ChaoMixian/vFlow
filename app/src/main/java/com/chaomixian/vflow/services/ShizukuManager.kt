// 文件路径: main/java/com/chaomixian/vflow/services/ShizukuManager.kt
package com.chaomixian.vflow.services

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
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
    private val isBinding = AtomicBoolean(false)
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
     * 获取服务实例，如果未连接则尝试连接。
     */
    private suspend fun getService(context: Context): IShizukuUserService? {
        if (shellService?.asBinder()?.isBinderAlive == true) {
            return shellService
        }
        for (attempt in 1..MAX_RETRY_COUNT) {
            try {
                return connect(context)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "第 $attempt 次连接 Shizuku 服务失败", e)
                if (attempt < MAX_RETRY_COUNT) {
                    delay(500L * attempt)
                }
            }
        }
        return null
    }

    /**
     * 建立与 Shizuku 用户服务的连接。
     */
    private suspend fun connect(context: Context): IShizukuUserService {
        if (!isShizukuActive(context)) {
            throw RuntimeException("Shizuku 未激活或未授权。")
        }

        if (!isBinding.compareAndSet(false, true)) {
            DebugLogger.w(TAG, "另一个绑定进程正在进行中，等待...")
            delay(BIND_TIMEOUT_MS)
            return shellService ?: throw IllegalStateException("另一个绑定进程正在进行但未完成。")
        }

        return try {
            withTimeout(BIND_TIMEOUT_MS) {
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

                    scope.launch(Dispatchers.Main) {
                        Shizuku.bindUserService(userServiceArgs, connection)
                    }
                }
            }
        } finally {
            isBinding.set(false)
        }
    }
}