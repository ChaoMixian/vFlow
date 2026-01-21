// 文件: main/java/com/chaomixian/vflow/core/types/complex/VCoordinate.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import com.chaomixian.vflow.core.module.Coordinate

/**
 * 坐标类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VCoordinate(val coordinate: Coordinate) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.COORDINATE
    override val raw: Any = coordinate
    override val propertyRegistry = Companion.registry

    override fun asString(): String = "${coordinate.x},${coordinate.y}"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VCoordinate 实例共享
        private val registry = PropertyRegistry().apply {
            register("x", getter = { host ->
                VNumber((host as VCoordinate).coordinate.x.toDouble())
            })
            register("y", getter = { host ->
                VNumber((host as VCoordinate).coordinate.y.toDouble())
            })
        }
    }
}