// 文件: main/java/com/chaomixian/vflow/core/types/complex/VScreenElement.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.Rect
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.properties.PropertyRegistry

/**
 * 屏幕控件元素类型的 VObject 实现
 * 表示从无障碍服务获取的 UI 控件快照
 */
data class VScreenElement(
    // === 位置和尺寸 ===
    val bounds: Rect,

    // === 文本内容 ===
    val text: String?,
    val contentDescription: String?,

    // === 控件标识 ===
    val viewId: String?,
    val className: String?,

    // === 交互状态 ===
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isFocusable: Boolean,
    val isFocused: Boolean,
    val isScrollable: Boolean,
    val isLongClickable: Boolean,
    val isSelected: Boolean,
    val isEditable: Boolean,

    // === 层级信息 ===
    val depth: Int,           // 在树中的深度
    val childCount: Int,      // 子节点数量

    // === 其他信息 ===
    val accessibilityId: Int? // 无障碍节点 ID
) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.SCREEN_ELEMENT
    override val raw: Any = this
    override val propertyRegistry = Companion.registry

    // 计算属性
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()
    val width: Int get() = bounds.width()
    val height: Int get() = bounds.height()

    override fun asString(): String {
        return when {
            !viewId.isNullOrEmpty() -> "$viewId (${text ?: "无文本"})"
            !className.isNullOrEmpty() -> "$className (${text ?: "无文本"})"
            !text.isNullOrEmpty() -> text
            else -> "UI Element"
        }
    }

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VScreenElement 实例共享
        private val registry = PropertyRegistry().apply {
            // === 文本属性 ===
            register("text", "文本", getter = { host ->
                VString((host as VScreenElement).text ?: "")
            })
            register("content_description", "contentDescription", "content-desc", getter = { host ->
                VString((host as VScreenElement).contentDescription ?: "")
            })

            // === 标识属性 ===
            register("id", "viewId", getter = { host ->
                VString((host as VScreenElement).viewId ?: "")
            })
            register("class", "className", getter = { host ->
                VString((host as VScreenElement).className ?: "")
            })

            // === 位置属性 ===
            register("x", "center_x", getter = { host ->
                VNumber((host as VScreenElement).centerX.toDouble())
            })
            register("y", "center_y", getter = { host ->
                VNumber((host as VScreenElement).centerY.toDouble())
            })
            register("left", getter = { host ->
                VNumber((host as VScreenElement).bounds.left.toDouble())
            })
            register("top", getter = { host ->
                VNumber((host as VScreenElement).bounds.top.toDouble())
            })
            register("right", getter = { host ->
                VNumber((host as VScreenElement).bounds.right.toDouble())
            })
            register("bottom", getter = { host ->
                VNumber((host as VScreenElement).bounds.bottom.toDouble())
            })

            // === 尺寸属性 ===
            register("width", "w", getter = { host ->
                VNumber((host as VScreenElement).width.toDouble())
            })
            register("height", "h", getter = { host ->
                VNumber((host as VScreenElement).height.toDouble())
            })

            // === 交互状态属性 ===
            register("clickable", "isClickable", getter = { host ->
                VBoolean((host as VScreenElement).isClickable)
            })
            register("enabled", "isEnabled", getter = { host ->
                VBoolean((host as VScreenElement).isEnabled)
            })
            register("checkable", "isCheckable", getter = { host ->
                VBoolean((host as VScreenElement).isCheckable)
            })
            register("checked", "isChecked", getter = { host ->
                VBoolean((host as VScreenElement).isChecked)
            })
            register("focusable", "isFocusable", getter = { host ->
                VBoolean((host as VScreenElement).isFocusable)
            })
            register("focused", "isFocused", getter = { host ->
                VBoolean((host as VScreenElement).isFocused)
            })
            register("scrollable", "isScrollable", getter = { host ->
                VBoolean((host as VScreenElement).isScrollable)
            })
            register("long_clickable", "isLongClickable", getter = { host ->
                VBoolean((host as VScreenElement).isLongClickable)
            })
            register("selected", "isSelected", getter = { host ->
                VBoolean((host as VScreenElement).isSelected)
            })
            register("editable", "isEditable", getter = { host ->
                VBoolean((host as VScreenElement).isEditable)
            })

            // === 层级属性 ===
            register("depth", getter = { host ->
                VNumber((host as VScreenElement).depth.toDouble())
            })
            register("child_count", "childCount", getter = { host ->
                VNumber((host as VScreenElement).childCount.toDouble())
            })

            // === 其他属性 ===
            register("accessibility_id", getter = { host ->
                val element = host as VScreenElement
                VNumber(element.accessibilityId?.toDouble() ?: -1.0)
            })
        }

        /**
         * 从 AccessibilityNodeInfo 创建 VScreenElement
         */
        fun fromAccessibilityNode(
            node: android.view.accessibility.AccessibilityNodeInfo,
            depth: Int = 0
        ): VScreenElement {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            return VScreenElement(
                bounds = bounds,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                isCheckable = node.isCheckable,
                isChecked = node.isChecked,
                isFocusable = node.isFocusable,
                isFocused = node.isFocused,
                isScrollable = node.isScrollable,
                isLongClickable = node.isLongClickable,
                isSelected = node.isSelected,
                isEditable = node.isEditable,
                depth = depth,
                childCount = node.childCount,
                accessibilityId = node.uniqueId?.toIntOrNull()
            )
        }
    }
}
