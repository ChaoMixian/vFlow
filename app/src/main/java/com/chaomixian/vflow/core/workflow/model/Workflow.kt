package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Workflow(
    val id: String,
    var name: String,
    var steps: List<ActionStep>,
) : Parcelable