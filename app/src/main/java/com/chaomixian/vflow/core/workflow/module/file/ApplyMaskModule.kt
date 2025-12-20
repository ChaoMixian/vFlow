// 文件: ApplyMaskModule.kt
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
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.min

class ApplyMaskModule : BaseModule() {

    override val id = "vflow.file.apply_mask"
    override val metadata = ActionMetadata(
        name = "应用蒙版",
        description = "为图片应用 Material You 风格圆角矩形蒙版。",
        iconRes = R.drawable.rounded_image_inset_24,
        category = "文件"
    )

    private val maskOptions = listOf("圆角矩形")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "源图像",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(ImageVariable.TYPE_NAME)
        ),
        InputDefinition(
            id = "mask_shape",
            name = "蒙版形状",
            staticType = ParameterType.ENUM,
            defaultValue = "圆角矩形",
            options = maskOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "处理后的图像", ImageVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val imagePill = PillUtil.createPillFromParam(
            step.parameters["image"],
            inputs.find { it.id == "image" }
        )
        val maskShapePill = PillUtil.createPillFromParam(
            step.parameters["mask_shape"],
            inputs.find { it.id == "mask_shape" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(
            context,
            "为图像 ",
            imagePill,
            " 应用 ",
            maskShapePill,
            " 蒙版"
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.magicVariables["image"] as? ImageVariable
            ?: return ExecutionResult.Failure("参数错误", "需要一个图像变量作为输入。")

        val appContext = context.applicationContext
        onProgress(ProgressUpdate("正在加载图像..."))

        return try {
            val request = ImageRequest.Builder(appContext)
                .data(Uri.parse(imageVar.uri))
                .allowHardware(false) // [修复] 禁止硬件位图
                .build()
            val result = Coil.imageLoader(appContext).execute(request)
            val originalBitmap = result.drawable?.toBitmap()
                ?: return ExecutionResult.Failure("图像加载失败", "无法从 URI 加载位图: ${imageVar.uri}")

            val softwareBitmap = toSoftwareBitmap(originalBitmap)

            onProgress(ProgressUpdate("正在应用圆角蒙版..."))

            val maskedBitmap = createRoundedBitmap(softwareBitmap)

            onProgress(ProgressUpdate("正在保存处理后的图像..."))
            // 使用 workDir
            val outputFile = File(context.workDir, "masked_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use {
                maskedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // 安全释放（softwareBitmap 如果等于 originalBitmap 则不要回收）
            if (softwareBitmap != originalBitmap) {
                softwareBitmap.recycle()
            }
            maskedBitmap.recycle()

            val outputUri = Uri.fromFile(outputFile).toString()
            ExecutionResult.Success(mapOf("image" to ImageVariable(outputUri)))

        } catch (e: Exception) {
            ExecutionResult.Failure("图像处理异常", e.localizedMessage ?: "发生未知错误")
        }
    }

    private fun toSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else bitmap
    }

    private fun createRoundedBitmap(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cornerRadius = min(width, height) * 0.15f

        val shapeModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
            .build()
        val drawable = MaterialShapeDrawable(shapeModel)
        drawable.setBounds(0, 0, width, height)
        drawable.setTint(Color.WHITE)
        drawable.draw(canvas)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return output
    }
}