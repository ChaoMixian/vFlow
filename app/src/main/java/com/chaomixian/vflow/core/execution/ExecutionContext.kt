package com.chaomixian.vflow.core.execution

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 执行时传递的上下文 (重构)。
 * @param applicationContext 应用的全局上下文。
 * @param variables 存储用户在编辑器中设置的静态参数值。
 * @param magicVariables 存储上游模块传递下来的动态魔法变量。
 * @param services 一个服务容器，模块可以从中按需获取所需的服务实例。
 * @param allSteps 整个工作流的步骤列表。
 * @param currentStepIndex 当前正在执行的步骤的索引。
 */
data class ExecutionContext(
    val applicationContext: Context,
    val variables: MutableMap<String, Any?>,
    val magicVariables: MutableMap<String, Any?>,
    val services: ExecutionServices,
    val allSteps: List<ActionStep>,
    val currentStepIndex: Int
)