// 文件: main/java/com/chaomixian/vflow/core/types/complex/VDate.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import java.text.SimpleDateFormat
import java.util.*

class VDate(val dateString: String) : BaseVObject() {
    override val type = VTypeRegistry.DATE
    override val raw: Any = dateString

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

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "year", "年" -> VNumber(calendar.get(Calendar.YEAR).toDouble())
            "month", "月" -> VNumber((calendar.get(Calendar.MONTH) + 1).toDouble())
            "day", "日" -> VNumber(calendar.get(Calendar.DAY_OF_MONTH).toDouble())
            "weekday", "星期" -> VNumber(calendar.get(Calendar.DAY_OF_WEEK).toDouble()) // 1=周日
            "timestamp", "时间戳" -> VNumber(calendar.timeInMillis.toDouble())
            else -> super.getProperty(propertyName)
        }
    }
}