// 文件: main/java/com/chaomixian/vflow/core/types/basic/VBoolean.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry

class VBoolean(override val raw: Boolean) : BaseVObject() {
    override val type = VTypeRegistry.BOOLEAN

    override fun asString(): String = raw.toString()

    override fun asNumber(): Double? = if (raw) 1.0 else 0.0

    override fun asBoolean(): Boolean = raw

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "not", "非", "反转" -> VBoolean(!raw)
            else -> super.getProperty(propertyName)
        }
    }
}