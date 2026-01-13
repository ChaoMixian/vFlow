// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IActivityManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers

import com.chaomixian.vflow.server.common.ServiceWrapper
import com.chaomixian.vflow.server.utils.ReflectionUtils
import java.lang.reflect.Method

class IActivityManagerWrapper : ServiceWrapper("activity", "android.app.IActivityManager\$Stub") {

    private var forceStopPackageMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        // forceStopPackage(String packageName, int userId)
        forceStopPackageMethod = ReflectionUtils.findMethodLoose(service.javaClass, "forceStopPackage")
    }

    fun forceStopPackage(packageName: String) {
        if (serviceInterface == null || forceStopPackageMethod == null) return
        try {
            val args = arrayOfNulls<Any>(forceStopPackageMethod!!.parameterTypes.size)
            args[0] = packageName
            if (args.size > 1) args[1] = 0 // userId = 0 (USER_OWNER)
            forceStopPackageMethod!!.invoke(serviceInterface, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}