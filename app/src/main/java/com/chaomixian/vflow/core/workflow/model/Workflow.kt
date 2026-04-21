package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import com.chaomixian.vflow.core.workflow.WorkflowVisuals
import kotlinx.parcelize.Parcelize

private const val MANUAL_TRIGGER_ID = "vflow.trigger.manual"

@Parcelize
data class Workflow(
    val id: String,
    var name: String,
    var triggers: List<ActionStep> = emptyList(),
    var steps: List<ActionStep> = emptyList(),
    var isEnabled: Boolean = true,
    var isFavorite: Boolean = false,
    var wasEnabledBeforePermissionsLost: Boolean = false,
    var folderId: String? = null,
    var order: Int = 0,
    var shortcutName: String? = null,
    var shortcutIconRes: String? = null,
    var cardIconRes: String = WorkflowVisuals.DEFAULT_ICON_RES_NAME,
    var cardThemeColor: String = WorkflowVisuals.DEFAULT_THEME_COLOR_HEX,
    var modifiedAt: Long = System.currentTimeMillis(),
    var version: String = "1.0.0",
    var vFlowLevel: Int = 1,
    var description: String = "",
    var author: String = "",
    var homepage: String = "",
    var tags: List<String> = emptyList(),
    var maxExecutionTime: Int? = null,
    var reentryBehavior: WorkflowReentryBehavior = WorkflowReentryBehavior.BLOCK_NEW
) : Parcelable {
    val allSteps: List<ActionStep>
        get() = triggers + steps

    fun hasTriggerType(triggerModuleId: String): Boolean {
        return triggers.any { it.moduleId == triggerModuleId }
    }

    fun triggerStepsByType(triggerModuleId: String): List<ActionStep> {
        return triggers.filter { it.moduleId == triggerModuleId }
    }

    fun autoTriggerSteps(): List<ActionStep> {
        return triggers.filter { it.moduleId != MANUAL_TRIGGER_ID }
    }

    fun hasAutoTriggers(): Boolean = autoTriggerSteps().isNotEmpty()

    fun hasManualTrigger(): Boolean = manualTrigger() != null

    fun manualTrigger(): ActionStep? = triggers.firstOrNull { it.moduleId == MANUAL_TRIGGER_ID }

    fun isManualOnly(): Boolean = hasManualTrigger() && !hasAutoTriggers()

    fun getTrigger(triggerId: String): ActionStep? = triggers.firstOrNull { it.id == triggerId }

    fun toAutoTriggerSpecs(): List<TriggerSpec> {
        return autoTriggerSteps().map { TriggerSpec(this, it) }
    }
}
