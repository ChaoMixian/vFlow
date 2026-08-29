// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/TakePhotoModule.kt
// 描述: 拍照模块，使用Android Camera2 API通过前置或后置摄像头在后台拍照并保存为图片文件
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.module.InputDefinition.Companion.slider
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VFile
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 拍照模块
 * 使用Android Camera2 API通过前置或后置摄像头拍照并保存为图片文件
 */
class TakePhotoModule : BaseModule() {

    override val id = "vflow.device.take_photo"

    override val metadata = ActionMetadata(
        name = "拍照",
        nameStringRes = R.string.module_vflow_device_take_photo_name,
        description = "使用前置或后置摄像头拍照并保存为图片文件。",
        descriptionStringRes = R.string.module_vflow_device_take_photo_desc,
        iconRes = R.drawable.rounded_photo_camera_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Take a photo using the front or back camera and save it to a file.",
        workflowStepDescription = "Take a photo using the front or back camera.",
        inputHints = mapOf(
            "camera" to "Camera lens facing: 'back' for rear camera or 'front' for front-facing camera.",
            "flash" to "Flash mode: 'off', 'auto', 'on', or 'torch'.",
            "quality" to "JPEG quality between 10 and 100 (default: 95).",
            "save_path" to "Optional output file path. If empty, saves to workflow temporary directory."
        ),
        requiredInputIds = setOf("camera")
    )

    companion object {
        const val CAMERA_BACK = "back"
        const val CAMERA_FRONT = "front"

        const val FLASH_OFF = "off"
        const val FLASH_AUTO = "auto"
        const val FLASH_ON = "on"
        const val FLASH_TORCH = "torch"

        private val CAMERA_OPTIONS = listOf(CAMERA_BACK, CAMERA_FRONT)
        private val FLASH_OPTIONS = listOf(FLASH_OFF, FLASH_AUTO, FLASH_ON, FLASH_TORCH)

        private val CAMERA_OPTION_RES = listOf(
            R.string.option_vflow_device_take_photo_camera_back,
            R.string.option_vflow_device_take_photo_camera_front
        )

        private val FLASH_OPTION_RES = listOf(
            R.string.option_vflow_device_take_photo_flash_off,
            R.string.option_vflow_device_take_photo_flash_auto,
            R.string.option_vflow_device_take_photo_flash_on,
            R.string.option_vflow_device_take_photo_flash_torch
        )

        private val CAMERA_LEGACY_MAP = mapOf(
            "后置" to CAMERA_BACK,
            "后置摄像头" to CAMERA_BACK,
            "前置" to CAMERA_FRONT,
            "前置摄像头" to CAMERA_FRONT
        )

        private val FLASH_LEGACY_MAP = mapOf(
            "关闭" to FLASH_OFF,
            "自动" to FLASH_AUTO,
            "开启" to FLASH_ON,
            "常亮" to FLASH_TORCH
        )
    }

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CAMERA)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "camera",
            name = "摄像头",
            staticType = ParameterType.ENUM,
            defaultValue = CAMERA_BACK,
            options = CAMERA_OPTIONS,
            optionsStringRes = CAMERA_OPTION_RES,
            legacyValueMap = CAMERA_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_take_photo_camera_name
        ),
        InputDefinition(
            id = "flash",
            name = "闪光灯",
            staticType = ParameterType.ENUM,
            defaultValue = FLASH_OFF,
            options = FLASH_OPTIONS,
            optionsStringRes = FLASH_OPTION_RES,
            legacyValueMap = FLASH_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_take_photo_flash_name
        ),
        InputDefinition(
            id = "quality",
            name = "图片质量 (10-100)",
            staticType = ParameterType.NUMBER,
            defaultValue = 95,
            sliderConfig = slider(10f, 100f, 1f),
            inputStyle = InputStyle.SLIDER,
            isFolded = true,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_take_photo_quality_name
        ),
        InputDefinition(
            id = "save_path",
            name = "保存路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.FILE.id),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_take_photo_save_path_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_take_photo_success_name
        ),
        OutputDefinition(
            id = "image",
            name = "照片图像",
            typeName = VTypeRegistry.IMAGE.id,
            nameStringRes = R.string.output_vflow_device_take_photo_image_name
        ),
        OutputDefinition(
            id = "file",
            name = "照片文件",
            typeName = VTypeRegistry.FILE.id,
            nameStringRes = R.string.output_vflow_device_take_photo_file_name
        ),
        OutputDefinition(
            id = "path",
            name = "文件路径",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_device_take_photo_path_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val camera = step.parameters["camera"] as? String ?: CAMERA_BACK
        val cameraLabel = if (camera == CAMERA_FRONT) {
            context.getString(R.string.option_vflow_device_take_photo_camera_front)
        } else {
            context.getString(R.string.option_vflow_device_take_photo_camera_back)
        }
        return context.getString(R.string.summary_vflow_device_take_photo, cameraLabel)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val inputs = getInputs()
        val cameraFacing = inputs.normalizeEnumValue("camera", context.getVariableAsString("camera", CAMERA_BACK)) ?: CAMERA_BACK
        val flashMode = inputs.normalizeEnumValue("flash", context.getVariableAsString("flash", FLASH_OFF)) ?: FLASH_OFF
        val qualityRaw = context.getVariableAsNumber("quality") ?: 95.0
        val quality = qualityRaw.toInt().coerceIn(10, 100)
        val customSavePathRaw = VariableResolver.resolve(context.getVariableAsString("save_path", ""), context).trim()

        val targetFile = if (customSavePathRaw.isNotEmpty()) {
            val file = File(customSavePathRaw)
            if (customSavePathRaw.endsWith("/") || file.isDirectory) {
                file.mkdirs()
                File(file, "photo_${System.currentTimeMillis()}.jpg")
            } else {
                file.parentFile?.mkdirs()
                file
            }
        } else {
            val workDir = context.workDir
            workDir.mkdirs()
            File(workDir, "photo_${System.currentTimeMillis()}.jpg")
        }

        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@withContext ExecutionResult.Failure("获取相机服务失败", "系统未能提供 CameraManager 服务。")

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_take_photo_opening)))

        val targetLensFacing = if (cameraFacing == CAMERA_FRONT) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        val cameraId = findCameraId(cameraManager, targetLensFacing)
            ?: return@withContext ExecutionResult.Failure(
                "未找到指定摄像头",
                "设备上未找到 ${if (cameraFacing == CAMERA_FRONT) "前置" else "后置"} 摄像头。"
            )

        val captureResult = withTimeoutOrNull(15000L) {
            performHeadlessCapture(
                cameraManager = cameraManager,
                cameraId = cameraId,
                flashMode = flashMode,
                quality = quality,
                targetFile = targetFile,
                onProgress = onProgress,
                context = appContext
            )
        }

        if (captureResult == null) {
            return@withContext ExecutionResult.Failure("拍照超时", "拍照操作在 15 秒内未完成。")
        }

        if (captureResult.isFailure) {
            val error = captureResult.exceptionOrNull()
            return@withContext ExecutionResult.Failure("拍照失败", error?.localizedMessage ?: "未知相机错误")
        }

        if (!targetFile.exists() || targetFile.length() == 0L) {
            return@withContext ExecutionResult.Failure("拍照失败", "生成的图片文件为空或不存在。")
        }

        if (customSavePathRaw.isNotEmpty() && !customSavePathRaw.contains("/.") && !customSavePathRaw.contains("/Android/data/")) {
            try {
                android.media.MediaScannerConnection.scanFile(appContext, arrayOf(targetFile.absolutePath), arrayOf("image/jpeg"), null)
            } catch (_: Exception) {}
        }

        val fileUri = Uri.fromFile(targetFile)
        val imageObj = VImage(fileUri.toString())
        val fileObj = VFile(fileUri.toString(), "image/jpeg")

        ExecutionResult.Success(
            mapOf(
                "success" to VBoolean(true),
                "image" to imageObj,
                "file" to fileObj,
                "path" to VString(targetFile.absolutePath)
            )
        )
    }

    private fun findCameraId(cameraManager: CameraManager, desiredLensFacing: Int): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == desiredLensFacing) {
                    return id
                }
            }
            return cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun performHeadlessCapture(
        cameraManager: CameraManager,
        cameraId: String,
        flashMode: String,
        quality: Int,
        targetFile: File,
        onProgress: suspend (ProgressUpdate) -> Unit,
        context: Context
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val handlerThread = HandlerThread("CameraBackgroundThread_${System.currentTimeMillis()}").apply { start() }
        val handler = Handler(handlerThread.looper)

        val isResumed = AtomicBoolean(false)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var imageReader: ImageReader? = null

        fun cleanup() {
            try { captureSession?.close() } catch (_: Exception) {}
            try { cameraDevice?.close() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { handlerThread.quitSafely() } catch (_: Exception) {}
        }

        fun safeResumeWith(result: Result<File>) {
            if (isResumed.compareAndSet(false, true)) {
                cleanup()
                continuation.resume(result)
            }
        }

        continuation.invokeOnCancellation {
            cleanup()
        }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes: Array<Size> = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            val selectedSize = jpegSizes.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)
            val reader = ImageReader.newInstance(selectedSize.width, selectedSize.height, ImageFormat.JPEG, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                val image = try {
                    ir.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }

                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        FileOutputStream(targetFile).use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        image.close()
                        safeResumeWith(Result.success(targetFile))
                    } catch (e: Exception) {
                        image.close()
                        safeResumeWith(Result.failure(e))
                    }
                }
            }, handler)

            val cameraCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val sessionCallback = object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(reader.surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                                        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                        if (hasFlash) {
                                            when (flashMode) {
                                                FLASH_ON -> {
                                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                                                }
                                                FLASH_AUTO -> {
                                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                                                }
                                                FLASH_TORCH -> {
                                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                                }
                                                else -> {
                                                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                                                }
                                            }
                                        }

                                        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                                        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                                        val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                                        val deviceRotation = display?.rotation ?: android.view.Surface.ROTATION_0
                                        val rotationDegrees = when (deviceRotation) {
                                            android.view.Surface.ROTATION_0 -> 0
                                            android.view.Surface.ROTATION_90 -> 90
                                            android.view.Surface.ROTATION_180 -> 180
                                            android.view.Surface.ROTATION_270 -> 270
                                            else -> 0
                                        }
                                        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                                        val jpegOrientation = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                            (sensorOrientation + rotationDegrees) % 360
                                        } else {
                                            (sensorOrientation - rotationDegrees + 360) % 360
                                        }
                                        set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                                        set(CaptureRequest.JPEG_QUALITY, quality.toByte())
                                    }

                                    session.capture(captureBuilder.build(), null, handler)
                                } catch (e: Exception) {
                                    safeResumeWith(Result.failure(e))
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                safeResumeWith(Result.failure(Exception("相机捕获会话配置失败")))
                            }
                        }

                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(listOf(reader.surface), sessionCallback, handler)
                    } catch (e: Exception) {
                        safeResumeWith(Result.failure(e))
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    safeResumeWith(Result.failure(Exception("相机已断开连接")))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    val errorMsg = when (error) {
                        ERROR_CAMERA_IN_USE -> "相机正在被其他应用使用"
                        ERROR_MAX_CAMERAS_IN_USE -> "已达到相机最大并发使用限制"
                        ERROR_CAMERA_DISABLED -> "相机已被系统禁用"
                        ERROR_CAMERA_DEVICE -> "相机设备发生致命错误"
                        ERROR_CAMERA_SERVICE -> "相机系统服务发生致命错误"
                        else -> "相机错误代码: $error"
                    }
                    safeResumeWith(Result.failure(Exception(errorMsg)))
                }
            }

            @Suppress("MissingPermission")
            cameraManager.openCamera(cameraId, cameraCallback, handler)
        } catch (e: Exception) {
            safeResumeWith(Result.failure(e))
        }
    }
}
