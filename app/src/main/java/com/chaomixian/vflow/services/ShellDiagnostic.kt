// 文件: main/java/com/chaomixian/vflow/services/ShellDiagnostic.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.core.logging.DebugLogger
import rikka.shizuku.Shizuku

/**
 * [移植自 vClick]
 * Shell 诊断工具，帮助排查绑定问题
 */
object ShellDiagnostic {

    private const val TAG = "ShellDiagnostic"

    /**
     * 全面诊断 Shell 状态
     */
    fun diagnose(context: Context) {
        DebugLogger.d(TAG, "=== Shell 环境诊断开始 ===")

        // 1. 检查 Shizuku 状态
        checkShizukuStatus()

        // 2. 检查 Root 状态
        checkRootStatus()

        // 3. 检查当前 Shell 偏好
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("default_shell_mode", "shizuku")
        DebugLogger.d(TAG, "当前首选 Shell 模式: $mode")

        DebugLogger.d(TAG, "=== Shell 环境诊断结束 ===")
    }

    private fun checkShizukuStatus() {
        DebugLogger.d(TAG, "--- 检查 Shizuku 状态 ---")
        try {
            if (Shizuku.pingBinder()) {
                DebugLogger.d(TAG, "Shizuku 服务: 已连接 (Version ${Shizuku.getVersion()})")
                val permission = if (Shizuku.isPreV11()) Shizuku.checkSelfPermission() else Shizuku.checkRemotePermission("android.permission.SHIZUKU")
                DebugLogger.d(TAG, "Shizuku 权限: ${if(permission == PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"}")
            } else {
                DebugLogger.w(TAG, "Shizuku 服务: 未连接")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Shizuku 检查异常", e)
        }
    }

    private fun checkRootStatus() {
        DebugLogger.d(TAG, "--- 检查 Root 状态 ---")
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                DebugLogger.d(TAG, "Root 访问: 成功")
                DebugLogger.d(TAG, "Root ID Info: $output")
            } else {
                DebugLogger.w(TAG, "Root 访问: 失败 (Code $exitCode)")
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Root 访问: 不可用 (${e.message})")
        }
    }

    /**
     * 专门用于按键触发器的详细诊断
     */
    suspend fun runKeyEventDiagnostic(context: Context) {
        DebugLogger.d(TAG, "=== 按键触发器深度诊断开始 ===")

        // 使用 ShellManager 自动模式执行诊断命令
        try {
            // 1. 检查 getevent 命令是否存在
            DebugLogger.d(TAG, "1. 检查 getevent 命令...")
            val whichGetevent = ShellManager.execShellCommand(context, "which getevent", ShellManager.ShellMode.AUTO)
            DebugLogger.d(TAG, "   > which getevent: $whichGetevent")

            // 2. 检查 /dev/input 目录权限和列表
            DebugLogger.d(TAG, "2. 检查 /dev/input 设备列表...")
            val lsOutput = ShellManager.execShellCommand(context, "ls -l /dev/input/", ShellManager.ShellMode.AUTO)
            DebugLogger.d(TAG, "   > ls -l /dev/input/ 输出:\n$lsOutput")

            // 3. 尝试读取一次事件流 (非阻塞，仅检查是否有输出或报错)
            // 使用 timeout 防止阻塞，检查是否有权限读取
            DebugLogger.d(TAG, "3. 权限测试 (尝试读取设备信息)...")
            val geteventInfo = ShellManager.execShellCommand(context, "getevent -p")
            if (geteventInfo.length > 500) {
                DebugLogger.d(TAG, "   > getevent -p 输出 (前500字符):\n${geteventInfo.take(500)}...")
            } else {
                DebugLogger.d(TAG, "   > getevent -p 输出:\n$geteventInfo")
            }

            // 4. 检查当前 Shell 用户身份
            val idInfo = ShellManager.execShellCommand(context, "id")
            DebugLogger.d(TAG, "4. Shell 用户身份: $idInfo")

            // 5. 检查当前 cache 目录
            val cacheDir = ShellManager.execShellCommand(context, "ls ${context.cacheDir.absolutePath}")
            DebugLogger.d(TAG, "5. Cache 目录: $cacheDir")

            // 6. 检查脚本文件内容
            val script = ShellManager.execShellCommand(context, "cat ${context.cacheDir.absolutePath}/key*")
            DebugLogger.d(TAG, "6. 按键监听脚本: $script")

        } catch (e: Exception) {
            DebugLogger.e(TAG, "诊断异常", e)
        }
        DebugLogger.d(TAG, "=== 按键触发器深度诊断结束 ===")
    }
}