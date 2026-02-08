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

    // 定义所有过滤选项
    private val senderFilterOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_sms_sender_any),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_not_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_sender_regex)
        )
    }
    // 添加"识别验证码"预设
    private val contentFilterOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_sms_content_any),
            appContext.getString(R.string.option_vflow_trigger_sms_content_code),
            appContext.getString(R.string.option_vflow_trigger_sms_content_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_content_not_contains),
            appContext.getString(R.string.option_vflow_trigger_sms_content_regex)
        )
    }

    private val anySenderOption get() = senderFilterOptions[0]
    private val anyContentOption get() = contentFilterOptions[0]
    private val codeContentOption get() = contentFilterOptions[1]

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("sender_filter_type", "发件人条件", ParameterType.ENUM,
            defaultValue = anySenderOption,
            options = senderFilterOptions,
            nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        ),
        // 发件人值 - 当 sender_filter_type 不等于 "任意" 时显示
        InputDefinition("sender_filter_value", "发件人值", ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_value_name,
            visibility = InputVisibility.notEquals("sender_filter_type", anySenderOption)
        ),
        InputDefinition("content_filter_type", "内容条件", ParameterType.ENUM,
            defaultValue = anyContentOption,
            options = contentFilterOptions,
            nameStringRes = R.string.param_vflow_trigger_sms_content_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        ),
        // 内容值 - 当 content_filter_type 既不等于 "任意" 也不等于 "识别验证码" 时显示
        InputDefinition("content_filter_value", "内容值", ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_trigger_sms_content_filter_value_name,
            visibility = InputVisibility.notIn("content_filter_type", listOf(anyContentOption, codeContentOption))
        )
    )

    /**
     * 当切换到不需要值的选项时，清空对应的值。
     */
    override fun onParameterUpdated(
        step: ActionStep,
        updatedParameterId: String,
        updatedValue: Any?
    ): Map<String, Any?> {
        val newParameters = step.parameters.toMutableMap()
        newParameters[updatedParameterId] = updatedValue

        // 当切换内容条件时，清空不需要的值
        if (updatedParameterId == "content_filter_type") {
            if (updatedValue == codeContentOption || updatedValue == anyContentOption) {
                newParameters["content_filter_value"] = ""
            }
        }
        return newParameters
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
        if (contentType == codeContentOption) { // 识别验证码
            outputs.add(OutputDefinition("verification_code", "验证码", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_sms_verification_code_name))
        }
        return outputs
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val senderCondition = step.parameters["sender_filter_type"] as? String ?: anySenderOption
        val senderValue = step.parameters["sender_filter_value"] as? String ?: ""
        val contentCondition = step.parameters["content_filter_type"] as? String ?: anyContentOption
        val contentValue = step.parameters["content_filter_value"] as? String ?: ""

        val isCodeText = context.getString(R.string.summary_vflow_trigger_sms_is_code)

        val senderPillText = if (senderCondition == anySenderOption || senderValue.isBlank()) senderCondition else "$senderCondition \"$senderValue\""
        val contentPillText = when {
            contentCondition == codeContentOption -> isCodeText // 识别验证码
            contentCondition == anyContentOption || contentValue.isBlank() -> contentCondition // 任意内容
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