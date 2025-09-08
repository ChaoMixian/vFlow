// 文件: main/java/com/chaomixian/vflow/services/ShizukuDiagnostic.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
        Log.d(TAG, "=== Shizuku 诊断开始 ===")

        // 1. 检查基础状态
        checkBasicStatus()

        // 2. 检查服务类
        checkServiceClass()

        // 3. 检查 Manifest 配置
        checkManifestConfig(context)

        Log.d(TAG, "=== Shizuku 诊断结束 ===")
    }

    private fun checkBasicStatus() {
        Log.d(TAG, "--- 检查基础状态 ---")
        try {
            Log.d(TAG, "Shizuku 版本: ${Shizuku.getVersion()}")
            Log.d(TAG, "Shizuku 可用性 (pingBinder): ${Shizuku.pingBinder()}")
            val permission = if (Shizuku.isPreV11()) Shizuku.checkSelfPermission() else Shizuku.checkRemotePermission("android.permission.SHIZUKU")
            Log.d(TAG, "权限状态: ${if(permission == PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"}")
            Log.d(TAG, "是否需要显示权限说明: ${Shizuku.shouldShowRequestPermissionRationale()}")
        } catch (e: Exception) {
            Log.e(TAG, "检查基础状态失败", e)
        }
    }

    private fun checkServiceClass() {
        Log.d(TAG, "--- 检查服务类 ---")
        try {
            val serviceClass = ShizukuUserService::class.java
            Log.d(TAG, "服务类名: ${serviceClass.name}")
            // 检查是否可以实例化
            val instance = serviceClass.getDeclaredConstructor().newInstance()
            Log.d(TAG, "服务类可以正常实例化: ${instance != null}")
        } catch (e: Exception) {
            Log.e(TAG, "检查服务类时发生异常", e)
        }
    }

    private fun checkManifestConfig(context: Context) {
        Log.d(TAG, "--- 检查 Manifest 配置 ---")
        try {
            val pm = context.packageManager
            // 检查 Provider 配置
            val providers = pm.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers
            val shizukuProvider = providers?.find { it.name == "rikka.shizuku.ShizukuProvider" }

            if (shizukuProvider != null) {
                Log.d(TAG, "找到 ShizukuProvider: ${shizukuProvider.authority}")
                Log.d(TAG, "Provider 启用状态: ${shizukuProvider.enabled}")
                Log.d(TAG, "Provider 导出状态: ${shizukuProvider.exported}")
            } else {
                Log.w(TAG, "未找到 ShizukuProvider 配置!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查 Manifest 配置时发生异常", e)
        }
    }
}