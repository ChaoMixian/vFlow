// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/ILocationManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * 位置管理器 Wrapper
 * 提供位置相关的功能
 */
class ILocationManagerWrapper : ServiceWrapper("location", "android.location.ILocationManager\$Stub") {

    private var getLastKnownLocationMethod: Method? = null
    private var isProviderEnabledMethod: Method? = null
    private var getAllProvidersMethod: Method? = null
    private var getProviderPropertiesMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        getAllProvidersMethod = ReflectionUtils.findMethodLoose(clazz, "getAllProviders")
        isProviderEnabledMethod = ReflectionUtils.findMethodLoose(clazz, "isProviderEnabled")
        getProviderPropertiesMethod = ReflectionUtils.findMethodLoose(clazz, "getProviderProperties")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Location service is not available or no permission")
            return result
        }

        when (method) {
            "isProviderEnabled" -> {
                val provider = params.getString("provider") ?: "gps"
                val enabled = isProviderEnabled(provider)
                result.put("success", true)
                result.put("enabled", enabled)
            }
            "getAllProviders" -> {
                val providers = getAllProviders()
                result.put("success", true)
                result.put("providers", JSONObject().apply {
                    providers.forEach { provider ->
                        put(provider, isProviderEnabled(provider))
                    }
                })
            }
            "getGpsStatus" -> {
                val enabled = isProviderEnabled("gps")
                result.put("success", true)
                result.put("gps_enabled", enabled)
            }
            "getNetworkLocationStatus" -> {
                val enabled = isProviderEnabled("network")
                result.put("success", true)
                result.put("network_location_enabled", enabled)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    /**
     * 检查指定位置提供者是否启用
     * @param provider 位置提供者名称 ("gps" 或 "network")
     */
    private fun isProviderEnabled(provider: String): Boolean {
        if (serviceInterface == null || isProviderEnabledMethod == null) return false
        return try {
            isProviderEnabledMethod!!.invoke(serviceInterface, provider) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取所有可用的位置提供者
     */
    private fun getAllProviders(): List<String> {
        if (serviceInterface == null || getAllProvidersMethod == null) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            getAllProvidersMethod!!.invoke(serviceInterface) as? List<String> ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
