// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ListeningTriggerHandler.kt
// 描述: 一个更高级的抽象基类，自动管理监听器的启动和停止，减少重复代码。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.Workflow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 一个更高级的抽象基类，自动管理监听器的启动和停止。
 * 具体的 Handler 只需要实现如何开始/停止监听，以及如何处理事件即可。
 */
abstract class ListeningTriggerHandler : BaseTriggerHandler() {

    // 使用线程安全的列表来存储当前正在监听此触发器的工作流
    protected val listeningWorkflows = CopyOnWriteArrayList<Workflow>()

    /**
     * 子类需要实现的：获取当前处理器对应的触发器模块ID。
     * 例如: "vflow.trigger.wifi"
     */
    protected abstract fun getTriggerModuleId(): String

    /**
     * 子类需要实现的：启动实际的系统事件监听。
     * 这个方法只会在第一个相关工作流被添加时调用一次。
     */
    protected abstract fun startListening(context: Context)

    /**
     * 子类需要实现的：停止实际的系统事件监听。
     * 这个方法只会在最后一个相关工作流被移除时调用一次。
     */
    protected abstract fun stopListening(context: Context)

    /**
     * 服务启动时的初始化逻辑。
     * 检查是否已存在需要监听的工作流，如果存在，则立即启动监听。
     */
    final override fun start(context: Context) {
        super.start(context)
        val activeWorkflows = workflowManager.getAllWorkflows().filter {
            it.isEnabled && it.triggerConfig?.get("type") == getTriggerModuleId()
        }
        if (activeWorkflows.isNotEmpty()) {
            listeningWorkflows.addAll(activeWorkflows)
            startListening(context)
        }
    }

    /**
     * 服务停止时的清理逻辑。
     */
    final override fun stop(context: Context) {
        super.stop(context)
        if (listeningWorkflows.isNotEmpty()) {
            stopListening(context)
        }
    }

    /**
     * 添加一个工作流时的逻辑。
     * 自动处理启动监听的逻辑。
     */
    final override fun addWorkflow(context: Context, workflow: Workflow) {
        val shouldStart = listeningWorkflows.isEmpty()
        listeningWorkflows.removeAll { it.id == workflow.id } // 确保唯一性
        listeningWorkflows.add(workflow)

        if (shouldStart) {
            startListening(context)
        }
    }

    /**
     * 移除一个工作流时的逻辑。
     * 自动处理停止监听的逻辑。
     */
    final override fun removeWorkflow(context: Context, workflowId: String) {
        val existed = listeningWorkflows.any { it.id == workflowId }
        if (existed) {
            listeningWorkflows.removeAll { it.id == workflowId }
            if (listeningWorkflows.isEmpty()) {
                stopListening(context)
            }
        }
    }
}