// 文件: AdjustImageModule.kt
// 描述: 定义了对图像进行各种视觉效果调整的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * “调整图像”模块。
 * 对输入的图像进行一系列参数化的视觉调整。
 */
class AdjustImageModule : BaseModule() {

    override val id = "vflow.file.adjust_image"
    override val metadata = ActionMetadata(
        name = "调整图像",
        description = "调整图像的曝光、对比度、饱和度等参数。",
        iconRes = R.drawable.rounded_photo_prints_24, // 使用新创建的图标
        category = "文件与图像"
    )

    // 定义所有可调整参数的常量
    companion object {
        const val INPUT_IMAGE = "image"
        const val P_EXPOSURE = "exposure"
        const val P_VIBRANCE = "vibrance"
        const val P_HIGHLIGHTS = "highlights"
        const val P_SHADOWS = "shadows"
        const val P_CONTRAST = "contrast"
        const val P_BRIGHTNESS = "brightness"
        const val P_BLACK_POINT = "blackPoint"
        const val P_SATURATION = "saturation"
        const val P_WARMTH = "warmth"
        const val P_TINT = "tint"
        const val P_SHARPNESS = "sharpness"
        const val P_CLARITY = "clarity"
        const val P_DENOISE = "denoise"
        const val P_VIGNETTE = "vignette"
    }

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = INPUT_IMAGE, name = "源图像", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(ImageVariable.TYPE_NAME)),
        // 参数范围: -100 到 100
        InputDefinition(id = P_EXPOSURE, name = "曝光", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_VIBRANCE, name = "鲜明度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_HIGHLIGHTS, name = "高光", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_SHADOWS, name = "阴影", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_CONTRAST, name = "对比度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_BRIGHTNESS, name = "亮度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_BLACK_POINT, name = "黑点", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_SATURATION, name = "饱和度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_WARMTH, name = "色温", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_TINT, name = "色调", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_VIGNETTE, name = "暗角", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        // 参数范围: 0 到 100
        InputDefinition(id = P_SHARPNESS, name = "锐化", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_CLARITY, name = "清晰度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),
        InputDefinition(id = P_DENOISE, name = "噪点消除", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME))
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "处理后的图像", ImageVariable.TYPE_NAME)
    )

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val image = step.parameters[INPUT_IMAGE]?.toString() ?: "..."
        val isVariable = image.startsWith("{{") && image.endsWith("}}")

        val adjustments = getInputs().asSequence().drop(1) // 跳过源图像输入
            .map { it.id to (step.parameters[it.id] as? Number ?: 0.0) }
            .filter { it.second.toDouble() != 0.0 }
            .joinToString(", ") { (id, value) ->
                val name = getInputs().find { it.id == id }?.name ?: id
                "$name: $value"
            }

        return PillUtil.buildSpannable(context,
            "调整图像 ",
            PillUtil.Pill(image, isVariable, parameterId = INPUT_IMAGE),
            if (adjustments.isNotEmpty()) " 使用: $adjustments" else " (无调整)"
        )
    }

    /**
     * 执行图像处理。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.magicVariables[INPUT_IMAGE] as? ImageVariable
            ?: return ExecutionResult.Failure("参数错误", "需要一个图像变量作为输入。")

        val appContext = context.applicationContext
        // 修正：使用 associate 正确创建 Map
        val params = getInputs().associate {
            it.id to ((context.magicVariables[it.id] as? NumberVariable)?.value?.toFloat()
                ?: (context.variables[it.id] as? Number)?.toFloat() ?: 0f)
        }

        onProgress(ProgressUpdate("正在加载图像..."))

        try {
            // 1. 加载图像
            val request = ImageRequest.Builder(appContext)
                .data(Uri.parse(imageVar.uri))
                .build()
            val result = Coil.imageLoader(appContext).execute(request)
            val originalBitmap = result.drawable?.toBitmap()
                ?: return ExecutionResult.Failure("图像加载失败", "无法从URI加载位图: ${imageVar.uri}")

            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            originalBitmap.recycle()

            onProgress(ProgressUpdate("正在应用图像效果..."))

            // 2. 应用效果
            val paint = Paint().apply {
                colorFilter = createColorMatrix(params)
            }
            val canvas = Canvas(mutableBitmap)
            canvas.drawBitmap(mutableBitmap, 0f, 0f, paint)

            // TODO: 高级效果实现
            // 锐化, 清晰度, 降噪, 暗角等效果使用ColorMatrix难以实现。
            // 需要更复杂的技术，如RenderScript (已弃用), OpenGL ES, 或第三方库 (如OpenCV)。
            // 此处暂时跳过这些效果的实现。
            // 修正：安全的比较方式
            if (params[P_SHARPNESS] ?: 0f > 0f) onProgress(ProgressUpdate("警告: 锐化效果暂未实现。"))
            if (params[P_CLARITY] ?: 0f > 0f) onProgress(ProgressUpdate("警告: 清晰度效果暂未实现。"))
            if (params[P_DENOISE] ?: 0f > 0f) onProgress(ProgressUpdate("警告: 噪点消除效果暂未实现。"))
            if (params[P_VIGNETTE] ?: 0f != 0f) onProgress(ProgressUpdate("警告: 暗角效果暂未实现。"))


            onProgress(ProgressUpdate("正在保存处理后的图像..."))

            // 3. 保存处理后的图像到临时文件
            val outputFile = File(appContext.cacheDir, "adjusted_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use {
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            mutableBitmap.recycle()

            val outputUri = Uri.fromFile(outputFile).toString()
            return ExecutionResult.Success(mapOf("image" to ImageVariable(outputUri)))

        } catch (e: Exception) {
            return ExecutionResult.Failure("图像处理异常", e.localizedMessage ?: "发生未知错误")
        }
    }

    /**
     * 根据参数创建 ColorMatrix。
     */
    private fun createColorMatrix(params: Map<String, Float>): ColorMatrixColorFilter {
        // 确保参数存在，否则使用默认值0f
        val saturationValue = params[P_SATURATION] ?: 0f
        val contrastValue = params[P_CONTRAST] ?: 0f
        val brightnessValue = params[P_BRIGHTNESS] ?: 0f
        val exposureValue = params[P_EXPOSURE] ?: 0f
        val warmthValue = params[P_WARMTH] ?: 0f
        val tintValue = params[P_TINT] ?: 0f

        val colorMatrix = ColorMatrix()
        val saturation = 1f + (saturationValue / 100f)
        val contrast = 1f + (contrastValue / 100f)
        val brightness = brightnessValue * 2.55f // 范围: -255 to 255

        val scaleExposure = Math.pow(2.0, exposureValue.toDouble() / 100.0).toFloat()

        // 饱和度
        colorMatrix.setSaturation(saturation)

        // 亮度 & 对比度
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        // 曝光
        val exposureMatrix = ColorMatrix(floatArrayOf(
            scaleExposure, 0f, 0f, 0f, 0f,
            0f, scaleExposure, 0f, 0f, 0f,
            0f, 0f, scaleExposure, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        // 色温和色调
        val warmthMatrix = ColorMatrix().apply {
            if (warmthValue != 0f) {
                // 增加或减少红色和绿色通道
                val amount = warmthValue / 100f
                postConcat(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, if (amount > 0) amount * 50 else 0f,
                    0f, 1f, 0f, 0f, if (amount > 0) amount * 25 else 0f,
                    0f, 0f, 1f, 0f, if (amount < 0) -amount * 50 else 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            if (tintValue != 0f) {
                // 增加或减少绿色通道
                val amount = tintValue / 100f
                postConcat(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, amount * 50,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }

        // 组合所有矩阵
        val finalMatrix = ColorMatrix()
        finalMatrix.postConcat(colorMatrix)
        finalMatrix.postConcat(brightnessMatrix)
        finalMatrix.postConcat(exposureMatrix)
        finalMatrix.postConcat(warmthMatrix)

        // TODO: 其他效果 (高光, 阴影, 黑点, 鲜明度) 的实现比较复杂，
        // 通常需要像素级别的操作，而不只是ColorMatrix。
        // 此处暂不实现。

        return ColorMatrixColorFilter(finalMatrix)
    }
}