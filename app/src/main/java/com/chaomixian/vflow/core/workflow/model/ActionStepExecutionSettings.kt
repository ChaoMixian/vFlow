package com.chaomixian.vflow.core.workflow.model

data class ActionStepExecutionSettings(
    val policy: String = POLICY_STOP,
    val retryCount: Int = DEFAULT_RETRY_COUNT,
    val retryIntervalMillis: Long = DEFAULT_RETRY_INTERVAL_MS
) {
    companion object {
        const val KEY_ERROR_POLICY = "__error_policy"
        const val KEY_RETRY_COUNT = "__retry_count"
        const val KEY_RETRY_INTERVAL = "__retry_interval"

        const val POLICY_STOP = "STOP"
        const val POLICY_SKIP = "SKIP"
        const val POLICY_RETRY = "RETRY"

        const val DEFAULT_RETRY_COUNT = 3
        const val DEFAULT_RETRY_INTERVAL_MS = 1000L

        fun fromParameters(parameters: Map<String, Any?>): ActionStepExecutionSettings {
            return ActionStepExecutionSettings(
                policy = parameters[KEY_ERROR_POLICY] as? String ?: POLICY_STOP,
                retryCount = (parameters[KEY_RETRY_COUNT] as? Number)?.toInt() ?: DEFAULT_RETRY_COUNT,
                retryIntervalMillis = (parameters[KEY_RETRY_INTERVAL] as? Number)?.toLong()
                    ?: DEFAULT_RETRY_INTERVAL_MS
            )
        }
    }
}
