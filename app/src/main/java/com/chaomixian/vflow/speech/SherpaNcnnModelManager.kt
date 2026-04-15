package com.chaomixian.vflow.speech

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chaomixian.vflow.core.logging.DebugLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

enum class SherpaNcnnModelStage {
    DOWNLOADING,
    IMPORTING,
    VERIFYING,
    EXTRACTING,
}

enum class SherpaNcnnDownloadSource(
    val preferenceValue: String,
    private val mirrorPrefix: String? = null,
) {
    DIRECT("direct"),
    GHPROXY_NET("ghproxy_net", "https://ghproxy.net/"),
    GH_PROXY_COM("gh_proxy_com", "https://gh-proxy.com/"),
    ;

    fun resolveUrl(originalUrl: String): String {
        return mirrorPrefix?.plus(originalUrl) ?: originalUrl
    }

    companion object {
        fun fromPreferenceValue(value: String?): SherpaNcnnDownloadSource {
            return entries.firstOrNull { it.preferenceValue == value } ?: DIRECT
        }
    }
}

data class SherpaNcnnModelProgress(
    val stage: SherpaNcnnModelStage,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
)

data class SherpaNcnnModelSpec(
    val key: String,
    val modelDirName: String,
    val archiveName: String,
    val downloadUrl: String,
    val sha256: String,
)

data class SherpaNcnnModelInstallResult(
    val modelDir: File,
    val downloaded: Boolean,
    val modelSpec: SherpaNcnnModelSpec,
)

class SherpaNcnnModelManager(context: Context) {
    companion object {
        private const val TAG = "SherpaNcnnModelManager"
        private const val MODELS_DIR_NAME = "sherpa_ncnn_models"
        private const val MODELS_RELEASE_BASE_URL =
            "https://github.com/k2-fsa/sherpa-ncnn/releases/download/models"
        const val MODELS_DOWNLOAD_PAGE_URL =
            "https://github.com/k2-fsa/sherpa-ncnn/releases/tag/models"
        const val DOWNLOAD_SOURCE_PREF_KEY = "sherpa_ncnn_download_source"

        val SMALL_BILINGUAL_MODEL = SherpaNcnnModelSpec(
            key = "small_bilingual_zh_en",
            modelDirName = "sherpa-ncnn-streaming-zipformer-small-bilingual-zh-en-2023-02-16",
            archiveName = "sherpa-ncnn-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2",
            downloadUrl = "$MODELS_RELEASE_BASE_URL/sherpa-ncnn-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2",
            sha256 = "ecd7f49183c003a1e4bffb952e424ec576ec4b135b7b8cc3e6e9f998b653f7fe",
        )

        val ENGLISH_MODEL = SherpaNcnnModelSpec(
            key = "english_streaming",
            modelDirName = "sherpa-ncnn-streaming-zipformer-en-2023-02-13",
            archiveName = "sherpa-ncnn-streaming-zipformer-en-2023-02-13.tar.bz2",
            downloadUrl = "$MODELS_RELEASE_BASE_URL/sherpa-ncnn-streaming-zipformer-en-2023-02-13.tar.bz2",
            sha256 = "bca17f828df49df029a7c7d3730c368e40c219166f408ebc4e6dac8b194033a7",
        )

        val SUPPORTED_MODEL_SPECS = listOf(
            SMALL_BILINGUAL_MODEL,
            ENGLISH_MODEL,
        )

        private val REQUIRED_MODEL_FILES = listOf(
            "encoder_jit_trace-pnnx.ncnn.param",
            "encoder_jit_trace-pnnx.ncnn.bin",
            "decoder_jit_trace-pnnx.ncnn.param",
            "decoder_jit_trace-pnnx.ncnn.bin",
            "joiner_jit_trace-pnnx.ncnn.param",
            "joiner_jit_trace-pnnx.ncnn.bin",
            "tokens.txt",
        )
    }

    private val appContext = context.applicationContext
    private val modelsRootDir = File(appContext.filesDir, MODELS_DIR_NAME)
    private val okHttpClient = OkHttpClient.Builder().build()

    fun resolveModelSpec(languageTag: String): SherpaNcnnModelSpec {
        val normalizedLanguageTag = languageTag.lowercase(Locale.ROOT)
        return when {
            normalizedLanguageTag.startsWith("en") -> ENGLISH_MODEL
            else -> SMALL_BILINGUAL_MODEL
        }
    }

    fun resolveInstalledModelSpec(languageTag: String): SherpaNcnnModelSpec? {
        val preferredSpec = resolveModelSpec(languageTag)
        return preferredSpec.takeIf { hasRequiredModelFiles(modelDirFor(it)) }
    }

    fun installedModelDir(languageTag: String): File? {
        return resolveInstalledModelSpec(languageTag)?.let(::modelDirFor)
    }

    fun isModelInstalled(languageTag: String): Boolean {
        return installedModelDir(languageTag) != null
    }

    fun isModelInstalled(spec: SherpaNcnnModelSpec): Boolean {
        return hasRequiredModelFiles(modelDirFor(spec))
    }

    fun uninstallModel(spec: SherpaNcnnModelSpec): Boolean {
        val dir = modelDirFor(spec)
        return if (dir.exists()) dir.deleteRecursively() else false
    }

    suspend fun downloadModel(
        languageTag: String,
        source: SherpaNcnnDownloadSource,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)? = null,
    ): SherpaNcnnModelInstallResult = withContext(Dispatchers.IO) {
        val spec = resolveModelSpec(languageTag)
        downloadModel(spec, source, onProgress)
    }

    suspend fun downloadModel(
        spec: SherpaNcnnModelSpec,
        source: SherpaNcnnDownloadSource,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)? = null,
    ): SherpaNcnnModelInstallResult = withContext(Dispatchers.IO) {
        val tempArchive = File(appContext.cacheDir, spec.archiveName)
        downloadArchive(spec, source, tempArchive, onProgress)
        installArchive(spec, tempArchive, downloaded = true, onProgress = onProgress)
    }

    suspend fun importModelArchive(
        languageTag: String,
        archiveUri: Uri,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)? = null,
    ): SherpaNcnnModelInstallResult = withContext(Dispatchers.IO) {
        val spec = importArchive(archiveUri, onProgress)
        if (!isModelCompatible(languageTag, spec.modelSpec)) {
            spec.modelDir.deleteRecursively()
            throw IOException("The selected model does not match the requested recognition language")
        }
        spec
    }

    suspend fun importModelArchive(
        archiveUri: Uri,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)? = null,
    ): SherpaNcnnModelInstallResult = withContext(Dispatchers.IO) {
        importArchive(archiveUri, onProgress)
    }

    private fun importArchive(
        archiveUri: Uri,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)? = null,
    ): SherpaNcnnModelInstallResult {
        modelsRootDir.mkdirs()
        val tempArchive = File(
            appContext.cacheDir,
            importedArchiveName(archiveUri)
        )
        copyArchiveFromUri(archiveUri, tempArchive, onProgress)
        val spec = identifyArchiveSpec(tempArchive)
            ?: run {
                tempArchive.delete()
                throw IOException("Unsupported Sherpa-ncnn model archive")
            }
        return installArchive(spec, tempArchive, downloaded = false, onProgress = onProgress)
    }

    private fun installArchive(
        spec: SherpaNcnnModelSpec,
        archiveFile: File,
        downloaded: Boolean,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)?,
    ): SherpaNcnnModelInstallResult {
        modelsRootDir.mkdirs()
        val targetDir = modelDirFor(spec)
        val parentPath = modelsRootDir.canonicalPath + File.separator

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }

        try {
            verifyArchive(spec, archiveFile, onProgress)
            onProgress?.invoke(SherpaNcnnModelProgress(stage = SherpaNcnnModelStage.EXTRACTING))
            extractArchive(archiveFile, modelsRootDir, parentPath)
            if (!hasRequiredModelFiles(targetDir)) {
                throw IOException("Sherpa model installation is incomplete for ${spec.key}.")
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        } finally {
            archiveFile.delete()
        }

        DebugLogger.i(TAG, "Sherpa model ${spec.key} is ready at ${targetDir.absolutePath}")
        return SherpaNcnnModelInstallResult(
            modelDir = targetDir,
            downloaded = downloaded,
            modelSpec = spec,
        )
    }

    private fun copyArchiveFromUri(
        archiveUri: Uri,
        targetFile: File,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)?,
    ) {
        val totalBytes = queryUriSize(archiveUri)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.parentFile?.mkdirs()

        appContext.contentResolver.openInputStream(archiveUri)?.use { input ->
            BufferedInputStream(input).use { bufferedInput ->
                BufferedOutputStream(FileOutputStream(targetFile)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    onProgress?.invoke(
                        SherpaNcnnModelProgress(
                            stage = SherpaNcnnModelStage.IMPORTING,
                            downloadedBytes = 0L,
                            totalBytes = totalBytes,
                        )
                    )
                    var read = bufferedInput.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            onProgress?.invoke(
                                SherpaNcnnModelProgress(
                                    stage = SherpaNcnnModelStage.IMPORTING,
                                    downloadedBytes = copiedBytes,
                                    totalBytes = totalBytes,
                                )
                            )
                        }
                        read = bufferedInput.read(buffer)
                    }
                }
            }
        } ?: throw IOException("Unable to read the selected archive")
    }

    private fun downloadArchive(
        spec: SherpaNcnnModelSpec,
        source: SherpaNcnnDownloadSource,
        targetFile: File,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)?,
    ) {
        val downloadUrl = source.resolveUrl(spec.downloadUrl)
        val existingBytes = targetFile.takeIf { it.exists() }?.length() ?: 0L
        val requestBuilder = Request.Builder().url(downloadUrl)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        DebugLogger.i(TAG, "Downloading Sherpa model ${spec.key} from $downloadUrl")

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            when {
                response.code == 416 -> {
                    targetFile.delete()
                    downloadArchive(spec, source, targetFile, onProgress)
                    return
                }
                !response.isSuccessful -> {
                    throw IOException("Sherpa model download failed with code ${response.code}")
                }
            }

            val responseBody = response.body ?: throw IOException("Sherpa model response body is empty")
            val append = existingBytes > 0L && response.code == 206
            if (!append && targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.parentFile?.mkdirs()

            val totalBytes = when {
                response.code == 206 -> {
                    parseTotalLengthFromContentRange(response.header("Content-Range"))
                        ?: (existingBytes + maxOf(0L, responseBody.contentLength()))
                }
                else -> responseBody.contentLength().takeIf { it > 0L }
            }

            var downloadedBytes = if (append) existingBytes else 0L
            onProgress?.invoke(
                SherpaNcnnModelProgress(
                    stage = SherpaNcnnModelStage.DOWNLOADING,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                )
            )

            responseBody.byteStream().use { input ->
                BufferedInputStream(input).use { bufferedInput ->
                    BufferedOutputStream(FileOutputStream(targetFile, append)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = bufferedInput.read(buffer)
                        while (read >= 0) {
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                onProgress?.invoke(
                                    SherpaNcnnModelProgress(
                                        stage = SherpaNcnnModelStage.DOWNLOADING,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                    )
                                )
                            }
                            read = bufferedInput.read(buffer)
                        }
                    }
                }
            }
        }
    }

    private fun verifyArchive(
        spec: SherpaNcnnModelSpec,
        archiveFile: File,
        onProgress: ((SherpaNcnnModelProgress) -> Unit)?,
    ) {
        onProgress?.invoke(SherpaNcnnModelProgress(stage = SherpaNcnnModelStage.VERIFYING))
        val actualSha256 = calculateSha256(archiveFile)
        if (!actualSha256.equals(spec.sha256, ignoreCase = true)) {
            archiveFile.delete()
            throw IOException("Sherpa model checksum mismatch for ${spec.key}")
        }
    }

    private fun extractArchive(archiveFile: File, outputDir: File, parentCanonicalPath: String) {
        BufferedInputStream(FileInputStream(archiveFile)).use { fileInput ->
            BZip2CompressorInputStream(fileInput).use { bzInput ->
                TarArchiveInputStream(bzInput).use { tarInput ->
                    var entry = tarInput.nextTarEntry
                    while (entry != null) {
                        val outputFile = File(outputDir, entry.name.removePrefix("./"))
                        val canonicalOutputPath = outputFile.canonicalPath
                        if (!canonicalOutputPath.startsWith(parentCanonicalPath)) {
                            throw IOException("Unexpected model entry path: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                                tarInput.copyTo(output)
                            }
                        }
                        entry = tarInput.nextTarEntry
                    }
                }
            }
        }
    }

    private fun identifyArchiveSpec(archiveFile: File): SherpaNcnnModelSpec? {
        val actualSha256 = calculateSha256(archiveFile)
        return supportedModelSpecs().firstOrNull { spec ->
            spec.sha256.equals(actualSha256, ignoreCase = true)
        }
    }

    private fun supportedModelSpecs(): List<SherpaNcnnModelSpec> {
        return SUPPORTED_MODEL_SPECS
    }

    private fun isModelCompatible(languageTag: String, spec: SherpaNcnnModelSpec): Boolean {
        val preferredSpec = resolveModelSpec(languageTag)
        return when (preferredSpec) {
            ENGLISH_MODEL -> spec == ENGLISH_MODEL || spec == SMALL_BILINGUAL_MODEL
            else -> spec == SMALL_BILINGUAL_MODEL
        }
    }

    private fun importedArchiveName(uri: Uri): String {
        val fromUri = queryUriName(uri)?.substringAfterLast('/')
        return when {
            fromUri.isNullOrBlank() -> "imported-sherpa-model.tar.bz2"
            fromUri.endsWith(".tar.bz2") -> fromUri
            else -> "$fromUri.tar.bz2"
        }
    }

    private fun queryUriName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    private fun queryUriSize(uri: Uri): Long? {
        val projection = arrayOf(OpenableColumns.SIZE)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index).takeIf { it > 0L }
                }
            }
        }
        return null
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseTotalLengthFromContentRange(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val total = contentRange.substringAfterLast('/').toLongOrNull()
        return total?.takeIf { it > 0L }
    }

    private fun modelDirFor(spec: SherpaNcnnModelSpec): File {
        return File(modelsRootDir, spec.modelDirName)
    }

    private fun hasRequiredModelFiles(dir: File): Boolean {
        return dir.isDirectory && REQUIRED_MODEL_FILES.all { name ->
            File(dir, name).isFile
        }
    }
}
