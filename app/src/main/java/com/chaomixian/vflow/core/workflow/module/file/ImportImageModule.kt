// 文件: ImportImageModule.kt
// 描述: 定义了从设备存储中导入图片的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService

/**
 * “导入图片”模块。
 * 唤起系统图片选择器，让用户选择一张图片，并将其作为 ImageVariable 输出。
 */
class ImportImageModule : BaseModule() {

    override val id = "vflow.file.import_image"
    override val metadata = ActionMetadata(
        name = "导入图片",
        description = "从相册或文件中选择一张图片。",
        iconRes = R.drawable.rounded_add_photo_alternate_24,
        category = "文件" // 更新分类
    )

    // 此模块需要存储权限来读取图片
    override val requiredPermissions = listOf(PermissionManager.STORAGE)

    // 此模块没有输入
    override fun getInputs(): List<InputDefinition> = emptyList()

    // 输出一个图像变量
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("image", "选择的图片", ImageVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return "选择一张图片"
    }

    /**
     * 执行模块逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure("服务缺失", "无法获取UI服务来选择图片。")

        onProgress(ProgressUpdate("等待用户选择图片..."))

        // 调用UI服务请求图片，并等待结果
        val imageUri = uiService.requestImage()
            ?: return ExecutionResult.Failure("用户取消", "用户取消了图片选择。")

        val resultVariable = ImageVariable(imageUri)

        onProgress(ProgressUpdate("获取到图片"))
        return ExecutionResult.Success(mapOf("image" to resultVariable))
    }
}