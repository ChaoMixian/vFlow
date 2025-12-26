// 文件: main/java/com/chaomixian/vflow/core/workflow/module/scripted/ModuleManager.kt
package com.chaomixian.vflow.core.workflow.module.scripted

import android.content.Context
import android.net.Uri
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.utils.StorageManager
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipInputStream

object ModuleManager {
    private const val TAG = "ModuleManager"
    private const val MANIFEST_NAME = "manifest.json"
    private const val SCRIPT_NAME = "script.lua"

    private val gson = Gson()
    private var hasLoaded = false

    // 安装会话数据类
    data class InstallSession(
        val manifest: ModuleManifest,
        val tempDir: File
    )

    /**
     * 阶段1：准备安装。
     * 解压 ZIP，校验文件结构，读取 Manifest。
     * 不会覆盖现有模块，用于 UI 检查冲突。
     */
    fun prepareInstall(context: Context, zipUri: Uri): Result<InstallSession> {
        return try {
            val inputStream = context.contentResolver.openInputStream(zipUri)
                ?: return Result.failure(Exception("无法打开文件"))

            // 使用临时目录
            val tempDir = File(StorageManager.tempDir, "install_${System.currentTimeMillis()}")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            // 解压所有文件
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // 防止 Zip Slip 漏洞
                    if (entryName.contains("..")) {
                        throw SecurityException("Zip entry path invalid: $entryName")
                    }
                    val file = File(tempDir, entryName)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }

            // 查找 manifest.json (处理可能的嵌套目录)
            val manifestFile = tempDir.walkTopDown().find { it.name == MANIFEST_NAME }
                ?: return Result.failure(Exception("模块格式错误：未找到 manifest.json"))

            // 确定模块的真实根目录
            val moduleRoot = manifestFile.parentFile ?: tempDir

            // 校验 script.lua
            if (!File(moduleRoot, SCRIPT_NAME).exists()) {
                return Result.failure(Exception("模块格式错误：未找到 script.lua"))
            }

            // 解析 Manifest
            val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
            if (manifest.id.isBlank()) {
                return Result.failure(Exception("模块 ID 不能为空"))
            }

            // 返回会话信息 (包含模块根目录)
            Result.success(InstallSession(manifest, moduleRoot))
        } catch (e: Exception) {
            DebugLogger.e(TAG, "准备安装模块失败", e)
            Result.failure(e)
        }
    }

    /**
     * 阶段2：提交安装。
     * 将临时文件移动到正式目录，并注册模块。
     */
    fun commitInstall(session: InstallSession): Result<String> {
        return try {
            val modulesDir = StorageManager.modulesDir
            // 使用 ID 作为目录名
            val targetDir = File(modulesDir, session.manifest.id)

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            // 从临时目录复制到正式目录
            session.tempDir.copyRecursively(targetDir, overwrite = true)

            // 清理临时文件
            session.tempDir.deleteRecursively()

            // 立即加载
            loadSingleModule(targetDir)

            Result.success("模块 '${session.manifest.name}' 安装成功")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "提交安装失败", e)
            Result.failure(e)
        }
    }

    /**
     * 加载所有已安装的模块。
     */
    fun loadModules(context: Context, force: Boolean = false) {
        DebugLogger.d(TAG, "开始加载用户模块...")
        if (hasLoaded && !force) {
            DebugLogger.d(TAG, "模块已经加载，跳过")
            return
        }

        // 从公共存储目录加载
        val modulesDir = StorageManager.modulesDir
        if (!modulesDir.exists()) {
            DebugLogger.d(TAG, "没有找到用户模块目录 (${modulesDir.absolutePath})")
            return
        }

        modulesDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            DebugLogger.d(TAG, "发现用户模块，开始加载: ${dir.name}")
            loadSingleModule(dir)
        }
        hasLoaded = true
    }

    private fun loadSingleModule(moduleDir: File) {
        try {
            val manifestFile = File(moduleDir, MANIFEST_NAME)
            val scriptFile = File(moduleDir, SCRIPT_NAME)

            if (manifestFile.exists() && scriptFile.exists()) {
                val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
                val scriptContent = scriptFile.readText()

                val module = ScriptedModule(manifest, scriptContent)
                ModuleRegistry.register(module)
                DebugLogger.d(TAG, "已加载用户模块: ${manifest.name} (${manifest.id})")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "加载模块 ${moduleDir.name} 失败", e)
        }
    }

    /**
     * 检查模块 ID 是否已安装
     */
    fun isModuleInstalled(moduleId: String): Boolean {
        return ModuleRegistry.getModule(moduleId) != null
    }
}