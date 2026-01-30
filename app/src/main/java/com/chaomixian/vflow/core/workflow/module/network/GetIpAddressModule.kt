// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/GetIpAddressModule.kt
// 描述: 定义了获取设备本地或外部IP地址的模块。

package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URL

/**
 * “获取IP地址”模块。
 * 获取设备的本地（局域网）或外部（公网）IP地址。
 */
class GetIpAddressModule : BaseModule() {

    override val id = "vflow.network.get_ip"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_get_ip_name,
        descriptionStringRes = R.string.module_vflow_network_get_ip_desc,
        name = "获取IP地址",  // Fallback
        description = "获取设备的本地或外部IP地址",  // Fallback
        iconRes = R.drawable.rounded_public_24,
        category = "网络"
    )

    private val typeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_network_get_ip_type_local),
            appContext.getString(R.string.option_vflow_network_get_ip_type_external)
        )
    }
    private val ipVersionOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_network_get_ip_version_ipv4),
            appContext.getString(R.string.option_vflow_network_get_ip_version_ipv6)
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "type",
            name = "类型",
            nameStringRes = R.string.param_vflow_network_get_ip_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = appContext.getString(R.string.option_vflow_network_get_ip_type_local),
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "ip_version",
            name = "IP版本",
            nameStringRes = R.string.param_vflow_network_get_ip_ip_version_name,
            staticType = ParameterType.ENUM,
            defaultValue = appContext.getString(R.string.option_vflow_network_get_ip_version_ipv4),
            options = ipVersionOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "ip_address",
            "IP地址",
            VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_get_ip_ip_address_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val type = step.parameters["type"] as? String ?: ""
        val ipVersion = step.parameters["ip_version"] as? String ?: ""
        val typePill = PillUtil.createPillFromParam(
            step.parameters["type"],
            getInputs().find { it.id == "type" },
            isModuleOption = true
        )
        val versionPill = PillUtil.createPillFromParam(
            step.parameters["ip_version"],
            getInputs().find { it.id == "ip_version" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_network_get_ip), typePill, " ", versionPill, " 地址")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.getVariableAsString("type", "本地")
        val ipVersion = context.getVariableAsString("ip_version", "IPv4")
        onProgress(ProgressUpdate("正在获取 $type $ipVersion 地址..."))

        return try {
            val ipAddress = if (type == "本地") {
                getLocalIpAddress(ipVersion == "IPv6")
            } else {
                getExternalIpAddress(ipVersion == "IPv6")
            }

            if (ipAddress != null) {
                ExecutionResult.Success(mapOf("ip_address" to VString(ipAddress)))
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_get_ip_fetch_failed),
                    "未能找到符合条件的 $type $ipVersion 地址。"
                )
            }
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_network_get_ip_exception),
                e.localizedMessage ?: "发生了未知网络错误"
            )
        }
    }

    /**
     * 获取本地IP地址。
     * @param useIPv6 是否获取IPv6地址，默认为false (获取IPv4)。
     * @return IP地址字符串，或 null。
     */
    private fun getLocalIpAddress(useIPv6: Boolean): String? {
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
            networkInterface.inetAddresses?.toList()?.forEach { inetAddress ->
                if (!inetAddress.isLoopbackAddress) {
                    when {
                        useIPv6 && inetAddress is Inet6Address -> return inetAddress.hostAddress?.substringBefore('%') // 移除 scope id
                        !useIPv6 && inetAddress is Inet4Address -> return inetAddress.hostAddress
                    }
                }
            }
        }
        return null
    }

    /**
     * 获取外部IP地址。
     * @param useIPv6 是否获取IPv6地址。
     * @return IP地址字符串，或 null。
     */
    private fun getExternalIpAddress(useIPv6: Boolean): String? {
        val apiUrl = if (useIPv6) "https://6.ipw.cn" else "https://4.ipw.cn"
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
        return null
    }
}