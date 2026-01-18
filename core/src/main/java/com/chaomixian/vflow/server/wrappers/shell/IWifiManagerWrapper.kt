// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IWifiManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import com.chaomixian.vflow.server.common.Logger
import org.json.JSONObject
import java.lang.reflect.Method

class IWifiManagerWrapper : ServiceWrapper("wifi", "android.net.wifi.IWifiManager\$Stub") {

    private var setWifiEnabledMethod: Method? = null
    private var getWifiEnabledStateMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        setWifiEnabledMethod = ReflectionUtils.findMethodLoose(clazz, "setWifiEnabled")
        getWifiEnabledStateMethod = ReflectionUtils.findMethodLoose(clazz, "getWifiEnabledState")

        Logger.debug("WifiManager", "=== WiFi Manager Methods ===")
        Logger.debug("WifiManager", "setWifiEnabled: ${setWifiEnabledMethod != null}")
        if (setWifiEnabledMethod != null) {
            Logger.debug("WifiManager", "setWifiEnabled params: ${setWifiEnabledMethod!!.parameterTypes.toList()}")
        }
        Logger.debug("WifiManager", "getWifiEnabledState: ${getWifiEnabledStateMethod != null}")
        if (getWifiEnabledStateMethod != null) {
            Logger.debug("WifiManager", "getWifiEnabledState params: ${getWifiEnabledStateMethod!!.parameterTypes.toList()}")
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "WiFi service is not available or no permission")
            return result
        }

        when (method) {
            "setWifiEnabled" -> {
                val success = setWifiEnabled(params.getBoolean("enabled"))
                result.put("success", success)
            }
            "isEnabled" -> {
                val enabled = isWifiEnabled()
                result.put("success", true)
                result.put("enabled", enabled)
            }
            "toggle" -> {
                val currentState = isWifiEnabled()
                val newState = !currentState
                val success = setWifiEnabled(newState)
                result.put("success", success)
                result.put("enabled", newState) // 返回新状态
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun setWifiEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null || setWifiEnabledMethod == null) return false
        return try {
            val args = arrayOfNulls<Any>(setWifiEnabledMethod!!.parameterTypes.size)
            args[0] = "com.android.shell"
            args[1] = enable
            setWifiEnabledMethod!!.invoke(serviceInterface, *args) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取 WiFi 当前状态
     */
    private fun isWifiEnabled(): Boolean {
        if (serviceInterface == null) return false

        // 使用 getWifiEnabledState 方法
        if (getWifiEnabledStateMethod != null) {
            return try {
                Logger.debug("WifiManager", "Using getWifiEnabledState method...")
                val state = getWifiEnabledStateMethod!!.invoke(serviceInterface) as? Int ?: -1
                Logger.debug("WifiManager", "getWifiEnabledState result: $state")

                // WifiManager.WIFI_STATE_DISABLED = 1, WIFI_STATE_DISABLING = 0
                // WifiManager.WIFI_STATE_ENABLED = 3, WIFI_STATE_ENABLING = 2
                val isEnabled = state == 3 // WIFI_STATE_ENABLED
                Logger.info("WifiManager", "WiFi enabled: $isEnabled (state=$state)")
                isEnabled
            } catch (e: Exception) {
                Logger.error("WifiManager", "getWifiEnabledState failed: ${e.message}", e)
                false
            }
        }

        Logger.warn("WifiManager", "No method available to check WiFi state")
        return false
    }
}