package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.permissions.PermissionManager

object WorkflowPermissionRecovery {

    private const val TAG = "WorkflowPermissionRecovery"

    fun recoverEligibleWorkflows(context: Context): Int {
        val appContext = context.applicationContext
        ModuleRegistry.initialize(appContext)
        ModuleManager.loadModules(appContext)
        val workflowManager = WorkflowManager(appContext)
        val recoverableWorkflows = workflowManager.getAllWorkflows().filter {
            it.wasEnabledBeforePermissionsLost && !it.isEnabled
        }

        if (recoverableWorkflows.isEmpty()) {
            return 0
        }

        var recoveredCount = 0
        recoverableWorkflows.forEach { workflow ->
            val missingPermissions = PermissionManager.getMissingPermissions(appContext, workflow)
            if (missingPermissions.isNotEmpty()) {
                return@forEach
            }

            DebugLogger.i(TAG, "权限已恢复，重新启用工作流: ${workflow.name}")
            workflowManager.saveWorkflow(
                workflow.copy(
                    isEnabled = true,
                    wasEnabledBeforePermissionsLost = false
                )
            )
            recoveredCount++
        }

        if (recoveredCount > 0) {
            DebugLogger.i(TAG, "已重新启用 $recoveredCount 个因权限丢失而暂停的工作流。")
        }

        return recoveredCount
    }
}
