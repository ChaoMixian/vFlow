// 文件: main/java/com/chaomixian/vflow/core/types/complex/VCoordinate.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.interaction.Coordinate

class VCoordinate(val coordinate: Coordinate) : BaseVObject() {
    override val type = VTypeRegistry.COORDINATE
    override val raw: Any = coordinate

    override fun asString(): String = "${coordinate.x},${coordinate.y}"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "x" -> VNumber(coordinate.x.toDouble())
            "y" -> VNumber(coordinate.y.toDouble())
            else -> super.getProperty(propertyName)
        }
    }
}