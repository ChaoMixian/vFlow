// 文件: main/java/com/chaomixian/vflow/core/logging/DebugLogger.kt
// 描述: 提供一个统一的日志记录工具，支持同时输出到 Logcat 和内存缓冲区。
package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {

    private val logBuffer = mutableListOf<String>()
    private var isLoggingEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var appContext: Context? = null // 存储Context以便获取版本名

    // 初始化时从 SharedPreferences 读取日志记录状态
    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        isLoggingEnabled = prefs.getBoolean("debugLoggingEnabled", false)
    }

    // 设置是否启用文件日志记录
    fun setLoggingEnabled(enabled: Boolean, context: Context) {
        isLoggingEnabled = enabled
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debugLoggingEnabled", enabled).apply()
        if (!enabled) {
            logBuffer.clear() // 关闭时清空缓冲区
        }
    }

    // 获取日志记录状态
    fun isLoggingEnabled(): Boolean = isLoggingEnabled

    // 获取所有日志记录
    fun getLogs(): String {
        val deviceInfo = "App Version: ${getAppVersion()}\n" +
                "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "------------------------------------\n"
        return deviceInfo + synchronized(logBuffer) {
            logBuffer.joinToString("\n")
        }
    }

    /**
     * 新增：获取应用版本名
     */
    private fun getAppVersion(): String {
        return try {
            appContext?.let {
                val pInfo = it.packageManager.getPackageInfo(it.packageName, 0)
                pInfo.versionName
            } ?: "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }


    // 清空日志
    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        d("DebugLogger", "日志缓冲区已清空。")
    }

    // --- 重载的日志方法 ---

    fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
        if (isLoggingEnabled) {
            addToBuffer("D", tag, message)
        }
    }

    fun d(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.d(tag, message, throwable)
        if (isLoggingEnabled) {
            addToBuffer("D", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
        }
    }

    fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
        if (isLoggingEnabled) {
            addToBuffer("I", tag, message)
        }
    }

    fun i(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.i(tag, message, throwable)
        if (isLoggingEnabled) {
            addToBuffer("I", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        android.util.Log.w(tag, message, throwable)
        if (isLoggingEnabled) {
            addToBuffer("W", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
        if (isLoggingEnabled) {
            addToBuffer("E", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
        }
    }

    // 将日志条目添加到缓冲区
    @Synchronized
    private fun addToBuffer(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        logBuffer.add("$timestamp $level/$tag: $message")
        // 为了防止内存溢出，可以限制缓冲区大小
        if (logBuffer.size > 5000) {
            logBuffer.removeAt(0)
        }
    }
}