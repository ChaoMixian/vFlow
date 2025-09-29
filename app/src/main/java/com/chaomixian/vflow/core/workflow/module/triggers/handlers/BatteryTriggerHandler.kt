// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BatteryTriggerHandler.kt
// 描述: 继承自 ListeningTriggerHandler，代码更简洁，职责更单一。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.launch

class BatteryTriggerHandler : ListeningTriggerHandler() {

    private var batteryReceiver: BroadcastReceiver? = null
    // 将 lastBatteryPercentage 作为 Handler 的实例变量，确保状态持久
    private var lastBatteryPercentage: Int = -1

    companion object {
        private const val TAG = "BatteryTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.battery"

    override fun startListening(context: Context) {
        if (batteryReceiver != null) return
        DebugLogger.d(TAG, "启动电量监听...")

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                handleBatteryChange(ctx, intent)
            }
        }
        // 使用粘性广播获取当前电量并初始化 lastBatteryPercentage
        val initialIntent = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (initialIntent != null) {
            val level = initialIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = initialIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                lastBatteryPercentage = (level * 100 / scale.toFloat()).toInt()
                DebugLogger.d(TAG, "电量监听初始化，当前电量: $lastBatteryPercentage%")
            }
        }
    }

    override fun stopListening(context: Context) {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
                DebugLogger.d(TAG, "电量监听已停止。")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "注销 BatteryReceiver 时出错: ${e.message}")
            } finally {
                batteryReceiver = null
                lastBatteryPercentage = -1
            }
        }
    }

    private fun handleBatteryChange(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale == -1) return

        val currentPercentage = (level * 100 / scale.toFloat()).toInt()
        if (lastBatteryPercentage == -1 || currentPercentage == lastBatteryPercentage) {
            lastBatteryPercentage = currentPercentage
            return
        }

        DebugLogger.d(TAG, "电量从 $lastBatteryPercentage% 变化到 $currentPercentage%")
        val previousPercentage = lastBatteryPercentage
        lastBatteryPercentage = currentPercentage

        triggerScope.launch {
            listeningWorkflows.forEach { workflow ->
                val config = workflow.triggerConfig ?: return@forEach
                val threshold = (config["level"] as? Number)?.toInt() ?: return@forEach
                val condition = config["above_or_below"] as? String ?: return@forEach

                // 检查是否跨越了阈值
                val shouldTrigger = when (condition) {
                    "below" -> previousPercentage >= threshold && currentPercentage < threshold
                    "above" -> previousPercentage <= threshold && currentPercentage > threshold
                    else -> false
                }

                if (shouldTrigger) {
                    DebugLogger.i(TAG, "条件满足, 触发工作流: ${workflow.name} (电量 $currentPercentage% $condition $threshold%)")
                    WorkflowExecutor.execute(workflow, context.applicationContext)
                }
            }
        }
    }
}