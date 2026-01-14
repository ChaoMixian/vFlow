// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IPowerManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import android.os.SystemClock
import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

class IPowerManagerWrapper : ServiceWrapper("power", "android.os.IPowerManager\$Stub") {

    private var wakeUpMethod: Method? = null
    private var goToSleepMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        wakeUpMethod = ReflectionUtils.findMethodLoose(clazz, "wakeUp")
        goToSleepMethod = ReflectionUtils.findMethodLoose(clazz, "goToSleep")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()
        when (method) {
            "wakeUp" -> { wakeUp(); result.put("success", true) }
            "goToSleep" -> { goToSleep(); result.put("success", true) }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun wakeUp() {
        if (serviceInterface == null || wakeUpMethod == null) return
        try {
            val time = SystemClock.uptimeMillis()
            val paramTypes = wakeUpMethod!!.parameterTypes
            val args = arrayOfNulls<Any>(paramTypes.size)

            // 根据参数类型填充默认值
            for (i in args.indices) {
                val paramType = paramTypes[i]
                args[i] = when {
                    // int 基本类型
                    paramType.isPrimitive && paramType == Int::class.javaPrimitiveType -> Integer.valueOf(0)
                    // long 基本类型
                    paramType.isPrimitive && paramType == Long::class.javaPrimitiveType -> time
                    // Integer 对象类型
                    paramType == Integer::class.java -> Integer.valueOf(0)
                    // Long 对象类型
                    paramType == Long::class.java -> time
                    // String 类型（使用空字符串而不是 null）
                    paramType == String::class.java -> ""
                    // 其他
                    else -> null
                }
            }

            wakeUpMethod!!.invoke(serviceInterface, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun goToSleep() {
        if (serviceInterface == null || goToSleepMethod == null) return
        try {
            val time = SystemClock.uptimeMillis()
            val paramTypes = goToSleepMethod!!.parameterTypes
            val args = arrayOfNulls<Any>(paramTypes.size)

            // 根据参数类型填充默认值
            for (i in args.indices) {
                val paramType = paramTypes[i]
                args[i] = when {
                    // int 基本类型
                    paramType.isPrimitive && paramType == Int::class.javaPrimitiveType -> Integer.valueOf(0)
                    // long 基本类型
                    paramType.isPrimitive && paramType == Long::class.javaPrimitiveType -> time
                    // Integer 对象类型
                    paramType == Integer::class.java -> Integer.valueOf(0)
                    // Long 对象类型
                    paramType == Long::class.java -> time
                    // String 类型（oddo 魔改不允许 null）
                    paramType == String::class.java -> ""
                    // 其他
                    else -> null
                }
            }

            goToSleepMethod!!.invoke(serviceInterface, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}