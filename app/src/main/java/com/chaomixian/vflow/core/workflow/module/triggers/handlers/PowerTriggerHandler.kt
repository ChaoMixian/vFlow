// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/PowerTriggerHandler.kt
// 描述: 电源触发器处理器，监听电源连接/断开事件
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.launch

class PowerTriggerHandler : ListeningTriggerHandler() {

    private var powerReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "PowerTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.power"

    override fun startListening(context: Context) {
        if (powerReceiver != null) return
        DebugLogger.d(TAG, "启动电源监听...")

        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                handlePowerChange(ctx, intent)
            }
        }

        // 注册监听充电状态变化的广播
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(powerReceiver, filter)
        DebugLogger.d(TAG, "电源监听已启动")
    }

    override fun stopListening(context: Context) {
        powerReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "电源监听已停止。")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "注销 PowerReceiver 时出错: ${e.message}")
            } finally {
                powerReceiver = null
            }
        }
    }

    private fun handlePowerChange(context: Context, intent: Intent) {
        val action = intent.action ?: return

        DebugLogger.d(TAG, "收到电源事件: $action")

        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val desiredState = config["power_state"] as? String ?: return@forEach

                val shouldTrigger = when (desiredState) {
                    "connected" -> action == Intent.ACTION_POWER_CONNECTED
                    "disconnected" -> action == Intent.ACTION_POWER_DISCONNECTED
                    else -> false
                }

                if (shouldTrigger) {
                    val stateDescription = if (desiredState == "connected") "已连接" else "已断开"
                    DebugLogger.i(TAG, "条件满足, 触发工作流: ${workflow.name} (电源 $stateDescription)")
                    WorkflowExecutor.execute(workflow, context.applicationContext)
                }
            }
        }
    }
}