// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IWifiManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

class IWifiManagerWrapper : ServiceWrapper("wifi", "android.net.wifi.IWifiManager\$Stub") {

    private var setWifiEnabledMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        setWifiEnabledMethod = ReflectionUtils.findMethodLoose(service.javaClass, "setWifiEnabled")
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
}