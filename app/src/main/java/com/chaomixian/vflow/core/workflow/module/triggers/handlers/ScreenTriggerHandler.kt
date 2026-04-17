package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.triggers.ScreenTriggerModule
import kotlinx.coroutines.launch

class ScreenTriggerHandler : ListeningTriggerHandler() {

    private var screenReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "ScreenTriggerHandler"
    }

    override fun startListening(context: Context) {
        if (screenReceiver != null) return
        DebugLogger.d(TAG, "启动屏幕事件监听...")

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                handleScreenEvent(ctx, intent)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenReceiver, filter)
        DebugLogger.d(TAG, "屏幕事件监听已启动")
    }

    override fun stopListening(context: Context) {
        screenReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "屏幕事件监听已停止。")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "注销 ScreenReceiver 时出错: ${e.message}")
            } finally {
                screenReceiver = null
            }
        }
    }

    private fun handleScreenEvent(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val event = when (action) {
            Intent.ACTION_SCREEN_ON -> ScreenTriggerModule.VALUE_SCREEN_ON
            Intent.ACTION_SCREEN_OFF -> ScreenTriggerModule.VALUE_SCREEN_OFF
            Intent.ACTION_USER_PRESENT -> ScreenTriggerModule.VALUE_UNLOCKED
            else -> return
        }

        DebugLogger.d(TAG, "收到屏幕事件: $action")

        triggerScope.launch {
            val eventInput = ScreenTriggerModule().getInputs().first { it.id == "screen_event" }
            listeningTriggers.forEach { trigger ->
                val rawExpectedEvent = trigger.parameters["screen_event"] as? String ?: return@forEach
                val expectedEvent = eventInput.normalizeEnumValue(rawExpectedEvent) ?: rawExpectedEvent
                if (expectedEvent != event) return@forEach

                DebugLogger.i(TAG, "条件满足, 触发工作流: ${trigger.workflowName} (事件: $event)")
                executeTrigger(context, trigger)
            }
        }
    }
}
