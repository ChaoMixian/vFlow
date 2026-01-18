// 文件: server/src/main/java/com/chaomixian/vflow/server/common/Logger.kt
package com.chaomixian.vflow.server.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * vFlow Core 日志工具
 * 提供分级日志输出，支持时间戳和调试开关
 */
object Logger {

    // 日志级别枚举
    enum class Level(val priority: Int, val tag: String) {
        DEBUG(0, "D"),
        INFO(1, "I"),
        WARN(2, "W"),
        ERROR(3, "E")
    }

    // 当前日志级别（只打印此级别及更高级别的日志）
    private var currentLevel = Level.DEBUG

    /**
     * 设置日志级别
     */
    fun setLevel(level: Level) {
        currentLevel = level
    }

    /**
     * 获取时间戳字符串
     */
    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 格式化日志输出
     */
    private fun format(level: Level, tag: String, message: String): String {
        val timestamp = getTimestamp()
        val thread = Thread.currentThread().name
        return "[$timestamp] $thread/${level.tag} [$tag] $message"
    }

    /**
     * 打印日志（如果级别允许）
     */
    private fun log(level: Level, tag: String, message: String) {
        if (level.priority >= currentLevel.priority) {
            val formatted = format(level, tag, message)
            when (level) {
                Level.ERROR -> System.err.println(formatted)
                Level.WARN -> System.err.println(formatted)
                else -> System.out.println(formatted)
            }
        }
    }

    /**
     * DEBUG 级别日志
     * 用于详细的调试信息
     */
    fun debug(tag: String, message: String) {
        log(Level.DEBUG, tag, message)
    }

    /**
     * INFO 级别日志
     * 用于一般信息输出
     */
    fun info(tag: String, message: String) {
        log(Level.INFO, tag, message)
    }

    /**
     * WARN 级别日志
     * 用于警告信息
     */
    fun warn(tag: String, message: String) {
        log(Level.WARN, tag, message)
    }

    /**
     * ERROR 级别日志
     * 用于错误信息
     */
    fun error(tag: String, message: String) {
        log(Level.ERROR, tag, message)
    }

    /**
     * ERROR 级别日志（带异常）
     */
    fun error(tag: String, message: String, throwable: Throwable) {
        log(Level.ERROR, tag, message)
        throwable.printStackTrace()
    }
}
