package com.chaomixian.vflow.ui.workflow_editor

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
