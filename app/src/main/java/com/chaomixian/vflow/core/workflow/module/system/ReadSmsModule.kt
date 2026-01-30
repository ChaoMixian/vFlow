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
import java.util.regex.Pattern

class ReadSmsModule : BaseModule() {
    override val id = "vflow.system.read_sms"
    override val metadata = ActionMetadata(
        name = "读取短信",  // Fallback
        nameStringRes = R.string.module_vflow_system_read_sms_name,
        description = "从收件箱中按条件查找短信，并支持提取验证码。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_read_sms_desc,
        iconRes = R.drawable.rounded_sms_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.SMS)
    override val uiProvider: ModuleUIProvider = ReadSmsModuleUIProvider()

    val filterOptions = listOf("最新一条", "来自发件人", "包含内容", "发件人与内容")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("filter_by", "筛选方式", ParameterType.ENUM, defaultValue = filterOptions.first(), options = filterOptions),
        InputDefinition("sender", "发件人号码", ParameterType.STRING),
        InputDefinition("content", "内容包含", ParameterType.STRING),
        InputDefinition("max_scan", "扫描数量", ParameterType.NUMBER, defaultValue = 20.0),
        InputDefinition("extract_code", "提取验证码", ParameterType.BOOLEAN, defaultValue = false)
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
        val filterBy = params["filter_by"] as? String ?: filterOptions.first()
        val extractCode = params["extract_code"] as? Boolean ?: false

        val parts = mutableListOf<Any>(context.getString(R.string.summary_vflow_system_read_sms_prefix))

        if (filterBy != "最新一条") {
            if (filterBy == "来自发件人" || filterBy == "发件人与内容") {
                parts.add(context.getString(R.string.summary_vflow_system_read_sms_from))
                parts.add(PillUtil.createPillFromParam(params["sender"], getInputs().find { it.id == "sender" }))
            }
            if (filterBy == "包含内容" || filterBy == "发件人与内容") {
                parts.add(context.getString(R.string.summary_vflow_system_read_sms_content))
                if (extractCode) {
                    // 更新 Pill 的构造以匹配新的签名
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
        val params = context.variables
        val magicVars = context.magicVariables

        val filterBy = params["filter_by"] as? String ?: filterOptions.first()
        val senderFilter = (magicVars["sender"] as? VString)?.raw ?: params["sender"] as? String
        val contentFilter = (magicVars["content"] as? VString)?.raw ?: params["content"] as? String
        val maxScan = if (filterBy == "最新一条") 1 else ((magicVars["max_scan"] as? VNumber)?.raw ?: params["max_scan"] as? Number ?: 20.0).toInt()
        val extractCode = params["extract_code"] as? Boolean ?: false

        onProgress(ProgressUpdate("开始扫描最近 $maxScan 条短信..."))

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

                // [修改] 如果开启了验证码提取，则内容匹配逻辑变为检查是否能提取出验证码
                val contentMatches = if (extractCode) {
                    !CodeExtractor.getCode(body).isNullOrEmpty()
                } else {
                    contentFilter.isNullOrEmpty() || body.contains(contentFilter, ignoreCase = true)
                }

                val isMatch = when (filterBy) {
                    "最新一条" -> true
                    "来自发件人" -> senderMatches
                    "包含内容" -> contentMatches
                    "发件人与内容" -> senderMatches && contentMatches
                    else -> false
                }

                if (isMatch) {
                    onProgress(ProgressUpdate("找到匹配短信: 来自 $sender"))

                    // [核心修改] 使用 CodeExtractor 提取验证码
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

        onProgress(ProgressUpdate("未找到匹配的短信"))
        return ExecutionResult.Success(mapOf("found" to VBoolean(false)))
    }
}