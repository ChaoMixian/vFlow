package com.chaomixian.vflow.core.workflow.model

import com.google.gson.annotations.SerializedName

enum class WorkflowReentryBehavior(val storedValue: String) {
    @SerializedName("block_new")
    BLOCK_NEW("block_new"),
    @SerializedName("stop_current_and_run_new")
    STOP_CURRENT_AND_RUN_NEW("stop_current_and_run_new"),
    @SerializedName("allow_parallel")
    ALLOW_PARALLEL("allow_parallel");

    companion object {
        fun fromStoredValue(value: String?): WorkflowReentryBehavior {
            return when (value?.trim()) {
                STOP_CURRENT_AND_RUN_NEW.storedValue -> STOP_CURRENT_AND_RUN_NEW
                ALLOW_PARALLEL.storedValue -> ALLOW_PARALLEL
                BLOCK_NEW.storedValue -> BLOCK_NEW
                else -> BLOCK_NEW
            }
        }
    }
}
