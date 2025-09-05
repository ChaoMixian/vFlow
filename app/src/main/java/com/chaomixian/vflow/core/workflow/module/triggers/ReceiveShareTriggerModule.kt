package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “接收分享” 触发器。
 * 当用户通过 Android 的分享菜单将内容分享给 vFlow 时，会触发此模块。
 */
class ReceiveShareTriggerModule : BaseModule() {

    override val id = "vflow.trigger.share"
    override val metadata = ActionMetadata(
        name = "接收分享",
        description = "当有内容（如文本、图片、文件）分享到vFlow时启动工作流。",
        iconRes = R.drawable.rounded_inbox_text_share_24, // 使用新创建的图标
        category = "触发器"
    )

    // 定义此触发器接受的内容类型
    private val acceptedTypes = listOf("任意", "文本", "链接", "图片", "文件")

    /**
     * 定义输入参数：只保留接收类型，入口由系统自动分配
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "acceptedType",
            name = "接收内容类型",
            staticType = ParameterType.ENUM,
            defaultValue = "任意",
            options = acceptedTypes,
            acceptsMagicVariable = false
        )
    )

    /**
     * 根据接收类型，动态定义输出参数
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val type = step?.parameters?.get("acceptedType") as? String ?: "任意"
        return when (type) {
            "文本", "链接" -> listOf(OutputDefinition("shared_content", "分享的文本", TextVariable.TYPE_NAME))
            "图片" -> listOf(OutputDefinition("shared_content", "分享的图片", ImageVariable.TYPE_NAME))
            // "文件" 和 "任意" 类型暂时都输出为文本（URI），未来可以扩展为更具体的类型
            else -> listOf(OutputDefinition("shared_content", "分享的内容", TextVariable.TYPE_NAME))
        }
    }

    /**
     * 生成模块摘要
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val type = step.parameters["acceptedType"]?.toString() ?: "任意"
        return PillUtil.buildSpannable(context,
            "当分享 ",
            PillUtil.Pill(type, false, "acceptedType", true),
            " 内容时"
        )
    }

    /**
     * 核心执行逻辑
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 从执行上下文中获取由 ShareReceiverActivity 传入的数据
        val receivedData = context.triggerData
            ?: return ExecutionResult.Failure("触发失败", "没有接收到分享数据。")

        onProgress(ProgressUpdate("已接收到分享内容"))
        // 将接收到的数据作为模块的输出
        return ExecutionResult.Success(outputs = mapOf("shared_content" to receivedData))
    }
}