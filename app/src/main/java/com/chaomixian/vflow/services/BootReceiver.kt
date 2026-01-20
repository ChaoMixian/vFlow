// 文件: main/java/com/chaomixian/vflow/services/BootReceiver.kt
// 描述: 在设备启动完成后启动 TriggerService。
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chaomixian.vflow.core.logging.DebugLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DebugLogger.d("BootReceiver", "设备启动完成，准备启动 TriggerService。")
            val serviceIntent = Intent(context, TriggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 检查 vFlow Core 自动启动
            val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("core_auto_start_enabled", false)) {
                DebugLogger.i("BootReceiver", "vFlow Core 自动启动已启用，尝试启动...")
                val coreIntent = Intent(context, CoreManagementService::class.java).apply {
                    action = CoreManagementService.ACTION_START_CORE
                    putExtra(CoreManagementService.EXTRA_AUTO_START, true)
                }
                context.startService(coreIntent)
            }
        }
    }
}