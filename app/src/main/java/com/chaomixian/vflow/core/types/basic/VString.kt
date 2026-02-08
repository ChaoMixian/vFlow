// 文件: main/java/com/chaomixian/vflow/core/types/basic/VString.kt
package com.chaomixian.vflow.core.types.basic

import android.os.Parcelable
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import kotlinx.parcelize.Parcelize

/**
 * 文本类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 *
 * 特殊逻辑：支持数字索引访问（如 str.0, str.-1 获取字符）
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

    /**
     * 重写 getProperty 以支持数字索引和切片访问
     * 优先级：索引/切片 > 内置属性 > VNull
     *
     * 支持的格式：
     * - 索引：str.0, str.-1（获取单个字符）
     * - 切片：str.0:5, str.::2, str[::-1]（获取子字符串）
     */
    override fun getProperty(propertyName: String): VObject? {
        // 检查是否是切片语法 (包含 : )
        if (propertyName.contains(":")) {
            val result = parseSlice(propertyName, raw)
            if (result != null) return result
        }

        // 特殊处理：数字索引访问
        val index = propertyName.toIntOrNull()
        if (index != null) {
            // 支持 Python 风格的负数索引 (例如 -1 表示最后一个字符)
            val actualIndex = if (index < 0) raw.length + index else index
            return if (actualIndex in raw.indices) VString(raw[actualIndex].toString()) else VNull
        }

        // 其他属性使用基类的实现（通过 propertyRegistry）
        return super.getProperty(propertyName)
    }

    /**
     * 解析Python风格的切片语法
     * 格式: start:stop:step，支持省略（默认为 0, length, 1）
     */
    private fun parseSlice(sliceStr: String, text: String): VObject? {
        val parts = sliceStr.split(":")
        if (parts.size !in 2..3) return null // 必须是 2 或 3 部分

        val length = text.length

        // 解析各部分
        val start = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val stop = parts.getOrNull(1)?.toIntOrNull() ?: length
        val step = parts.getOrNull(2)?.toIntOrNull() ?: 1

        // 处理负数索引
        val actualStart = if (start < 0) maxOf(0, length + start) else minOf(start, length)
        val actualStop = if (stop < 0) maxOf(0, length + stop) else minOf(stop, length)

        // 验证参数有效性
        if (step == 0) return VNull
        if (start == stop && step > 0) return VString("")

        // 执行切片
        val sliced = try {
            when {
                step > 0 -> {
                    // 正向切片：start < stop
                    if (actualStart >= actualStop) {
                        ""
                    } else {
                        text.substring(actualStart, minOf(actualStop, length))
                            .filterIndexed { index, _ -> (index - actualStart) % step == 0 }
                    }
                }
                step < 0 -> {
                    // 反向切片：start > stop (或省略)
                    // 实际起始位置应该是 start（如果为负则转换），但slice需要处理
                    val effectiveStart = if (start == 0) length - 1 else actualStart
                    val effectiveStop = if (stop == 0) -1 else actualStop

                    if (effectiveStart <= effectiveStop) {
                        ""
                    } else {
                        // 从 effectiveStart 开始，向下走到 effectiveStop（不包含），步长为 |step|
                        val result = StringBuilder()
                        var i = effectiveStart
                        val stepAbs = -step
                        while (i > effectiveStop) {
                            if (i in text.indices) {
                                result.append(text[i])
                            }
                            i -= stepAbs
                        }
                        result.toString()
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            return VNull
        }

        return VString(sliced)
    }

    /**
     * 获取所有可用的索引（供UI使用）
     * 返回格式：索引的列表，从 0 到 length-1
     */
    fun getAvailableIndices(): List<Int> = raw.indices.toList()
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
        register("trim", "trimmed", "去除首尾空格", getter = { host ->
            VString((host as VString).raw.trim())
        })
        register("removeSpaces", "remove_space", "去除空格", getter = { host ->
            VString((host as VString).raw.replace(" ", ""))
        })
        register("isempty", "为空", "empty", getter = { host ->
            VBoolean((host as VString).raw.isEmpty())
        })
    }
}