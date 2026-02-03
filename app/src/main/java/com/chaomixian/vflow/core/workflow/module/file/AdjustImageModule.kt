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
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.*

/**
 * “调整图像”模块。
 * 对输入的图像进行一系列参数化的视觉调整。
 */
class AdjustImageModule : BaseModule() {

    override val id = "vflow.file.adjust_image"
    override val metadata = ActionMetadata(
        name = "调整图像",  // Fallback
        nameStringRes = R.string.module_vflow_file_adjust_image_name,
        description = "调整图像的曝光、对比度、饱和度等参数。",  // Fallback
        descriptionStringRes = R.string.module_vflow_file_adjust_image_desc,
        iconRes = R.drawable.rounded_photo_prints_24,
        category = "文件"
    )

    override val uiProvider: ModuleUIProvider? = AdjustImageModuleUIProvider()

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

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(id = INPUT_IMAGE, name = "源图像", staticType = ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id), nameStringRes = R.string.param_vflow_file_adjust_image_image_name),
        InputDefinition(id = P_EXPOSURE, name = "曝光", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_exposure_name),
        InputDefinition(id = P_CONTRAST, name = "对比度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_contrast_name),
        InputDefinition(id = P_BRIGHTNESS, name = "亮度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_brightness_name),
        InputDefinition(id = P_SATURATION, name = "饱和度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_saturation_name),
        InputDefinition(id = P_VIBRANCE, name = "鲜明度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_vibrance_name),
        InputDefinition(id = P_HIGHLIGHTS, name = "高光", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_highlights_name),
        InputDefinition(id = P_SHADOWS, name = "阴影", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_shadows_name),
        InputDefinition(id = P_BLACK_POINT, name = "黑点", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_blackPoint_name),
        InputDefinition(id = P_WARMTH, name = "色温", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_warmth_name),
        InputDefinition(id = P_TINT, name = "色调", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_tint_name),
        InputDefinition(id = P_VIGNETTE, name = "暗角", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_vignette_name),
        InputDefinition(id = P_SHARPNESS, name = "锐化", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_sharpness_name),
        InputDefinition(id = P_CLARITY, name = "清晰度", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_clarity_name),
        InputDefinition(id = P_DENOISE, name = "噪点消除", staticType = ParameterType.NUMBER, defaultValue = 0.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_file_adjust_image_denoise_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "处理后的图像", VTypeRegistry.IMAGE.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val imagePill = PillUtil.createPillFromParam(step.parameters[INPUT_IMAGE], getInputs().find { it.id == INPUT_IMAGE })
        val adjustments = getInputs().asSequence().drop(1)
            .mapNotNull {
                val paramValue = step.parameters[it.id]
                if (paramValue is Number && paramValue.toDouble() != 0.0) "${it.name}: ${paramValue}" else null
            }
            .joinToString(", ")
        val suffix = if (adjustments.isNotEmpty()) context.getString(R.string.summary_vflow_file_adjust_image_with, adjustments) else context.getString(R.string.summary_vflow_file_adjust_image_none)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_file_adjust_image_prefix), imagePill, suffix)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.getVariable(INPUT_IMAGE) as? VImage
            ?: return ExecutionResult.Failure("参数错误", "需要一个图像变量作为输入。")

        val appContext = context.applicationContext
        val params = getInputs().associate {
            // 现在 variables 是 Map<String, VObject>，使用 getVariableAsNumber 获取
            it.id to (context.getVariableAsNumber(it.id) ?: 0.0).toFloat()
        }

        onProgress(ProgressUpdate("正在加载图像..."))
        try {
            // 设置 allowHardware(false) 以确保获取软件位图，避免 copy 异常
            val request = ImageRequest.Builder(appContext)
                .data(Uri.parse(imageVar.uriString))
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(appContext).execute(request)
            val originalBitmap = result.drawable?.toBitmap()
                ?: return ExecutionResult.Failure("图像加载失败", "无法从URI加载位图: ${imageVar.uriString}")

            var mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

            onProgress(ProgressUpdate("正在应用图像效果..."))

            // 链式应用所有效果
            val width = mutableBitmap.width
            val height = mutableBitmap.height
            val pixels = IntArray(width * height)
            mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            applyColorAdjustments(pixels, width, height, params)

            mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            // 应用基于卷积的效果
            if (params[P_SHARPNESS] ?: 0f > 0f) {
                mutableBitmap = applyConvolution(mutableBitmap, createSharpenKernel(params[P_SHARPNESS] ?: 0f))
            }
            if (params[P_CLARITY] ?: 0f > 0f) {
                mutableBitmap = applyClarity(mutableBitmap, params[P_CLARITY] ?: 0f)
            }
            if (params[P_DENOISE] ?: 0f > 0f) {
                mutableBitmap = applyDenoise(mutableBitmap, (params[P_DENOISE] ?: 0f).toInt())
            }
            if (params[P_VIGNETTE] ?: 0f != 0f) {
                mutableBitmap = applyVignette(mutableBitmap, params[P_VIGNETTE] ?: 0f)
            }

            onProgress(ProgressUpdate("正在保存处理后的图像..."))
            // 使用 context.workDir 存储结果
            val outputFile = File(context.workDir, "adjusted_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use {
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            mutableBitmap.recycle()

            val outputUri = Uri.fromFile(outputFile).toString()
            return ExecutionResult.Success(mapOf("image" to VImage(outputUri)))

        } catch (e: Exception) {
            return ExecutionResult.Failure("图像处理异常", e.localizedMessage ?: "发生未知错误")
        }
    }

    // 应用所有基于像素颜色调整的效果
    private fun applyColorAdjustments(pixels: IntArray, width: Int, height: Int, params: Map<String, Float>) {
        val hsv = FloatArray(3)
        // 预计算参数，将 -100..100 或 0..100 的范围转换为算法需要的因子
        val contrast = (params[P_CONTRAST]!! / 100.0 + 1.0).pow(2.0).toFloat()
        val brightness = (params[P_BRIGHTNESS]!! * 2.55f)
        val saturation = params[P_SATURATION]!! / 100f
        val vibrance = params[P_VIBRANCE]!! / 100f
        val exposure = 2.0.pow(params[P_EXPOSURE]!! / 100.0).toFloat()
        val highlights = params[P_HIGHLIGHTS]!! / 100f
        val shadows = params[P_SHADOWS]!! / 100f
        val blackPoint = params[P_BLACK_POINT]!! / 100f
        val warmth = params[P_WARMTH]!! / 100f
        val tint = params[P_TINT]!! / 100f

        for (i in pixels.indices) {
            var color = pixels[i]
            var r = Color.red(color)
            var g = Color.green(color)
            var b = Color.blue(color)

            // 曝光
            r = (r * exposure).toInt().coerceIn(0, 255)
            g = (g * exposure).toInt().coerceIn(0, 255)
            b = (b * exposure).toInt().coerceIn(0, 255)

            // 色温
            if (warmth != 0f) {
                r = (r + warmth * 50).toInt().coerceIn(0, 255)
                g = (g + warmth * 25).toInt().coerceIn(0, 255)
                b = (b - warmth * 50).toInt().coerceIn(0, 255)
            }
            // 色调
            if (tint != 0f) {
                g = (g + tint * 50).toInt().coerceIn(0, 255)
            }

            // 亮度 & 黑点
            r = (r + brightness).toInt().coerceIn(0, 255)
            g = (g + brightness).toInt().coerceIn(0, 255)
            b = (b + brightness).toInt().coerceIn(0, 255)
            if (blackPoint > 0) {
                r = (r * (1f - blackPoint)).toInt().coerceIn(0, 255)
                g = (g * (1f - blackPoint)).toInt().coerceIn(0, 255)
                b = (b * (1f - blackPoint)).toInt().coerceIn(0, 255)
            }

            // 对比度
            r = (((r / 255.0 - 0.5) * contrast + 0.5) * 255).toInt().coerceIn(0, 255)
            g = (((g / 255.0 - 0.5) * contrast + 0.5) * 255).toInt().coerceIn(0, 255)
            b = (((b / 255.0 - 0.5) * contrast + 0.5) * 255).toInt().coerceIn(0, 255)

            // 转换为HSV以调整色彩
            Color.RGBToHSV(r, g, b, hsv)
            var hue = hsv[0]
            var sat = hsv[1]
            var `val` = hsv[2]

            // 高光和阴影 (基于明度 V)
            if (highlights > 0) `val` += (`val` * highlights)
            else `val` += ((1f - `val`) * highlights) // highlights < 0
            if (shadows > 0) `val` += ((1f - `val`) * shadows)
            else `val` -= (`val` * shadows.absoluteValue) // shadows < 0

            // 鲜明度 (Vibrance)
            if (vibrance != 0f) {
                val amount = (1 - sat).pow(2) * vibrance
                sat += amount
            }

            // 饱和度
            sat += saturation

            hsv[1] = sat.coerceIn(0f, 1f)
            hsv[2] = `val`.coerceIn(0f, 1f)

            pixels[i] = Color.HSVToColor(hsv)
        }
    }

    // 应用卷积效果
    private fun applyConvolution(src: Bitmap, kernel: FloatArray): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val newPixels = IntArray(pixels.size)
        val kernelSize = sqrt(kernel.size.toDouble()).toInt()
        val kernelOffset = kernelSize / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (ky in 0 until kernelSize) {
                    for (kx in 0 until kernelSize) {
                        val pixelX = (x + kx - kernelOffset).coerceIn(0, width - 1)
                        val pixelY = (y + ky - kernelOffset).coerceIn(0, height - 1)
                        val pixel = pixels[pixelY * width + pixelX]
                        val kernelValue = kernel[ky * kernelSize + kx]
                        r += Color.red(pixel) * kernelValue
                        g += Color.green(pixel) * kernelValue
                        b += Color.blue(pixel) * kernelValue
                    }
                }
                newPixels[y * width + x] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
        }

        val newBitmap = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        newBitmap.setPixels(newPixels, 0, width, 0, 0, width, height)
        return newBitmap
    }

    // 创建锐化核
    private fun createSharpenKernel(amount: Float): FloatArray {
        val strength = amount / 100f * 4f // 将 0-100 映射到 0-4
        return floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f + strength, -1f,
            0f, -1f, 0f
        )
    }

    // 应用清晰度效果 (Unsharp Masking)
    private fun applyClarity(src: Bitmap, amount: Float): Bitmap {
        val blurred = applyGaussianBlur(src, 5) // 创建一个轻微模糊的版本
        val strength = amount / 100f

        val width = src.width
        val height = src.height
        val originalPixels = IntArray(width * height)
        val blurredPixels = IntArray(width * height)
        src.getPixels(originalPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurredPixels, 0, width, 0, 0, width, height)

        for (i in originalPixels.indices) {
            val originalColor = originalPixels[i]
            val blurredColor = blurredPixels[i]

            val r = Color.red(originalColor) + (Color.red(originalColor) - Color.red(blurredColor)) * strength
            val g = Color.green(originalColor) + (Color.green(originalColor) - Color.green(blurredColor)) * strength
            val b = Color.blue(originalColor) + (Color.blue(originalColor) - Color.blue(blurredColor)) * strength

            originalPixels[i] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }

        blurred.recycle()
        val newBitmap = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        newBitmap.setPixels(originalPixels, 0, width, 0, 0, width, height)
        return newBitmap
    }

    // 简单的中值滤波用于降噪
    private fun applyDenoise(src: Bitmap, strength: Int): Bitmap {
        if (strength <= 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val radius = 1 // 固定半径为1，strength控制迭代次数
        var currentBitmap = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        for(i in 0 until strength.coerceAtMost(5)) { // 最多迭代5次
            val nextBitmap = applyMedianFilter(currentBitmap)
            if (currentBitmap != src) currentBitmap.recycle()
            currentBitmap = nextBitmap
        }
        return currentBitmap
    }

    private fun applyMedianFilter(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val newPixels = IntArray(pixels.size)

        val r = IntArray(9)
        val g = IntArray(9)
        val b = IntArray(9)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var i = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        r[i] = Color.red(pixel)
                        g[i] = Color.green(pixel)
                        b[i] = Color.blue(pixel)
                        i++
                    }
                }
                r.sort()
                g.sort()
                b.sort()
                newPixels[y * width + x] = Color.rgb(r[4], g[4], b[4])
            }
        }
        val newBitmap = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        newBitmap.setPixels(newPixels, 0, width, 0, 0, width, height)
        return newBitmap
    }


    // 应用暗角效果
    private fun applyVignette(src: Bitmap, amount: Float): Bitmap {
        val strength = amount / 100f
        val width = src.width.toFloat()
        val height = src.height.toFloat()

        val radialGradient = RadialGradient(
            width / 2, height / 2, max(width, height) / 1.5f,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        val paint = Paint()
        paint.shader = radialGradient
        paint.alpha = (abs(strength) * 255).toInt()
        paint.xfermode = if (strength < 0) PorterDuffXfermode(PorterDuff.Mode.DARKEN) else PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)

        val canvas = Canvas(src)
        canvas.drawRect(0f, 0f, width, height, paint)

        return src
    }

    // 高斯模糊辅助函数 (用于清晰度)
    private fun applyGaussianBlur(src: Bitmap, radius: Int): Bitmap {
        // A simple and fast box blur approximation of Gaussian blur
        val blurred = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val w = blurred.width
        val h = blurred.height
        val pixels = IntArray(w * h)
        blurred.getPixels(pixels, 0, w, 0, 0, w, h)

        for (r in 0 until radius) {
            for (i in 0 until w * h) {
                val x = i % w
                val y = i / w
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val pixel = pixels[ny * w + nx]
                        rSum += Color.red(pixel)
                        gSum += Color.green(pixel)
                        bSum += Color.blue(pixel)
                        count++
                    }
                }
                pixels[i] = Color.rgb(rSum / count, gSum / count, bSum / count)
            }
        }
        blurred.setPixels(pixels, 0, w, 0, 0, w, h)
        return blurred
    }
}