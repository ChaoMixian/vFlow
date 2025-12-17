// 文件: main/java/com/chaomixian/vflow/core/workflow/module/scripted/ModuleManager.kt

package com.chaomixian.vflow.core.workflow.module.scripted

import android.content.Context
import android.net.Uri
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.services.ShizukuDiagnostic
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipInputStream

object ModuleManager {
    private const val TAG = "ModuleManager"
    private const val MODULES_DIR_NAME = "vflow_modules"
    private const val MANIFEST_NAME = "manifest.json"
    private const val SCRIPT_NAME = "script.lua"

    private val gson = Gson()
    private var hasLoaded = false // 新增：防止重复加载标志位

    /**
     * 从 ZIP 文件安装模块。
     */
    fun installModule(context: Context, zipUri: Uri): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(zipUri)
                ?: return Result.failure(Exception("无法打开文件"))

            // 临时解压目录
            val tempDir = File(context.cacheDir, "temp_module_install")
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
                        file.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            // 递归查找 manifest.json，以兼容套了一层文件夹的情况
            val manifestFile = tempDir.walkTopDown().find { it.name == MANIFEST_NAME }

            if (manifestFile == null) {
                return Result.failure(Exception("模块格式错误：未找到 manifest.json"))
            }

            // 确定模块的真实根目录（manifest.json 所在的目录）
            val moduleRoot = manifestFile.parentFile ?: tempDir
            val scriptFile = File(moduleRoot, SCRIPT_NAME)

            if (!scriptFile.exists()) {
                return Result.failure(Exception("模块格式错误：在 manifest.json 同级目录下未找到 script.lua"))
            }

            // 解析 Manifest 以获取 ID
            val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
            if (manifest.id.isBlank()) {
                return Result.failure(Exception("模块 ID 不能为空"))
            }

            // 最终安装目录
            val modulesDir = File(context.filesDir, MODULES_DIR_NAME)
            val targetDir = File(modulesDir, manifest.id)
            if (targetDir.exists()) targetDir.deleteRecursively() // 覆盖旧版本

            // 只将模块根目录下的内容复制到安装目录
            moduleRoot.copyRecursively(targetDir, overwrite = true)

            // 清理临时文件
            tempDir.deleteRecursively()

            // 立即加载新模块
            loadSingleModule(targetDir)

            Result.success("模块 '${manifest.name}' 安装成功")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "安装模块失败", e)
            Result.failure(e)
        }
    }

    /**
     * 加载所有已安装的模块。
     * @param force 是否强制重新加载（默认 false）。
     */
    fun loadModules(context: Context, force: Boolean = false) {
        DebugLogger.d(TAG, "开始加载用户模块...")
        if (hasLoaded && !force) {
            DebugLogger.d(TAG, "模块已经加载，跳过")
            return
        } // 如果已经加载过且不强制，则跳过

        val modulesDir = File(context.filesDir, MODULES_DIR_NAME)
        if (!modulesDir.exists()) {
            DebugLogger.d(TAG, "没有找到用户模块")
            return
        }

        modulesDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            DebugLogger.d(TAG, "发现用户模块，开始加载...")
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
     * 获取已安装模块列表 (用于 UI 显示)。
     */
    fun getInstalledModules(context: Context): List<ModuleManifest> {
        val list = mutableListOf<ModuleManifest>()
        val modulesDir = File(context.filesDir, MODULES_DIR_NAME)
        if (!modulesDir.exists()) return emptyList()

        modulesDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val manifestFile = File(dir, MANIFEST_NAME)
            if (manifestFile.exists()) {
                try {
                    val manifest = gson.fromJson(manifestFile.readText(), ModuleManifest::class.java)
                    list.add(manifest)
                } catch (e: Exception) { /* ignore */ }
            }
        }
        return list
    }

    fun deleteModule(context: Context, moduleId: String) {
        val modulesDir = File(context.filesDir, MODULES_DIR_NAME)
        val targetDir = File(modulesDir, moduleId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
    }
}