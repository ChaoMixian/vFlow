// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IBluetoothManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers

import com.chaomixian.vflow.server.common.ServiceWrapper
import com.chaomixian.vflow.server.utils.ReflectionUtils
import java.lang.reflect.Method

class IBluetoothManagerWrapper : ServiceWrapper("bluetooth_manager", "android.bluetooth.IBluetoothManager\$Stub") {

    private var enableMethod: Method? = null
    private var disableMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        enableMethod = ReflectionUtils.findMethodLoose(clazz, "enable")
        disableMethod = ReflectionUtils.findMethodLoose(clazz, "disable")
    }

    fun setBluetoothEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null) return false
        return try {
            val method = if (enable) enableMethod else disableMethod
            val args = arrayOfNulls<Any>(method?.parameterTypes?.size ?: 0)
            if (args.isNotEmpty()) args[0] = "com.android.shell" // 有些版本需要包名

            method?.invoke(serviceInterface, *args) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}