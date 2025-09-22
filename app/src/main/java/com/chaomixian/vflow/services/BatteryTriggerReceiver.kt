// 文件路径: main/java/com/chaomixian/vflow/services/BatteryTriggerReceiver.kt
package com.chaomixian.vflow.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryTriggerReceiver : BroadcastReceiver() {

    companion object {
        // 用于存储上一次已知的电量百分比, -1代表未知
        private var lastBatteryPercentage: Int = -1
        private const val TAG = "BatteryTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 确保只响应电量变化的广播
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        // 如果无法获取电量信息, 则直接返回
        if (level == -1 || scale == -1) return

        val currentPercentage = (level * 100 / scale.toFloat()).toInt()

        // 如果这是第一次接收到广播, 只记录当前电量, 不触发任何操作
        if (lastBatteryPercentage == -1) {
            Log.d(TAG, "首次接收电量广播, 记录当前电量为: $currentPercentage%")
            lastBatteryPercentage = currentPercentage
            return
        }

        // 如果电量没有变化, 则不执行任何操作
        if (currentPercentage == lastBatteryPercentage) {
            return
        }

        Log.d(TAG, "电量从 $lastBatteryPercentage% 变化到 $currentPercentage%")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val workflowManager = WorkflowManager(context.applicationContext)
                // 筛选出所有启用的电量触发工作流
                val workflows = workflowManager.getAllWorkflows().filter {
                    it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.battery"
                }

                workflows.forEach { workflow ->
                    val config = workflow.triggerConfig ?: return@forEach
                    val threshold = (config["level"] as? Number)?.toInt() ?: return@forEach
                    val condition = config["above_or_below"] as? String ?: return@forEach

                    // 检查是否跨越了阈值
                    val shouldTrigger = when (condition) {
                        "below" -> lastBatteryPercentage >= threshold && currentPercentage < threshold
                        "above" -> lastBatteryPercentage <= threshold && currentPercentage > threshold
                        else -> false
                    }

                    if (shouldTrigger) {
                        Log.i(TAG, "条件满足, 触发工作流: ${workflow.name} (电量 $currentPercentage% $condition $threshold%)")
                        WorkflowExecutor.execute(workflow, context.applicationContext)
                    }
                }
            } finally {
                // 更新上一次的电量记录
                lastBatteryPercentage = currentPercentage
                pendingResult.finish()
            }
        }
    }
}