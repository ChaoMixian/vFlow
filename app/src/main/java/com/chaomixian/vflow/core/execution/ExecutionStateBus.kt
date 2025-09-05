// 文件: main/java/com/chaomixian/vflow/core/execution/ExecutionStateBus.kt

package com.chaomixian.vflow.core.execution

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 表示工作流执行状态的密封类。
 */
sealed class ExecutionState {
    /** 工作流正在运行。 */
    data class Running(val workflowId: String) : ExecutionState()
    /** 工作流已正常结束。 */
    data class Finished(val workflowId: String) : ExecutionState()
    /** 工作流被用户或系统取消。 */
    data class Cancelled(val workflowId: String) : ExecutionState()
}

/**
 * 一个全局的事件总线，用于广播工作流的执行状态。
 * UI 组件可以订阅 stateFlow 来实时更新界面。
 */
object ExecutionStateBus {

    // 使用 MutableSharedFlow 来广播事件
    private val _stateFlow = MutableSharedFlow<ExecutionState>()
    val stateFlow = _stateFlow.asSharedFlow()

    /**
     * 发布一个新的执行状态。
     * @param state 要发布的状态。
     */
    suspend fun postState(state: ExecutionState) {
        _stateFlow.emit(state)
    }
}