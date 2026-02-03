// 文件: OpenCVManager.kt
package com.chaomixian.vflow.core.opencv

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import org.opencv.android.OpenCVLoader

/**
 * OpenCV SDK 管理器
 * 负责 OpenCV 库的初始化和状态管理
 */
object OpenCVManager {
    private const val TAG = "OpenCVManager"

    @Volatile
    var isInitialized = false
        private set

    /**
     * 初始化 OpenCV SDK
     * @param context 应用上下文
     * @return 是否初始化成功
     */
    fun initialize(context: Context): Boolean {
        if (isInitialized) return true

        val success = try {
            OpenCVLoader.initDebug()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "OpenCV initialization failed", e)
            false
        }

        if (success) {
            isInitialized = true
            DebugLogger.i(TAG, "OpenCV SDK initialized successfully")
        } else {
            DebugLogger.w(TAG, "OpenCV SDK initialization failed, will use legacy matcher")
        }

        return success
    }
}
