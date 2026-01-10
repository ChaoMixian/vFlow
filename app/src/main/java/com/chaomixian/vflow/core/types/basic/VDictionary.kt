// 文件: main/java/com/chaomixian/vflow/core/types/basic/VDictionary.kt
package com.chaomixian.vflow.core.types.basic

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry

class VDictionary(override val raw: Map<String, VObject>) : BaseVObject() {
    override val type = VTypeRegistry.DICTIONARY

    override fun asString(): String {
        // 生成 JSON 风格的字符串
        return raw.entries.joinToString(prefix = "{", postfix = "}") {
            "\"${it.key}\": \"${it.value.asString()}\""
        }
    }

    override fun asNumber(): Double? = raw.size.toDouble()

    override fun asBoolean(): Boolean = raw.isNotEmpty()

    // 转换为列表时，返回 Values 的列表
    override fun asList(): List<VObject> = raw.values.toList()

    override fun getProperty(propertyName: String): VObject? {
        // 优先查找内置属性
        return when (propertyName.lowercase()) {
            "count", "size", "数量" -> VNumber(raw.size.toDouble())
            "keys", "键" -> VList(raw.keys.map { VString(it) })
            "values", "值" -> VList(raw.values.toList())
            else -> {
                // 如果不是内置属性，则尝试查找 Map 中的 Key
                // 1. 直接匹配
                if (raw.containsKey(propertyName)) return raw[propertyName]

                // 2. 忽略大小写匹配 (为了用户体验)
                val key = raw.keys.find { it.equals(propertyName, ignoreCase = true) }
                if (key != null) return raw[key]

                return VNull
            }
        }
    }
}