package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "接收分享" 触发器。
 * 当用户通过 Android 的分享菜单将内容分享给 vFlow 时，会触发此模块。
 */
class ReceiveShareTriggerModule : BaseModule() {
    companion object {
        private const val TYPE_ANY = "any"
        private const val TYPE_TEXT = "text"
        private const val TYPE_LINK = "link"
        private const val TYPE_IMAGE = "image"
        private const val TYPE_FILE = "file"
    }

    override val id = "vflow.trigger.share"
    override val metadata = ActionMetadata(
        name = "接收分享",  // Fallback
        nameStringRes = R.string.module_vflow_trigger_share_name,
        description = "当有内容（如文本、图片、文件）分享到vFlow时启动工作流。",  // Fallback
        descriptionStringRes = R.string.module_vflow_trigger_share_desc,
        iconRes = R.drawable.rounded_inbox_text_share_24, // 使用新创建的图标
        category = "触发器",
        categoryId = "trigger"
    )

    // 定义此触发器接受的内容类型
    private val acceptedTypes = listOf(TYPE_ANY, TYPE_TEXT, TYPE_LINK, TYPE_IMAGE, TYPE_FILE)

    /**
     * 定义输入参数：只保留接收类型，入口由系统自动分配
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "acceptedType",
            nameStringRes = R.string.param_vflow_trigger_share_accepted_type_name,
            name = "接收内容类型",
            staticType = ParameterType.ENUM,
            defaultValue = TYPE_ANY,
            options = acceptedTypes,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_share_type_any,
                R.string.option_vflow_trigger_share_type_text,
                R.string.option_vflow_trigger_share_type_link,
                R.string.option_vflow_trigger_share_type_image,
                R.string.option_vflow_trigger_share_type_file
            ),
            legacyValueMap = mapOf(
                "任意" to TYPE_ANY,
                "Any" to TYPE_ANY,
                "文本" to TYPE_TEXT,
                "Text" to TYPE_TEXT,
                "链接" to TYPE_LINK,
                "Link" to TYPE_LINK,
                "图片" to TYPE_IMAGE,
                "Image" to TYPE_IMAGE,
                "文件" to TYPE_FILE,
                "File" to TYPE_FILE
            ),
            acceptsMagicVariable = false
        )
    )

    /**
     * 根据接收类型，动态定义输出参数
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val input = getInputs().first()
        val rawType = step?.parameters?.get("acceptedType") as? String
        val type = input.normalizeEnumValue(rawType) ?: TYPE_ANY
        return when (type) {
            TYPE_TEXT, TYPE_LINK -> listOf(OutputDefinition("shared_content", "分享的文本", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_share_text_name))
            TYPE_IMAGE -> listOf(OutputDefinition("shared_content", "分享的图片", VTypeRegistry.IMAGE.id, nameStringRes = R.string.output_vflow_trigger_share_image_name))
            else -> listOf(OutputDefinition("shared_content", "分享的内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_share_content_name))
        }
    }

    /**
     * 生成模块摘要
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val typePill = PillUtil.createPillFromParam(
            step.parameters["acceptedType"],
            getInputs().find { it.id == "acceptedType" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context,
            context.getString(R.string.summary_vflow_trigger_share_prefix),
            typePill,
            context.getString(R.string.summary_vflow_trigger_share_suffix)
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
