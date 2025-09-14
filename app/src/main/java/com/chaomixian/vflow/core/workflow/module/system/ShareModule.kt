// 文件: ShareModule.kt
// 描述: 定义了调用系统分享功能的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “分享”模块。
 * 调用安卓系统的分享功能，可以分享文本或图片。
 */
class ShareModule : BaseModule() {

    override val id = "vflow.system.share"
    override val metadata = ActionMetadata(
        name = "分享",
        description = "调用系统分享功能来分享文本或图片。",
        iconRes = R.drawable.rounded_ios_share_24, // 使用新图标
        category = "应用与系统"
    )

    // 定义输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            name = "分享内容",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME, ImageVariable.TYPE_NAME) // 接受文本和图片
        )
    )

    /**
     * 增加 success 输出以保持统一。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )


    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, "分享 ", contentPill)
    }

    /**
     * 执行分享操作。
     * 现在会输出一个布尔值表示操作是否成功启动。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取UI服务
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure("服务缺失", "无法获取UI服务来执行分享操作。")

        // 获取要分享的内容
        val content = context.magicVariables["content"] ?: context.variables["content"]

        if (content == null || (content is String && content.isEmpty())) {
            return ExecutionResult.Failure("内容为空", "没有可分享的内容。")
        }

        onProgress(ProgressUpdate("正在准备分享..."))

        // 调用UI服务发起分享请求，并等待其完成
        val success = uiService.requestShare(content)

        if (success == true) {
            onProgress(ProgressUpdate("分享窗口已弹出"))
        } else {
            onProgress(ProgressUpdate("分享操作未能成功启动或被取消"))
        }

        // 无论成功与否，都通过 success 输出返回结果
        return ExecutionResult.Success(mapOf("success" to BooleanVariable(success ?: false)))
    }
}