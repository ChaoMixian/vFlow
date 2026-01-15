// 文件: java/com/chaomixian/vflow/core/execution/UiLoopState.kt
package com.chaomixian.vflow.core.execution

/**
 * UI 循环状态
 * 必须放在 core.execution 包下以继承 sealed class LoopState
 */
data class UiLoopState(
    val sessionId: String
) : LoopState()