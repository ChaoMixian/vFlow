// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IConnectivityManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * 连接管理器 Wrapper
 * 提供网络连接相关的功能
 */
class IConnectivityManagerWrapper : ServiceWrapper("connectivity", "android.net.IConnectivityManager\$Stub") {

    private var getNetworkInfoMethod: Method? = null
    private var getActiveNetworkInfoMethod: Method? = null
    private var isDefaultNetworkActiveMethod: Method? = null
    private var getAllNetworksMethod: Method? = null
    private var getNetworkCapabilitiesMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        getNetworkInfoMethod = ReflectionUtils.findMethodLoose(clazz, "getNetworkInfo")
        getActiveNetworkInfoMethod = ReflectionUtils.findMethodLoose(clazz, "getActiveNetworkInfo")
        isDefaultNetworkActiveMethod = ReflectionUtils.findMethodLoose(clazz, "isDefaultNetworkActive")
        getAllNetworksMethod = ReflectionUtils.findMethodLoose(clazz, "getAllNetworks")
        getNetworkCapabilitiesMethod = ReflectionUtils.findMethodLoose(clazz, "getNetworkCapabilities")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Connectivity service is not available or no permission")
            return result
        }

        when (method) {
            "getActiveNetworkInfo" -> {
                val info = getActiveNetworkInfo()
                result.put("success", true)
                result.put("info", info)
            }
            "isDefaultNetworkActive" -> {
                val isActive = isDefaultNetworkActive()
                result.put("success", true)
                result.put("active", isActive)
            }
            "isNetworkConnected" -> {
                val networkType = params.optInt("networkType", 0) // 0=mobile, 1=WiFi
                val connected = isNetworkConnected(networkType)
                result.put("success", true)
                result.put("connected", connected)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    /**
     * 获取活动网络信息
     * 返回 JSON 包含类型、状态、是否可用等信息
     */
    private fun getActiveNetworkInfo(): JSONObject {
        if (serviceInterface == null || getActiveNetworkInfoMethod == null) {
            return JSONObject().put("available", false)
        }

        return try {
            val networkInfo = getActiveNetworkInfoMethod!!.invoke(serviceInterface) ?: return JSONObject().put("available", false)

            // 反射获取 NetworkInfo 的属性
            val infoClass = networkInfo.javaClass
            val isConnected = infoClass.getDeclaredMethod("isConnected").invoke(networkInfo) as? Boolean ?: false
            val isAvailable = infoClass.getDeclaredMethod("isAvailable").invoke(networkInfo) as? Boolean ?: false

            val typeNameMethod = infoClass.getDeclaredMethod("getTypeName")
            typeNameMethod.isAccessible = true
            val typeName = typeNameMethod.invoke(networkInfo) as? String ?: "UNKNOWN"

            JSONObject().apply {
                put("available", true)
                put("connected", isConnected)
                put("available", isAvailable)
                put("type", typeName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject().put("available", false)
        }
    }

    /**
     * 检查默认网络是否激活
     */
    private fun isDefaultNetworkActive(): Boolean {
        if (serviceInterface == null || isDefaultNetworkActiveMethod == null) return false
        return try {
            isDefaultNetworkActiveMethod!!.invoke(serviceInterface) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 检查指定网络类型是否连接
     * @param networkType 0=TYPE_MOBILE, 1=TYPE_WIFI
     */
    private fun isNetworkConnected(networkType: Int): Boolean {
        if (serviceInterface == null || getNetworkInfoMethod == null) return false
        return try {
            val networkInfo = getNetworkInfoMethod!!.invoke(serviceInterface, networkType) ?: return false
            val infoClass = networkInfo.javaClass
            infoClass.getDeclaredMethod("isConnected").invoke(networkInfo) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
