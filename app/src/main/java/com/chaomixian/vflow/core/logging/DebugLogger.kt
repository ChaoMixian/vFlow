// 文件: main/java/com/chaomixian/vflow/core/logging/DebugLogger.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {

    private val logBuffer = mutableListOf<String>()
    private var isLoggingEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var appContext: Context? = null

    // Shell 日志相关常量
    private const val SHELL_LOG_FILENAME = "vflow_shell.log"
    private const val MARKER_FILENAME = "vflow_logging_enabled.marker"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        isLoggingEnabled = prefs.getBoolean("debugLoggingEnabled", false)
        // 初始化时同步标记文件状态
        syncMarkerFile(context, isLoggingEnabled)
    }

    fun setLoggingEnabled(enabled: Boolean, context: Context) {
        isLoggingEnabled = enabled
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debugLoggingEnabled", enabled).apply()

        // 同步标记文件
        syncMarkerFile(context, enabled)

        if (!enabled) {
            clearLogs() // 关闭时清空所有日志
        }
    }

    // 私有辅助方法：同步标记文件
    private fun syncMarkerFile(context: Context, enabled: Boolean) {
        try {
            val markerFile = File(context.cacheDir, MARKER_FILENAME)
            if (enabled) {
                if (!markerFile.exists()) {
                    markerFile.createNewFile()
                }
            } else {
                if (markerFile.exists()) {
                    markerFile.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DebugLogger", "无法操作标记文件", e)
        }
    }

    fun isLoggingEnabled(): Boolean = isLoggingEnabled

    fun getLogs(): String {
        val deviceInfo = "App Version: ${getAppVersion()}\n" +
                "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "------------------------------------\n"

        val appLogs = synchronized(logBuffer) {
            logBuffer.joinToString("\n")
        }

        // 读取 Shell 日志
        val shellLogs = try {
            appContext?.let { ctx ->
                val shellFile = File(ctx.cacheDir, SHELL_LOG_FILENAME)
                if (shellFile.exists()) {
                    "\n\n========== Shell Script Logs ==========\n" + shellFile.readText()
                } else {
                    "\n\n[Warning: can not find shell logs\n"
                }
            } ?: ""
        } catch (e: Exception) {
            "\n[Error reading shell logs: ${e.message}]"
        }

        return deviceInfo + appLogs + shellLogs
    }

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

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        // 清除 Shell 日志文件
        try {
            appContext?.let { ctx ->
                val shellFile = File(ctx.cacheDir, SHELL_LOG_FILENAME)
                if (shellFile.exists()) {
                    shellFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        d("DebugLogger", "应用日志缓冲区和 Shell 日志文件已清空。")
    }

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
        // 为了防止内存溢出，限制缓冲区大小
        if (logBuffer.size > 5000) {
            logBuffer.removeAt(0)
        }
    }
}