// 文件: main/java/com/chaomixian/vflow/core/logging/LogManager.kt
package com.chaomixian.vflow.core.logging

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.ui.main.MainActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize
import java.util.Date

// 定义日志条目的数据结构
@Parcelize
data class LogEntry(
    val workflowId: String,
    val workflowName: String,
    val timestamp: Long,
    val status: LogStatus,
    val message: String? = null
) : Parcelable

// 定义日志状态的枚举
enum class LogStatus {
    SUCCESS,
    CANCELLED,
    FAILURE
}

/**
 * 日志管理器，负责持久化存储和检索工作流执行日志。
 */
object LogManager {
    private const val PREFS_NAME = MainActivity.LOG_PREFS_NAME
    private const val LOGS_KEY = "execution_logs"
    private const val MAX_LOGS = 50 // 最多保存50条日志

    private lateinit var context: Context
    private val gson = Gson()

    fun initialize(appContext: Context) {
        context = appContext
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

    private fun saveLogs(logs: List<LogEntry>) {
        val json = gson.toJson(logs)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LOGS_KEY, json)
            .apply()
    }

    private fun getLogs(): List<LogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LOGS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            try {
                // [关键修复] 在反序列化后，过滤掉任何可能导致崩溃的不完整条目
                val logs: List<LogEntry?>? = gson.fromJson(json, type)
                logs?.filterNotNull()?.filter {
                    // 确保所有必要的字段都不是 null
                    it.workflowId != null && it.workflowName != null && it.status != null
                } ?: emptyList()
            } catch (e: Exception) {
                // 如果解析失败，返回空列表以避免崩溃
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}