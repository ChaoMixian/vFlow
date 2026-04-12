// 文件: main/java/com/chaomixian/vflow/core/logging/LogManager.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.ui.main.MainActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize
import androidx.core.content.edit

// 定义日志条目的数据结构
@Parcelize
data class LogEntry(
    val workflowId: String,
    val workflowName: String,
    val timestamp: Long,
    val status: LogStatus,
    val message: String? = null,
    val detailedLog: String? = null,
    val messageKey: LogMessageKey? = null,
    val messageArgs: List<String> = emptyList()
) : Parcelable {
    fun resolveMessage(context: Context): String? {
        return LogMessageFormatter.resolve(this) { resId, formatArgs ->
            if (formatArgs.isEmpty()) {
                context.getString(resId)
            } else {
                context.getString(resId, *formatArgs)
            }
        }
    }
}

// 定义日志状态的枚举
enum class LogStatus {
    SUCCESS,
    CANCELLED,
    FAILURE
}

enum class LogMessageKey {
    EXECUTION_COMPLETED,
    EXECUTION_CANCELLED,
    EXECUTION_FAILED_AT_STEP
}

/**
 * 日志管理器，负责持久化存储和检索工作流执行日志。
 * 同时作为全局 ApplicationContext 的持有者供模块使用。
 */
object LogManager {
    private const val PREFS_NAME = MainActivity.LOG_PREFS_NAME
    private const val LOGS_KEY = "execution_logs"
    private const val MAX_LOGS = 50 // 最多保存50条日志

    // 公开 applicationContext 供其他模块（如动态权限检查）使用
    lateinit var applicationContext: Context
        private set

    private val gson = Gson()

    fun initialize(appContext: Context) {
        applicationContext = appContext
    }

    /**
     * 添加一条新的日志记录。
     */
    @Synchronized
    fun addLog(entry: LogEntry) {
        val logs = getLogs().toMutableList()
        logs.add(0, entry) // 添加到列表顶部
        // 保持列表大小不超过 MAX_LOGS
        val trimmedLogs = if (logs.size > MAX_LOGS) logs.subList(0, MAX_LOGS) else logs
        saveLogs(trimmedLogs)
    }

    /**
     * 获取最近的日志记录。
     * @param limit 要获取的日志数量。
     * @return 日志条目列表。
     */
    @Synchronized
    fun getRecentLogs(limit: Int): List<LogEntry> {
        return getLogs().take(limit)
    }

    @Synchronized
    fun getAllLogs(): List<LogEntry> {
        return getLogs()
    }

    private fun saveLogs(logs: List<LogEntry>) {
        val json = gson.toJson(logs)
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(LOGS_KEY, json)
            }
    }

    private fun getLogs(): List<LogEntry> {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LOGS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            try {
                // Gson 可能反序列化出字段为 null 的脏数据，这里统一过滤掉。
                val logs: List<LogEntry?>? = gson.fromJson(json, type)
                logs?.mapNotNull(::sanitizeLogEntry) ?: emptyList()
            } catch (e: Exception) {
                // 如果解析失败，返回空列表以避免崩溃
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun sanitizeLogEntry(entry: LogEntry?): LogEntry? {
        if (entry == null) return null
        return runCatching {
            LogEntry(
                workflowId = entry.workflowId,
                workflowName = entry.workflowName,
                timestamp = entry.timestamp,
                status = entry.status,
                message = entry.message,
                detailedLog = entry.detailedLog,
                messageKey = entry.messageKey,
                messageArgs = entry.messageArgs
            )
        }.getOrNull()
    }
}
