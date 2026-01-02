// 文件路径: main/java/com/chaomixian/vflow/services/ShellManager.kt
package com.chaomixian.vflow.services

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.Surface
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.core.logging.LogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shell 管理工具类 (原 ShizukuManager)。
 * 负责管理与 Shizuku 服务的连接，并提供核心的 Shell 命令执行功能。
 * 支持 Shizuku 和 Root 两种执行后端，并可根据设置自动切换。
 */
object ShellManager {
    private const val TAG = "vFlowShellManager"
    private const val BIND_TIMEOUT_MS = 3000L
    private const val MAX_RETRY_COUNT = 3

    /**
     * Shell 执行模式
     */
    enum class ShellMode {
        AUTO,    // 自动：根据用户在设置中的偏好决定
        SHIZUKU, // 强制使用 Shizuku
        ROOT     // 强制使用 Root
    }

    @Volatile
    private var shellService: IShizukuUserService? = null

    // 使用 Mutex 替代 AtomicBoolean 来保护 Shizuku 连接过程
    private val connectionMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 动态获取所需的权限。
     * 模块可以调用此方法来声明它们需要的权限，而无需自己判断是 Root 还是 Shizuku。
     * @param context 上下文，用于读取 SharedPreferences。
     * @return 包含 PermissionManager.ROOT 或 PermissionManager.SHIZUKU 的列表。
     */
    fun getRequiredPermissions(context: Context): List<Permission> {
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val defaultMode = prefs.getString("default_shell_mode", "shizuku")
        return if (defaultMode == "root") {
            listOf(PermissionManager.ROOT)
        } else {
            listOf(PermissionManager.SHIZUKU)
        }
    }

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
     * 检查 Root 权限是否可用。
     * 尝试执行 'su' 命令并检查退出码。
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动守护任务 (目前仅支持 Shizuku 模式)
     */
    fun startWatcher(context: Context) {
        // 守护任务依赖于 IShizukuUserService 的长连接，Root 模式下暂不支持此机制
        if (!isShizukuActive(context)) {
            DebugLogger.w(TAG, "Shizuku 未激活，无法启动守护任务。")
            return
        }

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
     * 停止守护任务 (目前仅支持 Shizuku 模式)
     */
    fun stopWatcher(context: Context) {
        if (!isShizukuActive(context)) return

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
     * 创建虚拟屏幕 (仅 Shizuku 模式)
     */
    suspend fun createVirtualDisplay(surface: Surface, width: Int, height: Int, dpi: Int): Int {
        val context = LogManager.applicationContext
        if (!isShizukuActive(context)) return -1

        val service = getService(context) ?: return -1
        return try {
            service.createVirtualDisplay(surface, width, height, dpi)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "调用 createVirtualDisplay 失败", e)
            -1
        }
    }

    /**
     * 销毁虚拟屏幕
     */
    suspend fun destroyVirtualDisplay(displayId: Int) {
        if (displayId == -1) return
        val context = LogManager.applicationContext
        val service = getService(context) ?: return
        try {
            service.destroyVirtualDisplay(displayId)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "调用 destroyVirtualDisplay 失败", e)
        }
    }

    /**
     * 执行一条 Shell 命令。
     * @param context 上下文
     * @param command 要执行的命令
     * @param mode 执行模式，默认为 AUTO (自动根据设置选择)
     * @return 命令输出字符串，或以 "Error:" 开头的错误信息。
     */
    suspend fun execShellCommand(
        context: Context,
        command: String,
        mode: ShellMode = ShellMode.AUTO
    ): String {
        return withContext(Dispatchers.IO) {
            // 确定最终使用的模式
            val finalMode = if (mode == ShellMode.AUTO) {
                val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                val defaultMode = prefs.getString("default_shell_mode", "shizuku")
                if (defaultMode == "root") ShellMode.ROOT else ShellMode.SHIZUKU
            } else {
                mode
            }

            // 根据模式分发执行
            if (finalMode == ShellMode.ROOT) {
                executeRootCommand(command)
            } else {
                executeShizukuCommand(context, command)
            }
        }
    }

    /**
     * 内部 Root 执行逻辑
     */
    private fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            // 写入命令和退出指令
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            // 读取输出
            // use 块会自动关闭流
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                if (stdout.isNotEmpty()) stdout.trim() else "Success"
            } else {
                // 优先返回 stderr，如果没有则返回 stdout，最后返回错误码
                val msg = if (stderr.isNotBlank()) stderr else if (stdout.isNotBlank()) stdout else "Exit code $exitCode"
                "Error: ${msg.trim()}"
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Root execution failed", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 内部 Shizuku 执行逻辑 (保留原有逻辑)
     */
    private suspend fun executeShizukuCommand(context: Context, command: String): String {
        val service = getService(context)
        if (service == null) {
            val status = if (!isShizukuActive(context)) "Shizuku 未激活或未授权" else "服务连接失败"
            return "Error: $status"
        }
        return try {
            service.exec(command)
        } catch (e: CancellationException) {
            // 捕获协程取消异常。
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
     * 通过 Shell 开启无障碍服务。
     * 使用 AUTO 模式，自动适配 Root 或 Shizuku。
     * @return 返回操作是否成功。
     */
    suspend fun enableAccessibilityService(context: Context): Boolean {
        val serviceName = "${context.packageName}/${com.chaomixian.vflow.services.AccessibilityService::class.java.name}"
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
        DebugLogger.d(TAG, "已通过 Shell 尝试启用无障碍服务。")
        return true
    }

    /**
     * 通过 Shell 关闭无障碍服务。
     * 使用 AUTO 模式，自动适配 Root 或 Shizuku。
     * @return 返回操作是否成功。
     */
    suspend fun disableAccessibilityService(context: Context): Boolean {
        val serviceName = "${context.packageName}/${com.chaomixian.vflow.services.AccessibilityService::class.java.name}"
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
        DebugLogger.d(TAG, "已通过 Shell 尝试禁用无障碍服务。")
        return true
    }

    /**
     * 获取服务实例，使用 Mutex 解决并发问题 (仅用于 Shizuku)。
     */
    private suspend fun getService(context: Context): IShizukuUserService? {
        // 在尝试加锁前，先进行一次快速检查，提高性能
        if (shellService?.asBinder()?.isBinderAlive == true) {
            return shellService
        }

        // 使用互斥锁确保只有一个协程可以执行连接操作
        connectionMutex.withLock {
            // 获取锁后，再次检查
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