// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/SmsTriggerModule.kt
// 描述: 定义了当收到短信时触发工作流的模块。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class SmsTriggerModule : BaseModule() {
    override val id = "vflow.trigger.sms"
    override val metadata = ActionMetadata(
        name = "短信触发",
        description = "当收到满足特定条件的短信时触发工作流。",
        iconRes = R.drawable.rounded_sms_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.SMS)
    override val uiProvider: ModuleUIProvider? = SmsTriggerUIProvider()

    // 定义所有过滤选项
    val senderFilterOptions = listOf("任意号码", "号码包含", "号码不包含", "正则匹配")
    // 添加“识别验证码”预设
    val contentFilterOptions = listOf("任意内容", "识别验证码", "内容包含", "内容不包含", "正则匹配")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("sender_filter_type", "发件人条件", ParameterType.ENUM, defaultValue = senderFilterOptions.first(), options = senderFilterOptions),
        InputDefinition("sender_filter_value", "发件人值", ParameterType.STRING, defaultValue = ""),
        InputDefinition("content_filter_type", "内容条件", ParameterType.ENUM, defaultValue = contentFilterOptions.first(), options = contentFilterOptions),
        InputDefinition("content_filter_value", "内容值", ParameterType.STRING, defaultValue = "")
    )

    /**
     * 当选择“识别验证码”时，不再需要自动填充正则表达式。
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
            // 如果从一个需要 value 的选项切换到不需要的选项（如“识别验证码”），可以考虑清空 value
            if (updatedValue == "识别验证码" || updatedValue == "任意内容") {
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
        if (senderType != "任意号码") {
            dynamicInputs.add(all.first { it.id == "sender_filter_value" })
        }

        dynamicInputs.add(all.first { it.id == "content_filter_type" })
        if (contentType != "任意内容" && contentType != "识别验证码") {
            dynamicInputs.add(all.first { it.id == "content_filter_value" })
        }

        return dynamicInputs
    }


    /**
     * 当选择“识别验证码”时，增加一个新的输出。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val outputs = mutableListOf(
            OutputDefinition("sender_number", "发件人号码", TextVariable.TYPE_NAME),
            OutputDefinition("message_content", "短信内容", TextVariable.TYPE_NAME)
        )
        val contentType = step?.parameters?.get("content_filter_type") as? String
        if (contentType == "识别验证码") {
            outputs.add(OutputDefinition("verification_code", "验证码", TextVariable.TYPE_NAME))
        }
        return outputs
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val senderCondition = step.parameters["sender_filter_type"] as? String ?: senderFilterOptions.first()
        val senderValue = step.parameters["sender_filter_value"] as? String ?: ""
        val contentCondition = step.parameters["content_filter_type"] as? String ?: contentFilterOptions.first()
        val contentValue = step.parameters["content_filter_value"] as? String ?: ""

        val senderPillText = if (senderCondition == "任意号码" || senderValue.isBlank()) senderCondition else "$senderCondition \"$senderValue\""
        val contentPillText = when {
            contentCondition == "识别验证码" -> "是验证码"
            contentCondition == "任意内容" || contentValue.isBlank() -> contentCondition
            else -> "$contentCondition \"$contentValue\""
        }

        val senderPill = PillUtil.Pill(senderPillText, "sender_filter_type", isModuleOption = true)
        val contentPill = PillUtil.Pill(contentPillText, "content_filter_type", isModuleOption = true)

        return PillUtil.buildSpannable(context, "当收到发件人 ", senderPill, " 且内容 ", contentPill, " 的短信时")
    }
    /**
     * 将提取到的验证码也作为输出。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("短信已收到"))
        val triggerData = context.triggerData as? DictionaryVariable
        val sender = triggerData?.value?.get("sender") ?: TextVariable("")
        val content = triggerData?.value?.get("content") ?: TextVariable("")
        val verificationCode = triggerData?.value?.get("verification_code") ?: TextVariable("")

        return ExecutionResult.Success(
            outputs = mapOf(
                "sender_number" to sender,
                "message_content" to content,
                "verification_code" to verificationCode
            )
        )
    }
}