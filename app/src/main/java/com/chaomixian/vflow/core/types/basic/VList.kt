// 文件: main/java/com/chaomixian/vflow/core/types/basic/VList.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 列表类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 *
 * 特殊逻辑：支持数字索引访问（如 list.0, list.-1）
 */
class VList(override val raw: List<VObject>) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.LIST
    override val propertyRegistry = Companion.registry

    override fun asString(): String {
        return raw.joinToString(", ") { it.asString() }
    }

    override fun asNumber(): Double? = raw.size.toDouble()

    override fun asBoolean(): Boolean = raw.isNotEmpty()

    override fun asList(): List<VObject> = raw

    /**
     * 重写 getProperty 以支持数字索引访问
     * 优先级：数字索引 > 内置属性 > VNull
     */
    override fun getProperty(propertyName: String): VObject? {
        // 特殊处理：数字索引访问
        val index = propertyName.toIntOrNull()
        if (index != null) {
            // 支持 Python 风格的负数索引 (例如 -1 表示最后一个)
            val actualIndex = if (index < 0) raw.size + index else index
            return if (actualIndex in raw.indices) raw[actualIndex] else VNull
        }

        // 其他属性使用注册表
        return super.getProperty(propertyName)
    }

    /**
     * 获取所有可用的索引（供UI使用）
     * 返回格式：索引的列表，从 0 到 size-1
     */
    fun getAvailableIndices(): List<Int> = raw.indices.toList()

    /**
     * 获取所有可用的索引及其值类型（供UI展示）
     */
    fun getAvailableIndicesWithTypes(): List<Pair<Int, String>> {
        return raw.mapIndexed { index, value ->
            val typeName = when (value) {
                is VString -> "文本"
                is VNumber -> "数字"
                is VBoolean -> "布尔"
                is VList -> "列表"
                is VDictionary -> "字典"
                else -> value.type.name
            }
            index to typeName
        }
    }

    companion object {
        // 属性注册表：所有 VList 实例共享
        private val registry = PropertyRegistry().apply {
            register("count", "size", "数量", "长度", "length", getter = { host ->
                VNumber((host as VList).raw.size.toDouble())
            })
            register("first", "第一个", "head", getter = { host ->
                val list = (host as VList).raw
                if (list.isNotEmpty()) list.first() else VNull
            })
            register("last", "最后一个", "tail", getter = { host ->
                val list = (host as VList).raw
                if (list.isNotEmpty()) list.last() else VNull
            })
            register("isempty", "为空", "empty", getter = { host ->
                VBoolean((host as VList).raw.isEmpty())
            })
            register("random", "随机", "rand", getter = { host ->
                val list = (host as VList).raw
                if (list.isNotEmpty()) list.random() else VNull
            })
            // 添加一个特殊的属性，用于UI获取索引列表
            register("availableIndices", "可用索引", getter = { host ->
                val list = host as VList
                VList(list.getAvailableIndices().map { VNumber(it.toDouble()) })
            })
        }
    }
}