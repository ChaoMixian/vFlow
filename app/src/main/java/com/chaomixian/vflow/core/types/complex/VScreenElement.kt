// 文件: main/java/com/chaomixian/vflow/core/types/complex/VScreenElement.kt
package com.chaomixian.vflow.core.types.complex

import android.graphics.Rect
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import com.chaomixian.vflow.core.utils.getCompatStableId

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
    val allTexts: List<String>,

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
) : android.os.Parcelable, EnhancedBaseVObject() {
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
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            // === 文本属性 ===
            register("text", "文本", returnType = VTypeRegistry.STRING, displayName = "文本内容", nameStringRes = R.string.vtype_screen_element_text, getter = { host ->
                VString((host as VScreenElement).text ?: "")
            })
            register("content_description", "contentDescription", "content-desc", returnType = VTypeRegistry.STRING, displayName = "内容描述", nameStringRes = R.string.vtype_screen_element_content_description, getter = { host ->
                VString((host as VScreenElement).contentDescription ?: "")
            })
            register("all_texts", "allTexts", "texts", returnType = VTypeRegistry.LIST, displayName = "所有文本", nameStringRes = R.string.vtype_screen_element_all_texts, getter = { host ->
                VList((host as VScreenElement).allTexts.map { VString(it) })
            })

            // === 标识属性 ===
            register("id", "viewId", returnType = VTypeRegistry.STRING, displayName = "控件ID", nameStringRes = R.string.vtype_screen_element_id, getter = { host ->
                VString((host as VScreenElement).viewId ?: "")
            })
            register("class", "className", returnType = VTypeRegistry.STRING, displayName = "类名", nameStringRes = R.string.vtype_screen_element_class, getter = { host ->
                VString((host as VScreenElement).className ?: "")
            })

            // === 位置属性 ===
            register("center", "center_point", returnType = VTypeRegistry.COORDINATE, displayName = "中心点", nameStringRes = R.string.vtype_screen_element_center, getter = { host ->
                val element = host as VScreenElement
                VCoordinate(element.centerX, element.centerY)
            })
            register("region", "bounds", returnType = VTypeRegistry.COORDINATE_REGION, displayName = "区域", nameStringRes = R.string.vtype_screen_element_region, getter = { host ->
                val element = host as VScreenElement
                VCoordinateRegion.fromRect(element.bounds)
            })
            register("x", "center_x", returnType = VTypeRegistry.NUMBER, displayName = "中心 X", nameStringRes = R.string.vtype_screen_element_center_x, getter = { host ->
                VNumber((host as VScreenElement).centerX.toDouble())
            })
            register("y", "center_y", returnType = VTypeRegistry.NUMBER, displayName = "中心 Y", nameStringRes = R.string.vtype_screen_element_center_y, getter = { host ->
                VNumber((host as VScreenElement).centerY.toDouble())
            })
            register("left", returnType = VTypeRegistry.NUMBER, displayName = "左边界", nameStringRes = R.string.vtype_screen_element_left, getter = { host ->
                VNumber((host as VScreenElement).bounds.left.toDouble())
            })
            register("top", returnType = VTypeRegistry.NUMBER, displayName = "上边界", nameStringRes = R.string.vtype_screen_element_top, getter = { host ->
                VNumber((host as VScreenElement).bounds.top.toDouble())
            })
            register("right", returnType = VTypeRegistry.NUMBER, displayName = "右边界", nameStringRes = R.string.vtype_screen_element_right, getter = { host ->
                VNumber((host as VScreenElement).bounds.right.toDouble())
            })
            register("bottom", returnType = VTypeRegistry.NUMBER, displayName = "下边界", nameStringRes = R.string.vtype_screen_element_bottom, getter = { host ->
                VNumber((host as VScreenElement).bounds.bottom.toDouble())
            })

            // === 尺寸属性 ===
            register("width", "w", returnType = VTypeRegistry.NUMBER, displayName = "宽度", nameStringRes = R.string.vtype_screen_element_width, getter = { host ->
                VNumber((host as VScreenElement).width.toDouble())
            })
            register("height", "h", returnType = VTypeRegistry.NUMBER, displayName = "高度", nameStringRes = R.string.vtype_screen_element_height, getter = { host ->
                VNumber((host as VScreenElement).height.toDouble())
            })

            // === 交互状态属性 ===
            register("clickable", "isClickable", returnType = VTypeRegistry.BOOLEAN, displayName = "可点击", nameStringRes = R.string.vtype_screen_element_clickable, getter = { host ->
                VBoolean((host as VScreenElement).isClickable)
            })
            register("enabled", "isEnabled", returnType = VTypeRegistry.BOOLEAN, displayName = "已启用", nameStringRes = R.string.vtype_screen_element_enabled, getter = { host ->
                VBoolean((host as VScreenElement).isEnabled)
            })
            register("checkable", "isCheckable", returnType = VTypeRegistry.BOOLEAN, displayName = "可勾选", nameStringRes = R.string.vtype_screen_element_checkable, getter = { host ->
                VBoolean((host as VScreenElement).isCheckable)
            })
            register("checked", "isChecked", returnType = VTypeRegistry.BOOLEAN, displayName = "已勾选", nameStringRes = R.string.vtype_screen_element_checked, getter = { host ->
                VBoolean((host as VScreenElement).isChecked)
            })
            register("focusable", "isFocusable", returnType = VTypeRegistry.BOOLEAN, displayName = "可聚焦", getter = { host ->
                VBoolean((host as VScreenElement).isFocusable)
            })
            register("focused", "isFocused", returnType = VTypeRegistry.BOOLEAN, displayName = "已聚焦", getter = { host ->
                VBoolean((host as VScreenElement).isFocused)
            })
            register("scrollable", "isScrollable", returnType = VTypeRegistry.BOOLEAN, displayName = "可滚动", getter = { host ->
                VBoolean((host as VScreenElement).isScrollable)
            })
            register("long_clickable", "isLongClickable", returnType = VTypeRegistry.BOOLEAN, displayName = "可长按", getter = { host ->
                VBoolean((host as VScreenElement).isLongClickable)
            })
            register("selected", "isSelected", returnType = VTypeRegistry.BOOLEAN, displayName = "已选中", getter = { host ->
                VBoolean((host as VScreenElement).isSelected)
            })
            register("editable", "isEditable", returnType = VTypeRegistry.BOOLEAN, displayName = "可编辑", nameStringRes = R.string.vtype_screen_element_editable, getter = { host ->
                VBoolean((host as VScreenElement).isEditable)
            })

            // === 层级属性 ===
            register("depth", returnType = VTypeRegistry.NUMBER, displayName = "层级", getter = { host ->
                VNumber((host as VScreenElement).depth.toDouble())
            })
            register("child_count", "childCount", returnType = VTypeRegistry.NUMBER, displayName = "子节点数量", getter = { host ->
                VNumber((host as VScreenElement).childCount.toDouble())
            })

            // === 其他属性 ===
            register("accessibility_id", returnType = VTypeRegistry.NUMBER, displayName = "无障碍节点 ID", getter = { host ->
                val element = host as VScreenElement
                VNumber(element.accessibilityId?.toDouble() ?: -1.0)
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()

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
                allTexts = collectAllTexts(node),
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
                accessibilityId = node.getCompatStableId()
            )
        }

        /**
         * Parcelable.Creator 实现
         */
        val CREATOR: android.os.Parcelable.Creator<VScreenElement> = object : android.os.Parcelable.Creator<VScreenElement> {
            override fun createFromParcel(parcel: android.os.Parcel): VScreenElement {
                val left = parcel.readInt()
                val top = parcel.readInt()
                val right = parcel.readInt()
                val bottom = parcel.readInt()
                val bounds = Rect(left, top, right, bottom)

                return VScreenElement(
                    bounds = bounds,
                    text = parcel.readString(),
                    contentDescription = parcel.readString(),
                    allTexts = buildList {
                        parcel.readStringList(this)
                    },
                    viewId = parcel.readString(),
                    className = parcel.readString(),
                    isClickable = parcel.readByte() != 0.toByte(),
                    isEnabled = parcel.readByte() != 0.toByte(),
                    isCheckable = parcel.readByte() != 0.toByte(),
                    isChecked = parcel.readByte() != 0.toByte(),
                    isFocusable = parcel.readByte() != 0.toByte(),
                    isFocused = parcel.readByte() != 0.toByte(),
                    isScrollable = parcel.readByte() != 0.toByte(),
                    isLongClickable = parcel.readByte() != 0.toByte(),
                    isSelected = parcel.readByte() != 0.toByte(),
                    isEditable = parcel.readByte() != 0.toByte(),
                    depth = parcel.readInt(),
                    childCount = parcel.readInt(),
                    accessibilityId = parcel.readValue(Int::class.java.classLoader) as? Int
                )
            }

            override fun newArray(size: Int): Array<VScreenElement?> {
                return arrayOfNulls(size)
            }
        }

        private fun collectAllTexts(node: android.view.accessibility.AccessibilityNodeInfo): List<String> {
            val texts = mutableListOf<String>()
            collectAllTextsRecursive(node, texts)
            return texts
        }

        private fun collectAllTextsRecursive(
            node: android.view.accessibility.AccessibilityNodeInfo,
            sink: MutableList<String>
        ) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(sink::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(sink::add)

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    collectAllTextsRecursive(child, sink)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeInt(bounds.left)
        parcel.writeInt(bounds.top)
        parcel.writeInt(bounds.right)
        parcel.writeInt(bounds.bottom)
        parcel.writeString(text)
        parcel.writeString(contentDescription)
        parcel.writeStringList(allTexts)
        parcel.writeString(viewId)
        parcel.writeString(className)
        parcel.writeByte(if (isClickable) 1 else 0)
        parcel.writeByte(if (isEnabled) 1 else 0)
        parcel.writeByte(if (isCheckable) 1 else 0)
        parcel.writeByte(if (isChecked) 1 else 0)
        parcel.writeByte(if (isFocusable) 1 else 0)
        parcel.writeByte(if (isFocused) 1 else 0)
        parcel.writeByte(if (isScrollable) 1 else 0)
        parcel.writeByte(if (isLongClickable) 1 else 0)
        parcel.writeByte(if (isSelected) 1 else 0)
        parcel.writeByte(if (isEditable) 1 else 0)
        parcel.writeInt(depth)
        parcel.writeInt(childCount)
        parcel.writeValue(accessibilityId)
    }

    override fun describeContents(): Int = 0
}
