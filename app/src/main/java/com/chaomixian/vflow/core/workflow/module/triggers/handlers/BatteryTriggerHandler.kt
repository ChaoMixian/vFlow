// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BatteryTriggerHandler.kt
// 描述: 使用动态注册的 BroadcastReceiver 替换静态接收器，确保在后台能可靠接收电量变化事件。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class BatteryTriggerHandler : BaseTriggerHandler() {

    private val listeningWorkflows = CopyOnWriteArrayList<Workflow>()
    private var batteryReceiver: BroadcastReceiver? = null
    // 将 lastBatteryPercentage 作为 Handler 的实例变量，确保状态持久
    private var lastBatteryPercentage: Int = -1

    companion object {
        private const val TAG = "BatteryTriggerHandler"
    }

    override fun start(context: Context) {
        super.start(context)
        // 只有当存在活动的电池工作流时，才开始监听
        if (hasActiveBatteryWorkflows()) {
            registerBatteryReceiver(context)
        }
        Log.d(TAG, "BatteryTriggerHandler 已启动。")
    }

    override fun stop(context: Context) {
        super.stop(context)
        unregisterBatteryReceiver(context)
        Log.d(TAG, "BatteryTriggerHandler 已停止。")
    }

    override fun addWorkflow(context: Context, workflow: Workflow) {
        listeningWorkflows.removeAll { it.id == workflow.id }
        listeningWorkflows.add(workflow)
        Log.d(TAG, "已添加 '${workflow.name}'。监听数量: ${listeningWorkflows.size}")
        // 如果这是第一个工作流，则启动监听
        if (batteryReceiver == null) {
            registerBatteryReceiver(context)
        }
    }

    override fun removeWorkflow(context: Context, workflowId: String) {
        if (listeningWorkflows.removeAll { it.id == workflowId }) {
            Log.d(TAG, "已移除 workflowId: $workflowId。监听数量: ${listeningWorkflows.size}")
            // 如果没有工作流需要监听了，就停止
            if (listeningWorkflows.isEmpty()) {
                unregisterBatteryReceiver(context)
            }
        }
    }

    private fun hasActiveBatteryWorkflows(): Boolean {
        return workflowManager.getAllWorkflows().any {
            it.isEnabled && it.triggerConfig?.get("type") == "vflow.trigger.battery"
        }
    }

    private fun registerBatteryReceiver(context: Context) {
        if (batteryReceiver != null) return
        Log.d(TAG, "正在注册 BatteryReceiver...")

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
                Log.d(TAG, "BatteryReceiver 初始化，当前电量: $lastBatteryPercentage%")
            }
        }
    }

    private fun unregisterBatteryReceiver(context: Context) {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "BatteryReceiver 已注销。")
            } catch (e: Exception) {
                Log.w(TAG, "注销 BatteryReceiver 时出错: ${e.message}")
            } finally {
                batteryReceiver = null
                lastBatteryPercentage = -1 // 重置状态
            }
        }
    }

    private fun handleBatteryChange(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale == -1) return

        val currentPercentage = (level * 100 / scale.toFloat()).toInt()

        if (lastBatteryPercentage == -1) {
            lastBatteryPercentage = currentPercentage
            return
        }

        if (currentPercentage == lastBatteryPercentage) return

        Log.d(TAG, "电量从 $lastBatteryPercentage% 变化到 $currentPercentage%")
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
                    Log.i(TAG, "条件满足, 触发工作流: ${workflow.name} (电量 $currentPercentage% $condition $threshold%)")
                    WorkflowExecutor.execute(workflow, context.applicationContext)
                }
            }
        }
    }
}