// 文件: main/java/com/chaomixian/vflow/core/types/basic/VNumber.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

/**
 * 数字类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
@Parcelize
data class VNumber(override val raw: Double) : EnhancedBaseVObject(), Parcelable {
    override val type = VTypeRegistry.NUMBER
    override val propertyRegistry: PropertyRegistry = VNumberCompanion.registry

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
}

/**
 * VNumber 的伴生对象，持有共享的属性注册表
 */
object VNumberCompanion {
    // 属性注册表：所有 VNumber 实例共享
    val registry = PropertyRegistry().apply {
        register("int", "整数", getter = { host ->
            VNumber((host as VNumber).raw.toInt().toDouble())
        })
        register("round", "四舍五入", getter = { host ->
            VNumber((host as VNumber).raw.roundToInt().toDouble())
        })
        register("abs", "绝对值", getter = { host ->
            VNumber(Math.abs((host as VNumber).raw))
        })
        register("length", "len", "长度", getter = { host ->
            VNumber((host as VNumber).raw.toLong().toString().length.toDouble())
        })
    }
}