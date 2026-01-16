// 文件: main/java/com/chaomixian/vflow/data/update/GitHubRelease.kt
package com.chaomixian.vflow.data.update

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitHub Release 数据模型
 */
@Parcelize
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val htmlUrl: String
) : Parcelable {

    /**
     * 获取版本号（去除v前缀）
     */
    fun getVersionWithoutPrefix(): String {
        return tagName.removePrefix("v")
    }

    /**
     * 判断是否为稳定版本（不包含-pr、-alpha、-beta等）
     */
    fun isStable(): Boolean {
        return !isPrerelease && !tagName.contains(Regex("-(alpha|beta|rc|pr)"))
    }

    /**
     * 获取格式化后的发布时间
     */
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val outputFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
            val date = inputFormat.parse(publishedAt)
            date?.let { outputFormat.format(it) } ?: publishedAt
        } catch (e: Exception) {
            publishedAt
        }
    }

    /**
     * 获取相对时间（如"3天前"）
     */
    fun getRelativeTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val publishDate = inputFormat.parse(publishedAt) ?: return publishedAt
            val now = Date()
            val diffInMillis = now.time - publishDate.time
            val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

            when {
                diffInDays == 0L -> "今天"
                diffInDays == 1L -> "昨天"
                diffInDays < 30L -> "${diffInDays}天前"
                diffInDays < 365L -> "${diffInDays / 30}个月前"
                else -> "${diffInDays / 365}年前"
            }
        } catch (e: Exception) {
            publishedAt
        }
    }
}

/**
 * 版本更新信息
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val release: GitHubRelease?
)
