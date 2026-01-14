// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IClipboardWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import org.json.JSONObject
import java.lang.reflect.Method

class IClipboardWrapper : ServiceWrapper("clipboard", "android.content.IClipboard\$Stub") {

    private var setPrimaryClipMethod: Method? = null
    private var getPrimaryClipMethod: Method? = null

    // ClipData 相关反射缓存
    private var newPlainTextMethod: Method? = null
    private var getItemAtMethod: Method? = null
    private var getTextMethod: Method? = null
    private var clipDataClass: Class<*>? = null

    override fun onServiceConnected(service: Any) {
        val methods = service.javaClass.methods
        setPrimaryClipMethod = methods.find { it.name == "setPrimaryClip" }
        getPrimaryClipMethod = methods.find { it.name == "getPrimaryClip" }

        // 初始化 ClipData 反射
        try {
            clipDataClass = Class.forName("android.content.ClipData")
            val itemClass = Class.forName("android.content.ClipData\$Item")
            newPlainTextMethod = clipDataClass!!.getDeclaredMethod("newPlainText", CharSequence::class.java, CharSequence::class.java)
            getItemAtMethod = clipDataClass!!.getDeclaredMethod("getItemAt", Int::class.javaPrimitiveType)
            getTextMethod = itemClass.getDeclaredMethod("getText")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()
        when (method) {
            "setClipboard" -> {
                val success = setClipboard(params.getString("text"))
                result.put("success", success)
                if (!success) result.put("error", "Failed to set clipboard")
            }
            "getClipboard" -> {
                result.put("text", getClipboard())
                result.put("success", true)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun setClipboard(text: String): Boolean {
        if (serviceInterface == null || setPrimaryClipMethod == null || newPlainTextMethod == null) return false
        return try {
            val clipData = newPlainTextMethod!!.invoke(null, "vFlow", text)

            // 动态参数适配逻辑
            val method = setPrimaryClipMethod!!
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            var stringArgCount = 0

            for (i in method.parameterTypes.indices) {
                args[i] = when (method.parameterTypes[i]) {
                    clipDataClass -> clipData
                    String::class.java -> if (stringArgCount++ == 0) "com.android.shell" else null
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
            method.invoke(serviceInterface, *args)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getClipboard(): String {
        if (serviceInterface == null || getPrimaryClipMethod == null) return ""
        return try {
            val method = getPrimaryClipMethod!!
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            var stringArgCount = 0

            for (i in method.parameterTypes.indices) {
                args[i] = when (method.parameterTypes[i]) {
                    String::class.java -> if (stringArgCount++ == 0) "com.android.shell" else null
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }

            val clipData = method.invoke(serviceInterface, *args) ?: return ""

            // 解析 ClipData
            val item = getItemAtMethod!!.invoke(clipData, 0)
            getTextMethod!!.invoke(item)?.toString() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}