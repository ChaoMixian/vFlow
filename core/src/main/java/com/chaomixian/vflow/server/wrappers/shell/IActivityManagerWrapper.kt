// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IActivityManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

class IActivityManagerWrapper : ServiceWrapper("activity", "android.app.IActivityManager\$Stub") {

    private var forceStopPackageMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        forceStopPackageMethod = ReflectionUtils.findMethodLoose(service.javaClass, "forceStopPackage")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Activity service is not available or no permission")
            return result
        }

        when (method) {
            "forceStopPackage" -> {
                forceStopPackage(params.getString("package"))
                result.put("success", true)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun forceStopPackage(packageName: String) {
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