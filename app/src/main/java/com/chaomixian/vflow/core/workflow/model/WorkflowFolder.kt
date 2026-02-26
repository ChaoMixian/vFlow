package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class WorkflowFolder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var parentId: String? = null,
    var order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var modifiedAt: Long = System.currentTimeMillis()
) : Parcelable
