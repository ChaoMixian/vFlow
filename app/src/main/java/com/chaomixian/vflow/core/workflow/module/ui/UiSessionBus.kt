// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/UiSessionBus.kt
package com.chaomixian.vflow.core.workflow.module.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * UI 事件
 *
 * 表示从 UI 发送到工作流的事件。
 *
 * @property sessionId 会话 ID，用于区分不同的界面窗口
 * @property elementId 触发事件的组件 ID
 * @property type 事件类型：click（点击）、change（值改变）、submit（提交）、closed（关闭）、back_pressed（返回键）
 * @property value 事件携带的值（如开关的布尔值、输入框的文本）
 * @property allComponentValues 所有输入组件的当前值映射（组件ID -> 值）
 */
data class UiEvent(
    val sessionId: String,
    val elementId: String,
    val type: String,
    val value: Any?,
    val allComponentValues: Map<String, Any?> = emptyMap()
)

/**
 * UI 指令
 *
 * 表示从工作流发送到 UI 的命令。
 *
 * @property type 指令类型：update（更新组件）、close（关闭界面）、toast（显示提示）
 * @property targetId 目标组件 ID（仅 update 类型需要）
 * @property payload 指令参数映射
 */
data class UiCommand(
    val type: String,
    val targetId: String?,
    val payload: Map<String, Any?>
)

/**
 * UI 会话总线
 *
 * 负责 UI 层和工作流层之间的双向通信。
 *
 * 功能：
 * - UI -> Workflow: 通过事件流传递用户操作
 * - Workflow -> UI: 通过指令流发送更新命令
 * - 管理多个会话的生命周期
 *
 * 使用流程：
 * 1. Workflow 调用 registerSession(sessionId) 创建会话
 * 2. UI 通过 emitEvent() 发送事件
 * 3. Workflow 通过 waitForEvent() 等待事件
 * 4. Workflow 通过 sendCommand() 发送指令
 * 5. UI 通过 getCommandFlow() 监听指令
 * 6. 最后调用 unregisterSession() 清理会话
 */
object UiSessionBus {
    // 全局事件流（UI -> Workflow）
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 20)
    val events = _events.asSharedFlow()

    // 每个 Session 的指令流（Workflow -> UI）
    private val sessionCommandFlows = ConcurrentHashMap<String, MutableSharedFlow<UiCommand>>()

    // Session 存活状态
    private val activeSessions = ConcurrentHashMap<String, Boolean>()

    fun registerSession(sessionId: String) {
        sessionCommandFlows[sessionId] = MutableSharedFlow(extraBufferCapacity = 10)
        activeSessions[sessionId] = true
    }

    fun unregisterSession(sessionId: String) {
        sessionCommandFlows.remove(sessionId)
        activeSessions.remove(sessionId)
    }

    fun isSessionClosed(sessionId: String): Boolean {
        return activeSessions[sessionId] != true
    }

    fun notifyClosed(sessionId: String) {
        activeSessions[sessionId] = false
    }

    // UI -> Workflow: 发送事件
    suspend fun emitEvent(event: UiEvent) {
        _events.emit(event)
    }

    // Workflow -> UI: 发送指令
    suspend fun sendCommand(sessionId: String, command: UiCommand) {
        sessionCommandFlows[sessionId]?.emit(command)
    }

    // UI 监听指令
    fun getCommandFlow(sessionId: String) = sessionCommandFlows[sessionId]?.asSharedFlow()

    // Workflow 等待特定 Session 的事件
    suspend fun waitForEvent(sessionId: String): UiEvent {
        // 过滤出属于当前 Session 的事件，并取第一个
        return _events.filter { it.sessionId == sessionId }.first()
    }
}