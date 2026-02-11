package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VScreenElement
import org.mozilla.javascript.*

/**
 * Rhino JavaScript 类型转换器。
 * 负责 Kotlin/vFlow 数据类型与 JavaScript 数据类型之间的双向深度转换。
 */
object JsValueConverter {

    /**
     * 将 Kotlin 对象转换为 JavaScript 值。
     * 支持基础类型、vFlow 变量类型、集合以及特定业务对象。
     */
    fun coerceToJs(context: Context, scope: Scriptable, value: Any?): Any? {
        return when (value) {
            null -> org.mozilla.javascript.Context.getUndefinedValue()
            is Undefined -> org.mozilla.javascript.Context.getUndefinedValue()

            // --- 基础类型 ---
            is String -> value
            is Boolean -> java.lang.Boolean.valueOf(value)
            is Char -> value.toString()

            // --- 数字类型 ---
            is Int -> Integer.valueOf(value)
            is Long -> java.lang.Long.valueOf(value)
            is Float -> java.lang.Float.valueOf(value)
            is Double -> java.lang.Double.valueOf(value)

            // --- VObject 类型拆箱 ---
            is VString -> value.raw
            is VNumber -> {
                val raw = value.raw
                when (raw) {
                    is Int -> Integer.valueOf(raw)
                    is Long -> java.lang.Long.valueOf(raw)
                    is Float -> java.lang.Float.valueOf(raw)
                    is Double -> java.lang.Double.valueOf(raw)
                    else -> raw
                }
            }
            is VBoolean -> java.lang.Boolean.valueOf(value.raw)
            is VList -> {
                val jsArray = context.newArray(scope, value.raw.size)
                value.raw.forEachIndexed { index, item ->
                    jsArray.put(index, jsArray, coerceToJs(context, scope, item))
                }
                jsArray
            }
            is VDictionary -> {
                val jsObj = context.newObject(scope)
                value.raw.forEach { (k, v) ->
                    jsObj.put(k, jsObj, coerceToJs(context, scope, v))
                }
                jsObj
            }

            // --- 业务对象转换 ---
            is VScreenElement -> {
                val jsObj = context.newObject(scope)
                jsObj.put("bounds", jsObj, value.bounds.toShortString())
                jsObj.put("text", jsObj, value.text ?: "")
                jsObj
            }
            is VCoordinate -> {
                val jsObj = context.newObject(scope)
                jsObj.put("x", jsObj, value.x)
                jsObj.put("y", jsObj, value.y)
                jsObj
            }

            // --- 集合类型递归转换 ---
            is Map<*, *> -> {
                val jsObj = context.newObject(scope)
                value.forEach { (k, v) ->
                    jsObj.put(k.toString(), jsObj, coerceToJs(context, scope, v))
                }
                jsObj
            }
            is Iterable<*> -> {
                val jsArray = context.newArray(scope, value.toList().size)
                value.toList().forEachIndexed { index, item ->
                    jsArray.put(index, jsArray, coerceToJs(context, scope, item))
                }
                jsArray
            }
            is Array<*> -> {
                val jsArray = context.newArray(scope, value.size)
                value.forEachIndexed { index, item ->
                    jsArray.put(index, jsArray, coerceToJs(context, scope, item))
                }
                jsArray
            }

            // --- 兜底策略 ---
            else -> value.toString()
        }
    }

    /**
     * 将 JavaScript 值转换为 Kotlin 对象。
     */
    fun coerceToKotlin(value: Any?): Any? {
        return when (value) {
            null -> null
            is org.mozilla.javascript.Undefined -> null

            // --- 基础类型 ---
            is java.lang.Boolean -> value.booleanValue()
            is java.lang.String -> value
            is java.lang.Integer -> value.toInt()
            is java.lang.Long -> value.toLong()
            is java.lang.Float -> value.toFloat()
            is java.lang.Double -> value.toDouble()

            // --- NativeArray (JS 数组) ---
            is NativeArray -> {
                val length = value.length.toInt()
                val list = mutableListOf<Any?>()
                for (i in 0 until length) {
                    list.add(coerceToKotlin(value.get(i)))
                }
                list
            }

            // --- NativeObject (JS 对象) ---
            is NativeObject -> {
                val map = mutableMapOf<String, Any?>()
                val ids = value.ids
                for (id in ids) {
                    val key = id.toString()
                    val propValue = value.get(key)
                    if (propValue != org.mozilla.javascript.Context.getUndefinedValue()) {
                        map[key] = coerceToKotlin(propValue)
                    }
                }
                map
            }

            // --- ScriptableObject (一般 JS 对象) ---
            is ScriptableObject -> {
                val map = mutableMapOf<String, Any?>()
                val ids = value.ids
                for (id in ids) {
                    try {
                        val key = id.toString()
                        val propValue = value.get(key, value)
                        if (propValue != org.mozilla.javascript.Context.getUndefinedValue()) {
                            map[key] = coerceToKotlin(propValue)
                        }
                    } catch (e: Exception) {
                        // 忽略无法访问的属性
                    }
                }
                map
            }

            // NativeJavaObject 可能包装 Java 对象
            is NativeJavaObject -> {
                val wrapped = value.unwrap()
                coerceToKotlin(wrapped)
            }

            // 其他类型直接转字符串
            else -> value.toString()
        }
    }
}
