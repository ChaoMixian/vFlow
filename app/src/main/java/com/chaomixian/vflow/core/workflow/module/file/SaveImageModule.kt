// 文件: SaveImageModule.kt
// 描述: 定义了将图片保存到设备存储的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

/**
 * “保存图片”模块。
 * 将一个 ImageVariable 保存到设备的公共图片目录中。
 */
class SaveImageModule : BaseModule() {

    override val id = "vflow.file.save_image"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_file_save_image_name,
        descriptionStringRes = R.string.module_vflow_file_save_image_desc,
        name = "保存图片",  // Fallback
        description = "将图片保存到相册",  // Fallback
        iconRes = R.drawable.rounded_save_24,
        category = "文件"
    )

    // 此模块需要存储权限
    override val requiredPermissions = listOf(PermissionManager.STORAGE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "图片",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id),
            nameStringRes = R.string.param_vflow_file_save_image_image_name
        )
    )

    /**
     * 增加 file_path 输出。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id
        ),
        OutputDefinition(
            id = "file_path",
            name = "文件路径",  // Fallback
            typeName = VTypeRegistry.STRING.id
        )
    )


    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val imagePill = PillUtil.createPillFromParam(
            step.parameters["image"],
            getInputs().find { it.id == "image" }
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_file_save_image),
            imagePill
        )
    }

    /**
     * 执行模块逻辑。
     * 保存成功后，同时输出布尔值和文件路径。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val imageVar = context.getVariable("image") as? VImage
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_file_save_image_param_error),
                appContext.getString(R.string.error_vflow_file_save_image_invalid)
            )

        val imageUri = Uri.parse(imageVar.uriString)
        val appContext = context.applicationContext

        onProgress(ProgressUpdate("正在保存图片..."))

        try {
            val resolver = appContext.contentResolver
            val inputStream = resolver.openInputStream(imageUri)
                ?: return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_file_save_image_file_error),
                    appContext.getString(R.string.error_vflow_file_save_image_read_failed)
                )

            val (mimeType, extension) = getMimeTypeAndExtension(imageUri.toString())
            val fileName = "vFlow_Image_${System.currentTimeMillis()}.${extension}"
            var newImageUri: Uri?
            var filePath: String?

            // 根据安卓版本使用不同的保存策略
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "vFlow")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                newImageUri = resolver.insert(collection, contentValues)
                if (newImageUri == null) {
                    return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_file_save_image_save_failed),
                        appContext.getString(R.string.error_vflow_file_save_image_create_failed)
                    )
                }

                resolver.openOutputStream(newImageUri).use { outputStream ->
                    if (outputStream == null) {
                        return ExecutionResult.Failure(
                            appContext.getString(R.string.error_vflow_file_save_image_save_failed),
                            appContext.getString(R.string.error_vflow_file_save_image_open_failed)
                        )
                    }
                    inputStream.use { input -> input.copyTo(outputStream) }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(newImageUri, contentValues, null, null)

                // 对于 Content URI，我们无法直接获得绝对路径，但可以尝试
                // 注意：这并非100%可靠，但对于大多数情况是有效的
                filePath = getPathFromUri(appContext, newImageUri)


            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val vflowDir = File(picturesDir, "vFlow")
                if (!vflowDir.exists()) vflowDir.mkdirs()

                val imageFile = File(vflowDir, fileName)
                FileOutputStream(imageFile).use { outputStream ->
                    inputStream.use { input -> input.copyTo(outputStream) }
                }

                // 通知媒体库扫描新文件
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                newImageUri = Uri.fromFile(imageFile)
                mediaScanIntent.data = newImageUri
                appContext.sendBroadcast(mediaScanIntent)

                filePath = imageFile.absolutePath
            }


            onProgress(ProgressUpdate("图片已保存至: $filePath"))
            return ExecutionResult.Success(
                mapOf(
                    "success" to VBoolean(true),
                    "file_path" to VString(filePath ?: newImageUri.toString()) // 如果路径获取失败，回退到URI
                )
            )

        } catch (e: Exception) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_file_save_image_save_exception),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_file_save_image_unknown)
            )
        }
    }


    private fun getMimeTypeAndExtension(uri: String): Pair<String, String> {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri)?.lowercase(Locale.getDefault()) ?: "jpeg"
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
        return mimeType to extension
    }

    private fun getCompressFormat(mimeType: String): Bitmap.CompressFormat {
        return when (mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    // 尝试从 Content URI 获取文件路径的辅助函数
    private fun getPathFromUri(context: Context, uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    path = cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            // 查询失败或列不存在
        }
        return path
    }

}