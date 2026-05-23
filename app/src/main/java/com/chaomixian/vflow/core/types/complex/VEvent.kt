// 文件: main/java/com/chaomixian/vflow/core/types/complex/VEvent.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent

/**
 * UI 事件类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VEvent(val event: UiEvent) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.EVENT
    override val raw: Any = event
    override val propertyRegistry = Companion.registry

    override fun asString(): String = "${event.type}: ${event.elementId}"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VEvent 实例共享
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            register("sessionId", "会话ID", returnType = VTypeRegistry.STRING, displayName = "会话ID", nameStringRes = R.string.vtype_event_session_id, getter = { host ->
                VString((host as VEvent).event.sessionId)
            })
            register("elementId", "组件ID", returnType = VTypeRegistry.STRING, displayName = "组件ID", nameStringRes = R.string.vtype_event_element_id, getter = { host ->
                VString((host as VEvent).event.elementId)
            })
            register("type", "事件类型", returnType = VTypeRegistry.STRING, displayName = "事件类型", nameStringRes = R.string.vtype_event_type, getter = { host ->
                VString((host as VEvent).event.type)
            })
            register("value", "事件值", returnType = VTypeRegistry.ANY, displayName = "事件值", nameStringRes = R.string.vtype_event_value, getter = { host ->
                VObjectFactory.from((host as VEvent).event.value)
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
    }
}
