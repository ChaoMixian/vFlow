// 文件: main/java/com/chaomixian/vflow/core/types/complex/VScreenElement.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement

class VScreenElement(val element: ScreenElement) : BaseVObject() {
    override val type = VTypeRegistry.UI_ELEMENT
    override val raw: Any = element

    override fun asString(): String = element.text ?: "UI Element"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "text", "文本" -> VString(element.text ?: "")
            "center_x", "x" -> VNumber(element.bounds.centerX().toDouble())
            "center_y", "y" -> VNumber(element.bounds.centerY().toDouble())
            "left" -> VNumber(element.bounds.left.toDouble())
            "top" -> VNumber(element.bounds.top.toDouble())
            "width", "w" -> VNumber(element.bounds.width().toDouble())
            "height", "h" -> VNumber(element.bounds.height().toDouble())
            else -> super.getProperty(propertyName)
        }
    }
}