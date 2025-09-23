// 文件: main/java/com/chaomixian/vflow/services/WifiTriggerReceiver.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.workflow.WorkflowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiTriggerReceiver"
        // 定义一个特殊值，用于在参数中代表“任意网络”
        const val ANY_WIFI_TARGET = "ANY_WIFI"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 确保只响应正确的广播动作
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        // 从 ConnectivityManager 获取网络信息
        @Suppress("DEPRECATION")
        val networkInfo = intent.getParcelableExtra<NetworkInfo>(ConnectivityManager.EXTRA_NETWORK_INFO)
        // 确保事件来自Wi-Fi
        if (networkInfo?.type != ConnectivityManager.TYPE_WIFI) return

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val isConnected = networkInfo.isConnected
        val currentSsid = if (isConnected) wifiManager.connectionInfo?.ssid?.trim('"') else ""

        // 忽略没有有效SSID的连接事件
        if (isConnected && (currentSsid.isNullOrEmpty() || currentSsid == "<unknown ssid>")) {
            return
        }

        val eventType = if (isConnected) "连接到" else "断开连接"
        Log.d(TAG, "Wi-Fi 事件: $eventType, SSID: $currentSsid")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workflowManager = WorkflowManager(context.applicationContext)
                // 筛选出所有已启用的Wi-Fi触发工作流
                val workflows = workflowManager.getAllWorkflows().filter {
                    it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.wifi"
                }

                workflows.forEach { workflow ->
                    val config = workflow.triggerConfig ?: return@forEach
                    val configEvent = config["event"] as? String
                    val configTarget = config["network_target"] as? String // 使用新的参数名

                    // 检查事件类型是否匹配
                    if (configEvent == eventType) {
                        // 检查网络目标是否匹配
                        val targetMatches = (configTarget == ANY_WIFI_TARGET) || (configTarget.equals(currentSsid, ignoreCase = true))

                        if (targetMatches) {
                            Log.i(TAG, "触发工作流 '${workflow.name}'，事件: $eventType $currentSsid")
                            val triggerData = TextVariable(currentSsid ?: "")
                            // BSSID 仅在连接时可用
                            val bssid = if (isConnected) wifiManager.connectionInfo?.bssid else ""
                            val variables = mapOf("bssid" to TextVariable(bssid ?: ""))
                            WorkflowExecutor.execute(workflow, context.applicationContext, triggerData)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}