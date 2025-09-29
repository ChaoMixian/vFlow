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
        }
    }
}