// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/model/TouchRecordingData.kt

package com.chaomixian.vflow.core.workflow.model

import com.google.gson.Gson

/**
 * 完整的触摸录制数据
 */
data class TouchRecordingData(
    val screenW: Int,                      // 录制时屏幕宽度
    val screenH: Int,                      // 录制时屏幕高度
    val duration: Long,                    // 录制总时长(ms)
    val events: List<TouchEventRecord>     // 触摸事件序列
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): TouchRecordingData? {
            return try {
                Gson().fromJson(json, TouchRecordingData::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
