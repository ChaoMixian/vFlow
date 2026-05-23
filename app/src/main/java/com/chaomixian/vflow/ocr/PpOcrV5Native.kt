package com.chaomixian.vflow.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PpOcrV5NativePoint(
    val x: Float,
    val y: Float,
)

data class PpOcrV5NativeItem(
    val text: String,
    val score: Float,
    val orientation: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
    val points: List<PpOcrV5NativePoint>,
)

data class PpOcrV5NativeResponse(
    val success: Boolean,
    val error: String? = null,
    val items: List<PpOcrV5NativeItem> = emptyList(),
)

class PpOcrV5Native(private val appContext: Context) {
    companion object {
        private val gson = Gson()
        private val initMutex = Mutex()
        @Volatile private var initializedModelDir: String? = null

        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("ppocrv5_jni")
        }
    }

    suspend fun recognizeUri(uri: Uri): PpOcrV5NativeResponse = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val bitmap = decodeBitmap(uri) ?: return@withContext PpOcrV5NativeResponse(
            success = false,
            error = "bitmap_decode_failed"
        )
        try {
            val json = nativeRecognizeBitmap(bitmap)
            gson.fromJson(json, PpOcrV5NativeResponse::class.java)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognizeUri(
        uri: Uri,
        region: Rect,
    ): PpOcrV5NativeResponse = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val bitmap = decodeBitmap(uri) ?: return@withContext PpOcrV5NativeResponse(
            success = false,
            error = "bitmap_decode_failed"
        )
        val croppedBitmap = cropBitmap(bitmap, region) ?: run {
            bitmap.recycle()
            return@withContext PpOcrV5NativeResponse(
                success = false,
                error = "invalid_region"
            )
        }
        try {
            val json = nativeRecognizeBitmap(croppedBitmap)
            gson.fromJson(json, PpOcrV5NativeResponse::class.java)
        } finally {
            croppedBitmap.recycle()
            bitmap.recycle()
        }
    }

    private suspend fun ensureModelLoaded() {
        val installResult = PpOcrV5ModelManager(appContext).ensureModelInstalled()
        val modelDirPath = installResult.modelDir.absolutePath
        if (initializedModelDir == modelDirPath) return

        initMutex.withLock {
            if (initializedModelDir == modelDirPath) return
            val loaded = nativeLoadModel(modelDirPath)
            if (!loaded) {
                throw IllegalStateException("PP-OCRv5 模型加载失败")
            }
            initializedModelDir = modelDirPath
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)?.let { bitmap ->
                if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap, region: Rect): Bitmap? {
        val left = region.left.coerceAtLeast(0)
        val top = region.top.coerceAtLeast(0)
        val right = region.right.coerceAtMost(bitmap.width)
        val bottom = region.bottom.coerceAtMost(bitmap.height)
        if (left >= right || top >= bottom) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private external fun nativeLoadModel(modelDirPath: String): Boolean
    private external fun nativeRecognizeBitmap(bitmap: Bitmap): String
    external fun nativeRelease()
}
