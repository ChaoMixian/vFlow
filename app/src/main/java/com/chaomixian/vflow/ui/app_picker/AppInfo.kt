// 文件: main/java/com/chaomixian/vflow/ui/app_picker/AppInfo.kt
// 描述: 用于在应用选择器中显示的应用信息数据类。
//      这个文件的缺失导致了 AppListAdapter 和 AppPickerActivity 中的大量 "Unresolved reference" 错误。
package com.chaomixian.vflow.ui.app_picker

import android.graphics.drawable.Drawable

/**
 * AppInfo 数据类
 * 封装了一个应用在列表中显示所需的基本信息。
 * @param appName 应用的显示名称 (例如 "微信")。
 * @param packageName 应用的包名 (例如 "com.tencent.mm")。
 * @param icon 应用的图标。
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)