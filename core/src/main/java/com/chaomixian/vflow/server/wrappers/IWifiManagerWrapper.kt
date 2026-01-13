// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IWifiManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers

import com.chaomixian.vflow.server.common.ServiceWrapper
import com.chaomixian.vflow.server.utils.ReflectionUtils
import java.lang.reflect.Method

class IWifiManagerWrapper : ServiceWrapper("wifi", "android.net.wifi.IWifiManager\$Stub") {

    private var setWifiEnabledMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        // setWifiEnabled(String packageName, boolean enable)
        setWifiEnabledMethod = ReflectionUtils.findMethodLoose(service.javaClass, "setWifiEnabled")
    }

    fun setWifiEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null || setWifiEnabledMethod == null) return false
        return try {
            val args = arrayOfNulls<Any>(setWifiEnabledMethod!!.parameterTypes.size)
            //通常第一个参数是包名，第二个是boolean
            args[0] = "com.android.shell"
            args[1] = enable
            setWifiEnabledMethod!!.invoke(serviceInterface, *args) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}