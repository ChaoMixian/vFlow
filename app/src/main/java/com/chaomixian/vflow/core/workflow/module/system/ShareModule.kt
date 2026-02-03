// 文件: ShareModule.kt
// 描述: 定义了调用系统分享功能的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
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
        nameStringRes = R.string.module_vflow_system_share_name,
        descriptionStringRes = R.string.module_vflow_system_share_desc,
        name = "分享",  // Fallback
        description = "调用系统分享功能来分享文本或图片。",  // Fallback
        iconRes = R.drawable.rounded_ios_share_24, // 使用新图标
        category = "应用与系统"
    )

    // 定义输入参数
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            nameStringRes = R.string.param_vflow_system_share_content_name,
            name = "分享内容",  // Fallback
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.IMAGE.id), // 接受文本和图片
            supportsRichText = true  // 支持混合变量
        )
    )

    /**
     * 增加 success 输出以保持统一。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )


    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_share_prefix), contentPill)
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

        // 获取内容变量
        val rawContent = context.getVariable("content")

        // 处理不同类型的内容
        val content: Any? = when (rawContent) {
            is VImage -> rawContent  // 图片变量直接使用，不解析
            is VNull -> null  // 空内容
            else -> {
                // 其他类型使用统一的变量访问方法获取字符串
                val strValue = context.getVariableAsString("content")
                if (strValue.isEmpty()) null else strValue
            }
        }

        if (content == null) {
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
        return ExecutionResult.Success(mapOf("success" to VBoolean(success ?: false)))
    }
}