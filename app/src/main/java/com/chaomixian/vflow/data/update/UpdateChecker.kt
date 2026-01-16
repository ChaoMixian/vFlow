// 文件: main/java/com/chaomixian/vflow/data/update/UpdateChecker.kt
package com.chaomixian.vflow.data.update

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub API 客户端
 * 用于获取vFlow的最新版本信息
 */
object UpdateChecker {

    private const val GITHUB_REPO = "ChaoMixian/vFlow"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 获取所有releases
     */
    suspend fun getAllReleases(): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("获取releases失败: ${response.code}")
                )
            }

            val json = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("响应体为空")
                )

            val releases = gson.fromJson<List<GitHubReleaseItem>>(
                json,
                object : TypeToken<List<GitHubReleaseItem>>() {}.type
            )

            val result = releases.map { it.toGitHubRelease() }
            Result.success(result)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException("解析releases失败: ${e.message}", e))
        }
    }

    /**
     * 检查更新
     * @param currentVersion 当前版本号（如"1.3.3"）
     */
    suspend fun checkUpdate(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val releasesResult = getAllReleases()

        releasesResult.fold(
            onSuccess = { releases ->
                // 查找最新的稳定版本
                val latestStableRelease = releases.firstOrNull { it.isStable() }

                if (latestStableRelease != null) {
                    val hasUpdate = compareVersions(currentVersion, latestStableRelease.getVersionWithoutPrefix()) < 0

                    Result.success(UpdateInfo(
                        hasUpdate = hasUpdate,
                        currentVersion = currentVersion,
                        latestVersion = latestStableRelease.getVersionWithoutPrefix(),
                        release = if (hasUpdate) latestStableRelease else null
                    ))
                } else {
                    Result.success(UpdateInfo(
                        hasUpdate = false,
                        currentVersion = currentVersion,
                        latestVersion = currentVersion,
                        release = null
                    ))
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * 比较两个版本号
     * @return 负数：v1 < v2，零：v1 == v2，正数：v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }

            if (p1 != p2) {
                return p1 - p2
            }
        }

        return 0
    }
}

/**
 * GitHub API 返回的 Release JSON 数据结构
 */
private data class GitHubReleaseItem(
    val tag_name: String,
    val name: String,
    val body: String,
    val prerelease: Boolean,
    val published_at: String,
    val html_url: String
) {
    fun toGitHubRelease(): GitHubRelease {
        return GitHubRelease(
            tagName = tag_name,
            name = name,
            body = body,
            isPrerelease = prerelease,
            publishedAt = published_at,
            htmlUrl = html_url
        )
    }
}
