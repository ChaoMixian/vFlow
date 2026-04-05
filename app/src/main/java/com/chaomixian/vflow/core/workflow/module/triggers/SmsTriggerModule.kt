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
    companion object {
        const val SENDER_ANY = "sender_any"
        const val SENDER_CONTAINS = "sender_contains"
        const val SENDER_NOT_CONTAINS = "sender_not_contains"
        const val SENDER_REGEX = "sender_regex"
        const val CONTENT_ANY = "content_any"
        const val CONTENT_CODE = "content_code"
        const val CONTENT_CONTAINS = "content_contains"
        const val CONTENT_NOT_CONTAINS = "content_not_contains"
        const val CONTENT_REGEX = "content_regex"
    }
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
    private val senderFilterOptions by lazy { listOf(SENDER_ANY, SENDER_CONTAINS, SENDER_NOT_CONTAINS, SENDER_REGEX) }
    // 添加"识别验证码"预设
    private val contentFilterOptions by lazy { listOf(CONTENT_ANY, CONTENT_CODE, CONTENT_CONTAINS, CONTENT_NOT_CONTAINS, CONTENT_REGEX) }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("sender_filter_type", "发件人条件", ParameterType.ENUM,
            defaultValue = SENDER_ANY,
            options = senderFilterOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_sms_sender_any,
                R.string.option_vflow_trigger_sms_sender_contains,
                R.string.option_vflow_trigger_sms_sender_not_contains,
                R.string.option_vflow_trigger_sms_sender_regex
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_sms_sender_any) to SENDER_ANY,
                appContext.getString(R.string.option_vflow_trigger_sms_sender_contains) to SENDER_CONTAINS,
                appContext.getString(R.string.option_vflow_trigger_sms_sender_not_contains) to SENDER_NOT_CONTAINS,
                appContext.getString(R.string.option_vflow_trigger_sms_sender_regex) to SENDER_REGEX
            ),
            nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        ),
        // 发件人值 - 当 sender_filter_type 不等于 "任意" 时显示
        InputDefinition("sender_filter_value", "发件人值", ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_trigger_sms_sender_filter_value_name,
            visibility = InputVisibility.notEquals("sender_filter_type", SENDER_ANY)
        ),
        InputDefinition("content_filter_type", "内容条件", ParameterType.ENUM,
            defaultValue = CONTENT_ANY,
            options = contentFilterOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_sms_content_any,
                R.string.option_vflow_trigger_sms_content_code,
                R.string.option_vflow_trigger_sms_content_contains,
                R.string.option_vflow_trigger_sms_content_not_contains,
                R.string.option_vflow_trigger_sms_content_regex
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_sms_content_any) to CONTENT_ANY,
                appContext.getString(R.string.option_vflow_trigger_sms_content_code) to CONTENT_CODE,
                appContext.getString(R.string.option_vflow_trigger_sms_content_contains) to CONTENT_CONTAINS,
                appContext.getString(R.string.option_vflow_trigger_sms_content_not_contains) to CONTENT_NOT_CONTAINS,
                appContext.getString(R.string.option_vflow_trigger_sms_content_regex) to CONTENT_REGEX
            ),
            nameStringRes = R.string.param_vflow_trigger_sms_content_filter_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        ),
        // 内容值 - 当 content_filter_type 既不等于 "任意" 也不等于 "识别验证码" 时显示
        InputDefinition("content_filter_value", "内容值", ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_trigger_sms_content_filter_value_name,
            visibility = InputVisibility.notIn("content_filter_type", listOf(CONTENT_ANY, CONTENT_CODE))
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
            if (updatedValue == CONTENT_CODE || updatedValue == CONTENT_ANY) {
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
        if (contentType == CONTENT_CODE) {
            outputs.add(OutputDefinition("verification_code", "验证码", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_sms_verification_code_name))
        }
        return outputs
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val senderCondition = step.parameters["sender_filter_type"] as? String ?: SENDER_ANY
        val senderValue = step.parameters["sender_filter_value"] as? String ?: ""
        val contentCondition = step.parameters["content_filter_type"] as? String ?: CONTENT_ANY
        val contentValue = step.parameters["content_filter_value"] as? String ?: ""

        val isCodeText = context.getString(R.string.summary_vflow_trigger_sms_is_code)
        val senderLabel = getOptionLabel("sender_filter_type", senderCondition)
        val contentLabel = getOptionLabel("content_filter_type", contentCondition)
        val senderPillText = if (senderCondition == SENDER_ANY || senderValue.isBlank()) senderLabel else "$senderLabel \"$senderValue\""
        val contentPillText = when {
            contentCondition == CONTENT_CODE -> isCodeText
            contentCondition == CONTENT_ANY || contentValue.isBlank() -> contentLabel
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

    private fun getOptionLabel(inputId: String, value: String): String {
        val input = getInputs().find { it.id == inputId } ?: return value
        val index = input.options?.indexOf(value) ?: -1
        val resId = input.optionsStringRes?.getOrNull(index) ?: return value
        return appContext.getString(resId)
    }
}
