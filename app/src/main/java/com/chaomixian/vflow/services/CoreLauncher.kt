// 文件: main/java/com/chaomixian/vflow/services/CoreLauncher.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.permissions.PermissionManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * vFlow Core 启动器。
 *
 * 职责：
 * - 仅负责启动 vFlowCore 进程
 * - 不负责生命周期管理（由 CoreManager 负责）
 * - 不负责健康检查（由 CoreManager 负责）
 */
object CoreLauncher {
    private const val TAG = "CoreLauncher"
    private const val CORE_CLASS = "com.chaomixian.vflow.server.VFlowCore"

    /**
     * 启动模式
     */
    enum class LaunchMode {
        AUTO,    // 自动根据偏好选择
        SHIZUKU, // 强制使用 Shizuku
        ROOT     // 强制使用 Root
    }

    /**
     * 启动结果
     */
    data class LaunchResult(
        val success: Boolean,
        val mode: LaunchMode,
        val privilegeMode: VFlowCoreBridge.PrivilegeMode = VFlowCoreBridge.PrivilegeMode.NONE,
        val error: String? = null
    )

    /**
     * 启动 vFlowCore 进程。
     *
     * @param context 上下文
     * @param mode 启动模式
     * @param forceRestart 是否强制重启（如果已运行）
     * @return 启动结果
     */
    suspend fun launch(
        context: Context,
        mode: LaunchMode = LaunchMode.AUTO,
        forceRestart: Boolean = false
    ): LaunchResult {
        DebugLogger.i(TAG, "准备启动 vFlowCore (模式: $mode, 强制重启: $forceRestart)...")

        // 如果 Core 已经在运行且不是强制重启模式，直接返回
        if (!forceRestart && VFlowCoreBridge.ping()) {
            DebugLogger.d(TAG, "vFlowCore 已在运行，无需启动")
            return LaunchResult(
                success = true,
                mode = mode,
                privilegeMode = VFlowCoreBridge.privilegeMode
            )
        }

        // 如果是强制重启且 Core 正在运行，先停止
        if (forceRestart && VFlowCoreBridge.ping()) {
            DebugLogger.d(TAG, "强制重启模式：检测到旧的 vFlowCore 正在运行，先停止...")
            stop(context)
            delay(1500) // 等待进程完全退出并释放 DEX 文件

            // 确认进程已退出
            if (VFlowCoreBridge.ping()) {
                DebugLogger.w(TAG, "进程仍未退出，强制等待...")
                delay(1000)
            }
        }

        return try {
            // 1. 部署 Dex 到公共目录
            val dexFile = deployDex(context)
            if (dexFile == null) {
                DebugLogger.e(TAG, "部署 vFlowCore.dex 失败")
                return LaunchResult(
                    success = false,
                    mode = mode,
                    error = "部署 vFlowCore.dex 失败"
                )
            }

            // 2. 准备日志文件
            val logFile = File(StorageManager.logsDir, "server_process.log")

            // 3. 确定最终使用的 ShellMode
            val finalMode = if (mode == LaunchMode.AUTO) {
                val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
                val preferredMode = prefs.getString("preferred_core_launch_mode", "shizuku")
                if (preferredMode == "root") LaunchMode.ROOT else LaunchMode.SHIZUKU
            } else {
                mode
            }

            // 4. 构建启动命令
            val command = buildLaunchCommand(dexFile, logFile, finalMode, context)

            // 5. 执行启动命令
            DebugLogger.d(TAG, "执行启动命令: $command (ShellMode: $finalMode)")
            val shellMode = when (finalMode) {
                LaunchMode.ROOT -> ShellManager.ShellMode.ROOT
                LaunchMode.SHIZUKU -> ShellManager.ShellMode.SHIZUKU
                LaunchMode.AUTO -> ShellManager.ShellMode.AUTO
            }

            val result = ShellManager.execShellCommand(context, command, shellMode)

            if (result.startsWith("Error")) {
                DebugLogger.e(TAG, "vFlowCore 启动命令执行失败: $result")
                LaunchResult(
                    success = false,
                    mode = finalMode,
                    error = result
                )
            } else {
                DebugLogger.i(TAG, "vFlowCore 启动命令已发送，正在等待响应...")
                // 给一点时间让进程启动
                delay(500)

                // 验证启动（带重试机制，最多等待5秒）
                var success = false
                for (i in 1..10) {
                    if (VFlowCoreBridge.ping()) {
                        DebugLogger.i(TAG, "vFlowCore 启动验证成功！(尝试 $i/10) 权限: ${VFlowCoreBridge.privilegeMode}")
                        success = true
                        break
                    }
                    if (i < 10) {
                        DebugLogger.d(TAG, "vFlowCore 未响应，继续等待... ($i/10)")
                        delay(500) // 每次等待500ms，总共最多5秒
                    }
                }

                if (success) {
                    LaunchResult(
                        success = true,
                        mode = finalMode,
                        privilegeMode = VFlowCoreBridge.privilegeMode
                    )
                } else {
                    DebugLogger.w(TAG, "vFlowCore 启动后未响应 Ping (尝试10次后仍失败)，请检查日志: ${logFile.absolutePath}")
                    LaunchResult(
                        success = false,
                        mode = finalMode,
                        error = "启动后未响应 Ping (等待5秒后超时)"
                    )
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "启动过程发生异常", e)
            LaunchResult(
                success = false,
                mode = mode,
                error = e.message
            )
        }
    }

    /**
     * 停止 vFlowCore 进程。
     * 发送 shutdown 请求让 Core 自己退出，不使用 pkill。
     */
    suspend fun stop(context: Context): Boolean {
        DebugLogger.i(TAG, "正在停止 vFlowCore...")

        // 1. 发送优雅退出请求
        val shutdownSent = VFlowCoreBridge.shutdown()

        if (!shutdownSent) {
            DebugLogger.w(TAG, "发送 shutdown 请求失败，可能 Core 已停止")
        } else {
            DebugLogger.i(TAG, "shutdown 请求已成功发送")
        }

        // 2. 主动断开 Bridge 连接（避免 ping() 重连）
        VFlowCoreBridge.disconnect()

        // 3. 等待 Core 进程退出（不给时间让Core退出，直接返回）
        // Core 进程会在后台自行退出
        DebugLogger.i(TAG, "vFlowCore 停止请求已完成")

        return true
    }

    /**
     * 重启 vFlowCore 进程。
     */
    suspend fun restart(context: Context, mode: LaunchMode = LaunchMode.AUTO): LaunchResult {
        DebugLogger.i(TAG, "正在重启 vFlowCore...")
        stop(context)
        delay(500) // 等待进程完全退出
        return launch(context, mode, forceRestart = true)
    }

    /**
     * 部署 vFlowCore.dex 到公共目录。
     */
    private fun deployDex(context: Context): File? {
        // 检查存储权限
        if (!PermissionManager.isGranted(context, PermissionManager.STORAGE)) {
            DebugLogger.w(TAG, "存储权限未授予，无法部署 vFlowCore.dex")
            Toast.makeText(
                context,
                "需要文件访问权限才能启动 vFlowCore",
                Toast.LENGTH_LONG
            ).show()
            return null
        }

        val dexFile = File(StorageManager.tempDir, "vFlowCore.dex")

        try {
            // StorageManager.tempDir 已经会创建目录，不需要额外检查
            context.assets.open("vFlowCore.dex").use { input ->
                dexFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    DebugLogger.d(TAG, "Dex 已部署到公共目录: ${dexFile.absolutePath} ($bytesCopied bytes)")

                    // 验证文件大小
                    if (dexFile.length() == 0L) {
                        DebugLogger.e(TAG, "部署的文件大小为0")
                        return null
                    }
                }
            }

            // 设置文件可读
            dexFile.setReadable(true, false)

            return dexFile
        } catch (e: Exception) {
            DebugLogger.e(TAG, "部署 vFlowCore.dex 失败", e)
            Toast.makeText(
                context,
                "部署 vFlowCore.dex 失败: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            return null
        }
    }

    /**
     * 构建启动命令。
     */
    private fun buildLaunchCommand(
        dexFile: File,
        logFile: File,
        mode: LaunchMode,
        context: Context
    ): String {
        val classpath = dexFile.absolutePath
        val logPath = logFile.absolutePath

        return if (mode == LaunchMode.ROOT) {
            // ROOT 模式：需要部署 vflow_shell_exec 来降权启动 shell worker
            val shellLauncher = deployShellLauncher(context)
            if (shellLauncher != null) {
                // 创建临时 shell 脚本来正确处理参数
                val tempScript = File(StorageManager.tempDir, "start_vflowcore_${System.currentTimeMillis()}.sh")
                tempScript.writeText("""
                    #!/system/bin/sh
                    export CLASSPATH="$classpath"
                    exec app_process /system/bin $CORE_CLASS --shell-launcher "${shellLauncher.absolutePath}"
                """.trimIndent())
                tempScript.setExecutable(true)

                DebugLogger.d(TAG, "ROOT 模式：使用 vflow_shell_exec 降权，path: ${shellLauncher.absolutePath}")
                "sh ${tempScript.absolutePath} > \"$logPath\" 2>&1 & rm -f ${tempScript.absolutePath}"
            } else {
                DebugLogger.w(TAG, "ROOT 模式但 vflow_shell_exec 部署失败，回退到直接启动（可能有权限问题）")
                "sh -c 'export CLASSPATH=\"$classpath\"; exec app_process /system/bin $CORE_CLASS' > \"$logPath\" 2>&1 &"
            }
        } else {
            // Shizuku 或 AUTO 模式：Master 以 shell 权限运行，无需降权，直接启动
            DebugLogger.d(TAG, "Shell 模式：直接启动（无需降权）")
            "sh -c 'export CLASSPATH=\"$classpath\"; exec app_process /system/bin $CORE_CLASS' > \"$logPath\" 2>&1 &"
        }
    }

    /**
     * 部署 vflow_shell_exec 到应用私有目录。
     */
    private fun deployShellLauncher(context: Context): File? {
        val deviceAbi = detectDeviceAbi()
        DebugLogger.d(TAG, "Device ABI: $deviceAbi")

        val execDir = File(context.filesDir, "bin")
        if (!execDir.exists()) {
            execDir.mkdirs()
        }

        val execFile = File(execDir, "vflow_shell_exec")
        val assetPath = "vflow_shell_exec/$deviceAbi/vflow_shell_exec"

        return try {
            context.assets.open(assetPath).use { input ->
                execFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 设置可执行权限 (755: rwxr-xr-x)
            execFile.setExecutable(true, false)
            execFile.setReadable(true, false)
            execFile.setWritable(true, true) // 仅所有者可写

            DebugLogger.d(TAG, "vflow_shell_exec deployed: ${execFile.absolutePath}")
            execFile
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to deploy vflow_shell_exec", e)
            null
        }
    }

    /**
     * 检测设备架构。
     */
    private fun detectDeviceAbi(): String {
        val primaryAbi = android.os.Build.SUPPORTED_ABIS[0]
        return when {
            primaryAbi.startsWith("arm64") -> "arm64-v8a"
            primaryAbi.startsWith("armeabi-v7a") -> "armeabi-v7a"
            primaryAbi.startsWith("x86_64") -> "x86_64"
            primaryAbi.startsWith("x86") -> "x86"
            else -> "arm64-v8a" // 默认
        }
    }
}
