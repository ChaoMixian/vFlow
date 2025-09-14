// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/AppStartTriggerHandler.kt
// 描述: [已修复] 实现了精细化的工作流管理，性能更佳，稳定性更高。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class AppStartTriggerHandler : BaseTriggerHandler() {

    // 使用线程安全的列表来存储正在监听的工作流
    private val listeningWorkflows = CopyOnWriteArrayList<Workflow>()
    private val debounceTime = 100L
    private var collectorJob: Job? = null

    companion object {
        private const val TAG = "AppStartTriggerHandler"
    }

    override fun start(context: Context) {
        super.start(context)
        collectorJob = triggerScope.launch {
            // [核心修复] 使用 debounce 和 onEach 代替 collectLatest 中的 delay
            // 这可以防止在高频事件下丢失触发
            ServiceStateBus.windowChangeEventFlow
                .debounce(debounceTime) // 在事件流停止 debounceTime 后才发出最新的一个事件
                .onEach { (packageName, className) -> // 对每个发出的事件执行操作
                    checkForAppStartTrigger(context, packageName, className)
                }
                .launchIn(this) // 在当前协程作用域中启动流收集
        }
        Log.d(TAG, "AppStartTriggerHandler 已启动并开始监听窗口变化事件。")
    }

    override fun stop(context: Context) {
        super.stop(context)
        collectorJob?.cancel()
        Log.d(TAG, "AppStartTriggerHandler 已停止。")
    }

    // 实现精细化添加
    override fun addWorkflow(context: Context, workflow: Workflow) {
        // 避免重复添加
        if (listeningWorkflows.none { it.id == workflow.id }) {
            listeningWorkflows.add(workflow)
        }
        Log.d(TAG, "已添加 '${workflow.name}' 到监听列表。当前监听数量: ${listeningWorkflows.size}")
    }

    // [核心修复] 实现精细化移除
    override fun removeWorkflow(context: Context, workflowId: String) {
        val removed = listeningWorkflows.removeAll { it.id == workflowId }
        if (removed) {
            Log.d(TAG, "已从监听列表移除 workflowId: $workflowId。当前监听数量: ${listeningWorkflows.size}")
        }
    }

    private fun checkForAppStartTrigger(context: Context, packageName: String, className: String) {
        // 直接遍历当前正在监听的列表
        listeningWorkflows.forEach { workflow ->
            val targetPackage = workflow.triggerConfig?.get("packageName") as? String
            val targetActivity = workflow.triggerConfig?.get("activityName") as? String

            if (targetPackage == packageName) {
                val activityMatches = targetActivity == "LAUNCH" || targetActivity == className
                if (activityMatches) {
                    Log.i(TAG, "触发器匹配成功！准备执行工作流: ${workflow.name}")
                    triggerScope.launch {
                        WorkflowExecutor.execute(workflow, context)
                    }
                }
            }
        }
    }
}