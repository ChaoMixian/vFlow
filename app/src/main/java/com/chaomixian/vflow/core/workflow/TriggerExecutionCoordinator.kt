package com.chaomixian.vflow.core.workflow

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogEntry
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.logging.LogMessageKey
import com.chaomixian.vflow.core.logging.LogStatus
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

object TriggerExecutionCoordinator {
    private const val TAG = "TriggerExecutionCoordinator"

    suspend fun recoverMissingPermissions(
        context: Context,
        workflow: Workflow
    ): List<Permission> {
        val appContext = context.applicationContext
        val missingPermissions = PermissionManager.getMissingPermissions(appContext, workflow)
        return recoverPermissionsIfPossible(appContext, missingPermissions)
    }

    suspend fun executeTrigger(
        context: Context,
        trigger: TriggerSpec,
        triggerData: Parcelable? = null
    ): Boolean {
        val appContext = context.applicationContext
        val remainingPermissions = recoverMissingPermissions(appContext, trigger.workflow)

        if (remainingPermissions.isNotEmpty()) {
            val permissionNames = remainingPermissions.joinToString { it.getLocalizedName(appContext) }
            DebugLogger.w(
                TAG,
                "触发器已命中，但工作流 '${trigger.workflowName}' 仍缺少权限: $permissionNames"
            )
            LogManager.addLog(
                LogEntry(
                    workflowId = trigger.workflowId,
                    workflowName = trigger.workflowName,
                    timestamp = System.currentTimeMillis(),
                    status = LogStatus.CANCELLED,
                    messageKey = LogMessageKey.TRIGGER_SKIPPED_MISSING_PERMISSIONS,
                    messageArgs = listOf(permissionNames)
                )
            )
            return false
        }

        WorkflowExecutor.execute(
            workflow = trigger.workflow,
            context = appContext,
            triggerData = triggerData,
            triggerStepId = trigger.stepId
        )
        return true
    }

    private suspend fun recoverPermissionsIfPossible(
        context: Context,
        missingPermissions: List<Permission>
    ): List<Permission> {
        if (missingPermissions.isEmpty()) {
            return emptyList()
        }

        missingPermissions.forEach { permission ->
            runCatching {
                PermissionManager.autoGrantPermission(context, permission)
            }.onFailure { error ->
                DebugLogger.e(TAG, "自动恢复权限失败: ${permission.getLocalizedName(context)}", error)
            }
        }

        return missingPermissions.filterNot { PermissionManager.isGranted(context, it) }
    }
}
