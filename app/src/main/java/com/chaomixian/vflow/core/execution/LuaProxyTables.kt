// 文件: main/java/com/chaomixian/vflow/core/execution/LuaProxyTables.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/**
 * Kotlin Map 的 Lua 代理表。
 *
 * 核心特性：
 * 1. **零拷贝读写**：Lua 中对该表的读写操作直接映射到 Kotlin 的 MutableMap，不产生中间副本。
 * 2. **实时同步**：Kotlin 端修改 Map，Lua 端立即感知；反之亦然。
 * 3. **迭代支持**：支持 Lua 的 pairs() 遍历。
 *
 * @param backingMap 被代理的 Kotlin Map 对象。
 */
class MapProxy(val backingMap: MutableMap<String, Any?>) : LuaTable() {

    /**
     * 拦截 Lua 的读取操作 (table[key])。
     */
    override fun get(key: LuaValue): LuaValue {
        val strKey = key.tojstring()
        // 直接从 Kotlin Map 读取
        val value = backingMap[strKey]
        // 动态转换回 Lua 类型返回
        return LuaValueConverter.coerceToLua(value)
    }

    /**
     * 拦截 Lua 的写入操作 (table[key] = value)。
     */
    override fun set(key: LuaValue, value: LuaValue) {
        val strKey = key.tojstring()
        // 将 Lua 值转换为 Kotlin 类型并写入 Map
        val kotlinValue = LuaValueConverter.coerceToKotlin(value)
        backingMap[strKey] = kotlinValue
    }

    /**
     * 支持 Lua 的 pairs() 迭代器。
     * 重写 next 函数以遍历 Kotlin Map 的键。
     */
    override fun next(key: LuaValue): Varargs {
        val strKey = if (key.isnil()) null else key.tojstring()
        val keys = backingMap.keys.toList()

        val nextIndex = if (strKey == null) 0 else keys.indexOf(strKey) + 1

        if (nextIndex >= 0 && nextIndex < keys.size) {
            val nextKey = keys[nextIndex]
            val nextVal = backingMap[nextKey]
            // 返回 key, value
            return LuaValue.varargsOf(
                LuaValue.valueOf(nextKey),
                LuaValueConverter.coerceToLua(nextVal)
            )
        }
        return LuaValue.NIL
    }

    /**
     * 优化：返回 Map 的大小。
     */
    override fun len(): LuaValue {
        return LuaValue.valueOf(backingMap.size)
    }
}

/**
 * Kotlin Map<String, VObject> 的 Lua 代理表。
 * 用于 vFlow 的统一 VObject 类型系统。
 *
 * @param backingMap 被代理的 Kotlin Map 对象。
 */
class VObjectMapProxy(val backingMap: MutableMap<String, VObject>) : LuaTable() {

    /**
     * 拦截 Lua 的读取操作 (table[key])。
     */
    override fun get(key: LuaValue): LuaValue {
        val strKey = key.tojstring()
        // 直接从 Kotlin Map 读取 VObject
        val value = backingMap[strKey]
        // 将 VObject 转换为 Lua 类型返回
        return LuaValueConverter.coerceToLua(value)
    }

    /**
     * 拦截 Lua 的写入操作 (table[key] = value)。
     */
    override fun set(key: LuaValue, value: LuaValue) {
        val strKey = key.tojstring()
        // 将 Lua 值转换为 VObject 并写入 Map
        val kotlinValue = LuaValueConverter.coerceToKotlin(value)
        backingMap[strKey] = VObjectFactory.from(kotlinValue)
    }

    /**
     * 支持 Lua 的 pairs() 迭代器。
     */
    override fun next(key: LuaValue): Varargs {
        val strKey = if (key.isnil()) null else key.tojstring()
        val keys = backingMap.keys.toList()

        val nextIndex = if (strKey == null) 0 else keys.indexOf(strKey) + 1

        if (nextIndex >= 0 && nextIndex < keys.size) {
            val nextKey = keys[nextIndex]
            val nextVal = backingMap[nextKey]
            // 返回 key, value
            return LuaValue.varargsOf(
                LuaValue.valueOf(nextKey),
                LuaValueConverter.coerceToLua(nextVal)
            )
        }
        return LuaValue.NIL
    }

    /**
     * 返回 Map 的大小。
     */
    override fun len(): LuaValue {
        return LuaValue.valueOf(backingMap.size)
    }
}