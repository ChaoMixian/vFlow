package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.util.Base64
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import java.io.File

/**
 * 截屏模块（Core）。
 * 使用 vFlow Core 捕获屏幕截图，基于scrcpy原理实现。
 * 截图会自动保存到工作流临时目录，并返回VImage对象。
 */
class CoreCaptureScreenModule : BaseModule() {

    override val id = "vflow.core.capture_screen"
    override val metadata = ActionMetadata(
        name = "截屏",  // Fallback
        nameStringRes = R.string.module_vflow_core_capture_screen_name,
        description = "使用 vFlow Core 捕获屏幕截图，基于scrcpy原理实现。",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_capture_screen_desc,
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "Core (Beta)"
    )

    private val outputFormatOptions = listOf("PNG", "JPEG")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("output_format", "输出格式", ParameterType.ENUM, "PNG", options = outputFormatOptions, acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_core_capture_screen_output_format_name),
        InputDefinition("quality", "JPEG质量", ParameterType.NUMBER, 90.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_quality_name),
        InputDefinition("max_width", "最大宽度", ParameterType.NUMBER, 0.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_max_width_name),
        InputDefinition("max_height", "最大高度", ParameterType.NUMBER, 0.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_max_height_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_capture_screen_success_name),
        OutputDefinition("image", "截图图片", VTypeRegistry.IMAGE.id, nameStringRes = R.string.output_vflow_core_capture_screen_image_name),
        OutputDefinition("width", "图像宽度", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_core_capture_screen_width_name),
        OutputDefinition("height", "图像高度", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_core_capture_screen_height_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val format = step.parameters["output_format"] as? String ?: "PNG"
        return context.getString(R.string.summary_vflow_core_capture_screen, format)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 确保 Core 连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已授予 Shizuku 或 Root 权限。"
            )
        }

        // 2. 获取参数
        val outputFormat = context.getVariableAsString("output_format", "PNG")
        // 现在 variables 是 Map<String, VObject>，使用 getVariableAsInt 获取
        val quality = context.getVariableAsInt("quality") ?: 90
        val maxWidth = context.getVariableAsInt("max_width") ?: 0
        val maxHeight = context.getVariableAsInt("max_height") ?: 0

        val format = outputFormat.lowercase()

        onProgress(ProgressUpdate("正在截屏..."))

        // 3. 执行截图并保存到工作流临时目录
        val result = withContext(Dispatchers.IO) {
            captureAndSaveToWorkDir(format, quality, maxWidth, maxHeight, context.workDir)
        }

        return when (result) {
            is CaptureResult.Success -> {
                onProgress(ProgressUpdate("截屏成功"))
                val imageUri = "file://${result.filePath}"
                val outputs = mapOf(
                    "success" to VBoolean(true),
                    "image" to VImage(imageUri),
                    "width" to VNumber(result.width.toDouble()),
                    "height" to VNumber(result.height.toDouble())
                )
                ExecutionResult.Success(outputs)
            }
            is CaptureResult.Error -> {
                ExecutionResult.Failure("截屏失败", result.message)
            }
        }
    }

    private fun captureAndSaveToWorkDir(
        format: String,
        quality: Int,
        maxWidth: Int,
        maxHeight: Int,
        workDir: File
    ): CaptureResult {
        return try {
            // 1. 从Core获取Base64数据
            val base64Data = VFlowCoreBridge.captureScreenEx(format, quality, maxWidth, maxHeight)
            if (base64Data.isEmpty()) {
                return CaptureResult.Error("未能获取屏幕截图数据")
            }

            // 2. 生成文件名并保存到工作流临时目录
            val fileName = "screenshot_${System.currentTimeMillis()}.$format"
            val file = File(workDir, fileName)

            // 3. 解码Base64并写入文件
            val imageBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            file.writeBytes(imageBytes)

            // 4. 获取图像尺寸
            val dimensions = VFlowCoreBridge.getScreenSize()

            CaptureResult.Success(
                width = dimensions.first,
                height = dimensions.second,
                filePath = file.absolutePath
            )
        } catch (e: Exception) {
            CaptureResult.Error("截图失败: ${e.message}")
        }
    }

    private sealed class CaptureResult {
        data class Success(val width: Int, val height: Int, val filePath: String) : CaptureResult()
        data class Error(val message: String) : CaptureResult()
    }
}
