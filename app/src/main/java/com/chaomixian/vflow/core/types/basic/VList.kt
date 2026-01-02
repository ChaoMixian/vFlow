// 文件: main/java/com/chaomixian/vflow/core/types/basic/VList.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry

class VList(override val raw: List<VObject>) : BaseVObject() {
    override val type = VTypeRegistry.LIST

    override fun asString(): String {
        return raw.joinToString(", ") { it.asString() }
    }

    override fun asNumber(): Double? = raw.size.toDouble()

    override fun asBoolean(): Boolean = raw.isNotEmpty()

    override fun asList(): List<VObject> = raw

    override fun getProperty(propertyName: String): VObject? {
        // 支持通过索引访问，例如 "0", "1"
        val index = propertyName.toIntOrNull()
        if (index != null) {
            // 支持 Python 风格的负数索引 (例如 -1 表示最后一个)
            val actualIndex = if (index < 0) raw.size + index else index
            return if (actualIndex in raw.indices) raw[actualIndex] else VNull
        }

        return when (propertyName.lowercase()) {
            "count", "size", "数量", "长度" -> VNumber(raw.size.toDouble())
            "first", "第一个" -> if (raw.isNotEmpty()) raw.first() else VNull
            "last", "最后一个" -> if (raw.isNotEmpty()) raw.last() else VNull
            "isempty", "为空" -> VBoolean(raw.isEmpty())
            "random", "随机" -> if (raw.isNotEmpty()) raw.random() else VNull
            else -> super.getProperty(propertyName)
        }
    }
}