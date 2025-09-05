// 文件: SaveImageModule.kt
// 描述: 定义了将图片保存到设备存储的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.OutputStream

/**
 * “保存图片”模块。
 * 将一个 ImageVariable 保存到设备的公共图片目录中。
 */
class SaveImageModule : BaseModule() {

    override val id = "vflow.file.save_image"
    override val metadata = ActionMetadata(
        name = "保存图片",
        description = "将图片保存到相册。",
        iconRes = R.drawable.rounded_save_24,
        category = "文件" // 更新分类
    )

    // 此模块需要存储权限
    override val requiredPermissions = listOf(PermissionManager.STORAGE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "图片",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(ImageVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val image = step.parameters["image"]?.toString() ?: "..."
        val isVariable = image.startsWith("{{") && image.endsWith("}}")

        return PillUtil.buildSpannable(
            context,
            "保存图片 ",
            PillUtil.Pill(image, isVariable, parameterId = "image")
        )
    }

    /**
     * 执行模块逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.magicVariables["image"] as? ImageVariable
            ?: return ExecutionResult.Failure("参数错误", "输入的不是一个有效的图片变量。")

        val imageUri = Uri.parse(imageVar.uri)
        val appContext = context.applicationContext

        onProgress(ProgressUpdate("正在保存图片..."))

        try {
            val resolver = appContext.contentResolver
            val inputStream = resolver.openInputStream(imageUri)
                ?: return ExecutionResult.Failure("文件错误", "无法读取源图片文件。")

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "vFlow_Image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val newImageUri = resolver.insert(collection, contentValues)
                ?: return ExecutionResult.Failure("保存失败", "无法在相册中创建新图片条目。")

            val outputStream: OutputStream = resolver.openOutputStream(newImageUri)
                ?: return ExecutionResult.Failure("保存失败", "无法打开新图片文件的输出流。")

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(newImageUri, contentValues, null, null)
            }

            onProgress(ProgressUpdate("图片已保存"))
            return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))

        } catch (e: Exception) {
            return ExecutionResult.Failure("保存异常", e.localizedMessage ?: "发生了未知错误")
        }
    }
}