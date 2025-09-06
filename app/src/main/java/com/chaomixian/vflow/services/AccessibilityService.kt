package com.chaomixian.vflow.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccessibilityService : AccessibilityService() {

    private lateinit var workflowManager: WorkflowManager
    // 存储需要监听的应用启动触发器的工作流列表
    private var appStartWorkflows = listOf<Workflow>()
    // 用于去抖，防止短时间内重复触发同一个事件
    private var debounceJob: Job? = null
    private val debounceTime = 500L // 500毫秒的去抖时间

    // 创建一个广播接收器，用于接收工作流更新的通知
    private val workflowUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 当接收到广播时，重新加载触发器列表
            if (intent?.action == WorkflowManager.ACTION_WORKFLOWS_UPDATED) {
                Log.d("VFlowAccessibility", "接收到工作流更新广播，正在重新加载触发器...")
                loadTriggerWorkflows()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务连接成功，立即向总线上报自己的实例
        ServiceStateBus.onAccessibilityServiceConnected(this, this)
        // 初始化WorkflowManager
        workflowManager = WorkflowManager(applicationContext)

        // 注册广播接收器
        val filter = IntentFilter(WorkflowManager.ACTION_WORKFLOWS_UPDATED)
        LocalBroadcastManager.getInstance(this).registerReceiver(workflowUpdateReceiver, filter)

        loadTriggerWorkflows() // 首次加载
        Log.d("VFlowAccessibility", "无障碍服务已连接，并加载了 ${appStartWorkflows.size} 个应用启动触发器。")
    }

    /**
     * [核心] 这是无障碍服务的事件回调方法。
     * 安卓系统会把屏幕上发生的各种事件（如窗口打开、内容变化等）通过这个方法发送给我们。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 我们只关心窗口状态变化的事件，这通常意味着一个新的应用或Activity被打开了
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            // 增加日志，确认事件是否被接收
            Log.v("VFlowAccessibility", "事件接收: $packageName/$className")

            debounceJob?.cancel() // 取消上一个未完成的延时任务
            debounceJob = CoroutineScope(Dispatchers.Default).launch {
                delay(debounceTime) // 延迟一小段时间
                // 在延迟之后，实际处理事件
                Log.d("VFlowAccessibility", "处理窗口变化事件: $packageName / $className")
                checkForAppStartTrigger(packageName, className)
            }
        }
    }

    /**
     * 检查当前的应用/Activity启动事件是否匹配任何工作流的触发器。
     */
    private fun checkForAppStartTrigger(packageName: String, className: String) {
        // 在检查前增加日志，显示当前正在监听的触发器数量
        Log.d("VFlowAccessibility", "正在用 ${appStartWorkflows.size} 个触发器检查事件...")
        appStartWorkflows.forEach { workflow ->
            val targetPackage = workflow.triggerConfig?.get("packageName") as? String
            val targetActivity = workflow.triggerConfig?.get("activityName") as? String

            // 增加详细的匹配日志
            Log.v("VFlowAccessibility", "检查工作流 '${workflow.name}': [目标: $targetPackage / $targetActivity] vs [事件: $packageName / $className]")

            if (targetPackage != packageName) {
                return@forEach // Kotlin forEach中的return@forEach等同于for循环中的continue
            }

            // 检查Activity是否匹配
            // "LAUNCH" 是一个特殊值，意味着只要应用启动就触发（不关心具体Activity）
            // 或者，如果配置的Activity名与当前事件的Activity名匹配
            val activityMatches = targetActivity == "LAUNCH" || targetActivity == className

            if (activityMatches) {
                Log.i("VFlowAccessibility", "触发器匹配成功！准备执行工作流: ${workflow.name}")
                // 匹配成功！调用 WorkflowExecutor 来执行工作流
                WorkflowExecutor.execute(workflow, applicationContext)
            }
        }
    }

    /**
     * 从WorkflowManager加载所有需要监听的工作流。
     * 这个方法可以被重复调用以刷新触发器列表。
     */
    private fun loadTriggerWorkflows() {
        appStartWorkflows = workflowManager.findAppStartTriggerWorkflows()
        Log.d("VFlowAccessibility", "触发器列表已刷新，当前数量: ${appStartWorkflows.size}")
    }


    override fun onInterrupt() {
        // [修复] onInterrupt是抽象方法，没有父类实现，因此不能调用super.onInterrupt()
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        debounceJob?.cancel()
        // 服务中断时，注销广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(workflowUpdateReceiver)
        Log.w("VFlowAccessibility", "无障碍服务被中断。")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceStateBus.onAccessibilityServiceDisconnected(this)
        debounceJob?.cancel()
        // 服务解绑时，注销广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(workflowUpdateReceiver)
        Log.w("VFlowAccessibility", "无障碍服务已解绑。")
        // [修改] 将super调用放在最后是推荐的做法
        return super.onUnbind(intent)
    }
}