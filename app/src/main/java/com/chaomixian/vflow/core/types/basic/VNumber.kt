// 文件: main/java/com/chaomixian/vflow/core/types/basic/VNumber.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import kotlin.math.roundToInt

class VNumber(override val raw: Double) : BaseVObject() {
    override val type = VTypeRegistry.NUMBER

    override fun asString(): String {
        // 如果是整数（无小数部分），去掉小数点显示
        return if (raw % 1.0 == 0.0) {
            raw.toLong().toString()
        } else {
            raw.toString()
        }
    }

    override fun asNumber(): Double = raw

    override fun asBoolean(): Boolean = raw != 0.0

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "int", "整数" -> VNumber(raw.toInt().toDouble())
            "round", "四舍五入" -> VNumber(raw.roundToInt().toDouble())
            "abs", "绝对值" -> VNumber(Math.abs(raw))
            else -> super.getProperty(propertyName)
        }
    }
}