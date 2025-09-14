// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ITriggerHandler.kt

package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.Workflow

/**
 * 触发器处理器接口。
 * 每个实现类负责一种特定类型的自动触发逻辑。
 */
interface ITriggerHandler {
    /**
     * 启动此触发器处理器的监听逻辑。
     * @param context 应用上下文。
     */
    fun start(context: Context)

    /**
     * 停止此触发器处理器的所有活动。
     * @param context 应用上下文。
     */
    fun stop(context: Context)

    /**
     * 当一个工作流被启用或添加时调用。
     * 处理器应开始监听这个工作流的触发条件。
     * @param context 应用上下文。
     * @param workflow 新增的或被启用的工作流。
     */
    fun addWorkflow(context: Context, workflow: Workflow)

    /**
     * 当一个工作流被禁用或删除时调用。
     * 处理器应停止监听这个工作流的触发条件。
     * @param context 应用上下文。
     * @param workflowId 被移除或禁用的工作流的ID。
     */
    fun removeWorkflow(context: Context, workflowId: String)
}