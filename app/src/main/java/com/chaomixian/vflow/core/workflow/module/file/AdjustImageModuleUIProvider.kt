// 文件: AdjustImageModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.slider.Slider
import kotlinx.coroutines.*

class AdjustImageEditorViewHolder(
    view: View,
    val sourceImageSelector: LinearLayout,
    val sourceImagePillContainer: FrameLayout,
    val previewImage: ImageView,
    val sliders: Map<String, Slider>,
    var originalBitmap: Bitmap? = null,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : CustomEditorViewHolder(view) {
    private var updateJob: Job? = null

    fun updatePreviewImage(context: Context, module: AdjustImageModule) {
        val original = originalBitmap ?: return

        // 使用防抖来避免过于频繁的重绘
        updateJob?.cancel()
        updateJob = uiScope.launch {
            delay(50) // 50ms 防抖
            val params = readAllSliderValues()

            // 在IO线程执行耗时的图像处理
            val adjustedBitmap = withContext(Dispatchers.IO) {
                val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val paint = Paint().apply {
                    colorFilter = module.createColorMatrixForPreview(params)
                }
                canvas.drawBitmap(mutableBitmap, 0f, 0f, paint)
                mutableBitmap
            }
            previewImage.setImageBitmap(adjustedBitmap)
        }
    }

    private fun readAllSliderValues(): Map<String, Float> {
        return sliders.mapValues { it.value.value }
    }
}

class AdjustImageModuleUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> {
        // 接管所有参数，除了源图像本身，它的值通过魔法变量选择器来设置
        return setOf(
            AdjustImageModule.P_EXPOSURE, AdjustImageModule.P_VIBRANCE, AdjustImageModule.P_HIGHLIGHTS,
            AdjustImageModule.P_SHADOWS, AdjustImageModule.P_CONTRAST, AdjustImageModule.P_BRIGHTNESS,
            AdjustImageModule.P_BLACK_POINT, AdjustImageModule.P_SATURATION, AdjustImageModule.P_WARMTH,
            AdjustImageModule.P_TINT, AdjustImageModule.P_SHARPNESS, AdjustImageModule.P_CLARITY,
            AdjustImageModule.P_DENOISE, AdjustImageModule.P_VIGNETTE
        )
    }

    override fun createPreview(context: Context, parent: ViewGroup, step: ActionStep, onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?): View? {
        return null
    }

    override fun createEditor(
        context: Context, parent: ViewGroup, currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit, onMagicVariableRequested: ((inputId: String) -> Unit)?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.partial_adjust_image_editor, parent, false)

        val sliders = mapOf(
            AdjustImageModule.P_EXPOSURE to view.findViewById<Slider>(R.id.slider_exposure),
            AdjustImageModule.P_VIBRANCE to view.findViewById<Slider>(R.id.slider_vibrance),
            AdjustImageModule.P_HIGHLIGHTS to view.findViewById<Slider>(R.id.slider_highlights),
            AdjustImageModule.P_SHADOWS to view.findViewById<Slider>(R.id.slider_shadows),
            AdjustImageModule.P_CONTRAST to view.findViewById<Slider>(R.id.slider_contrast),
            AdjustImageModule.P_BRIGHTNESS to view.findViewById<Slider>(R.id.slider_brightness),
            AdjustImageModule.P_BLACK_POINT to view.findViewById<Slider>(R.id.slider_black_point),
            AdjustImageModule.P_SATURATION to view.findViewById<Slider>(R.id.slider_saturation),
            AdjustImageModule.P_WARMTH to view.findViewById<Slider>(R.id.slider_warmth),
            AdjustImageModule.P_TINT to view.findViewById<Slider>(R.id.slider_tint),
            AdjustImageModule.P_VIGNETTE to view.findViewById<Slider>(R.id.slider_vignette),
            AdjustImageModule.P_SHARPNESS to view.findViewById<Slider>(R.id.slider_sharpness),
            AdjustImageModule.P_CLARITY to view.findViewById<Slider>(R.id.slider_clarity),
            AdjustImageModule.P_DENOISE to view.findViewById<Slider>(R.id.slider_denoise)
        )

        val holder = AdjustImageEditorViewHolder(
            view,
            view.findViewById(R.id.source_image_selector),
            view.findViewById(R.id.source_image_pill_container),
            view.findViewById(R.id.image_preview),
            sliders
        )

        // 设置源图像选择器的点击事件
        holder.sourceImageSelector.setOnClickListener {
            onMagicVariableRequested?.invoke(AdjustImageModule.INPUT_IMAGE)
        }

        // 更新源图像Pill
        val imageVar = currentParameters[AdjustImageModule.INPUT_IMAGE] as? String
        if (imageVar != null && imageVar.isMagicVariable()) {
            val pill = inflater.inflate(R.layout.magic_variable_pill, holder.sourceImagePillContainer, false)
            pill.findViewById<TextView>(R.id.pill_text).text = "已连接变量"
            holder.sourceImagePillContainer.addView(pill)
        }

        // 异步加载预览图
        CoroutineScope(Dispatchers.IO).launch {
            // [修复] 定义一个明确的预览图尺寸
            val previewWidth = 400
            val previewHeight = 400

            val loadedBitmap = if (imageVar != null && imageVar.isMagicVariable()) {
                // 如果是魔法变量，加载内置样片
                ContextCompat.getDrawable(context, R.drawable.sample_image_for_preview)
                    ?.toBitmap(width = previewWidth, height = previewHeight) // [修复] 提供明确的宽高
            } else {
                // 如果有真实图片URI，则加载它
                try {
                    val request = ImageRequest.Builder(context).data(Uri.parse(imageVar)).build()
                    context.imageLoader.execute(request).drawable?.toBitmap()
                } catch (e: Exception) {
                    // URI无效或加载失败，同样回退到样片
                    ContextCompat.getDrawable(context, R.drawable.sample_image_for_preview)
                        ?.toBitmap(width = previewWidth, height = previewHeight) // [修复] 提供明确的宽高
                }
            }

            withContext(Dispatchers.Main) {
                holder.originalBitmap = loadedBitmap
                if (loadedBitmap != null) {
                    val module = ModuleRegistry.getModule("vflow.file.adjust_image") as AdjustImageModule
                    holder.updatePreviewImage(context, module)
                } else {
                    // 如果最终图片还是空的，显示一个错误图标
                    holder.previewImage.setImageResource(R.drawable.rounded_broken_image_24)
                }
            }
        }

        // 初始化所有Slider的值
        sliders.forEach { (key, slider) ->
            slider.value = (currentParameters[key] as? Number ?: 0.0).toFloat()
        }

        // 为所有Slider添加一个统一的监听器
        val listener = Slider.OnChangeListener { _, _, _ ->
            val module = ModuleRegistry.getModule("vflow.file.adjust_image") as AdjustImageModule
            holder.updatePreviewImage(context, module)
        }
        sliders.values.forEach { it.addOnChangeListener(listener) }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as AdjustImageEditorViewHolder
        return h.sliders.mapValues { it.value.value.toDouble() }
    }
}

/**
 * [重要] 在 AdjustImageModule 中添加一个公共方法，用于UI提供者调用。
 * 这样可以确保预览效果和最终执行效果完全一致。
 */
fun AdjustImageModule.createColorMatrixForPreview(params: Map<String, Float>): ColorMatrixColorFilter {
    // 这里的实现应该与 AdjustImageModule 内部的 private fun createColorMatrix 完全相同
    // 为了简洁，直接在这里复制一份，并设为 public
    val saturationValue = params[AdjustImageModule.P_SATURATION] ?: 0f
    val contrastValue = params[AdjustImageModule.P_CONTRAST] ?: 0f
    val brightnessValue = params[AdjustImageModule.P_BRIGHTNESS] ?: 0f
    val exposureValue = params[AdjustImageModule.P_EXPOSURE] ?: 0f
    val warmthValue = params[AdjustImageModule.P_WARMTH] ?: 0f
    val tintValue = params[AdjustImageModule.P_TINT] ?: 0f

    val colorMatrix = ColorMatrix()
    val saturation = 1f + (saturationValue / 100f)
    val contrast = 1f + (contrastValue / 100f)
    val brightness = brightnessValue * 2.55f

    val scaleExposure = Math.pow(2.0, exposureValue.toDouble() / 100.0).toFloat()

    colorMatrix.setSaturation(saturation)

    val brightnessMatrix = ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    ))

    val exposureMatrix = ColorMatrix(floatArrayOf(
        scaleExposure, 0f, 0f, 0f, 0f,
        0f, scaleExposure, 0f, 0f, 0f,
        0f, 0f, scaleExposure, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    val warmthMatrix = ColorMatrix().apply {
        if (warmthValue != 0f) {
            val amount = warmthValue / 100f
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, if (amount > 0) amount * 50 else 0f,
                0f, 1f, 0f, 0f, if (amount > 0) amount * 25 else 0f,
                0f, 0f, 1f, 0f, if (amount < 0) -amount * 50 else 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        if (tintValue != 0f) {
            val amount = tintValue / 100f
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, amount * 50,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
    }

    val finalMatrix = ColorMatrix()
    finalMatrix.postConcat(colorMatrix)
    finalMatrix.postConcat(brightnessMatrix)
    finalMatrix.postConcat(exposureMatrix)
    finalMatrix.postConcat(warmthMatrix)

    return ColorMatrixColorFilter(finalMatrix)
}