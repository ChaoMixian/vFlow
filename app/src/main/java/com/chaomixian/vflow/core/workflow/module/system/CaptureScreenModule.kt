// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/CaptureScreenModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ShizukuManager
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
    override val metadata = ActionMetadata(
        name = "截屏",
        description = "捕获当前屏幕内容。",
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "界面交互"
    )

    private val modeOptions = listOf("自动", "screencap") // 暂时移除MediaProjection入口

    // 动态权限声明
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        // 如果 step 为 null (如在模块管理器列表中)，返回所有可能的权限
        if (step == null) {
            return listOf(PermissionManager.SHIZUKU, PermissionManager.ROOT)
        }

        // 读取全局 Shell 设置 (Root 还是 Shizuku)
        // 使用 LogManager 获取全局 Context
        val context = LogManager.applicationContext
        val prefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val defaultShellMode = prefs.getString("default_shell_mode", "shizuku")
        return when (defaultShellMode) {
            "root" -> listOf(PermissionManager.ROOT)
            "shizuku" -> listOf(PermissionManager.SHIZUKU)
            else -> listOf(PermissionManager.SHIZUKU)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = modeOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "截图", ImageVariable.TYPE_NAME)
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
        val appContext = context.applicationContext

        val prefs = appContext.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        // 默认为 shizuku，因为它是较安全且通用的选择
        val defaultShellMode = prefs.getString("default_shell_mode", "shizuku")

        onProgress(ProgressUpdate("准备截屏 (模式: $mode)..."))

        val imageUri: Uri? = when (mode) {
            "自动" -> performAutomaticCapture(context, defaultShellMode, onProgress)
            "screencap" -> performShellCapture(appContext, defaultShellMode, onProgress)
            else -> return ExecutionResult.Failure("参数错误", "无效的截屏模式")
        }

        if (imageUri != null) {
            onProgress(ProgressUpdate("截屏成功"))
            return ExecutionResult.Success(mapOf("image" to ImageVariable(imageUri.toString())))
        } else {
            return ExecutionResult.Failure("截屏失败", "无法获取屏幕图像，请检查权限或重试")
        }
    }

    /**
     * 自动模式下的执行逻辑
     */
    private suspend fun performAutomaticCapture(
        context: ExecutionContext,
        defaultShellMode: String?,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Uri? {
        val appContext = context.applicationContext

        // 1. 如果设置为 Root，优先尝试 Root
        if (defaultShellMode == "root") {
            onProgress(ProgressUpdate("自动模式：优先尝试 Root 截图..."))
            val uri = captureWithShell(appContext, useRoot = true)
            if (uri != null) return uri
            DebugLogger.w("CaptureScreenModule", "Root 截图失败，尝试回落。")
        }

        // 2. 如果设置为 Shizuku 或 Root 失败且 Shizuku 激活，尝试 Shizuku
        // 注意：如果首选是 Shizuku，这里会是第一步
        if (ShizukuManager.isShizukuActive(appContext)) {
            onProgress(ProgressUpdate("自动模式：尝试 Shizuku 截图..."))
            val uri = captureWithShell(appContext, useRoot = false)
            if (uri != null) return uri
            DebugLogger.w("CaptureScreenModule", "Shizuku 截图失败，尝试回落。")
        }

        // 3. 如果首选是 Shizuku 且失败了，尝试 Root (前提是首选不是 Root，因为那样在第1步已经试过了)
        if (defaultShellMode == "shizuku") {
            // 这里是一个隐式尝试，如果用户没 Root 权限这步会很快失败
            val uri = captureWithShell(appContext, useRoot = true)
            if (uri != null) return uri
        }

        // 4. 最终回落：MediaProjection
        onProgress(ProgressUpdate("自动模式：回落到 MediaProjection..."))
        return captureWithMediaProjection(context, onProgress)
    }

    /**
     * 强制 Shell 模式下的逻辑
     */
    private suspend fun performShellCapture(
        context: Context,
        defaultShellMode: String?,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Uri? {
        // 遵循用户偏好，如果偏好是 Root 则只用 Root，反之亦然
        val useRoot = defaultShellMode == "root"
        onProgress(ProgressUpdate("正在使用 ${if (useRoot) "Root" else "Shizuku"} 执行 screencap..."))
        return captureWithShell(context, useRoot)
    }

    private suspend fun captureWithShell(context: Context, useRoot: Boolean): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val fileName = "screenshot_$timestamp.png"
        val cacheFile = File(context.cacheDir, fileName)
        val path = cacheFile.absolutePath

        val command = "screencap -p \"$path\""

        return withContext(Dispatchers.IO) {
            try {
                if (useRoot) {
                    val process = Runtime.getRuntime().exec("su")
                    val os = java.io.DataOutputStream(process.outputStream)
                    os.writeBytes("$command\n")
                    os.writeBytes("exit\n")
                    os.flush()
                    process.waitFor()
                } else {
                    ShizukuManager.execShellCommand(context, command)
                }

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    Uri.fromFile(cacheFile)
                } else {
                    DebugLogger.w("CaptureScreenModule", "Shell 截图文件未生成或为空。")
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
                        val file = File(appContext.cacheDir, "screenshot_mp_$timestamp.png")
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