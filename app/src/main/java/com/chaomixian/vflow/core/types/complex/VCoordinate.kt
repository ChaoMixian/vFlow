// 文件: main/java/com/chaomixian/vflow/core/types/complex/VCoordinate.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 坐标类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
data class VCoordinate(
    val x: Int,
    val y: Int
) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.COORDINATE
    override val raw: Any = this
    override val propertyRegistry = Companion.registry

    override fun asString(): String = "$x,$y"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VCoordinate 实例共享
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            register("x", returnType = VTypeRegistry.NUMBER, displayName = "X 坐标", nameStringRes = R.string.vtype_coordinate_x, getter = { host ->
                VNumber((host as VCoordinate).x.toDouble())
            })
            register("y", returnType = VTypeRegistry.NUMBER, displayName = "Y 坐标", nameStringRes = R.string.vtype_coordinate_y, getter = { host ->
                VNumber((host as VCoordinate).y.toDouble())
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
    }
}
