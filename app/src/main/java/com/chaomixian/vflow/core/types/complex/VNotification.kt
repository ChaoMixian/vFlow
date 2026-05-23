// 文件: main/java/com/chaomixian/vflow/core/types/complex/VNotification.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.EnhancedBaseVObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.properties.PropertyRegistry
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject

/**
 * 通知类型的 VObject 实现
 * 使用属性注册表管理属性，消除了重复的 when 语句
 */
class VNotification(val notification: NotificationObject) : EnhancedBaseVObject() {
    override val type = VTypeRegistry.NOTIFICATION
    override val raw: Any = notification
    override val propertyRegistry = Companion.registry

    override fun asString(): String = "${notification.title}: ${notification.content}"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    companion object {
        // 属性注册表：所有 VNotification 实例共享
        private val registry: PropertyRegistry = PropertyRegistry().apply {
            register("title", "标题", returnType = VTypeRegistry.STRING, displayName = "标题", nameStringRes = R.string.vtype_notification_title, getter = { host ->
                VString((host as VNotification).notification.title)
            })
            register("content", "内容", returnType = VTypeRegistry.STRING, displayName = "内容", nameStringRes = R.string.vtype_notification_content, getter = { host ->
                VString((host as VNotification).notification.content)
            })
            register("package", "包名", returnType = VTypeRegistry.STRING, displayName = "应用包名", nameStringRes = R.string.vtype_notification_package, getter = { host ->
                VString((host as VNotification).notification.packageName)
            })
            register("id", returnType = VTypeRegistry.STRING, displayName = "通知 ID", nameStringRes = R.string.vtype_notification_id, getter = { host ->
                VString((host as VNotification).notification.id)
            })
        }

        fun propertyDefs(): List<com.chaomixian.vflow.core.types.VPropertyDef> = registry.toPropertyDefs()
    }
}
