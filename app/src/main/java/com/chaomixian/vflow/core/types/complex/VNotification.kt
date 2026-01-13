// 文件: main/java/com/chaomixian/vflow/core/types/complex/VNotification.kt
package com.chaomixian.vflow.core.types.complex

import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject

class VNotification(val notification: NotificationObject) : BaseVObject() {
    override val type = VTypeRegistry.NOTIFICATION
    override val raw: Any = notification

    override fun asString(): String = "${notification.title}: ${notification.content}"

    override fun asNumber(): Double? = null

    override fun asBoolean(): Boolean = true

    override fun getProperty(propertyName: String): VObject? {
        return when (propertyName.lowercase()) {
            "title", "标题" -> VString(notification.title)
            "content", "内容" -> VString(notification.content)
            "package", "包名" -> VString(notification.packageName)
            "id" -> VString(notification.id)
            else -> super.getProperty(propertyName)
        }
    }
}