// 文件: RotateImageModule.kt
// 描述: 定义了旋转图像的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import java.util.*

class RotateImageModule : BaseModule() {

    override val id = "vflow.file.rotate_image"
    override val metadata = ActionMetadata(
        name = "旋转图片",
        description = "将图片按指定角度旋转。",
        iconRes = R.drawable.rounded_rotate_90_degrees_cw_24,
        category = "文件"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "源图像",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(ImageVariable.TYPE_NAME)
        ),
        InputDefinition(
            id = "degrees",
            name = "旋转角度",
            staticType = ParameterType.NUMBER,
            defaultValue = 90.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "旋转后的图像", ImageVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val imagePill = PillUtil.createPillFromParam(
            step.parameters["image"],
            inputs.find { it.id == "image" }
        )
        val degreesPill = PillUtil.createPillFromParam(
            step.parameters["degrees"],
            inputs.find { it.id == "degrees" }
        )

        return PillUtil.buildSpannable(
            context,
            "将图像 ",
            imagePill,
            " 旋转 ",
            degreesPill,
            " 度"
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.magicVariables["image"] as? ImageVariable
            ?: return ExecutionResult.Failure("参数错误", "需要一个图像变量作为输入。")

        val degreesValue = context.magicVariables["degrees"] ?: context.variables["degrees"]
        val degrees = when(degreesValue) {
            is NumberVariable -> degreesValue.value.toFloat()
            is Number -> degreesValue.toFloat()
            is String -> degreesValue.toFloatOrNull() ?: 90f
            else -> 90f
        }

        val appContext = context.applicationContext
        onProgress(ProgressUpdate("正在加载图像..."))

        return try {
            val request = ImageRequest.Builder(appContext)
                .data(Uri.parse(imageVar.uri))
                .build()
            val result = Coil.imageLoader(appContext).execute(request)
            val originalBitmap = result.drawable?.toBitmap()
                ?: return ExecutionResult.Failure("图像加载失败", "无法从 URI 加载位图: ${imageVar.uri}")

            onProgress(ProgressUpdate("正在旋转图像..."))

            val matrix = Matrix().apply { postRotate(degrees) }
            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

            onProgress(ProgressUpdate("正在保存处理后的图像..."))

            val outputFile = File(appContext.cacheDir, "rotated_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use {
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            originalBitmap.recycle()
            rotatedBitmap.recycle()

            val outputUri = Uri.fromFile(outputFile).toString()
            ExecutionResult.Success(mapOf("image" to ImageVariable(outputUri)))

        } catch (e: Exception) {
            ExecutionResult.Failure("图像处理异常", e.localizedMessage ?: "发生未知错误")
        }
    }
}