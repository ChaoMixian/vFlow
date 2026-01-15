// 文件: java/com/chaomixian/vflow/core/types/complex/VUiComponent.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement

/**
 * UI 组件的 VObject 包装类
 *
 * 将 UiElement 包装为 VObject，支持：
 * - 在魔法变量中传递组件对象
 * - 通过属性访问符访问组件信息
 * - 在 UpdateUiComponentModule 中传递整个对象
 *
 * 支持的属性访问：
 * - .id: 组件 ID
 * - .type: 组件类型（text/button/input/switch）
 * - .label: 标签文本
 * - .value: 当前值（需要从组件值存储中获取）
 * - .placeholder: 占位符文本
 * - .required: 是否必填
 * - .triggerEvent: 是否触发事件
 */
class VUiComponent(
    val element: UiElement,
    val currentValue: Any? = null
) : BaseVObject() {

    override val type = VTypeRegistry.UI_COMPONENT
    override val raw: Any = element

    override fun asString(): String = when (element.type) {
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.TEXT -> element.label
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.BUTTON -> element.label
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.INPUT -> currentValue?.toString() ?: element.defaultValue
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.SWITCH -> currentValue?.toString() ?: element.defaultValue
    }

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    /**
     * 属性访问
     *
     * 示例：
     * - {{button.id}} -> "btn1"
     * - {{button.type}} -> "button"
     * - {{input.value}} -> "用户输入的文本"
     */
    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            // 基本属性
            "id" -> VString(element.id)
            "type" -> VString(element.type.name.lowercase())
            "label" -> VString(element.label)

            // 值相关
            "value", "text" -> when (currentValue) {
                null -> VString(element.defaultValue)
                else -> VString(currentValue.toString())
            }
            "defaultvalue", "default" -> VString(element.defaultValue)
            "placeholder" -> VString(element.placeholder)

            // 布尔属性
            "required" -> VBoolean(element.isRequired)
            "trigger_event", "triggerevent" -> VBoolean(element.triggerEvent)

            // 类型判断
            "istext" -> VBoolean(element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.TEXT)
            "isbutton" -> VBoolean(element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.BUTTON)
            "isinput" -> VBoolean(element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.INPUT)
            "isswitch" -> VBoolean(element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.SWITCH)

            // 中文别名
            "类型" -> VString(element.type.name.lowercase())
            "标签" -> VString(element.label)
            "值", "内容" -> when (currentValue) {
                null -> VString(element.defaultValue)
                else -> VString(currentValue.toString())
            }
            "默认值" -> VString(element.defaultValue)
            "占位符" -> VString(element.placeholder)

            else -> null
        }
    }

    /**
     * 获取组件的当前值（如果有）
     */
    fun getValue(): Any? = currentValue ?: element.defaultValue

    /**
     * 获取组件的 ID
     */
    fun getId(): String = element.id

    /**
     * 判断是否是特定类型
     */
    fun isType(type: com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType): Boolean {
        return element.type == type
    }
}

/**
 * 创建一个未找到的组件对象
 */
fun VUiComponentNotFound(id: String): VUiComponent {
    val fakeElement = UiElement(
        id = id,
        type = com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.TEXT,
        label = "未找到",
        defaultValue = "",
        placeholder = "",
        isRequired = false,
        triggerEvent = false
    )
    return VUiComponent(fakeElement, null)
}
