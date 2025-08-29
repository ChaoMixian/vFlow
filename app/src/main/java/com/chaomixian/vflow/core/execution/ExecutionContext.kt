package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.services.AccessibilityService

// 执行时传递的上下文，包含变量和对系统服务的引用
data class ExecutionContext(
    val variables: MutableMap<String, Any?>,
    val accessibilityService: AccessibilityService // 传递服务实例以便模块调用
)