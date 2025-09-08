// 文件: main/java/com/chaomixian/vflow/services/ShellService.kt
package com.chaomixian.vflow.services

import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku 用户服务。
 * 重要：这个类不能继承 Service！
 * UserService 需要是一个普通的类，被 Shizuku 框架托管。
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    companion object {
        private const val TAG = "vFlowShellService"
    }

    init {
        Log.d(TAG, "ShellService 实例被创建")
    }

    override fun destroy() {
        Log.d(TAG, "收到 destroy 请求 (Shizuku 标准方法)")
        System.exit(0)
    }

    override fun exec(command: String?): String {
        Log.d(TAG, "收到命令执行请求: $command")

        if (command.isNullOrBlank()) {
            Log.w(TAG, "命令为空")
            return "Error: Empty command"
        }

        return try {
            Log.d(TAG, "开始执行命令: $command")

            // 使用 sh -c 执行命令，这样可以处理管道、重定向等复杂命令
            val processBuilder = ProcessBuilder("sh", "-c", command)
            processBuilder.redirectErrorStream(false)  // 分别处理 stdout 和 stderr

            val process = processBuilder.start()

            // 读取输出
            val stdout = readStream(process.inputStream)
            val stderr = readStream(process.errorStream)

            // 等待进程完成
            val exitCode = process.waitFor()

            Log.d(TAG, "命令执行完成: exitCode=$exitCode, stdout长度=${stdout.length}, stderr长度=${stderr.length}")

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
            Log.e(TAG, errorMsg, e)
            errorMsg
        } catch (e: Exception) {
            val errorMsg = "Exception: ${e.message}"
            Log.e(TAG, errorMsg, e)
            errorMsg
        }
    }

    override fun exit() {
        Log.d(TAG, "收到退出请求")
        try {
            // 给一点时间让响应返回
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        System.exit(0)
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
            Log.e(TAG, "读取流时发生异常", e)
            "Error reading stream: ${e.message}"
        }
    }

    override fun asBinder(): IBinder {
        Log.d(TAG, "asBinder() 被调用")
        return super.asBinder()
    }
}