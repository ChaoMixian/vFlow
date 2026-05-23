package com.chaomixian.vflow.services

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WorkflowTriggerRef(
    val triggerId: String,
    val type: String,
) : Parcelable

@Parcelize
data class WorkflowTriggerDelta(
    val workflowId: String,
    val oldTriggerRefs: List<WorkflowTriggerRef> = emptyList(),
) : Parcelable
