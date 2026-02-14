package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import kotlinx.coroutines.launch

class CallTriggerHandler : ListeningTriggerHandler() {

    private var callReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "CallTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.call"

    override fun startListening(context: Context) {
        if (callReceiver != null) return
        DebugLogger.d(TAG, "启动电话监听...")

        callReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: ""
                    val state = when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> "来电"
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> "接通"
                        TelephonyManager.EXTRA_STATE_IDLE -> "挂断"
                        else -> stateStr
                    }

                    DebugLogger.d(TAG, "电话状态变化: $state")
                    findAndExecuteWorkflows(ctx, state)
                }
            }
        }
        context.registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    override fun stopListening(context: Context) {
        callReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "电话监听已停止。")
            } finally {
                callReceiver = null
            }
        }
    }

    private fun findAndExecuteWorkflows(context: Context, callState: String) {
        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val matchResult = checkFilters(callState, config)
                if (matchResult.isMatch) {
                    DebugLogger.i(TAG, "电话事件满足条件，触发工作流 '${workflow.name}'")
                    val triggerDataMap = mutableMapOf(
                        "call_state" to VString(callState)
                    )
                    WorkflowExecutor.execute(workflow, context.applicationContext, VDictionary(triggerDataMap))
                }
            }
        }
    }

    private data class FilterMatchResult(val isMatch: Boolean)

    private fun checkFilters(callState: String, config: Map<String, Any?>): FilterMatchResult {
        val callType = config["call_type"] as? String ?: "任意"

        val callTypeMatches = when (callType) {
            "任意" -> true
            "来电" -> callState == "来电"
            "接通" -> callState == "接通"
            "挂断" -> callState == "挂断"
            else -> false
        }

        return FilterMatchResult(callTypeMatches)
    }
}
