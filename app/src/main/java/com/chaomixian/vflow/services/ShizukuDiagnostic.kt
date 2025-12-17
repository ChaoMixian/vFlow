// 文件: main/java/com/chaomixian/vflow/services/ShizukuDiagnostic.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.chaomixian.vflow.core.logging.DebugLogger
import rikka.shizuku.Shizuku

/**
 * [移植自 vClick]
 * Shizuku 诊断工具，帮助排查绑定问题
 */
object ShizukuDiagnostic {

    private const val TAG = "ShizukuDiagnostic"

    /**
     * 全面诊断 Shizuku 状态
     */
    fun diagnose(context: Context) {
        DebugLogger.d(TAG, "=== Shizuku 诊断开始 ===")

        // 1. 检查基础状态
        checkBasicStatus()

        // 2. 检查服务类
        checkServiceClass()

        // 3. 检查 Manifest 配置
        checkManifestConfig(context)

        DebugLogger.d(TAG, "=== Shizuku 诊断结束 ===")
    }

    private fun checkBasicStatus() {
        DebugLogger.d(TAG, "--- 检查基础状态 ---")
        try {
            DebugLogger.d(TAG, "Shizuku 版本: ${Shizuku.getVersion()}")
            DebugLogger.d(TAG, "Shizuku 可用性 (pingBinder): ${Shizuku.pingBinder()}")
            val permission = if (Shizuku.isPreV11()) Shizuku.checkSelfPermission() else Shizuku.checkRemotePermission("android.permission.SHIZUKU")
            DebugLogger.d(TAG, "权限状态: ${if(permission == PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"}")
            DebugLogger.d(TAG, "是否需要显示权限说明: ${Shizuku.shouldShowRequestPermissionRationale()}")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "检查基础状态失败", e)
        }
    }

    private fun checkServiceClass() {
        DebugLogger.d(TAG, "--- 检查服务类 ---")
        try {
            val serviceClass = ShizukuUserService::class.java
            DebugLogger.d(TAG, "服务类名: ${serviceClass.name}")
            // 检查是否可以实例化
            val instance = serviceClass.getDeclaredConstructor().newInstance()
            DebugLogger.d(TAG, "服务类可以正常实例化: ${instance != null}")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "检查服务类时发生异常", e)
        }
    }

    private fun checkManifestConfig(context: Context) {
        DebugLogger.d(TAG, "--- 检查 Manifest 配置 ---")
        try {
            val pm = context.packageManager
            // 检查 Provider 配置
            val providers = pm.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers
            val shizukuProvider = providers?.find { it.name == "rikka.shizuku.ShizukuProvider" }

            if (shizukuProvider != null) {
                DebugLogger.d(TAG, "找到 ShizukuProvider: ${shizukuProvider.authority}")
                DebugLogger.d(TAG, "Provider 启用状态: ${shizukuProvider.enabled}")
                DebugLogger.d(TAG, "Provider 导出状态: ${shizukuProvider.exported}")
            } else {
                DebugLogger.w(TAG, "未找到 ShizukuProvider 配置!")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "检查 Manifest 配置时发生异常", e)
        }
    }

    /**
     * 专门用于按键触发器的详细诊断
     */
    suspend fun runKeyEventDiagnostic(context: Context) {
        DebugLogger.d(TAG, "=== 按键触发器深度诊断开始 ===")

        if (!ShizukuManager.isShizukuActive(context)) {
            DebugLogger.e(TAG, "诊断终止：Shizuku 未激活或无权限。")
            return
        }

        try {
            // 1. 检查 getevent 命令是否存在
            DebugLogger.d(TAG, "1. 检查 getevent 命令...")
            val whichGetevent = ShizukuManager.execShellCommand(context, "which getevent")
            DebugLogger.d(TAG, "   > which getevent: $whichGetevent")

            // 2. 检查 /dev/input 目录权限和列表
            DebugLogger.d(TAG, "2. 检查 /dev/input 设备列表...")
            val lsOutput = ShizukuManager.execShellCommand(context, "ls -l /dev/input/")
            DebugLogger.d(TAG, "   > ls -l /dev/input/ 输出:\n$lsOutput")

            // 3. 尝试读取一次事件流 (非阻塞，仅检查是否有输出或报错)
            // 使用 timeout 防止阻塞，检查是否有权限读取
            DebugLogger.d(TAG, "3. 权限测试 (尝试读取设备信息)...")
            val geteventInfo = ShizukuManager.execShellCommand(context, "getevent -p")
            if (geteventInfo.length > 500) {
                DebugLogger.d(TAG, "   > getevent -p 输出 (前500字符):\n${geteventInfo.take(500)}...")
            } else {
                DebugLogger.d(TAG, "   > getevent -p 输出:\n$geteventInfo")
            }

            // 4. 检查当前 Shell 用户身份
            val idInfo = ShizukuManager.execShellCommand(context, "id")
            DebugLogger.d(TAG, "4. Shell 用户身份: $idInfo")

            // 5. 检查当前 cache 目录
            val cacheDir = ShizukuManager.execShellCommand(context, "ls ${context.cacheDir.absolutePath}")
            DebugLogger.d(TAG, "5. Cache 目录: $cacheDir")

            // 6. 检查脚本文件内容
            val script = ShizukuManager.execShellCommand(context, "cat ${context.cacheDir.absolutePath}/key*")
            DebugLogger.d(TAG, "6. 按键监听脚本: $script")

        } catch (e: Exception) {
            DebugLogger.e(TAG, "按键触发器诊断过程中发生异常", e)
        }

        DebugLogger.d(TAG, "=== 按键触发器深度诊断结束 ===")
    }
}