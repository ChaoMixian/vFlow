// 文件: main/java/com/chaomixian/vflow/services/CoreManagementService.kt
package com.chaomixian.vflow.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.utils.StorageManager
import kotlinx.coroutines.*
import java.io.File

/**
 * vFlowCore 管理服务。
 * 负责 vFlowCore (app_process) 的部署、启动和停止。
 */
class CoreManagementService : Service() {

    companion object {
        private const val TAG = "CoreManagementService"

        const val ACTION_START_CORE = "com.chaomixian.vflow.action.START_CORE"
        const val ACTION_STOP_CORE = "com.chaomixian.vflow.action.STOP_CORE"
        const val ACTION_RESTART_CORE = "com.chaomixian.vflow.action.RESTART_CORE"
        const val ACTION_CHECK_HEALTH = "com.chaomixian.vflow.action.CHECK_HEALTH"

        private const val CORE_CLASS = "com.chaomixian.vflow.server.VFlowCore"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isStarting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CORE -> startvFlowCoreProcess(forceRestart = false)
            ACTION_RESTART_CORE -> startvFlowCoreProcess(forceRestart = true)
            ACTION_STOP_CORE -> stopvFlowCoreProcess()
            ACTION_CHECK_HEALTH -> checkAndRestartIfNeeded()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        DebugLogger.d(TAG, "CoreManagementService 已销毁")
    }

    /**
     * 检测设备架构
     */
    private fun detectDeviceAbi(): String {
        val primaryAbi = Build.SUPPORTED_ABIS[0]
        return when {
            primaryAbi.startsWith("arm64") -> "arm64-v8a"
            primaryAbi.startsWith("armeabi-v7a") -> "armeabi-v7a"
            primaryAbi.startsWith("x86_64") -> "x86_64"
            primaryAbi.startsWith("x86") -> "x86"
            else -> "arm64-v8a" // 默认
        }
    }

    /**
     * 部署 vflow_shell_exec 到应用私有目录
     */
    private fun deployShellLauncher(): File? {
        val deviceAbi = detectDeviceAbi()
        DebugLogger.d(TAG, "Device ABI: $deviceAbi")

        val execDir = File(applicationContext.filesDir, "bin")
        if (!execDir.exists()) execDir.mkdirs()

        val execFile = File(execDir, "vflow_shell_exec")
        val assetPath = "vflow_shell_exec/$deviceAbi/vflow_shell_exec"

        return try {
            applicationContext.assets.open(assetPath).use { input ->
                execFile.outputStream().use { output -> input.copyTo(output) }
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

    private fun startvFlowCoreProcess(forceRestart: Boolean = false) {
        if (isStarting) return
        serviceScope.launch {
            isStarting = true
            try {
                // 只在明确要求重启时才停止现有进程
                if (forceRestart && VFlowCoreBridge.ping()) {
                    DebugLogger.d(TAG, "强制重启模式：检测到旧的 vFlowCore 正在运行，先停止...")
                    stopvFlowCoreProcess()
                    delay(1500) // 等待进程完全退出并释放 DEX 文件

                    // 确认进程已退出
                    if (VFlowCoreBridge.ping()) {
                        DebugLogger.w(TAG, "进程仍未退出，强制等待...")
                        delay(1000)
                    }
                }

                // 如果 Core 已经在运行且不是强制重启模式，直接返回
                if (!forceRestart && VFlowCoreBridge.ping()) {
                    DebugLogger.d(TAG, "vFlowCore 已在运行，无需启动")
                    isStarting = false
                    return@launch
                }

                DebugLogger.i(TAG, "正在部署并启动 vFlowCore...")

                // 部署 Dex 到 Shell 可读的目录
                val dexFile = File(StorageManager.tempDir, "vFlowCore.dex")

                try {
                    if (!dexFile.parentFile.exists()) dexFile.parentFile.mkdirs()

                    // 即使文件存在也覆盖，确保版本更新
                    applicationContext.assets.open("vFlowCore.dex").use { input ->
                        dexFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    DebugLogger.d(TAG, "Dex 已部署到公共目录: ${dexFile.absolutePath}")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "部署 vFlowCore.dex 失败", e)
                    return@launch
                }

                // 准备日志文件
                val logFile = File(StorageManager.logsDir, "server_process.log")

                // 部署 vflow_shell_exec
                val shellLauncher = deployShellLauncher()

                // 构建启动命令
                val classpath = dexFile.absolutePath
                val logPath = logFile.absolutePath

                var tempScript: File? = null
                val fullCmd = if (shellLauncher != null) {
                    // 创建临时 shell 脚本来正确处理参数
                    // 避免在 sh -c 中使用复杂的引号嵌套
                    tempScript = File(StorageManager.tempDir, "start_vflowcore_${System.currentTimeMillis()}.sh")
                    tempScript.writeText("""
                        #!/system/bin/sh
                        export CLASSPATH="$classpath"
                        exec app_process /system/bin $CORE_CLASS --shell-launcher "${shellLauncher.absolutePath}"
                    """.trimIndent())
                    tempScript.setExecutable(true)

                    DebugLogger.d(TAG, "vflow_shell_exec path: ${shellLauncher.absolutePath}")
                    "sh ${tempScript.absolutePath} > \"$logPath\" 2>&1 & rm -f ${tempScript.absolutePath}"
                } else {
                    // 回退到原有方式
                    "sh -c 'export CLASSPATH=\"$classpath\"; exec app_process /system/bin $CORE_CLASS' > \"$logPath\" 2>&1 &"
                }

                // 执行命令
                DebugLogger.d(TAG, "执行启动命令: $fullCmd")
                val result = ShellManager.execShellCommand(applicationContext, fullCmd, ShellManager.ShellMode.AUTO)

                if (result.startsWith("Error")) {
                    DebugLogger.e(TAG, "vFlowCore 启动命令执行失败: $result")
                } else {
                    DebugLogger.i(TAG, "vFlowCore 启动命令已发送，正在等待响应...")
                    // 给一点时间让进程启动
                    delay(500)
                    if (VFlowCoreBridge.ping()) {
                        DebugLogger.i(TAG, "vFlowCore 启动验证成功！")
                    } else {
                        DebugLogger.w(TAG, "vFlowCore 启动后未立即响应 Ping，请检查日志: $logPath")
                    }
                }

            } catch (e: Exception) {
                DebugLogger.e(TAG, "启动过程发生异常", e)
            } finally {
                isStarting = false
            }
        }
    }

    private fun stopvFlowCoreProcess() {
        serviceScope.launch {
            DebugLogger.i(TAG, "正在停止 vFlowCore...")
            // 优先使用优雅退出
            if (VFlowCoreBridge.shutdown()) {
                DebugLogger.i(TAG, "vFlowCore 已收到退出指令")
                // 等待进程退出
                delay(1000)
            } else {
                // 如果优雅退出失败（比如进程未响应），使用强制杀死
                DebugLogger.w(TAG, "优雅退出失败，使用强制终止")
                val cmd = "pkill -f $CORE_CLASS"
                ShellManager.execShellCommand(applicationContext, cmd, ShellManager.ShellMode.AUTO)
            }
        }
    }

    private fun checkAndRestartIfNeeded() {
        serviceScope.launch {
            if (!VFlowCoreBridge.ping()) {
                DebugLogger.w(TAG, "健康检查：vFlowCore 未响应，正在启动...")
                startvFlowCoreProcess(forceRestart = false)
            }
        }
    }
}