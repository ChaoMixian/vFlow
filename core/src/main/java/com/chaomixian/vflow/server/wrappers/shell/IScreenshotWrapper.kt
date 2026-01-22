// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IScreenshotWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Base64
import com.chaomixian.vflow.server.common.utils.DisplayCaptureUtils
import com.chaomixian.vflow.server.common.utils.ImageReaderHelper
import com.chaomixian.vflow.server.wrappers.IWrapper
import org.json.JSONObject
import android.media.ImageReader
import android.view.Surface

/**
 * 截图服务Wrapper
 * 基于scrcpy的原理实现屏幕捕获
 *
 * 支持的方法：
 * - captureScreen: 截取屏幕并返回Base64编码的图像数据
 * - captureScreenToFile: 截取屏幕并保存到文件
 */
class IScreenshotWrapper : IWrapper {

    companion object {
        private const val TAG = "IScreenshotWrapper"

        // 等待图像渲染的超时时间（毫秒）
        private const val IMAGE_WAIT_TIMEOUT = 3000L

        // 默认截图格式和质量
        private const val DEFAULT_FORMAT = "png"
        private const val DEFAULT_JPEG_QUALITY = 90
    }

    /**
     * 处理截图请求
     */
    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        when (method) {
            "captureScreen" -> {
                // 参数解析
                val displayId = params.optInt("displayId", 0)
                val format = params.optString("format", DEFAULT_FORMAT)
                val quality = params.optInt("quality", DEFAULT_JPEG_QUALITY)
                val maxWidth = params.optInt("maxWidth", 0)
                val maxHeight = params.optInt("maxHeight", 0)
                val includeBase64 = params.optBoolean("includeBase64", true)

                // 执行截图
                val captureResult = captureScreen(
                    displayId = displayId,
                    format = format,
                    quality = quality,
                    maxWidth = if (maxWidth > 0) maxWidth else null,
                    maxHeight = if (maxHeight > 0) maxHeight else null
                )

                if (captureResult != null) {
                    result.put("success", true)
                    result.put("width", captureResult.width)
                    result.put("height", captureResult.height)
                    result.put("format", captureResult.format)

                    // 根据请求决定是否包含Base64数据
                    if (includeBase64) {
                        result.put("data", captureResult.base64Data)
                        result.put("size", captureResult.data.size)
                    } else {
                        result.put("size", captureResult.data.size)
                        // 不包含Base64数据，只返回元数据
                    }
                } else {
                    result.put("success", false)
                    result.put("error", "Failed to capture screen")
                }
            }

            "captureScreenToFile" -> {
                val displayId = params.optInt("displayId", 0)
                val filePath = params.getString("filePath")
                val format = params.optString("format", DEFAULT_FORMAT).substringBefore(".") // 去掉可能的点号
                val quality = params.optInt("quality", DEFAULT_JPEG_QUALITY)

                val success = captureScreenToFile(
                    displayId = displayId,
                    filePath = filePath,
                    format = format,
                    quality = quality
                )

                result.put("success", success)
                if (!success) {
                    result.put("error", "Failed to capture screen to file")
                }
            }

            "getScreenSize" -> {
                val displayId = params.optInt("displayId", 0)
                val displayInfo = DisplayCaptureUtils.getDisplayInfo(displayId)

                if (displayInfo != null) {
                    result.put("success", true)
                    result.put("width", displayInfo.width)
                    result.put("height", displayInfo.height)
                    result.put("rotation", displayInfo.rotation)
                    result.put("displayId", displayInfo.displayId)
                } else {
                    result.put("success", false)
                    result.put("error", "Failed to get screen size")
                }
            }

            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }

        return result
    }

    /**
     * 截取屏幕
     * @param displayId 显示器ID
     * @param format 输出格式 ("png" 或 "jpeg")
     * @param quality JPEG质量（1-100）
     * @param maxWidth 最大宽度（null表示不限制）
     * @param maxHeight 最大高度（null表示不限制）
     * @return ScreenshotResult 或 null（失败时）
     */
    private fun captureScreen(
        displayId: Int,
        format: String,
        quality: Int,
        maxWidth: Int?,
        maxHeight: Int?
    ): ScreenshotResult? {
        var imageReader: ImageReader? = null
        var surface: Surface? = null
        var virtualDisplay: Any? = null
        var displayToken: IBinder? = null
        var useSurfaceControl = false

        try {
            // 1. 获取显示信息
            val displayInfo = DisplayCaptureUtils.getDisplayInfo(displayId)
                ?: return null

            var screenWidth = displayInfo.width
            var screenHeight = displayInfo.height

            // 2. 计算输出尺寸（如果限制了最大尺寸）
            val outputWidth = maxWidth ?: screenWidth
            val outputHeight = maxHeight ?: screenHeight

            // 3. 创建ImageReader和Surface
            val readerAndSurface = ImageReaderHelper.createImageReaderAndSurface(outputWidth, outputHeight)
                ?: return null

            imageReader = readerAndSurface.first
            surface = readerAndSurface.second

            // 4. 创建虚拟显示器
            // 优先使用DisplayManager API，失败则使用SurfaceControl API
            virtualDisplay = DisplayCaptureUtils.createVirtualDisplay(
                name = "vflow_screenshot",
                width = outputWidth,
                height = outputHeight,
                displayId = displayId,
                surface = surface
            )

            if (virtualDisplay == null) {
                // DisplayManager失败，尝试SurfaceControl
                displayToken = DisplayCaptureUtils.createDisplay("vflow_screenshot", false)
                if (displayToken != null) {
                    val deviceRect = Rect(0, 0, screenWidth, screenHeight)
                    val displayRect = Rect(0, 0, outputWidth, outputHeight)

                    DisplayCaptureUtils.setDisplaySurface(displayToken, surface)
                    DisplayCaptureUtils.setDisplayProjection(
                        displayToken,
                        0, // orientation
                        deviceRect,
                        displayRect
                    )
                    DisplayCaptureUtils.setDisplayLayerStack(displayToken, displayInfo.layerStack)
                    useSurfaceControl = true
                } else {
                    return null
                }
            }

            // 5. 等待图像渲染
            Thread.sleep(200) // 等待Surface准备就绪

            // 6. 获取图像数据
            val imageData = ImageReaderHelper.acquireLatestImage(imageReader!!, format, quality)
                ?: return null

            return ScreenshotResult(
                data = imageData,
                base64Data = Base64.encodeToString(imageData, Base64.NO_WRAP),
                width = outputWidth,
                height = outputHeight,
                format = format
            )

        } catch (e: Exception) {
            com.chaomixian.vflow.server.common.Logger.error(TAG, "Failed to capture screen", e)
            return null
        } finally {
            // 7. 清理资源
            try {
                if (virtualDisplay != null) {
                    // VirtualDisplay.release()
                    val releaseMethod = virtualDisplay.javaClass.getMethod("release")
                    releaseMethod.invoke(virtualDisplay)
                }
            } catch (e: Exception) {
                com.chaomixian.vflow.server.common.Logger.error(TAG, "Failed to release virtual display", e)
            }

            if (useSurfaceControl && displayToken != null) {
                DisplayCaptureUtils.destroyDisplay(displayToken)
            }

            if (imageReader != null) {
                ImageReaderHelper.disposeImageReader(imageReader)
            }
        }
    }

    /**
     * 截取屏幕并保存到文件
     */
    private fun captureScreenToFile(
        displayId: Int,
        filePath: String,
        format: String,
        quality: Int
    ): Boolean {
        val result = captureScreen(displayId, format, quality, null, null)
            ?: return false

        return try {
            java.io.FileOutputStream(filePath).use { fos ->
                fos.write(result.data)
                fos.flush()
            }
            true
        } catch (e: Exception) {
            com.chaomixian.vflow.server.common.Logger.error(TAG, "Failed to save screenshot to file", e)
            false
        }
    }

    /**
     * 截图结果数据类
     */
    private data class ScreenshotResult(
        val data: ByteArray,
        val base64Data: String,
        val width: Int,
        val height: Int,
        val format: String
    )
}
