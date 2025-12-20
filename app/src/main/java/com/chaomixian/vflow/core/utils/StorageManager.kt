package com.chaomixian.vflow.core.utils

import android.os.Environment
import java.io.File

/**
 * 存储路径管理器。
 * 统一管理 /sdcard/vFlow 及其子目录，解决 Shell/App 权限差异问题。
 */
object StorageManager {

    private const val ROOT_DIR_NAME = "vFlow"

    /** 获取根目录: /sdcard/vFlow */
    val rootDir: File
        get() {
            val dir = File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /** 获取脚本目录: /sdcard/vFlow/scripts */
    val scriptsDir: File
        get() = getSubDir("scripts")

    /** 获取日志目录: /sdcard/vFlow/logs */
    val logsDir: File
        get() = getSubDir("logs")

    /** 获取临时文件目录: /sdcard/vFlow/temp */
    val tempDir: File
        get() = getSubDir("temp")

    /** 获取用户模块目录: /sdcard/vFlow/modules */
    val modulesDir: File
        get() = getSubDir("modules")

    /** 获取备份目录: /sdcard/vFlow/backups */
    val backupsDir: File
        get() = getSubDir("backups")

    private fun getSubDir(name: String): File {
        val dir = File(rootDir, name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 清理过期的临时文件
     */
    fun clearOldTempFiles(maxAgeMillis: Long = 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        tempDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMillis) {
                file.delete()
            }
        }
    }
}