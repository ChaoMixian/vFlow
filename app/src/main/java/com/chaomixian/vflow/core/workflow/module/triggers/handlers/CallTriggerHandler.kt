package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.module.triggers.CallTriggerModule
import kotlinx.coroutines.launch

class CallTriggerHandler : ListeningTriggerHandler() {

    private var callReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "CallTriggerHandler"
    }

    override fun startListening(context: Context) {
        if (callReceiver != null) return
        DebugLogger.d(TAG, "启动电话监听...")

        callReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: ""
                    val callType = when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> CallTriggerModule.TYPE_INCOMING
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> CallTriggerModule.TYPE_ANSWERED
                        TelephonyManager.EXTRA_STATE_IDLE -> CallTriggerModule.TYPE_ENDED
                        else -> null
                    }
                    val displayState = when (callType) {
                        CallTriggerModule.TYPE_INCOMING -> "来电"
                        CallTriggerModule.TYPE_ANSWERED -> "接通"
                        CallTriggerModule.TYPE_ENDED -> "挂断"
                        else -> stateStr
                    }

                    if (callType == null) return

                    DebugLogger.d(TAG, "电话状态变化: $displayState")
                    findAndExecuteWorkflows(ctx, callType, displayState)
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

    private fun findAndExecuteWorkflows(context: Context, callType: String, displayState: String) {
        triggerScope.launch {
            listeningTriggers.forEach { trigger ->
                val config = trigger.parameters
                val matchResult = checkFilters(callType, config)
                if (matchResult.isMatch) {
                    DebugLogger.i(TAG, "电话事件满足条件，触发工作流 '${trigger.workflowName}'")
                    val triggerDataMap = mutableMapOf(
                        "call_state" to VString(displayState)
                    )
                    executeTrigger(context, trigger, VDictionary(triggerDataMap))
                }
            }
        }
    }

    private data class FilterMatchResult(val isMatch: Boolean)

    private fun checkFilters(callType: String, config: Map<String, Any?>): FilterMatchResult {
        val callTypeInput = CallTriggerModule().getInputs().first { it.id == "call_type" }
        val configCallType = callTypeInput.normalizeEnumValueOrNull(config["call_type"] as? String)
            ?: CallTriggerModule.TYPE_ANY

        val callTypeMatches = when (configCallType) {
            CallTriggerModule.TYPE_ANY -> true
            CallTriggerModule.TYPE_INCOMING -> callType == CallTriggerModule.TYPE_INCOMING
            CallTriggerModule.TYPE_ANSWERED -> callType == CallTriggerModule.TYPE_ANSWERED
            CallTriggerModule.TYPE_ENDED -> callType == CallTriggerModule.TYPE_ENDED
            else -> false
        }

        return FilterMatchResult(callTypeMatches)
    }
}
