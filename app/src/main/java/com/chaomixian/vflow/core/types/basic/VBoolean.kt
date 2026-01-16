// 文件: main/java/com/chaomixian/vflow/core/types/basic/VBoolean.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 布尔类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VBoolean(override val raw: Boolean) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.BOOLEAN
    override val propertyRegistry = Companion.registry

    override fun asString(): String = raw.toString()

    override fun asNumber(): Double? = if (raw) 1.0 else 0.0

    override fun asBoolean(): Boolean = raw

    companion object {
        // 属性注册表：所有 VBoolean 实例共享
        private val registry = PropertyRegistry().apply {
            register("not", "非", "反转", "invert", getter = { host ->
                VBoolean(!(host as VBoolean).raw)
            })
        }
    }
}