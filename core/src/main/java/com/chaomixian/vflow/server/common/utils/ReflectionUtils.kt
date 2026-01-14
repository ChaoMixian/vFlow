package com.chaomixian.vflow.server.common.utils

import java.lang.reflect.Method

object ReflectionUtils {

    fun getMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        var currentClazz: Class<*>? = clazz
        while (currentClazz != null) {
            try {
                return currentClazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            } catch (e: NoSuchMethodException) {
                currentClazz = currentClazz.superclass
            }
        }
        return null
    }

    // 尝试查找方法，忽略参数类型（用于重载不多的情况，兼容性更好）
    fun findMethodLoose(clazz: Class<*>, methodName: String): Method? {
        return clazz.methods.find { it.name == methodName }
    }

    fun <T> invoke(method: Method?, target: Any?, vararg args: Any?): T? {
        return try {
            @Suppress("UNCHECKED_CAST")
            method?.invoke(target, *args) as? T
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}