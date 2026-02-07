// 文件: main/java/com/chaomixian/vflow/core/workflow/model/Workflow.kt

package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Workflow(
    val id: String,
    var name: String,
    var steps: List<ActionStep>,
    // 工作流是否启用，对非手动触发器有效
    var isEnabled: Boolean = true,
    // 用于存储触发器的特定配置，例如分享别名
    val triggerConfig: @RawValue Map<String, Any?>? = null,
    // 工作流是否被收藏
    var isFavorite: Boolean = false,
    // 记录在权限丢失前，此工作流是否由用户设置为启用
    var wasEnabledBeforePermissionsLost: Boolean = false,
    // 所属文件夹 ID - 使用 @RawValue 确保 Parcelize 正确处理
    var folderId: @RawValue String? = null,
    // 用于列表排序
    var order: Int = 0
) : Parcelable