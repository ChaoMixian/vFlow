// 文件: main/java/com/chaomixian/vflow/core/workflow/module/notification/NotificationObject.kt
package com.chaomixian.vflow.core.workflow.module.notification

import android.os.Parcelable
import com.chaomixian.vflow.core.types.VTypeRegistry
import kotlinx.parcelize.Parcelize

/**
 * 代表一个状态栏通知的对象。
 * @param id 通知的唯一标识符 (key)。
 * @param packageName 发出通知的应用包名。
 * @param title 通知的标题。
 * @param content 通知的正文内容。
 */
@Parcelize
data class NotificationObject(
    val id: String,
    val packageName: String,
    val title: String,
    val content: String
) : Parcelable {
    companion object {
        /** NotificationObject 类型的唯一标识符。 */
        val TYPE_NAME = VTypeRegistry.NOTIFICATION.id
    }
}
