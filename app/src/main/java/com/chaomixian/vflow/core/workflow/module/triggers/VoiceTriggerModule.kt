package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity

class VoiceTriggerModule : BaseModule() {
    override val id = "vflow.trigger.voice_template"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_voice_template_name,
        descriptionStringRes = R.string.module_vflow_trigger_voice_template_desc,
        name = "语音触发器",
        description = "使用模块配置中的全局语音模板，在后台持续监听相似语音片段并触发工作流",
        iconRes = R.drawable.rounded_voice_chat_24,
        category = "触发器",
        categoryId = "trigger",
    )
    override val requiredPermissions = listOf(PermissionManager.MICROPHONE)
    override val uiProvider = VoiceTriggerUIProvider()

    override fun getInputs(): List<com.chaomixian.vflow.core.module.InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "similarity",
            name = "相似度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_voice_template_similarity_name,
        ),
        OutputDefinition(
            id = "segmentDurationMs",
            name = "语音时长",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_voice_template_segment_duration_name,
        ),
        OutputDefinition(
            id = "hitCount",
            name = "命中模板数",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_voice_template_hit_count_name,
        ),
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        if (!VoiceTriggerConfig.hasTemplates(context)) {
            return context.getString(R.string.summary_vflow_trigger_voice_template_not_recorded)
        }
        return context.getString(
            R.string.summary_vflow_trigger_voice_template_ready,
            context.getString(R.string.summary_vflow_trigger_voice_template_prefix),
            context.getString(R.string.summary_vflow_trigger_voice_template_templates_ready)
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit,
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_voice_template_triggered)))
        val triggerData = context.triggerData as? VoiceTriggerData
            ?: VoiceTriggerData(similarity = 0f, segmentDurationMs = 0L, hitCount = 0)
        return ExecutionResult.Success(
            outputs = mapOf(
                "similarity" to VNumber(triggerData.similarity.toDouble()),
                "segmentDurationMs" to VNumber(triggerData.segmentDurationMs.toDouble()),
                "hitCount" to VNumber(triggerData.hitCount.toDouble()),
            )
        )
    }
}
