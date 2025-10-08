// 文件: main/java/com/chaomixian/vflow/services/VFlowNotificationListenerService.kt
package com.chaomixian.vflow.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.NotificationTriggerHandler

/**
 * 监听系统通知的服务。
 * 当服务启动并获得授权后，它会接收到所有新发布的通知。
 */
class VFlowNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 当服务成功连接时，调用Handler的静态方法进行通知
        NotificationTriggerHandler.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 当服务断开时，调用Handler的静态方法进行通知
        NotificationTriggerHandler.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // 将收到的新通知转发给 Handler 处理
        sbn?.let { NotificationTriggerHandler.onNotificationPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // (可选) 如果未来需要处理通知移除事件，可以在这里添加逻辑
    }
}