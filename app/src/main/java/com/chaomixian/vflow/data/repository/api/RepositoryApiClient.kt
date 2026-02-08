// 文件: main/java/com/chaomixian/vflow/data/repository/api/RepositoryApiClient.kt
package com.chaomixian.vflow.data.repository.api

import com.chaomixian.vflow.data.repository.model.RepoIndex
import com.chaomixian.vflow.data.repository.model.RepoModuleIndex
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 仓库API客户端
 * 用于从GitHub仓库获取工作流列表、模块列表和下载资源
 */
object RepositoryApiClient {

    private const val BASE_URL = "https://raw.githubusercontent.com/ChaoMixian/vFlow-Repos/main"
    private const val WORKFLOW_INDEX_URL = "$BASE_URL/workflows/index.json"
    private const val MODULE_INDEX_URL = "$BASE_URL/modules/index.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 获取工作流仓库索引
     * @return RepoIndex 仓库索引数据
     * @throws IOException 网络错误或解析错误
     */
    suspend fun fetchWorkflowIndex(): Result<RepoIndex> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(WORKFLOW_INDEX_URL)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("获取工作流索引失败: ${response.code}")
                )
            }

            val json = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("响应体为空")
                )

            val index = gson.fromJson(json, RepoIndex::class.java)
            Result.success(index)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException("解析工作流索引失败: ${e.message}", e))
        }
    }

    /**
     * 获取模块仓库索引
     * @return RepoModuleIndex 模块索引数据
     * @throws IOException 网络错误或解析错误
     */
    suspend fun fetchModuleIndex(): Result<RepoModuleIndex> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(MODULE_INDEX_URL)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("获取模块索引失败: ${response.code}")
                )
            }

            val json = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("响应体为空")
                )

            val index = gson.fromJson(json, RepoModuleIndex::class.java)
            Result.success(index)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException("解析模块索引失败: ${e.message}", e))
        }
    }

    /**
     * 下载工作流JSON
     * @param downloadUrl 工作流的下载URL
     * @return String 工作流JSON字符串（可能是单个工作流或文件夹导出格式）
     * @throws IOException 网络错误或下载失败
     */
    suspend fun downloadWorkflowRaw(downloadUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("下载工作流失败: ${response.code}")
                )
            }

            val json = response.body?.string()
                ?: return@withContext Result.failure(
                    IOException("响应体为空")
                )

            Result.success(json)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException("下载工作流失败: ${e.message}", e))
        }
    }

    /**
     * 下载模块ZIP文件到字节数组
     * @param downloadUrl 模块的下载URL
     * @return ByteArray ZIP文件字节数组
     * @throws IOException 网络错误
     */
    suspend fun downloadModule(downloadUrl: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("下载模块失败: ${response.code}")
                )
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(
                    IOException("响应体为空")
                )

            Result.success(bytes)

        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException("下载模块失败: ${e.message}", e))
        }
    }

    /**
     * 解析工作流JSON，忽略 _meta 字段
     * 使用 Gson 的自定义反序列化来跳过未知字段
     */
    private fun parseWorkflowIgnoringMeta(json: String): Workflow {
        return gson.fromJson(json, Workflow::class.java)
    }
}
