// 文件: main/java/com/chaomixian/vflow/core/types/complex/VDate.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VDate(val dateString: String) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.DATE
    override val raw: Any = dateString
    override val propertyRegistry = Companion.registry

    // 假设格式为 yyyy-MM-dd，如果解析失败则使用当前时间或抛错
    private val calendar: Calendar by lazy {
        val cal = Calendar.getInstance()
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            cal.time = sdf.parse(dateString) ?: Date()
        } catch (e: Exception) {
            // 解析失败，保持当前时间
        }
        cal
    }

    override fun asString(): String = dateString

    override fun asNumber(): Double? = calendar.timeInMillis.toDouble()

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VDate 实例共享
        private val registry = PropertyRegistry().apply {
            register("year", "年", getter = { host ->
                VNumber((host as VDate).calendar.get(Calendar.YEAR).toDouble())
            })
            register("month", "月", getter = { host ->
                VNumber(((host as VDate).calendar.get(Calendar.MONTH) + 1).toDouble())
            })
            register("day", "日", getter = { host ->
                VNumber((host as VDate).calendar.get(Calendar.DAY_OF_MONTH).toDouble())
            })
            register("weekday", "星期", getter = { host ->
                VNumber((host as VDate).calendar.get(Calendar.DAY_OF_WEEK).toDouble())
            })
            register("timestamp", "时间戳", getter = { host ->
                VNumber((host as VDate).calendar.timeInMillis.toDouble())
            })
        }
    }
}