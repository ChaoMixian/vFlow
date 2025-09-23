// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/WifiTriggerHandler.kt
// 描述: 采用有状态的 NetworkCallback，可靠地跟踪连接和断开事件，并增加SSID获取的稳定性。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

class WifiTriggerHandler : BaseTriggerHandler() {

    private val listeningWorkflows = CopyOnWriteArrayList<Workflow>()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 用于存储当前连接的网络状态
    private val activeWifiConnections = ConcurrentHashMap<Network, String>()

    companion object {
        private const val TAG = "WifiTriggerHandler"
        const val ANY_WIFI_TARGET = "ANY_WIFI"
    }

    override fun start(context: Context) {
        super.start(context)
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (hasActiveWifiWorkflows()) {
            registerNetworkCallback(context)
        }
        Log.d(TAG, "WifiTriggerHandler 已启动。")
    }

    override fun stop(context: Context) {
        super.stop(context)
        unregisterNetworkCallback()
        Log.d(TAG, "WifiTriggerHandler 已停止。")
    }

    override fun addWorkflow(context: Context, workflow: Workflow) {
        listeningWorkflows.removeAll { it.id == workflow.id }
        listeningWorkflows.add(workflow)
        Log.d(TAG, "已添加 '${workflow.name}'。监听数量: ${listeningWorkflows.size}")
        if (networkCallback == null && listeningWorkflows.isNotEmpty()) {
            registerNetworkCallback(context)
        }
    }

    override fun removeWorkflow(context: Context, workflowId: String) {
        if (listeningWorkflows.removeAll { it.id == workflowId }) {
            Log.d(TAG, "已移除 workflowId: $workflowId。监听数量: ${listeningWorkflows.size}")
            if (listeningWorkflows.isEmpty()) {
                unregisterNetworkCallback()
            }
        }
    }

    private fun hasActiveWifiWorkflows(): Boolean {
        return workflowManager.getAllWorkflows().any {
            it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.wifi"
        }
    }

    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        Log.d(TAG, "正在注册 NetworkCallback...")

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
                        Log.d(TAG, "Wi-Fi 已连接: $ssid (Network: ${network})")
                        handleWifiChangeEvent(context, "连接到", ssid)
                    } else {
                        Log.w(TAG, "Wi-Fi 已连接，但无法获取 SSID。")
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // 从我们记录的连接中移除，并用记录的SSID触发事件
                val lostSsid = activeWifiConnections.remove(network)
                if (lostSsid != null) {
                    Log.d(TAG, "Wi-Fi 已断开: $lostSsid (Network: ${network})")
                    handleWifiChangeEvent(context, "断开连接", lostSsid)
                }
            }
        }
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            Log.d(TAG, "正在注销 NetworkCallback。")
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } finally {
                networkCallback = null
                activeWifiConnections.clear()
            }
        }
    }

    /**
     * 带重试机制的SSID获取函数，提高稳定性
     */
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

    private fun handleWifiChangeEvent(context: Context, eventType: String, ssid: String) {
        listeningWorkflows.forEach { workflow ->
            val config = workflow.triggerConfig ?: return@forEach
            val configEvent = config["event"] as? String
            val configTarget = config["network_target"] as? String

            if (configEvent == eventType) {
                val targetMatches = (configTarget == ANY_WIFI_TARGET) || (configTarget.equals(ssid, ignoreCase = true))
                if (targetMatches) {
                    triggerScope.launch {
                        Log.i(TAG, "触发工作流 '${workflow.name}'，事件: $eventType '$ssid'")
                        val triggerData = TextVariable(ssid)
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val bssid = if (eventType == "连接到") wifiManager.connectionInfo?.bssid else ""
                        val variables = mapOf("bssid" to TextVariable(bssid ?: ""))
                        WorkflowExecutor.execute(workflow, context.applicationContext, triggerData)
                    }
                }
            }
        }
    }
}