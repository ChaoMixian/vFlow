package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.services.AccessibilityService

/**
 * 执行时传递的上下文。
 * @param variables 存储用户在编辑器中设置的静态参数值。
 * @param magicVariables 存储上游模块传递下来的动态魔法变量。
 * @param accessibilityService 无障碍服务实例。
 */
data class ExecutionContext(
    val variables: MutableMap<String, Any?>,
    val magicVariables: MutableMap<String, Any?>,
    val accessibilityService: AccessibilityService
)