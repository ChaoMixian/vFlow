package com.chaomixian.vflow.core.recording

/**
 * 一次完整的 UI 录制结果。
 *
 * 录制以「时间段」(segment) 为单位保存：每 300ms 采样一次界面结构，
 * 只有界面结构发生变化时才开启新的时间段并保存截图，未变化的时间段
 * 只延长 endMs，从而显著减少截图和数据的体积。
 */
data class UiRecording(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val durationMs: Long = 0L,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val segments: List<UiRecordingSegment> = emptyList(),
    val events: List<UiRecordingEvent> = emptyList(),
)

/**
 * 一个 UI 状态时间段。
 *
 * @param screenshot 相对于录制根目录的截图路径，例如 `rec_xxx/seg_0.jpg`；
 *                   没有截图时为 null。
 */
data class UiRecordingSegment(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val packageName: String = "",
    val activityName: String = "",
    val nodes: List<RecordedUiNode> = emptyList(),
    val screenshot: String? = null,
)

/**
 * 录制时保存的单个 UI 节点快照。
 */
data class RecordedUiNode(
    val text: String = "",
    val description: String = "",
    val hint: String = "",
    val viewId: String = "",
    val className: String = "",
    val packageName: String = "",
    val gkd: String = "",
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focusable: Boolean = false,
    val focused: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = false,
    val password: Boolean = false,
    val depth: Int = 0,
    val index: Int = 0,
    val actions: List<String> = emptyList(),
)

/**
 * 录制过程中捕获的交互事件。
 *
 * [timestampMs] 在录制文件中是相对于录制开始的毫秒数；
 * 无障碍服务刚发出时则是 [android.os.SystemClock.elapsedRealtime]。
 */
data class UiRecordingEvent(
    val timestampMs: Long = 0L,
    val type: String = TYPE_CLICK,
    val gkd: String = "",
    val viewId: String = "",
    val className: String = "",
    val text: String = "",
    val packageName: String = "",
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        const val CLICK = "CLICK"
        const val TEXT_CHANGED = "TEXT_CHANGED"
        const val SCROLL = "SCROLL"
        const val WINDOW_CHANGED = "WINDOW_CHANGED"

        const val TYPE_CLICK = CLICK
        const val TYPE_TEXT_CHANGED = TEXT_CHANGED
        const val TYPE_SCROLL = SCROLL
        const val TYPE_WINDOW_CHANGED = WINDOW_CHANGED
    }
}
