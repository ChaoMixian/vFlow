package com.chaomixian.vflow.ocr

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PpOcrV5ModelInstallResult(
    val modelDir: File,
)

class PpOcrV5ModelManager(context: Context) {
    companion object {
        private const val TAG = "PpOcrV5ModelManager"
        private const val MODELS_DIR_NAME = "ppocr_v5_models"
        private const val ASSETS_DIR = "ppocr_v5"

        const val DET_PARAM_FILE = "det.ncnn.param"
        const val DET_BIN_FILE = "det.ncnn.bin"
        const val REC_PARAM_FILE = "rec.ncnn.param"
        const val REC_BIN_FILE = "rec.ncnn.bin"
        const val KEYS_FILE = "ppocr_keys.txt"

        private val REQUIRED_MODEL_FILES = listOf(
            DET_PARAM_FILE,
            DET_BIN_FILE,
            REC_PARAM_FILE,
            REC_BIN_FILE,
            KEYS_FILE,
        )
    }

    private val appContext = context.applicationContext
    private val modelsRootDir = File(appContext.filesDir, MODELS_DIR_NAME)

    fun modelDir(): File = File(modelsRootDir, "ppocr_v5_mobile")

    fun isModelInstalled(): Boolean {
        return hasRequiredModelFiles(modelDir())
    }

    fun uninstallModel(): Boolean {
        val dir = modelDir()
        return if (dir.exists()) dir.deleteRecursively() else false
    }

    suspend fun ensureModelInstalled(): PpOcrV5ModelInstallResult = withContext(Dispatchers.IO) {
        installBundledModelIfNeeded(force = false)
    }

    suspend fun reinstallBundledModel(): PpOcrV5ModelInstallResult = withContext(Dispatchers.IO) {
        installBundledModelIfNeeded(force = true)
    }

    private fun installBundledModelIfNeeded(force: Boolean): PpOcrV5ModelInstallResult {
        modelsRootDir.mkdirs()
        val targetDir = modelDir()
        if (!force && hasRequiredModelFiles(targetDir)) {
            return PpOcrV5ModelInstallResult(modelDir = targetDir)
        }

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        try {
            REQUIRED_MODEL_FILES.forEach { fileName ->
                copyAssetToFile("$ASSETS_DIR/$fileName", File(targetDir, fileName))
            }
            if (!hasRequiredModelFiles(targetDir)) {
                throw IOException("内置 PP-OCRv5 模型安装不完整")
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        }

        DebugLogger.i(TAG, "Bundled PP-OCRv5 model is ready at ${targetDir.absolutePath}")
        return PpOcrV5ModelInstallResult(modelDir = targetDir)
    }

    private fun copyAssetToFile(assetPath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        appContext.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun hasRequiredModelFiles(dir: File): Boolean {
        return dir.isDirectory && REQUIRED_MODEL_FILES.all { File(dir, it).isFile }
    }
}
