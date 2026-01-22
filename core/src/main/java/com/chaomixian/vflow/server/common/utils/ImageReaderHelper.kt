package com.chaomixian.vflow.server.common.utils

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// 日志工具别名
private val Logger = com.chaomixian.vflow.server.common.Logger

/**
 * ImageReader 辅助类
 * 用于创建和管理ImageReader，从Surface捕获图像
 */
object ImageReaderHelper {

    private const val TAG = "ImageReaderHelper"

    /**
     * 创建ImageReader和Surface
     * @param width 图像宽度
     * @param height 图像高度
     * @param format 图像格式（默认使用RGBA_8888以支持高版本）
     * @return Pair<ImageReader, Surface>
     */
    fun createImageReaderAndSurface(width: Int, height: Int, format: Int = PixelFormat.RGBA_8888): Pair<ImageReader, Surface>? {
        return try {
            val maxImages = 1 // 只需要一帧图像
            val imageReader = ImageReader.newInstance(width, height, format, maxImages)
            val surface = imageReader.surface
            Pair(imageReader, surface)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create ImageReader", e)
            null
        }
    }

    /**
     * 从ImageReader获取最新的图像并转换为字节数组（PNG格式）
     * @param imageReader ImageReader实例
     * @param format 输出格式 ("png" 或 "jpeg")
     * @param quality JPEG质量（1-100），仅对JPEG有效
     * @return 图像的字节数组，如果失败则返回null
     */
    fun acquireLatestImage(imageReader: ImageReader, format: String = "png", quality: Int = 90): ByteArray? {
        var image: Image? = null
        return try {
            image = imageReader.acquireLatestImage()
            if (image == null) {
                Logger.warn(TAG, "No image available")
                return null
            }

            // 将Image转换为Bitmap
            val bitmap = imageToBitmap(image)
            if (bitmap == null) {
                Logger.error(TAG, "Failed to convert image to bitmap")
                return null
            }

            // 将Bitmap转换为字节数组
            val stream = ByteArrayOutputStream()
            val compressFormat = when (format.lowercase()) {
                "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
                else -> Bitmap.CompressFormat.PNG
            }

            bitmap.compress(compressFormat, quality, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to acquire image", e)
            null
        } finally {
            image?.close()
        }
    }

    /**
     * 将Image转换为Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                Logger.error(TAG, "Image has no planes")
                return null
            }

            val width = image.width
            val height = image.height

            // 获取像素数据
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride

            // 计算需要的缓冲区大小
            val rowPadding = rowStride - pixelStride * width
            val bufferSize = width * height * 4 // RGBA_8888: 4 bytes per pixel

            // 创建Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()

            // 复制像素数据
            if (rowPadding == 0) {
                // 没有padding，直接复制
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                // 有padding，需要逐行复制
                val pixels = IntArray(width * height)
                var offset = 0
                for (row in 0 until height) {
                    for (col in 0 until width) {
                        val r = buffer.get(offset++).toInt() and 0xFF
                        val g = buffer.get(offset++).toInt() and 0xFF
                        val b = buffer.get(offset++).toInt() and 0xFF
                        val a = buffer.get(offset++).toInt() and 0xFF

                        // 将RGBA转换为ARGB
                        pixels[row * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    // 跳过padding
                    offset += rowPadding
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            return bitmap
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to convert image to bitmap", e)
            return null
        }
    }

    /**
     * 释放ImageReader资源
     */
    fun disposeImageReader(imageReader: ImageReader) {
        try {
            imageReader.surface?.release()
            imageReader.close()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to dispose ImageReader", e)
        }
    }
}
