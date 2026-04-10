// 文件: main/java/com/chaomixian/vflow/core/logging/DebugLogger.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Build
import com.chaomixian.vflow.core.utils.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {

    private val logBuffer = mutableListOf<String>()
    private val crashBuffer = mutableListOf<String>()
    private var isLoggingEnabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var appContext: Context? = null

    // 使用公共目录下的日志文件
    private const val SHELL_LOG_FILENAME = "vflow_shell.log"
    private const val MARKER_FILENAME = "vflow_logging_enabled.marker"
    private const val MAX_CRASH_BUFFER = 200

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        isLoggingEnabled = prefs.getBoolean("debugLoggingEnabled", false)
        syncMarkerFile(isLoggingEnabled)
    }

    fun setLoggingEnabled(enabled: Boolean, context: Context) {
        isLoggingEnabled = enabled
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debugLoggingEnabled", enabled).apply()

        syncMarkerFile(enabled)

        if (!enabled) {
            clearLogs()
        }
    }

    // 使用 StorageManager 的日志目录
    private fun syncMarkerFile(enabled: Boolean) {
        try {
            val markerFile = File(StorageManager.logsDir, MARKER_FILENAME)
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
            android.util.Log.e("DebugLogger", "无法操作标记文件 (Permission denied?)", e)
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

        // 从 StorageManager 读取 Shell 日志
        val shellLogs = try {
            val shellFile = File(StorageManager.logsDir, SHELL_LOG_FILENAME)
            if (shellFile.exists()) {
                "\n\n========== Shell Script Logs ==========\n" + shellFile.readText()
            } else {
                "\n\n[Shell logs not found at ${shellFile.absolutePath}]\n"
            }
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
        synchronized(crashBuffer) {
            crashBuffer.clear()
        }
        // 清除 StorageManager 中的 Shell 日志
        try {
            val shellFile = File(StorageManager.logsDir, SHELL_LOG_FILENAME)
            if (shellFile.exists()) {
                shellFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        d("DebugLogger", "应用日志缓冲区和 Shell 日志文件已清空。")
    }

    private inline fun logToSystem(block: () -> Unit) {
        runCatching(block)
    }

    fun d(tag: String, message: String) {
        logToSystem { android.util.Log.d(tag, message) }
        recordLog("D", tag, message)
    }

    fun d(tag: String, message: String, throwable: Throwable?) {
        logToSystem { android.util.Log.d(tag, message, throwable) }
        recordLog("D", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
    }

    fun i(tag: String, message: String) {
        logToSystem { android.util.Log.i(tag, message) }
        recordLog("I", tag, message)
    }

    fun i(tag: String, message: String, throwable: Throwable?) {
        logToSystem { android.util.Log.i(tag, message, throwable) }
        recordLog("I", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        logToSystem { android.util.Log.w(tag, message, throwable) }
        recordLog("W", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        logToSystem { android.util.Log.e(tag, message, throwable) }
        recordLog("E", tag, "$message\n${throwable?.stackTraceToString() ?: ""}")
    }

    private fun recordLog(level: String, tag: String, message: String) {
        val entry = buildLogEntry(level, tag, message)
        synchronized(crashBuffer) {
            crashBuffer.add(entry)
            if (crashBuffer.size > MAX_CRASH_BUFFER) {
                crashBuffer.removeAt(0)
            }
        }
        if (isLoggingEnabled) {
            synchronized(logBuffer) {
                logBuffer.add(entry)
                if (logBuffer.size > 5000) {
                    logBuffer.removeAt(0)
                }
            }
        }
    }

    fun getRecentLogsForCrash(limit: Int = MAX_CRASH_BUFFER): List<String> {
        return synchronized(crashBuffer) {
            crashBuffer.takeLast(limit).toList()
        }
    }

    fun getShellLogsForCrash(maxChars: Int = 8000): String? {
        return runCatching {
            val shellFile = File(StorageManager.logsDir, SHELL_LOG_FILENAME)
            if (!shellFile.exists()) return null
            val contents = shellFile.readText()
            if (contents.length <= maxChars) contents else contents.takeLast(maxChars)
        }.getOrNull()
    }

    private fun buildLogEntry(level: String, tag: String, message: String): String {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        return "$timestamp $level/$tag: $message"
    }
}
