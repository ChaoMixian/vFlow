// 文件: main/java/com/chaomixian/vflow/core/types/complex/VScreenElement.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement

/**
 * 屏幕元素类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VScreenElement(val element: ScreenElement) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.UI_ELEMENT
    override val raw: Any = element
    override val propertyRegistry = Companion.registry

    override fun asString(): String = element.text ?: "UI Element"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VScreenElement 实例共享
        private val registry = PropertyRegistry().apply {
            register("text", "文本", getter = { host ->
                VString((host as VScreenElement).element.text ?: "")
            })
            register("center_x", "x", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.centerX().toDouble())
            })
            register("center_y", "y", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.centerY().toDouble())
            })
            register("left", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.left.toDouble())
            })
            register("top", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.top.toDouble())
            })
            register("width", "w", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.width().toDouble())
            })
            register("height", "h", getter = { host ->
                VNumber((host as VScreenElement).element.bounds.height().toDouble())
            })
        }
    }
}