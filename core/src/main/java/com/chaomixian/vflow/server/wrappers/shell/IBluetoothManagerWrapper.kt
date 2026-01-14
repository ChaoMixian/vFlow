// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IBluetoothManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

class IBluetoothManagerWrapper : ServiceWrapper("bluetooth_manager", "android.bluetooth.IBluetoothManager\$Stub") {

    private var enableMethod: Method? = null
    private var disableMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        enableMethod = ReflectionUtils.findMethodLoose(clazz, "enable")
        disableMethod = ReflectionUtils.findMethodLoose(clazz, "disable")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Bluetooth service is not available or no permission")
            return result
        }

        when (method) {
            "setBluetoothEnabled" -> {
                val success = setBluetoothEnabled(params.getBoolean("enabled"))
                result.put("success", success)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun setBluetoothEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null) return false
        return try {
            val method = if (enable) enableMethod else disableMethod
            if (method == null) return false

            // 打印方法签名
            // System.err.println("=== Bluetooth ${if (enable) "enable" else "disable"} Method ===")
            // System.err.println("Method: ${method.name}")
            // System.err.println("Parameter count: ${method.parameterTypes.size}")
            // for (i in method.parameterTypes.indices) {
            //     System.err.println("  Param[$i]: ${method.parameterTypes[i].name}")
            // }

            val args = arrayOfNulls<Any>(method.parameterTypes.size)

            // 预加载 AttributionSource 类（如果可用）
            val attributionSourceClass = try {
                Class.forName("android.content.AttributionSource")
            } catch (e: ClassNotFoundException) {
                null
            }

            // System.err.println("AttributionSource available: ${attributionSourceClass != null}")

            // 根据参数类型填充默认值
            for (i in args.indices) {
                val paramType = method.parameterTypes[i]
                args[i] = when {
                    // AttributionSource 类型 (Android 12+)
                    attributionSourceClass != null && paramType == attributionSourceClass -> {
                        // 创建 AttributionSource
                        createAttributionSource()
                    }
                    // String 类型 (旧版本 Android 11-)
                    paramType == String::class.java -> "com.android.shell"
                    // int 类型
                    paramType == java.lang.Integer.TYPE || paramType == Int::class.javaPrimitiveType -> Integer.valueOf(0)
                    // boolean 类型
                    paramType == java.lang.Boolean.TYPE || paramType == Boolean::class.javaPrimitiveType -> java.lang.Boolean.FALSE
                    // 其他
                    else -> null
                }
                // System.err.println("  args[$i] = ${args[i]} (${args[i]?.javaClass?.name})")
            }

            val result = method.invoke(serviceInterface, *args) as? Boolean ?: false
            // System.err.println("Result: $result")
            result
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建 AttributionSource 对象 (Android 12+)
     */
    private fun createAttributionSource(): Any {
        return try {
            // 使用 AttributionSource.Builder (Android 12+)
            val builderClass = Class.forName("android.content.AttributionSource\$Builder")

            // AttributionSource.Builder 需要 uid 参数
            // 使用 Process.myUid() 获取当前进程 uid
            val processClass = Class.forName("android.os.Process")
            val myUidMethod = processClass.getDeclaredMethod("myUid")
            val uid = myUidMethod.invoke(null) as Int

            // System.err.println("Creating AttributionSource with uid=$uid, packageName=com.android.shell")

            // 使用 Builder(int uid) 构造函数
            val constructor = builderClass.getConstructor(Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(uid)

            // 尝试设置 packageName
            try {
                val setPackageNameMethod = builderClass.getDeclaredMethod("setPackageName", String::class.java)
                setPackageNameMethod.invoke(builder, "com.android.shell")
                // System.err.println("Set packageName to com.android.shell")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setPackageName method not found, trying setAttributionTag")
            }

            // 尝试设置 attributionTag
            try {
                val setAttributionTagMethod = builderClass.getDeclaredMethod("setAttributionTag", String::class.java)
                setAttributionTagMethod.invoke(builder, null)
                // System.err.println("Set attributionTag to null")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setAttributionTag method not found")
            }

            // 尝试设置 permission 字段
            try {
                val setPermissionMethod = builderClass.getDeclaredMethod("setPermission", String::class.java)
                setPermissionMethod.invoke(builder, null)
                // System.err.println("Set permission to null")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setPermission method not found")
            }

            val buildMethod = builderClass.getDeclaredMethod("build")
            val attributionSource = buildMethod.invoke(builder)

            // System.err.println("AttributionSource created: $attributionSource")
            // System.err.println("AttributionSource details: uid=${getAttributionSourceUid(attributionSource)}, packageName=${getAttributionSourcePackageName(attributionSource)}")

            attributionSource
        } catch (e: Exception) {
            // 如果创建失败，抛出异常
            System.err.println("Failed to create AttributionSource: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun getAttributionSourceUid(attributionSource: Any): Int {
        return try {
            val method = attributionSource.javaClass.getDeclaredMethod("getUid")
            method.invoke(attributionSource) as Int
        } catch (e: Exception) {
            -1
        }
    }

    private fun getAttributionSourcePackageName(attributionSource: Any): String? {
        return try {
            val method = attributionSource.javaClass.getDeclaredMethod("getPackageName")
            method.invoke(attributionSource) as? String
        } catch (e: Exception) {
            null
        }
    }
}