package com.chaomixian.vflow.speech.voice

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.speech.SherpaNcnnDownloadSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class VoiceTriggerModelProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
)

class VoiceTriggerModelManager(context: Context) {
    companion object {
        private const val TAG = "VoiceTriggerModelMgr"
        private const val MODELS_DIR_NAME = "voice_trigger_models"
        private const val MODEL_FILE_NAME = "silero_vad.onnx"
        private const val MODEL_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/snakers4/silero-vad/v6.0/src/silero_vad/data/silero_vad.onnx"
        const val MODELS_DOWNLOAD_PAGE_URL =
            "https://github.com/snakers4/silero-vad/tree/v6.0/src/silero_vad/data"
    }

    private val appContext = context.applicationContext
    private val modelsRootDir = File(appContext.filesDir, MODELS_DIR_NAME)
    private val okHttpClient = OkHttpClient.Builder().build()

    fun modelFile(): File = File(modelsRootDir, MODEL_FILE_NAME)

    fun isModelInstalled(): Boolean {
        val file = modelFile()
        return file.isFile && file.length() > 0L
    }

    fun uninstallModel(): Boolean {
        val file = modelFile()
        return if (file.exists()) file.delete() else false
    }

    suspend fun downloadModel(
        source: SherpaNcnnDownloadSource,
        onProgress: ((VoiceTriggerModelProgress) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        modelsRootDir.mkdirs()
        val targetFile = modelFile()
        val tempFile = File(appContext.cacheDir, "$MODEL_FILE_NAME.download")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        downloadArchive(source.resolveUrl(MODEL_DOWNLOAD_URL), tempFile, onProgress)
        validateModel(tempFile)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        DebugLogger.i(TAG, "Voice trigger model is ready at ${targetFile.absolutePath}")
    }

    fun validateInstalledModel() {
        validateModel(modelFile())
    }

    private fun validateModel(file: File) {
        if (!file.isFile || file.length() <= 0L) {
            throw IOException("Voice trigger model file is missing")
        }
        OnnxSileroVad.validateModel(file)
    }

    private fun downloadArchive(
        url: String,
        targetFile: File,
        onProgress: ((VoiceTriggerModelProgress) -> Unit)?,
    ) {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Voice trigger model download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Voice trigger model download returned empty body")
            val totalBytes = body.contentLength().takeIf { it > 0L }
            targetFile.parentFile?.mkdirs()

            BufferedInputStream(body.byteStream()).use { input ->
                BufferedOutputStream(FileOutputStream(targetFile)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    onProgress?.invoke(VoiceTriggerModelProgress(0L, totalBytes))
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress?.invoke(VoiceTriggerModelProgress(downloadedBytes, totalBytes))
                        }
                        read = input.read(buffer)
                    }
                }
            }
        }
    }
}
