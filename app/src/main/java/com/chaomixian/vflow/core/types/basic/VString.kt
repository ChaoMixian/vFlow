// 文件: main/java/com/chaomixian/vflow/core/types/basic/VString.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry

class VString(override val raw: String) : BaseVObject() {
    override val type = VTypeRegistry.STRING

    override fun asString(): String = raw

    override fun asNumber(): Double? = raw.toDoubleOrNull()

    // 空字符串、"false"、"0" 视为 false
    override fun asBoolean(): Boolean =
        raw.isNotEmpty() && !raw.equals("false", ignoreCase = true) && raw != "0"

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "length", "长度", "count" -> VNumber(raw.length.toDouble())
            "uppercase", "大写" -> VString(raw.uppercase())
            "lowercase", "小写" -> VString(raw.lowercase())
            "trim", "去空格" -> VString(raw.trim())
            "isempty", "为空" -> VBoolean(raw.isEmpty())
            else -> super.getProperty(propertyName)
        }
    }
}