// 文件: main/java/com/chaomixian/vflow/core/types/complex/VTime.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString

class VTime(val timeString: String) : BaseVObject() {
    override val type = VTypeRegistry.TIME
    override val raw: Any = timeString

    // 格式 HH:mm
    private val parts by lazy {
        val split = timeString.split(":")
        if (split.size >= 2) {
            split[0].toIntOrNull() to split[1].toIntOrNull()
        } else null
    }

    override fun asString(): String = timeString

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "hour", "时" -> parts?.first?.let { VNumber(it.toDouble()) }
            "minute", "分" -> parts?.second?.let { VNumber(it.toDouble()) }
            else -> super.getProperty(propertyName)
        }
    }
}