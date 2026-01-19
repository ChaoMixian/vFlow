// 文件: main/java/com/chaomixian/vflow/core/types/basic/VBoolean.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize

/**
 * 布尔类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
@Parcelize
data class VBoolean(override val raw: Boolean) : EnhancedBaseVObject(), Parcelable {
    override val type = VTypeRegistry.BOOLEAN
    override val propertyRegistry: PropertyRegistry = VBooleanCompanion.registry

    override fun asString(): String = raw.toString()

    override fun asNumber(): Double? = if (raw) 1.0 else 0.0

    override fun asBoolean(): Boolean = raw
}

/**
 * VBoolean 的伴生对象，持有共享的属性注册表
 */
object VBooleanCompanion {
    // 属性注册表：所有 VBoolean 实例共享
    val registry = PropertyRegistry().apply {
        register("not", "非", "反转", "invert", getter = { host ->
            VBoolean(!(host as VBoolean).raw)
        })
    }
}