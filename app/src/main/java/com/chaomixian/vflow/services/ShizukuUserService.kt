// 文件: main/java/com/chaomixian/vflow/services/ShizukuUserService.kt
package com.chaomixian.vflow.services

import android.os.IBinder
import android.util.Log
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 用户服务。
 * 重要：这个类不能继承 Service！
 * UserService 需要是一个普通的类，被 Shizuku 框架托管。
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    // 为服务本身创建一个独立的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watcherJob: Job? = null // 用于持有守护任务的引用

    companion object {
        private const val TAG = "vFlowShellService"
    }

    init {
        DebugLogger.d(TAG, "ShellService 实例被创建")
    }

    override fun destroy() {
        DebugLogger.d(TAG, "收到 destroy 请求 (Shizuku 标准方法)")
        serviceScope.cancel() // [新增] 销毁时取消所有协程
        System.exit(0)
    }

    override fun exec(command: String?): String {
        DebugLogger.d(TAG, "收到命令执行请求: $command")

        if (command.isNullOrBlank()) {
            DebugLogger.w(TAG, "命令为空")
            return "Error: Empty command"
        }

        return try {
            DebugLogger.d(TAG, "开始执行命令: $command")

            // 使用 sh -c 执行命令，这样可以处理管道、重定向等复杂命令
            val processBuilder = ProcessBuilder("sh", "-c", command)
            processBuilder.redirectErrorStream(false)  // 分别处理 stdout 和 stderr

            val process = processBuilder.start()

            // 读取输出
            val stdout = readStream(process.inputStream)
            val stderr = readStream(process.errorStream)

            // 等待进程完成
            val exitCode = process.waitFor()

            DebugLogger.d(TAG, "命令执行完成: exitCode=$exitCode, stdout长度=${stdout.length}, stderr长度=${stderr.length}")

            // 根据退出码返回结果
            when {
                exitCode == 0 -> {
                    if (stdout.isNotEmpty()) stdout else "Command executed successfully"
                }
                stderr.isNotEmpty() -> "Error (code $exitCode): $stderr"
                else -> "Error (code $exitCode): Command failed with no error message"
            }
        } catch (e: SecurityException) {
            val errorMsg = "Permission denied: ${e.message}"
            DebugLogger.e(TAG, errorMsg, e)
            errorMsg
        } catch (e: Exception) {
            val errorMsg = "Exception: ${e.message}"
            DebugLogger.e(TAG, errorMsg, e)
            errorMsg
        }
    }

    override fun exit() {
        DebugLogger.d(TAG, "收到退出请求")
        serviceScope.cancel() // 退出时取消所有协程
        try {
            // 给一点时间让响应返回
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        System.exit(0)
    }

    /**
     * 启动守护任务
     */
    override fun startWatcher(packageName: String?, serviceName: String?) {
        if (packageName.isNullOrBlank() || serviceName.isNullOrBlank()) {
            DebugLogger.w(TAG, "Watcher 启动失败：包名或服务名为空。")
            return
        }
        // 先停止旧的守护任务，确保只有一个在运行
        stopWatcher()
        DebugLogger.i(TAG, "启动服务守护任务: $packageName/$serviceName")
        watcherJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 每 5 分钟检查并尝试启动一次服务
                    delay(5 * 60 * 1000)
                    val command = "am start-service -n $packageName/$serviceName"
                    DebugLogger.d(TAG, "[Watcher] 执行保活命令: $command")
                    // 直接执行启动命令，如果服务已在运行，此命令无害
                    exec(command)
                } catch (e: CancellationException) {
                    DebugLogger.d(TAG, "[Watcher] 守护任务被正常取消。")
                    break
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "[Watcher] 守护任务执行时发生异常。", e)
                    // 发生异常后，等待一段时间再重试
                    delay(60 * 1000)
                }
            }
        }
    }

    /**
     * 停止守护任务
     */
    override fun stopWatcher() {
        if (watcherJob?.isActive == true) {
            DebugLogger.i(TAG, "正在停止服务守护任务...")
            watcherJob?.cancel()
        }
        watcherJob = null
    }

    /**
     * 读取输入流内容
     */
    private fun readStream(inputStream: java.io.InputStream): String {
        return try {
            val result = StringBuilder()
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (result.isNotEmpty()) {
                        result.append('\n')
                    }
                    result.append(line)
                }
            }
            result.toString()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "读取流时发生异常", e)
            "Error reading stream: ${e.message}"
        }
    }

    override fun asBinder(): IBinder {
        DebugLogger.d(TAG, "asBinder() 被调用")
        return super.asBinder()
    }
}