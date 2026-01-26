// 文件: main/java/com/chaomixian/vflow/core/types/complex/VCoordinateRegion.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.Rect
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 坐标区域类型的 VObject 实现
 * 用于表示屏幕上的矩形区域（OCR 结果、选区、截图区域等）
 */
data class VCoordinateRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.COORDINATE_REGION
    override val raw: Any = this
    override val propertyRegistry = Companion.registry

    // 计算属性
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    override fun asString(): String = "($left,$top)-($right,$bottom)"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VCoordinateRegion 实例共享
        private val registry = PropertyRegistry().apply {
            // 边界坐标
            register("left", getter = { host ->
                VNumber((host as VCoordinateRegion).left.toDouble())
            })
            register("top", getter = { host ->
                VNumber((host as VCoordinateRegion).top.toDouble())
            })
            register("right", getter = { host ->
                VNumber((host as VCoordinateRegion).right.toDouble())
            })
            register("bottom", getter = { host ->
                VNumber((host as VCoordinateRegion).bottom.toDouble())
            })

            // 尺寸
            register("width", "w", getter = { host ->
                VNumber((host as VCoordinateRegion).width.toDouble())
            })
            register("height", "h", getter = { host ->
                VNumber((host as VCoordinateRegion).height.toDouble())
            })

            // 中心点
            register("center", "center_point", getter = { host ->
                val region = host as VCoordinateRegion
                VCoordinate(region.centerX, region.centerY)
            })
            register("center_x", "x", getter = { host ->
                VNumber((host as VCoordinateRegion).centerX.toDouble())
            })
            register("center_y", "y", getter = { host ->
                VNumber((host as VCoordinateRegion).centerY.toDouble())
            })

            // 字符串表示
            register("as_string", "string", getter = { host ->
                VString(host.asString())
            })

            // 布尔检查
            register("is_empty", "isEmpty", getter = { host ->
                VBoolean((host as VCoordinateRegion).width <= 0 || (host as VCoordinateRegion).height <= 0)
            })
            register("is_valid", "isValid", getter = { host ->
                val region = host as VCoordinateRegion
                VBoolean(region.left < region.right && region.top < region.bottom)
            })
        }

        /**
         * 从 Rect 创建 VCoordinateRegion
         */
        fun fromRect(rect: Rect): VCoordinateRegion {
            return VCoordinateRegion(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    /**
     * 转换为 Rect
     */
    fun toRect(): Rect {
        return Rect(left, top, right, bottom)
    }
}
