// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/WifiTriggerHandler.kt
// 描述: 增加了对 Wi-Fi 开关状态的监听，现在能同时处理两种类型的触发事件。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WifiTriggerHandler : ListeningTriggerHandler() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private val activeWifiConnections = ConcurrentHashMap<Network, String>()

    companion object {
        private const val TAG = "WifiTriggerHandler"
        const val ANY_WIFI_TARGET = "ANY_WIFI"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.wifi"

    override fun startListening(context: Context) {
        DebugLogger.d(TAG, "启动 Wi-Fi 监听...")
        // 注册网络连接回调
        registerNetworkCallback(context)
        // 注册 Wi-Fi 开关状态广播
        registerWifiStateReceiver(context)
    }

    override fun stopListening(context: Context) {
        DebugLogger.d(TAG, "停止 Wi-Fi 监听...")
        unregisterNetworkCallback()
        unregisterWifiStateReceiver(context)
    }

    // --- Network Connection Logic ---

    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                triggerScope.launch {
                    val ssid = getWifiSsidWithRetry(context)
                    if (ssid != null) {
                        activeWifiConnections[network] = ssid
                        DebugLogger.d(TAG, "网络已连接: $ssid")
                        findAndExecuteWorkflows(context, "网络连接", "连接到", ssid)
                    }
                }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                activeWifiConnections.remove(network)?.let { lostSsid ->
                    DebugLogger.d(TAG, "网络已断开: $lostSsid")
                    findAndExecuteWorkflows(context, "网络连接", "断开连接", lostSsid)
                }
            }
        }
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } finally {
                networkCallback = null
                activeWifiConnections.clear()
            }
        }
    }

    // --- Wifi State Logic ---

    private fun registerWifiStateReceiver(context: Context) {
        if (wifiStateReceiver != null) return
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    when (wifiState) {
                        WifiManager.WIFI_STATE_ENABLED -> {
                            DebugLogger.d(TAG, "Wi-Fi 已开启")
                            findAndExecuteWorkflows(context, "Wi-Fi状态", "开启时", null)
                        }
                        WifiManager.WIFI_STATE_DISABLED -> {
                            DebugLogger.d(TAG, "Wi-Fi 已关闭")
                            findAndExecuteWorkflows(context, "Wi-Fi状态", "关闭时", null)
                        }
                    }
                }
            }
        }
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
    }

    private fun unregisterWifiStateReceiver(context: Context) {
        wifiStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } finally {
                wifiStateReceiver = null
            }
        }
    }

    // --- Common Execution Logic ---

    private fun findAndExecuteWorkflows(context: Context, triggerType: String, event: String, ssid: String?) {
        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val configTriggerType = config["trigger_type"] as? String
                if (configTriggerType != triggerType) return@forEach

                if (triggerType == "网络连接") {
                    val configEvent = config["connection_event"] as? String
                    val configTarget = config["network_target"] as? String
                    val targetMatches = (configTarget == ANY_WIFI_TARGET) || (configTarget.equals(ssid, ignoreCase = true))
                    if (configEvent == event && targetMatches) {
                        DebugLogger.i(TAG, "触发工作流 '${workflow.name}'，事件: $event '$ssid'")
                        val triggerData = VString(ssid ?: "")
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val bssid = if (event == "连接到") wifiManager.connectionInfo?.bssid else ""
                        val variables = mapOf("bssid" to VString(bssid ?: ""))
                        WorkflowExecutor.execute(workflow, context.applicationContext, triggerData)
                    }
                } else { // Wi-Fi状态
                    val configEvent = config["state_event"] as? String
                    if (configEvent == event) {
                        DebugLogger.i(TAG, "触发工作流 '${workflow.name}'，事件: Wi-Fi $event")
                        WorkflowExecutor.execute(workflow, context.applicationContext)
                    }
                }
            }
        }
    }

    private suspend fun getWifiSsidWithRetry(context: Context, retries: Int = 3, delayMs: Long = 200): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        for (i in 1..retries) {
            val ssid = wifiManager.connectionInfo?.ssid?.trim('"')
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                return ssid
            }
            delay(delayMs)
        }
        return null
    }
}