// 文件: java/com/chaomixian/vflow/core/types/complex/VUiComponent.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
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
 *
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VUiComponent(
    val element: UiElement,
    currentValue: Any? = null,
    private val valueProvider: (() -> Any?)? = null
) : EnhancedBaseVObject() {

    // 当前值（可以是初始值，或通过 valueProvider 动态获取）
    val currentValue: Any?
        get() = valueProvider?.invoke() ?: _currentValue

    private val _currentValue: Any? = currentValue

    override val type = VTypeRegistry.UI_COMPONENT
    override val raw: Any = element
    override val propertyRegistry = Companion.registry

    override fun asString(): String = when (element.type) {
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.TEXT -> element.label
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.BUTTON -> element.label
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.INPUT -> currentValue?.toString() ?: element.defaultValue
        com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.SWITCH -> currentValue?.toString() ?: element.defaultValue
    }

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VUiComponent 实例共享
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            // 基本属性
            register("id", returnType = VTypeRegistry.STRING, displayName = "组件ID", nameStringRes = R.string.vtype_uicomponent_id, getter = { host ->
                VString((host as VUiComponent).element.id)
            })
            register("type", "类型", returnType = VTypeRegistry.STRING, displayName = "类型", nameStringRes = R.string.vtype_uicomponent_type, getter = { host ->
                VString((host as VUiComponent).element.type.name.lowercase())
            })
            register("label", "标签", returnType = VTypeRegistry.STRING, displayName = "标签", nameStringRes = R.string.vtype_uicomponent_label, getter = { host ->
                VString((host as VUiComponent).element.label)
            })

            // 值相关（需要特殊处理currentValue）
            register("value", "text", "值", "内容", returnType = VTypeRegistry.ANY, displayName = "值", nameStringRes = R.string.vtype_uicomponent_value, getter = { host ->
                val component = host as VUiComponent
                when (val value = component.currentValue) {
                    null -> VString(component.element.defaultValue)
                    is VObject -> VString(value.asString())
                    else -> VString(value.toString())
                }
            })
            register("defaultvalue", "default", "默认值", returnType = VTypeRegistry.ANY, displayName = "默认值", nameStringRes = R.string.vtype_uicomponent_defaultvalue, getter = { host ->
                VString((host as VUiComponent).element.defaultValue)
            })
            register("placeholder", "占位符", returnType = VTypeRegistry.STRING, displayName = "占位符", nameStringRes = R.string.vtype_uicomponent_placeholder, getter = { host ->
                VString((host as VUiComponent).element.placeholder)
            })

            // 布尔属性
            register("required", returnType = VTypeRegistry.BOOLEAN, displayName = "必填", nameStringRes = R.string.vtype_uicomponent_required, getter = { host ->
                VBoolean((host as VUiComponent).element.isRequired)
            })
            register("trigger_event", "triggerevent", "triggerEvent", returnType = VTypeRegistry.BOOLEAN, displayName = "触发事件", nameStringRes = R.string.vtype_uicomponent_triggerEvent, getter = { host ->
                VBoolean((host as VUiComponent).element.triggerEvent)
            })

            // 类型判断
            register("istext", returnType = VTypeRegistry.BOOLEAN, displayName = "是否文本", nameStringRes = R.string.vtype_uicomponent_istext, getter = { host ->
                VBoolean((host as VUiComponent).element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.TEXT)
            })
            register("isbutton", returnType = VTypeRegistry.BOOLEAN, displayName = "是否按钮", nameStringRes = R.string.vtype_uicomponent_isbutton, getter = { host ->
                VBoolean((host as VUiComponent).element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.BUTTON)
            })
            register("isinput", returnType = VTypeRegistry.BOOLEAN, displayName = "是否输入框", nameStringRes = R.string.vtype_uicomponent_isinput, getter = { host ->
                VBoolean((host as VUiComponent).element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.INPUT)
            })
            register("isswitch", returnType = VTypeRegistry.BOOLEAN, displayName = "是否开关", nameStringRes = R.string.vtype_uicomponent_isswitch, getter = { host ->
                VBoolean((host as VUiComponent).element.type == com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType.SWITCH)
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
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
