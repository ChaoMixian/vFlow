// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/SmsTriggerModule.kt
// 描述: 定义了当收到短信时触发工作流的模块。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class SmsTriggerModule : BaseModule() {
    override val id = "vflow.trigger.sms"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_sms_name,
        descriptionStringRes = R.string.module_vflow_trigger_sms_desc,
        name = "短信触发",  // Fallback
        description = "当收到满足特定条件的短信时触发工作流",  // Fallback
        iconRes = R.drawable.rounded_sms_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.SMS)
    override val uiProvider: ModuleUIProvider? = SmsTriggerUIProvider()

    // 定义所有过滤选项
    val senderFilterOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_sms_sender_any),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_not_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_regex)
        )
    }
    // 添加"识别验证码"预设
    val contentFilterOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_sms_content_any),
            appContext.getString(R.string.option_vflow_trigger_sms_content_code),
            appContext.getString(R.string.option_vflow_trigger_sms_content_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_content_not_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_content_regex)
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("sender_filter_type", "发件人条件", ParameterType.ENUM, defaultValue = senderFilterOptions.first(), options = senderFilterOptions, nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_type_name),
        InputDefinition("sender_filter_value", "发件人值", ParameterType.STRING, defaultValue = "", nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_value_name),
        InputDefinition("content_filter_type", "内容条件", ParameterType.ENUM, defaultValue = contentFilterOptions.first(), options = contentFilterOptions, nameStringRes = R.string.param_vflow_trigger_sms_content_filter_type_name),
        InputDefinition("content_filter_value", "内容值", ParameterType.STRING, defaultValue = "", nameStringRes = R.string.param_vflow_trigger_sms_content_filter_value_name)
    )

    /**
     * 当选择"识别验证码"时，不再需要自动填充正则表达式。
     */
    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue

        // 识别验证码不再需要特殊处理 value，因为 UI 和 Handler 会直接处理这个选项
        if (updatedParameterId == "content_filter_type") {
            // 如果从一个需要 value 的选项切换到不需要的选项（如"识别验证码"），可以考虑清空 value
            if (updatedValue == contentFilterOptions[1] || updatedValue == contentFilterOptions[0]) {
                newParameters["content_filter_value"] = ""
            }
        }
        return newParameters
    }

    /**
     * 动态隐藏/显示输入框。
     */
    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val all = getInputs()
        val params = step?.parameters ?: emptyMap()
        val senderType = params["sender_filter_type"] as? String ?: senderFilterOptions.first()
        val contentType = params["content_filter_type"] as? String ?: contentFilterOptions.first()
        val dynamicInputs = mutableListOf<InputDefinition>()

        dynamicInputs.add(all.first { it.id == "sender_filter_type" })
        if (senderType != senderFilterOptions[0]) {
            dynamicInputs.add(all.first { it.id == "sender_filter_value" })
        }

        dynamicInputs.add(all.first { it.id == "content_filter_type" })
        if (contentType != contentFilterOptions[0] && contentType != contentFilterOptions[1]) {
            dynamicInputs.add(all.first { it.id == "content_filter_value" })
        }

        return dynamicInputs
    }


    /**
     * 当选择"识别验证码"时，增加一个新的输出。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val outputs = mutableListOf(
            OutputDefinition("sender_number", "发件人号码", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_sms_sender_number_name),
            OutputDefinition("message_content", "短信内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_sms_message_content_name)
        )
        val contentType = step?.parameters?.get("content_filter_type") as? String
        if (contentType == contentFilterOptions[1]) { // 识别验证码
            outputs.add(OutputDefinition("verification_code", "验证码", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_sms_verification_code_name))
        }
        return outputs
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val senderCondition = step.parameters["sender_filter_type"] as? String ?: senderFilterOptions.first()
        val senderValue = step.parameters["sender_filter_value"] as? String ?: ""
        val contentCondition = step.parameters["content_filter_type"] as? String ?: contentFilterOptions.first()
        val contentValue = step.parameters["content_filter_value"] as? String ?: ""

        val isCodeText = context.getString(R.string.summary_vflow_trigger_sms_is_code)

        val senderPillText = if (senderCondition == senderFilterOptions[0] || senderValue.isBlank()) senderCondition else "$senderCondition \"$senderValue\""
        val contentPillText = when {
            contentCondition == contentFilterOptions[1] -> isCodeText // 识别验证码
            contentCondition == contentFilterOptions[0] || contentValue.isBlank() -> contentCondition // 任意内容
            else -> "$contentCondition \"$contentValue\""
        }

        val senderPill = PillUtil.Pill(senderPillText, "sender_filter_type", isModuleOption = true)
        val contentPill = PillUtil.Pill(contentPillText, "content_filter_type", isModuleOption = true)

        val prefix = context.getString(R.string.summary_vflow_trigger_sms_prefix)
        val middle = context.getString(R.string.summary_vflow_trigger_sms_middle)
        val suffix = context.getString(R.string.summary_vflow_trigger_sms_suffix)

        return PillUtil.buildSpannable(context, "$prefix", senderPill, "$middle", contentPill, "$suffix")
    }
    /**
     * 将提取到的验证码也作为输出。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_sms_received)))
        val triggerData = context.triggerData as? VDictionary
        val sender = triggerData?.raw?.get("sender") as? VString ?: VString("")
        val content = triggerData?.raw?.get("content") as? VString ?: VString("")
        val verificationCode = triggerData?.raw?.get("verification_code") as? VString ?: VString("")

        return ExecutionResult.Success(
            outputs = mapOf(
                "sender_number" to sender,
                "message_content" to content,
                "verification_code" to verificationCode
            )
        )
    }
}