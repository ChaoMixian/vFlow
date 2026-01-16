// 文件: main/java/com/chaomixian/vflow/core/types/complex/VTime.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 时间类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VTime(val timeString: String) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.TIME
    override val raw: Any = timeString
    override val propertyRegistry = Companion.registry

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

    companion object {
        // 属性注册表：所有 VTime 实例共享
        private val registry = PropertyRegistry().apply {
            register("hour", "时", getter = { host ->
                (host as VTime).parts?.first?.let { VNumber(it.toDouble()) } ?: VNull
            })
            register("minute", "分", getter = { host ->
                (host as VTime).parts?.second?.let { VNumber(it.toDouble()) } ?: VNull
            })
        }
    }
}