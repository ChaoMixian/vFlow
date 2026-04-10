package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.provider.Telephony
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.utils.CodeExtractor
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ReadSmsModule : BaseModule() {
    companion object {
        const val FILTER_LATEST = "latest"
        const val FILTER_SENDER = "sender"
        const val FILTER_CONTENT = "content"
        const val FILTER_BOTH = "both"
        private val FILTER_LEGACY_MAP = mapOf(
            "最新一条" to FILTER_LATEST,
            "来自发件人" to FILTER_SENDER,
            "包含内容" to FILTER_CONTENT,
            "发件人与内容" to FILTER_BOTH
        )
    }

    override val id = "vflow.system.read_sms"
    override val metadata = ActionMetadata(
        name = "读取短信",  // Fallback
        nameStringRes = R.string.module_vflow_system_read_sms_name,
        description = "从收件箱中按条件查找短信，并支持提取验证码。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_read_sms_desc,
        iconRes = R.drawable.rounded_sms_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val requiredPermissions = listOf(PermissionManager.SMS)
    override val uiProvider: ModuleUIProvider = ReadSmsModuleUIProvider()

    val filterOptions = listOf(FILTER_LATEST, FILTER_SENDER, FILTER_CONTENT, FILTER_BOTH)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "filter_by",
            "筛选方式",
            ParameterType.ENUM,
            defaultValue = FILTER_LATEST,
            options = filterOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_system_read_sms_filter_latest,
                R.string.option_vflow_system_read_sms_filter_sender,
                R.string.option_vflow_system_read_sms_filter_content,
                R.string.option_vflow_system_read_sms_filter_both
            ),
            legacyValueMap = FILTER_LEGACY_MAP,
            nameStringRes = R.string.param_vflow_system_read_sms_filter_by_name
        ),
        InputDefinition("sender", "发件人号码", ParameterType.STRING, nameStringRes = R.string.param_vflow_system_read_sms_sender_name),
        InputDefinition("content", "内容包含", ParameterType.STRING, nameStringRes = R.string.param_vflow_system_read_sms_content_name),
        InputDefinition("max_scan", "扫描数量", ParameterType.NUMBER, defaultValue = 20.0, nameStringRes = R.string.param_vflow_system_read_sms_max_scan_name),
        InputDefinition("extract_code", "提取验证码", ParameterType.BOOLEAN, defaultValue = false, nameStringRes = R.string.param_vflow_system_read_sms_extract_code_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("found", "是否找到", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("sender", "发件人号码", VTypeRegistry.STRING.id),
        OutputDefinition("content", "短信全文", VTypeRegistry.STRING.id),
        OutputDefinition("timestamp", "接收时间", VTypeRegistry.NUMBER.id),
        OutputDefinition("verification_code", "提取的验证码", VTypeRegistry.STRING.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val params = step.parameters
        val filterInput = getInputs().first { it.id == "filter_by" }
        val rawFilterBy = params["filter_by"] as? String ?: FILTER_LATEST
        val filterBy = filterInput.normalizeEnumValue(rawFilterBy) ?: rawFilterBy
        val extractCode = params["extract_code"] as? Boolean ?: false

        val parts = mutableListOf<Any>(context.getString(R.string.summary_vflow_system_read_sms_prefix))

        if (filterBy != FILTER_LATEST) {
            if (filterBy == FILTER_SENDER || filterBy == FILTER_BOTH) {
                parts.add(context.getString(R.string.summary_vflow_system_read_sms_from))
                parts.add(PillUtil.createPillFromParam(params["sender"], getInputs().find { it.id == "sender" }))
            }
            if (filterBy == FILTER_CONTENT || filterBy == FILTER_BOTH) {
                parts.add(context.getString(R.string.summary_vflow_system_read_sms_content))
                if (extractCode) {
                    parts.add(PillUtil.Pill(context.getString(R.string.summary_vflow_system_read_sms_code), "extract_code"))
                } else {
                    parts.add(PillUtil.createPillFromParam(params["content"], getInputs().find { it.id == "content" }))
                }
            }
            parts.add(context.getString(R.string.summary_vflow_system_read_sms_suffix))
        } else {
            parts.add(context.getString(R.string.summary_vflow_system_read_sms_latest))
        }

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val resolver = context.applicationContext.contentResolver
        val filterInput = getInputs().first { it.id == "filter_by" }
        val rawFilterBy = context.getVariableAsString("filter_by", FILTER_LATEST)
        val filterBy = filterInput.normalizeEnumValue(rawFilterBy) ?: rawFilterBy
        val senderFilter = context.getVariableAsString("sender", "").ifBlank { null }
        val contentFilter = context.getVariableAsString("content", "").ifBlank { null }
        val maxScan = if (filterBy == FILTER_LATEST) 1 else (context.getVariableAsInt("max_scan") ?: 20)
        val extractCode = context.getVariableAsBoolean("extract_code") ?: false

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_read_sms_scanning, maxScan)))

        val cursor = resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $maxScan"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                val senderMatches = senderFilter.isNullOrEmpty() || sender.contains(senderFilter)

                val contentMatches = if (extractCode) {
                    !CodeExtractor.getCode(body).isNullOrEmpty()
                } else {
                    contentFilter.isNullOrEmpty() || body.contains(contentFilter, ignoreCase = true)
                }

                val isMatch = when (filterBy) {
                    FILTER_LATEST -> true
                    FILTER_SENDER -> senderMatches
                    FILTER_CONTENT -> contentMatches
                    FILTER_BOTH -> senderMatches && contentMatches
                    else -> false
                }

                if (isMatch) {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_read_sms_found, sender)))

                    val verificationCode = if (extractCode) {
                        CodeExtractor.getCode(body) ?: ""
                    } else {
                        ""
                    }

                    // 如果要求提取验证码但最终没提取到，则跳过此条（除非没有设置其他过滤条件）
                    if (extractCode && verificationCode.isEmpty() && !contentFilter.isNullOrEmpty()) {
                        continue
                    }

                    return ExecutionResult.Success(mapOf(
                        "found" to VBoolean(true),
                        "sender" to VString(sender),
                        "content" to VString(body),
                        "timestamp" to VNumber(timestamp.toDouble()),
                        "verification_code" to VString(verificationCode)
                    ))
                }
            }
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_read_sms_not_found)))
        return ExecutionResult.Success(mapOf("found" to VBoolean(false)))
    }
}
