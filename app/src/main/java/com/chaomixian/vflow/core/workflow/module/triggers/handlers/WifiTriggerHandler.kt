// main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/WifiTriggerHandler.kt
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.WifiTriggerReceiver

class WifiTriggerHandler : BaseTriggerHandler() {

    companion object {
        private const val TAG = "WifiTriggerHandler"
    }

    /**
     * 启用或禁用 WifiTriggerReceiver 组件。
     * @param context 上下文。
     * @param enabled 是否启用。
     */
    private fun setReceiverState(context: Context, enabled: Boolean) {
        val componentName = ComponentName(context, WifiTriggerReceiver::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
        Log.d(TAG, "WifiTriggerReceiver state set to: $enabled")
    }

    /**
     * 检查当前是否存在任何已启用的Wi-Fi触发工作流。
     */
    private fun hasActiveWifiWorkflows(): Boolean {
        return workflowManager.getAllWorkflows().any {
            it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.wifi"
        }
    }

    override fun start(context: Context) {
        super.start(context)
        // 启动时, 根据当前是否存在活动的Wi-Fi工作流来决定接收器的初始状态
        setReceiverState(context, hasActiveWifiWorkflows())
        Log.d(TAG, "WifiTriggerHandler started.")
    }

    override fun stop(context: Context) {
        super.stop(context)
        // 无需再反注册接收器，因为它是在 Manifest 中声明的
    }

    /**
     * 当添加或启用一个工作流时，我们知道至少有一个工作流是活动的，
     * 因此可以直接、无条件地启用接收器。
     */
    override fun addWorkflow(context: Context, workflow: Workflow) {
        setReceiverState(context, true)
        Log.d(TAG, "Added workflow '${workflow.name}', ensuring receiver is enabled.")
    }

    /**
     * 当一个工作流被移除或禁用后, 再次评估是否还有其他的活动工作流。
     */
    override fun removeWorkflow(context: Context, workflowId: String) {
        // 检查除了将要被移除的这一个之外，是否还存在其他任何已启用的Wi-Fi工作流。
        val hasOtherActive = workflowManager.getAllWorkflows().any {
            it.id != workflowId && it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.wifi"
        }

        // 如果没有其他活动的Wi-Fi工作流了，就禁用接收器以节省资源。
        if (!hasOtherActive) {
            setReceiverState(context, false)
            Log.d(TAG, "Removed last active Wi-Fi workflow, disabling receiver.")
        }
    }
}