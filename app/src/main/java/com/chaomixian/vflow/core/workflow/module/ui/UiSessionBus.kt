// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/UiSessionBus.kt
package com.chaomixian.vflow.core.workflow.module.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

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
    // 每个 Session 的事件流（UI -> Workflow）- 改为独立通道，避免事件混淆
    private val sessionEventFlows = ConcurrentHashMap<String, MutableSharedFlow<UiEvent>>()

    // 每个 Session 的指令流（Workflow -> UI）
    private val sessionCommandFlows = ConcurrentHashMap<String, MutableSharedFlow<UiCommand>>()

    // Session 存活状态
    private val activeSessions = ConcurrentHashMap<String, Boolean>()

    fun registerSession(sessionId: String) {
        sessionEventFlows[sessionId] = MutableSharedFlow(
            replay = 0,  // 不重放，每次只接收新事件
            extraBufferCapacity = 50,  // 增加缓冲到 50 个事件，避免快速点击时丢失
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST  // 缓冲满时丢弃最旧的事件
        )
        sessionCommandFlows[sessionId] = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 50,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
        activeSessions[sessionId] = true
    }

    fun unregisterSession(sessionId: String) {
        sessionEventFlows.remove(sessionId)
        sessionCommandFlows.remove(sessionId)
        activeSessions.remove(sessionId)
    }

    fun isSessionClosed(sessionId: String): Boolean {
        return activeSessions[sessionId] != true
    }

    fun notifyClosed(sessionId: String) {
        activeSessions[sessionId] = false
    }

    // UI -> Workflow: 发送事件到指定 session 的通道
    suspend fun emitEvent(event: UiEvent) {
        val flow = sessionEventFlows[event.sessionId]
        if (flow != null) {
            android.util.Log.d("UiSessionBus", "发送事件: ${event.elementId} - ${event.type}")
            flow.emit(event)
        } else {
            android.util.Log.e("UiSessionBus", "事件流不存在: ${event.sessionId}")
        }
    }

    // Workflow -> UI: 发送指令
    suspend fun sendCommand(sessionId: String, command: UiCommand) {
        val flow = sessionCommandFlows[sessionId]
        if (flow != null) {
            flow.emit(command)
        }
    }

    // UI 监听指令
    fun getCommandFlow(sessionId: String) = sessionCommandFlows[sessionId]?.asSharedFlow()

    // Workflow 等待特定 Session 的事件
    suspend fun waitForEvent(sessionId: String): UiEvent? {
        val flow = sessionEventFlows[sessionId]
        if (flow == null) {
            android.util.Log.e("UiSessionBus", "事件流不存在: $sessionId")
            return null
        }

        android.util.Log.d("UiSessionBus", "开始等待事件: $sessionId")

        // 直接阻塞等待事件，直到 session 关闭
        while (!isSessionClosed(sessionId)) {
            try {
                // first() 会挂起直到有事件到达，不会丢失缓冲区中的事件
                val event = flow.first()
                android.util.Log.d("UiSessionBus", "接收到事件: ${event.elementId} - ${event.type} (session: $sessionId)")
                return event
            } catch (e: CancellationException) {
                // 协程被取消，检查 session 状态后决定是否继续等待
                android.util.Log.d("UiSessionBus", "等待被取消，检查 session 状态: $sessionId")
                if (isSessionClosed(sessionId)) {
                    android.util.Log.d("UiSessionBus", "Session 已关闭，退出: $sessionId")
                    return null
                }
                // Session 仍然活跃，重新进入循环等待
            } catch (e: Exception) {
                // 其他异常，记录日志并返回 null
                android.util.Log.e("UiSessionBus", "等待事件异常: ${e.message}", e)
                return null
            }
        }

        android.util.Log.d("UiSessionBus", "Session 已关闭，退出等待: $sessionId")
        return null
    }
}