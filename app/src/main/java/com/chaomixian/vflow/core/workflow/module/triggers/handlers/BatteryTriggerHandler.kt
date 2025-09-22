// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BatteryTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.BatteryTriggerReceiver

class BatteryTriggerHandler : BaseTriggerHandler() {

    companion object {
        private const val TAG = "BatteryTriggerHandler"

        /**
         * 启用或禁用 BatteryTriggerReceiver 组件。
         * @param context 上下文。
         * @param enabled 是否启用。
         */
        private fun setReceiverState(context: Context, enabled: Boolean) {
            val componentName = ComponentName(context, BatteryTriggerReceiver::class.java)
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            // 设置组件的启用状态
            context.packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
            Log.d(TAG, "BatteryTriggerReceiver state set to: $enabled")
        }
    }

    /**
     * 检查当前是否存在任何已启用的电池触发工作流。
     */
    private fun hasActiveBatteryWorkflows(context: Context): Boolean {
        // 直接从 WorkflowManager 获取最新数据进行判断
        return workflowManager.getAllWorkflows().any {
            it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.battery"
        }
    }

    override fun start(context: Context) {
        super.start(context)
        // 启动时, 根据当前是否存在活动的电池工作流来决定接收器的初始状态
        setReceiverState(context, hasActiveBatteryWorkflows(context))
        Log.d(TAG, "BatteryTriggerHandler started.")
    }

    /**
     * 当添加或启用一个工作流时，我们知道至少有一个工作流是活动的，
     * 因此可以直接、无条件地启用接收器。
     * @param context 应用上下文。
     * @param workflow 新增的或被启用的工作流。
     */
    override fun addWorkflow(context: Context, workflow: Workflow) {
        // 因为我们正在添加一个活动的电池工作流，所以直接启用接收器。
        setReceiverState(context, true)
        Log.d(TAG, "Added workflow '${workflow.name}', ensuring receiver is enabled.")
    }

    /**
     * 当一个工作流被移除或禁用后, 再次评估是否还有其他的活动工作流。
     * @param context 应用上下文。
     * @param workflowId 被移除或禁用的工作流的ID。
     */
    override fun removeWorkflow(context: Context, workflowId: String) {
        // 检查除了将要被移除的这一个之外，是否还存在其他任何已启用的电池工作流。
        val hasOtherActive = workflowManager.getAllWorkflows().any {
            it.id != workflowId && it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.battery"
        }

        // 如果没有其他活动的电池工作流了，就禁用接收器以节省资源。
        if (!hasOtherActive) {
            setReceiverState(context, false)
            Log.d(TAG, "Removed last active battery workflow, disabling receiver.")
        }
    }
}