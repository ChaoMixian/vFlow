package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import com.chaomixian.vflow.ui.viewmodel.SettingsViewModel
import com.chaomixian.vflow.core.workflow.model.ActionStepExecutionSettings

internal fun ActionEditorSessionState.getExecutionSettings(): ActionStepExecutionSettings {
    return ActionStepExecutionSettings.fromParameters(asMap())
}

internal fun ActionEditorSessionState.setExecutionSettings(settings: ActionStepExecutionSettings) {
    this[ActionStepExecutionSettings.KEY_ERROR_POLICY] = settings.policy

    if (settings.policy == ActionStepExecutionSettings.POLICY_RETRY) {
        this[ActionStepExecutionSettings.KEY_RETRY_COUNT] = settings.retryCount
        this[ActionStepExecutionSettings.KEY_RETRY_INTERVAL] = settings.retryIntervalMillis
    } else {
        remove(ActionStepExecutionSettings.KEY_RETRY_COUNT)
        remove(ActionStepExecutionSettings.KEY_RETRY_INTERVAL)
    }
}

internal fun ActionEditorSessionState.applyDefaultExecutionSettings(context: Context) {
    val prefs = context.getSharedPreferences(SettingsViewModel.PREFS_NAME, Context.MODE_PRIVATE)
    val policy = prefs.getString(
        "defaultErrorPolicy",
        ActionStepExecutionSettings.POLICY_STOP
    ) ?: ActionStepExecutionSettings.POLICY_STOP
    val retryCount = prefs.getInt(
        "defaultRetryCount",
        ActionStepExecutionSettings.DEFAULT_RETRY_COUNT
    )
    val retryInterval = prefs.getLong(
        "defaultRetryInterval",
        ActionStepExecutionSettings.DEFAULT_RETRY_INTERVAL_MS
    )
    setExecutionSettings(
        ActionStepExecutionSettings(
            policy = policy,
            retryCount = retryCount,
            retryIntervalMillis = retryInterval
        )
    )
}
