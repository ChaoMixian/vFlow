// 文件: main/java/com/chaomixian/vflow/core/types/basic/VList.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * 列表类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 *
 * 特殊逻辑：支持数字索引访问（如 list.0, list.-1）
 */
@Parcelize
data class VList(override val raw: @RawValue List<VObject>) : EnhancedBaseVObject(), Parcelable {
    @IgnoredOnParcel
    override val type = VTypeRegistry.LIST
    @IgnoredOnParcel
    override val propertyRegistry: PropertyRegistry = VListCompanion.registry

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

        // 其他属性使用基类的实现（通过 propertyRegistry）
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
}

/**
 * VList 的伴生对象，持有共享的属性注册表
 */
object VListCompanion {
    // 属性注册表：所有 VList 实例共享
    val registry: PropertyRegistry = PropertyRegistry().apply {
        register("count", "size", "数量", "长度", "length", returnType = VTypeRegistry.NUMBER, displayName = "数量", nameStringRes = R.string.vtype_list_count, getter = { host ->
            VNumber((host as VList).raw.size.toDouble())
        })
        register("first", "第一个", "head", returnType = VTypeRegistry.ANY, displayName = "第一项", nameStringRes = R.string.vtype_list_first, getter = { host ->
            val list = (host as VList).raw
            if (list.isNotEmpty()) list.first() else VNull
        })
        register("last", "最后一个", "tail", returnType = VTypeRegistry.ANY, displayName = "最后一项", nameStringRes = R.string.vtype_list_last, getter = { host ->
            val list = (host as VList).raw
            if (list.isNotEmpty()) list.last() else VNull
        })
        register("isempty", "为空", "empty", returnType = VTypeRegistry.BOOLEAN, displayName = "是否为空", nameStringRes = R.string.vtype_list_isempty, getter = { host ->
            VBoolean((host as VList).raw.isEmpty())
        })
        register("random", "随机", "rand", returnType = VTypeRegistry.ANY, displayName = "随机一项", nameStringRes = R.string.vtype_list_random, getter = { host ->
            val list = (host as VList).raw
            if (list.isNotEmpty()) list.random() else VNull
        })
        // 添加一个特殊的属性，用于UI获取索引列表
        register("availableIndices", "可用索引", returnType = VTypeRegistry.LIST, displayName = "可用索引", getter = { host ->
            val list = host as VList
            VList(list.getAvailableIndices().map { VNumber(it.toDouble()) })
        })
    }

    fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
}
