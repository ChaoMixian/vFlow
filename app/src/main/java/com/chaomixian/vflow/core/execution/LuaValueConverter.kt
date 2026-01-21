// 文件: main/java/com/chaomixian/vflow/core/execution/LuaValueConverter.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.module.Coordinate
import com.chaomixian.vflow.core.module.ScreenElement
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * Lua 类型转换器。
 * 负责 Kotlin/vFlow 数据类型与 Lua 数据类型之间的双向深度转换。
 * 包含对 Proxy Table 的特殊支持。
 */
object LuaValueConverter {

    /**
     * 将 Kotlin 对象转换为 LuaValue。
     * 支持基础类型、vFlow 变量类型、集合以及特定业务对象。
     */
    fun coerceToLua(value: Any?): LuaValue {
        return when (value) {
            null -> LuaValue.NIL
            is LuaValue -> value // 如果已经是 LuaValue，直接返回
            is String -> LuaValue.valueOf(value)
            is Boolean -> LuaValue.valueOf(value)

            // --- 数字类型 ---
            is Int -> LuaValue.valueOf(value)
            is Long -> LuaValue.valueOf(value.toDouble()) // Luaj 对 Long 支持有限，转 Double
            is Float -> LuaValue.valueOf(value.toDouble())
            is Double -> LuaValue.valueOf(value)

            // --- vFlow 变量类型拆箱 ---
            is TextVariable -> LuaValue.valueOf(value.value)
            is NumberVariable -> LuaValue.valueOf(value.value)
            is BooleanVariable -> LuaValue.valueOf(value.value)
            is ImageVariable -> LuaValue.valueOf(value.uri) // 图片转为 URI 字符串
            // 列表和字典变量递归调用自身进行转换
            is DictionaryVariable -> coerceToLua(value.value)
            is ListVariable -> coerceToLua(value.value)

            // --- 业务对象转换 ---
            is ScreenElement -> {
                // 将 ScreenElement 转为 Table: { bounds = "rect string", text = "..." }
                val table = LuaTable()
                table.set("bounds", LuaValue.valueOf(value.bounds.toShortString()))
                table.set("text", LuaValue.valueOf(value.text ?: ""))
                table
            }
            is Coordinate -> {
                // 将 Coordinate 转为 Table: { x = 100, y = 200 }
                val table = LuaTable()
                table.set("x", LuaValue.valueOf(value.x))
                table.set("y", LuaValue.valueOf(value.y))
                table
            }

            // --- 集合类型递归转换 ---
            is Map<*, *> -> {
                val table = LuaTable()
                value.forEach { (k, v) ->
                    // 键和值都进行递归转换
                    table.set(coerceToLua(k), coerceToLua(v))
                }
                table
            }
            is Iterable<*> -> { // 覆盖 List, Set 等
                val table = LuaTable()
                value.forEachIndexed { i, v ->
                    // Lua 数组索引从 1 开始
                    table.set(i + 1, coerceToLua(v))
                }
                table
            }
            is Array<*> -> {
                val table = LuaTable()
                value.forEachIndexed { i, v ->
                    table.set(i + 1, coerceToLua(v))
                }
                table
            }

            // --- 兜底策略 ---
            else -> LuaValue.valueOf(value.toString())
        }
    }

    /**
     * 将 LuaValue 转换为 Kotlin 对象。
     * 支持智能数字转换、Table 转 List/Map，以及 Proxy Table 的直接解包。
     */
    fun coerceToKotlin(value: LuaValue): Any? {
        return when {
            value.isnil() -> null

            // --- 基础类型 ---
            value.isboolean() -> value.toboolean()
            value.isstring() -> value.tojstring()
            value.isnumber() -> {
                val doubleVal = value.todouble()
                // 智能数字转换：如果没有小数部分且在 Long 范围内，转为 Long，否则保留 Double
                if (doubleVal % 1.0 == 0.0 && doubleVal >= Long.MIN_VALUE && doubleVal <= Long.MAX_VALUE) {
                    doubleVal.toLong()
                } else {
                    doubleVal
                }
            }

            // --- Table 处理 ---
            value.istable() -> {
                // [重点优化] 检查是否为 MapProxy
                // 如果是代理表，直接返回底层的 Kotlin Map，避免无意义的拷贝
                if (value is MapProxy) {
                    return value.backingMap
                }

                val table = value.checktable()

                // 启发式判断：是转为 List 还是 Map？
                // 如果 table[1] 存在，且键是连续整数，通常视为 List
                // 为了性能和简化，这里只要 table[1] 存在就优先尝试转 List
                if (!table.get(1).isnil()) {
                    val list = mutableListOf<Any?>()
                    var i = 1
                    while (true) {
                        val v = table.get(i)
                        if (v.isnil()) break
                        // 递归转换列表元素
                        list.add(coerceToKotlin(v))
                        i++
                    }
                    list
                } else {
                    // 转为 Map
                    val map = mutableMapOf<String, Any?>()
                    var key = LuaValue.NIL
                    while (true) {
                        val next = table.next(key)
                        key = next.arg1()
                        if (key.isnil()) break
                        val v = next.arg(2)

                        // 强制将 Key 转为 String (vFlow 变量系统要求 Map Key 为 String)
                        val keyStr = if (key.isstring()) key.tojstring() else key.toString()

                        // 递归转换 Map 值
                        map[keyStr] = coerceToKotlin(v)
                    }
                    map
                }
            }

            // 其他类型直接转字符串
            else -> value.toString()
        }
    }
}