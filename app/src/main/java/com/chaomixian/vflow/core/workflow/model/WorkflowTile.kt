package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WorkflowTile(
    val tileIndex: Int,
    val workflowId: String? = null
) : Parcelable {
    companion object {
        const val TILE_COUNT = 20
    }
}
