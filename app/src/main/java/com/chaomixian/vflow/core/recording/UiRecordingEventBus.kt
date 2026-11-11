package com.chaomixian.vflow.core.recording

/**
 * UI 录制事件总线。
 *
 * 无障碍服务在捕获到点击、文本变化、滚动、窗口切换等事件时通过
 * [emit] 发布；录制前台服务在录制期间注册 [listener] 收集事件。
 * 不使用 Flow，避免在无障碍回调中引入协程开销。
 */
object UiRecordingEventBus {

    @Volatile
    var listener: ((UiRecordingEvent) -> Unit)? = null

    val hasListener: Boolean
        get() = listener != null

    fun emit(event: UiRecordingEvent) {
        val current = listener ?: return
        runCatching { current(event) }
    }
}
