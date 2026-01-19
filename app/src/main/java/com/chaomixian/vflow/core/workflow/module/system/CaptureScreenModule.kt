// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/CaptureScreenModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CaptureScreenModule : BaseModule() {

    override val id = "vflow.system.capture_screen"
    override val uiProvider = CaptureScreenModuleUIProvider()
    override val metadata = ActionMetadata(
        name = "截屏",
        description = "捕获当前屏幕内容。",
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "界面交互"
    )

    private val modeOptions = listOf("自动", "screencap") // 暂时移除MediaProjection入口

    // 动态权限声明
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = modeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "region",
            name = "区域 (可选)",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "截图", VTypeRegistry.IMAGE.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val modePill = PillUtil.createPillFromParam(
            step.parameters["mode"],
            getInputs().find { it.id == "mode" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, "使用 ", modePill, " 模式截屏")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val mode = context.variables["mode"] as? String ?: "自动"
        val regionStr = context.variables["region"] as? String ?: ""
        val appContext = context.applicationContext

        onProgress(ProgressUpdate("准备截屏 (模式: $mode)..."))

        val imageUri: Uri? = when (mode) {
            "自动" -> performAutomaticCapture(context, onProgress)
            "screencap" -> performShellCapture(appContext, context.workDir, onProgress)
            else -> return ExecutionResult.Failure("参数错误", "无效的截屏模式")
        }

        if (imageUri != null) {
            // 如果指定了区域，裁剪图片
            val finalUri = if (regionStr.isNotEmpty()) {
                val region = parseRegion(regionStr)
                if (region != null) {
                    onProgress(ProgressUpdate("裁剪区域: $regionStr"))
                    cropImageRegion(appContext, context.workDir, imageUri, region)
                } else {
                    imageUri
                }
            } else {
                imageUri
            }

            if (finalUri != null) {
                onProgress(ProgressUpdate("截屏成功"))
                return ExecutionResult.Success(mapOf("image" to VImage(finalUri.toString())))
            } else {
                return ExecutionResult.Failure("裁剪失败", "无法裁剪图片")
            }
        } else {
            return ExecutionResult.Failure("截屏失败", "无法获取屏幕图像，请检查权限或重试")
        }
    }

    /**
     * 解析区域字符串
     * 格式: "left,top,right,bottom" 或 "left,top,width,height" (百分比或像素值)
     */
    private fun parseRegion(regionStr: String): Rect? {
        return try {
            val parts = regionStr.split(",")
            if (parts.size == 4) {
                val values = parts.map { it.trim().toFloat() }
                // 暂时只支持像素值：left,top,right,bottom
                Rect(
                    values[0].toInt(),
                    values[1].toInt(),
                    values[2].toInt(),
                    values[3].toInt()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            DebugLogger.e("CaptureScreenModule", "解析区域失败: $regionStr", e)
            null
        }
    }

    /**
     * 裁剪图片的指定区域
     */
    private suspend fun cropImageRegion(
        context: Context,
        workDir: File,
        imageUri: Uri,
        region: Rect
    ): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // 从 URI 加载图片
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    DebugLogger.e("CaptureScreenModule", "无法加载图片: $imageUri")
                    return@withContext null
                }

                // 确保区域在图片范围内
                val safeRegion = Rect(
                    region.left.coerceIn(0, bitmap.width),
                    region.top.coerceIn(0, bitmap.height),
                    region.right.coerceIn(0, bitmap.width),
                    region.bottom.coerceIn(0, bitmap.height)
                )

                if (safeRegion.width() <= 0 || safeRegion.height() <= 0) {
                    DebugLogger.e("CaptureScreenModule", "无效的区域: $safeRegion")
                    bitmap.recycle()
                    return@withContext null
                }

                // 裁剪图片
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    safeRegion.left,
                    safeRegion.top,
                    safeRegion.width(),
                    safeRegion.height()
                )

                // 保存裁剪后的图片
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                val outputFile = File(workDir, "screenshot_cropped_$timestamp.png")
                FileOutputStream(outputFile).use { fos ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                bitmap.recycle()
                if (cropped != bitmap) {
                    cropped.recycle()
                }

                Uri.fromFile(outputFile)
            } catch (e: Exception) {
                DebugLogger.e("CaptureScreenModule", "裁剪图片失败", e)
                null
            }
        }
    }

    private suspend fun performAutomaticCapture(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Uri? {
        val appContext = context.applicationContext

        // 1. 尝试 Shell 截图 (自动模式)
        onProgress(ProgressUpdate("自动模式：尝试 Shell 截图..."))
        val uri = performShellCapture(appContext, context.workDir, onProgress)
        if (uri != null) return uri

        DebugLogger.w("CaptureScreenModule", "Shell 截图失败，回落到 MediaProjection。")

        // 2. 最终回落：MediaProjection
        onProgress(ProgressUpdate("自动模式：回落到 MediaProjection..."))
        return captureWithMediaProjection(context, onProgress)
    }

    private suspend fun performShellCapture(
        context: Context,
        workDir: File,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val fileName = "screenshot_$timestamp.png"
        val cacheFile = File(workDir, fileName)
        val path = cacheFile.absolutePath

        val command = "screencap -p \"$path\""

        return withContext(Dispatchers.IO) {
            try {
                // 使用 ShellManager 自动选择最佳方式 (Root/Shizuku)
                val result = ShellManager.execShellCommand(context, command, ShellManager.ShellMode.AUTO)

                // 检查文件是否存在且大小正常 (忽略 ShellManager 的文本返回值，只看文件结果)
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    Uri.fromFile(cacheFile)
                } else {
                    DebugLogger.w("CaptureScreenModule", "Shell 截图未生成文件: $result")
                    null
                }
            } catch (e: Exception) {
                DebugLogger.e("CaptureScreenModule", "Shell 截图异常", e)
                null
            }
        }
    }

    private suspend fun captureWithMediaProjection(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): Uri? {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: throw IllegalStateException("UI Service not available")

        // 1. 请求权限
        onProgress(ProgressUpdate("正在请求截屏权限..."))
        val resultData = uiService.requestMediaProjectionPermission()
            ?: return null

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val deferred = CompletableDeferred<Uri?>()

        val handlerThread = HandlerThread("ScreenCapture")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)

        try {
            val projectionManager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // 此时 TriggerService 应该是以前台服务运行的，且类型为 mediaProjection
            val mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, resultData)

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "vFlow-ScreenCapture",
                width, height, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, handler
            )

            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val finalBitmap = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)

                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                        val file = File(context.workDir, "screenshot_mp_$timestamp.png")
                        val fos = FileOutputStream(file)
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        fos.close()

                        if (bitmap != finalBitmap) bitmap.recycle()
                        finalBitmap.recycle()

                        virtualDisplay?.release()
                        mediaProjection?.stop()
                        handlerThread.quitSafely()

                        deferred.complete(Uri.fromFile(file))
                    }
                } catch (e: Exception) {
                    DebugLogger.e("CaptureScreenModule", "ImageReader 处理异常", e)
                    deferred.complete(null)
                    virtualDisplay?.release()
                    mediaProjection?.stop()
                    handlerThread.quitSafely()
                }
            }, handler)

            onProgress(ProgressUpdate("正在捕获屏幕..."))
            return deferred.await()

        } catch (e: SecurityException) {
            DebugLogger.e("CaptureScreenModule", "MediaProjection 安全异常: 请检查前台服务权限。", e)
            return null
        } catch (e: Exception) {
            DebugLogger.e("CaptureScreenModule", "MediaProjection 未知异常", e)
            return null
        }
    }
}