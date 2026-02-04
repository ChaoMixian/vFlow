// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/INfcAdapterWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import com.chaomixian.vflow.server.common.Logger
import org.json.JSONObject
import java.lang.reflect.Method

class INfcAdapterWrapper : ServiceWrapper("nfc", "android.nfc.INfcAdapter\$Stub") {

    private var enableMethod: Method? = null
    private var disableMethod: Method? = null
    private var isEnabledMethod: Method? = null
    private var getStateMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        enableMethod = ReflectionUtils.findMethodLoose(clazz, "enable")
        disableMethod = ReflectionUtils.findMethodLoose(clazz, "disable")
        isEnabledMethod = ReflectionUtils.findMethodLoose(clazz, "isEnabled")
        // 尝试查找 getState 方法作为备选
        getStateMethod = ReflectionUtils.findMethodLoose(clazz, "getState")

        Logger.debug("NfcAdapter", "=== NFC Adapter Methods ===")
        Logger.debug("NfcAdapter", "enable: ${enableMethod != null}")
        Logger.debug("NfcAdapter", "disable: ${disableMethod != null}")
        Logger.debug("NfcAdapter", "isEnabled: ${isEnabledMethod != null}")
        if (isEnabledMethod != null) {
            Logger.debug("NfcAdapter", "isEnabled params: ${isEnabledMethod!!.parameterTypes.toList()}")
        }
        Logger.debug("NfcAdapter", "getState: ${getStateMethod != null}")
        if (getStateMethod != null) {
            Logger.debug("NfcAdapter", "getState params: ${getStateMethod!!.parameterTypes.toList()}")
        }

        // 列出所有相关方法
        Logger.debug("NfcAdapter", "=== All Available Methods ===")
        clazz.declaredMethods.forEach { method ->
            if (method.name.contains("enable") || method.name.contains("Enable") ||
                method.name.contains("state") || method.name.contains("State")) {
                Logger.debug("NfcAdapter", "${method.name}: ${method.parameterTypes.toList()}")
            }
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "NFC service is not available or no permission")
            return result
        }

        when (method) {
            "setNfcEnabled" -> {
                val success = setNfcEnabled(params.getBoolean("enabled"))
                result.put("success", success)
            }
            "isEnabled" -> {
                val enabled = isEnabled()
                result.put("success", true)
                result.put("enabled", enabled)
            }
            "toggle" -> {
                val currentState = isEnabled()
                val newState = !currentState
                val success = setNfcEnabled(newState)
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

    private fun setNfcEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null) return false
        return try {
            val method = if (enable) enableMethod else disableMethod
            if (method == null) return false

            val args = arrayOfNulls<Any>(method.parameterTypes.size)

            // 预加载 AttributionSource 类（如果可用）
            val attributionSourceClass = try {
                Class.forName("android.content.AttributionSource")
            } catch (e: ClassNotFoundException) {
                null
            }

            // 根据参数类型填充默认值
            for (i in args.indices) {
                val paramType = method.parameterTypes[i]
                args[i] = when {
                    // AttributionSource 类型 (Android 12+)
                    attributionSourceClass != null && paramType == attributionSourceClass -> {
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
            }

            val result = method.invoke(serviceInterface, *args) as? Boolean ?: false
            result
        } catch (e: Exception) {
            Logger.error("NfcAdapter", "setNfcEnabled failed: ${e.message}", e)
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

            // 使用 Builder(int uid) 构造函数
            val constructor = builderClass.getConstructor(Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(uid)

            // 尝试设置 packageName
            try {
                val setPackageNameMethod = builderClass.getDeclaredMethod("setPackageName", String::class.java)
                setPackageNameMethod.invoke(builder, "com.android.shell")
            } catch (e: NoSuchMethodException) {
                // ignore
            }

            // 尝试设置 attributionTag
            try {
                val setAttributionTagMethod = builderClass.getDeclaredMethod("setAttributionTag", String::class.java)
                setAttributionTagMethod.invoke(builder, null)
            } catch (e: NoSuchMethodException) {
                // ignore
            }

            // 尝试设置 permission 字段
            try {
                val setPermissionMethod = builderClass.getDeclaredMethod("setPermission", String::class.java)
                setPermissionMethod.invoke(builder, null)
            } catch (e: NoSuchMethodException) {
                // ignore
            }

            val buildMethod = builderClass.getDeclaredMethod("build")
            buildMethod.invoke(builder)
        } catch (e: Exception) {
            Logger.error("NfcAdapter", "Failed to create AttributionSource: ${e.message}", e)
            throw e
        }
    }

    /**
     * 获取NFC当前状态
     */
    private fun isEnabled(): Boolean {
        if (serviceInterface == null) return false

        // 首先尝试使用 isEnabled 方法
        if (isEnabledMethod != null) {
            return try {
                Logger.debug("NfcAdapter", "Using isEnabled method...")
                val paramCount = isEnabledMethod!!.parameterTypes.size
                Logger.debug("NfcAdapter", "isEnabled has $paramCount parameters")

                val result = if (paramCount == 0) {
                    // 无参数版本
                    isEnabledMethod!!.invoke(serviceInterface) as? Boolean ?: false
                } else {
                    // 有参数版本，尝试传入默认值
                    val args = arrayOfNulls<Any>(paramCount)
                    for (i in args.indices) {
                        val paramType = isEnabledMethod!!.parameterTypes[i]
                        args[i] = when {
                            paramType == String::class.java -> "com.android.shell"
                            paramType == Int::class.javaPrimitiveType || paramType == Integer::class.java -> 0
                            else -> null
                        }
                    }
                    isEnabledMethod!!.invoke(serviceInterface, *args) as? Boolean ?: false
                }
                Logger.debug("NfcAdapter", "isEnabled result: $result")
                result
            } catch (e: Exception) {
                Logger.error("NfcAdapter", "isEnabled failed: ${e.message}", e)
                // 继续尝试其他方法
                false
            }
        }

        // 如果 isEnabled 不存在或失败，尝试使用 getState 方法
        if (getStateMethod != null) {
            return try {
                Logger.debug("NfcAdapter", "Using getState method...")
                val state = getStateMethod!!.invoke(serviceInterface) as? Int ?: -1
                Logger.debug("NfcAdapter", "getState result: $state")
                // NfcAdapter 状态常量:
                // STATE_OFF = 1, STATE_TURNING_ON = 2, STATE_ON = 3, STATE_TURNING_OFF = 4
                val isEnabled = state == 3 // STATE_ON
                Logger.info("NfcAdapter", "NFC enabled: $isEnabled (state=$state)")
                isEnabled
            } catch (e: Exception) {
                Logger.error("NfcAdapter", "getState failed: ${e.message}", e)
                false
            }
        }

        // 最后的备选方案：使用 dumpsys 命令
        Logger.warn("NfcAdapter", "Trying dumpsys nfc as fallback...")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys nfc | grep '^mEnabled=' | cut -d'=' -f2"))
            val output = process.inputStream.bufferedReader().readText().trim()
            Logger.debug("NfcAdapter", "dumpsys output: '$output'")
            output.toBoolean() || output == "true"
        } catch (e: Exception) {
            Logger.error("NfcAdapter", "dumpsys failed: ${e.message}", e)
            false
        }
    }
}
