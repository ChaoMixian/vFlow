// 文件: main/java/com/chaomixian/vflow/core/types/basic/VString.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize

/**
 * 文本类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
@Parcelize
data class VString(override val raw: String) : EnhancedBaseVObject(), Parcelable {
    override val type = VTypeRegistry.STRING
    override val propertyRegistry: PropertyRegistry = VStringCompanion.registry

    override fun asString(): String = raw

    override fun asNumber(): Double? = raw.toDoubleOrNull()

    // 空字符串、"false"、"0" 视为 false
    override fun asBoolean(): Boolean =
        raw.isNotEmpty() && !raw.equals("false", ignoreCase = true) && raw != "0"
}

/**
 * VString 的伴生对象，持有共享的属性注册表
 */
object VStringCompanion {
    // 属性注册表：所有 VString 实例共享
    val registry = PropertyRegistry().apply {
        register("length", "len", "长度", "count", getter = { host ->
            VNumber((host as VString).raw.length.toDouble())
        })
        register("uppercase", "大写", "upper", getter = { host ->
            VString((host as VString).raw.uppercase())
        })
        register("lowercase", "小写", "lower", getter = { host ->
            VString((host as VString).raw.lowercase())
        })
        register("trim", "去空格", "trimmed", getter = { host ->
            VString((host as VString).raw.trim())
        })
        register("isempty", "为空", "empty", getter = { host ->
            VBoolean((host as VString).raw.isEmpty())
        })
    }
}