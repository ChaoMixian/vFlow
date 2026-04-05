// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ListeningTriggerHandler.kt
// 描述: 一个更高级的抽象基类，自动管理监听器的启动和停止，减少重复代码。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 一个更高级的抽象基类，自动管理监听器的启动和停止。
 * 具体的 Handler 只需要实现如何开始/停止监听，以及如何处理事件即可。
 */
abstract class ListeningTriggerHandler : BaseTriggerHandler() {

    protected val listeningTriggers = CopyOnWriteArrayList<TriggerSpec>()

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

    final override fun start(context: Context) {
        super.start(context)
    }

    final override fun stop(context: Context) {
        super.stop(context)
        if (listeningTriggers.isNotEmpty()) {
            stopListening(context)
        }
        listeningTriggers.clear()
    }

    final override fun addTrigger(context: Context, trigger: TriggerSpec) {
        val shouldStart = listeningTriggers.isEmpty()
        listeningTriggers.removeAll { it.triggerId == trigger.triggerId }
        listeningTriggers.add(trigger)

        if (shouldStart) {
            startListening(context)
        }
    }

    final override fun removeTrigger(context: Context, triggerId: String) {
        val existed = listeningTriggers.any { it.triggerId == triggerId }
        if (existed) {
            listeningTriggers.removeAll { it.triggerId == triggerId }
            if (listeningTriggers.isEmpty()) {
                stopListening(context)
            }
        }
    }
}
