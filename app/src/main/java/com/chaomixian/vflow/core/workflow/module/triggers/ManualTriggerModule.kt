package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.module.BooleanVariable

// 文件：ManualTriggerModule.kt
// 描述：定义手动触发器模块，用于通过用户点击启动工作流。

/**
 * "手动触发" 模块。
 * 作为工作流的起点，允许用户通过界面操作（如点击按钮）来启动工作流。
 */
class ManualTriggerModule : BaseModule() {
    override val id = "vflow.trigger.manual" // 模块唯一ID
    override val metadata = ActionMetadata(
        name = "手动触发",
        description = "通过点击按钮手动启动此工作流",
        iconRes = R.drawable.rounded_play_arrow_24, // 图标
        category = "触发器" // 分类
    )

    /** 输出参数：触发是否成功 (始终为 true)。 */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    /** 模块摘要：直接使用元数据中的名称。 */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return metadata.name
    }

    /** 执行逻辑：报告被手动触发，并返回成功。 */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("工作流被手动触发"))
        return ExecutionResult.Success(outputs = mapOf("success" to BooleanVariable(true)))
    }
}