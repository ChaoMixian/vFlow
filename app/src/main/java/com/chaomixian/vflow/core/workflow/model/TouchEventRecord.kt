// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/model/TouchEventRecord.kt

package com.chaomixian.vflow.core.workflow.model

/**
 * 单个触摸事件记录
 */
data class TouchEventRecord(
    val action: Int,              // MotionEvent.ACTION_DOWN/UP/MOVE
    val x: Float,                 // X坐标
    val y: Float,                 // Y坐标
    val pointerId: Int = 0,       // 指针ID
    val timestamp: Long           // 从录制开始的时间偏移(ms)
)
