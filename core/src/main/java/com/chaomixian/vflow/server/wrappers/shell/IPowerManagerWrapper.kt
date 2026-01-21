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
    private var isInteractiveMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        wakeUpMethod = ReflectionUtils.findMethodLoose(clazz, "wakeUp")
        goToSleepMethod = ReflectionUtils.findMethodLoose(clazz, "goToSleep")
        isInteractiveMethod = ReflectionUtils.findMethodLoose(clazz, "isInteractive")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()
        when (method) {
            "wakeUp" -> { wakeUp(); result.put("success", true) }
            "goToSleep" -> { goToSleep(); result.put("success", true) }
            "isInteractive" -> {
                val interactive = isInteractive()
                result.put("success", true)
                result.put("enabled", interactive)
            }
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

    private fun isInteractive(): Boolean {
        if (serviceInterface == null || isInteractiveMethod == null) return false
        return try {
            val paramTypes = isInteractiveMethod!!.parameterTypes
            if (paramTypes.isEmpty()) {
                // 无参数版本
                ReflectionUtils.invoke<Boolean>(isInteractiveMethod, serviceInterface) ?: false
            } else {
                // 有参数版本（可能需要 flags 参数）
                val args = arrayOfNulls<Any>(paramTypes.size)
                for (i in args.indices) {
                    val paramType = paramTypes[i]
                    args[i] = when {
                        paramType.isPrimitive && paramType == Int::class.javaPrimitiveType -> Integer.valueOf(0)
                        paramType == Integer::class.java -> Integer.valueOf(0)
                        else -> null
                    }
                }
                ReflectionUtils.invoke<Boolean>(isInteractiveMethod, serviceInterface, *args) ?: false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}