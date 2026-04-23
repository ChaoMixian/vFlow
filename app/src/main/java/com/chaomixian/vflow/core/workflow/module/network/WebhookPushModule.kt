package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.AiModuleUsageScope
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebhookPushModule : BaseModule() {
    companion object {
        private const val METHOD_POST = "POST"
        private const val METHOD_PUT = "PUT"
        private const val METHOD_PATCH = "PATCH"

        private const val BODY_JSON = "json"
        private const val BODY_CUSTOM_JSON = "custom_json"
        private const val BODY_RAW = "raw"
        private const val BODY_FORM = "form"
    }

    override val id = "vflow.network.webhook_push"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_network_webhook_push_name,
        descriptionStringRes = R.string.module_vflow_network_webhook_push_desc,
        name = "Webhook 推送",
        description = "向任意 Webhook 地址推送消息",
        iconRes = R.drawable.rounded_ios_share_24,
        category = "网络",
        categoryId = "network"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.HIGH,
        workflowStepDescription = "Send a webhook request with JSON, custom JSON, raw text, or form data.",
        inputHints = mapOf(
            "url" to "Webhook endpoint URL.",
            "method" to "Request method, usually POST.",
            "body_type" to "Payload mode: json, custom_json, raw, or form.",
            "message" to "Main message body when not using custom JSON mode.",
            "custom_json_body" to "Full JSON payload for custom_json mode.",
        ),
        requiredInputIds = setOf("url", "method"),
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "url",
            name = "Webhook 地址",
            nameStringRes = R.string.param_vflow_network_webhook_push_url_name,
            staticType = ParameterType.STRING,
            supportsRichText = true
        ),
        InputDefinition(
            id = "method",
            name = "请求方法",
            nameStringRes = R.string.param_vflow_network_webhook_push_method_name,
            staticType = ParameterType.ENUM,
            defaultValue = METHOD_POST,
            options = listOf(METHOD_POST, METHOD_PUT, METHOD_PATCH),
            optionsStringRes = listOf(
                R.string.option_vflow_network_webhook_push_method_post,
                R.string.option_vflow_network_webhook_push_method_put,
                R.string.option_vflow_network_webhook_push_method_patch
            ),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "body_type",
            name = "内容格式",
            nameStringRes = R.string.param_vflow_network_webhook_push_body_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = BODY_JSON,
            options = listOf(BODY_JSON, BODY_CUSTOM_JSON, BODY_RAW, BODY_FORM),
            optionsStringRes = listOf(
                R.string.option_vflow_network_webhook_push_body_json,
                R.string.option_vflow_network_webhook_push_body_custom_json,
                R.string.option_vflow_network_webhook_push_body_raw,
                R.string.option_vflow_network_webhook_push_body_form
            ),
            legacyValueMap = mapOf(
                "JSON" to BODY_JSON,
                "自定义JSON" to BODY_CUSTOM_JSON,
                "文本" to BODY_RAW,
                "表单" to BODY_FORM
            ),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "title",
            name = "标题",
            nameStringRes = R.string.param_vflow_network_webhook_push_title_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            visibility = InputVisibility.whenNotEquals("body_type", BODY_CUSTOM_JSON)
        ),
        InputDefinition(
            id = "message",
            name = "消息内容",
            nameStringRes = R.string.param_vflow_network_webhook_push_message_name,
            staticType = ParameterType.STRING,
            supportsRichText = true,
            visibility = InputVisibility.whenNotEquals("body_type", BODY_CUSTOM_JSON)
        ),
        InputDefinition(
            id = "custom_json_body",
            name = "JSON 内容",
            nameStringRes = R.string.param_vflow_network_webhook_push_custom_json_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            visibility = InputVisibility.whenEquals("body_type", BODY_CUSTOM_JSON),
            hintStringRes = R.string.hint_vflow_network_webhook_push_custom_json
        ),
        InputDefinition(
            id = "raw_body",
            name = "自定义文本",
            nameStringRes = R.string.param_vflow_network_webhook_push_raw_body_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            visibility = InputVisibility.whenEquals("body_type", BODY_RAW),
            hintStringRes = R.string.hint_vflow_network_webhook_push_raw_body
        ),
        InputDefinition(
            id = "form_fields_json",
            name = "表单字段 JSON",
            nameStringRes = R.string.param_vflow_network_webhook_push_form_fields_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            visibility = InputVisibility.whenEquals("body_type", BODY_FORM),
            hintStringRes = R.string.hint_vflow_network_webhook_push_form_fields
        ),
        InputDefinition(
            id = "headers_json",
            name = "请求头 JSON",
            nameStringRes = R.string.param_vflow_network_webhook_push_headers_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            supportsRichText = true,
            isFolded = true,
            hintStringRes = R.string.hint_vflow_network_webhook_push_headers
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            nameStringRes = R.string.param_vflow_network_webhook_push_timeout_name,
            staticType = ParameterType.NUMBER,
            defaultValue = 15,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_network_webhook_push_success_name),
        OutputDefinition("status_code", "状态码", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_network_webhook_push_status_code_name),
        OutputDefinition("response_body", "响应内容", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_webhook_push_response_body_name),
        OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_network_webhook_push_error_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val methodPill = PillUtil.createPillFromParam(step.parameters["method"], inputs.find { it.id == "method" }, isModuleOption = true)
        val urlPill = PillUtil.createPillFromParam(step.parameters["url"], inputs.find { it.id == "url" })
        return PillUtil.buildSpannable(context, "Webhook 推送", methodPill, urlPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val url = step.parameters["url"]?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            return ValidationResult(false, "Webhook 地址不能为空")
        }
        val inputs = getInputs()
        val bodyType = inputs.find { it.id == "body_type" }?.normalizeEnumValue(step.parameters["body_type"]?.toString()) ?: BODY_JSON
        val message = step.parameters["message"]?.toString()?.trim().orEmpty()
        val customJsonBody = step.parameters["custom_json_body"]?.toString().orEmpty().trim()

        if (bodyType == BODY_CUSTOM_JSON) {
            if (customJsonBody.isEmpty()) {
                return ValidationResult(false, "自定义 JSON 不能为空")
            }
            try {
                JsonParser.parseString(customJsonBody)
            } catch (_: Exception) {
                return ValidationResult(false, "自定义 JSON 格式错误")
            }
        } else if (message.isEmpty()) {
            return ValidationResult(false, "消息内容不能为空")
        }

        if (!step.parameters["headers_json"]?.toString().isNullOrBlank() &&
            parseHeadersJson(step.parameters["headers_json"]?.toString().orEmpty()).error != null
        ) {
            return ValidationResult(false, "请求头 JSON 格式错误")
        }

        if (bodyType == BODY_FORM) {
            val formFieldsJson = step.parameters["form_fields_json"]?.toString().orEmpty().trim()
            if (formFieldsJson.isNotEmpty() && parseJsonObjectStringMap(formFieldsJson) == null) {
                return ValidationResult(false, "表单字段 JSON 必须是对象")
            }
        }

        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val inputs = getInputs()
                val timeout = context.getVariableAsLong("timeout") ?: 15L
                val url = VariableResolver.resolve(context.getVariableAsString("url", ""), context).trim()
                val title = VariableResolver.resolve(context.getVariableAsString("title", ""), context).trim()
                val message = VariableResolver.resolve(context.getVariableAsString("message", ""), context).trim()
                val rawMethod = context.getVariableAsString("method", METHOD_POST)
                val method = inputs.find { it.id == "method" }?.normalizeEnumValue(rawMethod) ?: rawMethod
                val rawBodyType = context.getVariableAsString("body_type", BODY_JSON)
                val bodyType = inputs.find { it.id == "body_type" }?.normalizeEnumValue(rawBodyType) ?: rawBodyType
                val customJsonBody = VariableResolver.resolve(context.getVariableAsString("custom_json_body", ""), context).trim()
                val rawBody = VariableResolver.resolve(context.getVariableAsString("raw_body", ""), context)
                val formFieldsJson = VariableResolver.resolve(context.getVariableAsString("form_fields_json", ""), context).trim()
                val headersJson = VariableResolver.resolve(context.getVariableAsString("headers_json", ""), context).trim()

                val missingRequiredFields = if (bodyType == BODY_CUSTOM_JSON) {
                    url.isEmpty() || customJsonBody.isEmpty()
                } else {
                    url.isEmpty() || message.isEmpty()
                }
                if (missingRequiredFields) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_webhook_push_param_error),
                        if (bodyType == BODY_CUSTOM_JSON) {
                            appContext.getString(R.string.error_vflow_network_webhook_push_required_custom_json)
                        } else {
                            appContext.getString(R.string.error_vflow_network_webhook_push_required_fields)
                        }
                    )
                }
                if (url.toHttpUrlOrNull() == null) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_webhook_push_param_error),
                        appContext.getString(R.string.error_vflow_network_webhook_push_invalid_url)
                    )
                }

                val parsedHeaders = parseHeadersJson(headersJson)
                if (parsedHeaders.error != null) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_webhook_push_param_error),
                        parsedHeaders.error
                    )
                }

                val requestBody = createRequestBody(bodyType, title, message, customJsonBody, rawBody, formFieldsJson)
                    ?: return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_webhook_push_param_error),
                        appContext.getString(R.string.error_vflow_network_webhook_push_invalid_payload)
                    )

                val client = OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(timeout, TimeUnit.SECONDS)
                    .callTimeout(timeout, TimeUnit.SECONDS)
                    .build()

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_webhook_push_sending, method, url)))
                val requestBuilder = Request.Builder().url(url)
                parsedHeaders.headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
                requestBuilder.method(method, requestBody)

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_network_webhook_push_failed),
                        responseBody.ifBlank { response.message },
                        mapOf(
                            "success" to VBoolean(false),
                            "status_code" to VNumber(response.code.toDouble()),
                            "response_body" to VString(responseBody),
                            "error" to VString(responseBody.ifBlank { response.message })
                        )
                    )
                }

                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "status_code" to VNumber(response.code.toDouble()),
                        "response_body" to VString(responseBody),
                        "error" to VString("")
                    )
                )
            } catch (e: IOException) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_webhook_push_network_error),
                    e.message ?: appContext.getString(R.string.error_vflow_network_webhook_push_unknown_error)
                )
            } catch (e: Exception) {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_network_webhook_push_execution_failed),
                    e.localizedMessage ?: appContext.getString(R.string.error_vflow_network_webhook_push_unknown_error)
                )
            }
        }
    }

    private fun createRequestBody(
        bodyType: String,
        title: String,
        message: String,
        customJsonBody: String,
        rawBody: String,
        formFieldsJson: String
    ): RequestBody? {
        return when (bodyType) {
            BODY_JSON -> Gson().toJson(buildDefaultWebhookJsonPayload(title, message))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            BODY_CUSTOM_JSON -> {
                val payload = try {
                    JsonParser.parseString(customJsonBody)
                } catch (_: Exception) {
                    return null
                }
                Gson().toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
            }
            BODY_RAW -> {
                val finalBody = rawBody.ifBlank { buildDefaultWebhookRawBody(title, message) }
                finalBody.toRequestBody("text/plain; charset=utf-8".toMediaType())
            }
            BODY_FORM -> {
                val fields = if (formFieldsJson.isBlank()) {
                    buildDefaultWebhookFormFields(title, message)
                } else {
                    parseJsonObjectStringMap(formFieldsJson) ?: return null
                }
                FormBody.Builder().apply {
                    fields.forEach { (key, value) -> add(key, value) }
                }.build()
            }
            else -> null
        }
    }
}
