// 文件: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewModule.kt
// 描述: 打开网页 (WebView) 模块 —— 在真实的 Android WebView 中打开网址，
//      支持后台（无界面）与确认（非后台）两种模式，支持自定义方法/请求头/
//      请求体、Cookie 收集、自定义 JS 与返回变量求值。
//      作为 vFlow 的内建模块提供。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/network/webview/WebViewModule.kt
// Description: Open Web Page (WebView) module — opens a URL inside a real
//      Android WebView, in either background (headless) or confirmation
//      (non-headless) mode, with a custom method/headers/body, cookie
//      collection, custom JavaScript and return-variable evaluation.
//      Provided as a built-in module of vFlow.

package com.chaomixian.vflow.core.workflow.module.network.webview

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.AiModuleMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.AiModuleUsageScope
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 打开网页 (WebView) 模块。
 *
 * 在真实的 Android WebView 中打开一个网址：
 *  - 初始主框架请求以用户配置的方法/请求头/请求体执行，Cookie 处理
 *    留给 WebView 的 CookieManager，重定向由 WebView 自行跟随；
 *  - 后台模式（默认）：页面加载、执行自定义 JS，导航稳定后自动完成；
 *  - 确认模式：显示带应用栏的确认界面（返回键 = WebView 历史后退，
 *    标题 = 页面标题，确认按钮 = 显式确认闸门），模块暂停直到用户确认；
 *  - 完成后在同一个存活的页面上下文中求值返回变量，并输出最终 URL、
 *    状态码、请求头、响应头、响应内容（存活 DOM，原始 HTML 文本）、
 *    页面标题、Cookies、返回值与 JS 错误。自定义 JS 以顶层脚本执行，
 *    其 var / let / const 声明成为页面全局绑定，返回变量求值可以
 *    看到它们（若以函数包装执行，这些声明会被限制在局部作用域，
 *    返回值将总是 missing）。
 *
 * Open Web Page (WebView) module.
 *
 * Opens a URL inside a real Android WebView:
 *  - The initial main-frame request runs with the user-configured
 *    method/headers/body; cookies stay in WebView's CookieManager and
 *    redirects are followed by the WebView itself;
 *  - Headless mode (default): the page loads, custom JS runs, and the
 *    module completes automatically once navigation settles;
 *  - Confirmation mode: a confirmation screen with an app bar is shown
 *    (Back = WebView history back, title = page title, Check button =
 *    explicit confirmation gate); the module stays paused until the user
 *    confirms;
 *  - After completion the return variable is evaluated in the same live
 *    page context, and the module emits the final URL, status code,
 *    request headers, response headers, response body (live DOM, plain
 *    HTML text), page title, cookies, return value and JS error. Custom
 *    JS runs as a top-level script whose var / let / const declarations
 *    become page globals the return-variable evaluation can see (a
 *    function wrapper would confine those declarations to its local
 *    scope, leaving the return value always missing).
 */
class WebViewModule : BaseModule() {

    override val id = "vflow.network.webview"
    override val metadata = com.chaomixian.vflow.core.module.ActionMetadata(
        nameStringRes = R.string.module_vflow_network_webview_name,
        descriptionStringRes = R.string.module_vflow_network_webview_desc,
        name = "打开网页 (WebView)",  // Fallback / 备用名称
        description = "在内嵌 WebView 中打开网页，交互确认后返回请求头、响应、Cookies 与 JS 结果",  // Fallback / 备用描述
        iconRes = R.drawable.rounded_web_24,
        category = "网络",
        categoryId = "network"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.HIGH,
        workflowStepDescription = "Open a URL inside an embedded WebView, run custom JavaScript, and return the request/response headers, final page, cookies, and JS results.",
        inputHints = mapOf(
            "url" to "Absolute http/https URL. Bare domains are prefixed with https://.",
            "method" to "HTTP method for the initial request: GET or POST.",
            "headers" to "JSON object of request headers applied to the initial request, e.g. {\"Authorization\": \"Bearer x\"}.",
            "body" to "Request body for non-GET methods. JSON-looking bodies default to application/json, others to form encoding.",
            "custom_js" to "JavaScript executed in the page after each page load. Top-level var/let/const declarations become page globals readable by return_variable; errors are captured into js_error and never fail the module.",
            "return_variable" to "Name of a JavaScript variable (dotted paths allowed) evaluated in the live page context after custom JS; when the variable is undefined and custom JS used a top-level return, that return value is used.",
            "headless" to "true = run fully in the background; false = show the confirmation screen with a Check button.",
            "timeout" to "Seconds until the headless execution gives up (default 60). The confirmation mode has no module timeout."
        ),
        requiredInputIds = setOf("url")
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "url",
            name = "网址",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_network_webview_url_name
        ),
        InputDefinition(
            id = "method",
            name = "方法",
            staticType = ParameterType.ENUM,
            defaultValue = "GET",
            options = HTTP_METHODS,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_network_webview_method_name
        ),
        InputDefinition(
            id = "headers",
            name = "请求头",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_network_webview_headers_name,
            hint = "{\"Authorization\": \"Bearer ...\"}",
            hintStringRes = R.string.param_vflow_network_webview_headers_hint
        ),
        InputDefinition(
            id = "headless",
            name = "后台模式",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_network_webview_headless_name
        ),
        InputDefinition(
            id = "body",
            name = "请求体",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            nameStringRes = R.string.param_vflow_network_webview_body_name
        ),
        InputDefinition(
            id = "custom_js",
            name = "自定义 JS",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            nameStringRes = R.string.param_vflow_network_webview_custom_js_name
        ),
        InputDefinition(
            id = "return_variable",
            name = "返回变量",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            isFolded = true,
            nameStringRes = R.string.param_vflow_network_webview_return_variable_name
        ),
        InputDefinition(
            id = "timeout",
            name = "超时(秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 60.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true,
            nameStringRes = R.string.param_vflow_network_webview_timeout_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "final_url", "最终网址", VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_webview_final_url_name
        ),
        OutputDefinition(
            "status_code", "状态码", VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_network_webview_status_code_name
        ),
        OutputDefinition(
            "request_headers", "请求头", VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_network_webview_request_headers_name
        ),
        OutputDefinition(
            "response_headers", "响应头", VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_network_webview_response_headers_name
        ),
        OutputDefinition(
            "response_body", "响应内容", VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_webview_response_body_name
        ),
        OutputDefinition(
            "page_title", "页面标题", VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_webview_page_title_name
        ),
        OutputDefinition(
            "cookies", "Cookies", VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_network_webview_cookies_name
        ),
        OutputDefinition(
            "return_value", "返回值", VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_network_webview_return_value_name
        ),
        OutputDefinition(
            "return_value_type", "返回值类型", VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_webview_return_value_type_name
        ),
        OutputDefinition(
            "js_error", "JS 错误", VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_network_webview_js_error_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val rawUrl = step.parameters["url"]?.toString() ?: ""
        val method = (step.parameters["method"] as? String) ?: "GET"
        val urlPill = PillUtil.createPillFromParam(
            step.parameters["url"],
            inputs.find { it.id == "url" }
        )

        if (VariableResolver.isComplex(rawUrl)) {
            return PillUtil.buildSpannable(context, method, urlPill, PillUtil.richTextPreview(rawUrl))
        }
        return PillUtil.buildSpannable(context, method, urlPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        // 编辑期校验使用原始文本；变量插值发生在执行期。
        // Validation at edit time uses the raw text; variable interpolation
        // happens at execution time.
        val rawUrl = step.parameters["url"]?.toString() ?: ""
        if (rawUrl.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_network_webview_empty_url)
            )
        }
        val normalized = WebViewRequestSpec.normalizeUrl(rawUrl.trim())
        if (!WebViewRequestSpec.isHttpUrl(normalized)) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_network_webview_invalid_url)
            )
        }
        val returnVariable = (step.parameters["return_variable"] as? String)
            ?.trim()?.takeIf { it.isNotEmpty() }
        if (returnVariable != null && !JsScripts.isValidVariablePath(returnVariable)) {
            return ValidationResult(
                isValid = false,
                errorMessage = String.format(
                    appContext.getString(R.string.error_vflow_network_webview_invalid_return_variable),
                    returnVariable
                )
            )
        }
        val headersRaw = step.parameters["headers"]
        if (headersRaw != null) {
            val parsed = WebViewRequestSpec.parseHeaders(headersRaw)
            if (parsed is HeaderParseResult.Invalid) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = String.format(
                        appContext.getString(R.string.error_vflow_network_webview_invalid_headers),
                        parsed.detail
                    )
                )
            }
        }
        return ValidationResult(isValid = true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 参数在执行期解析：变量插值（富文本药丸）在此处生效。
        // Parameters are resolved at execution time: variable interpolation
        // (rich-text pills) takes effect here.
        val rawUrl = context.getVariableAsString("url", "")
        val url = VariableResolver.resolve(rawUrl, context)
        val method = context.getVariableAsString("method", "GET")
        val headersRaw = VariableResolver.resolve(context.getVariableAsString("headers", ""), context)
        val body = VariableResolver.resolve(context.getVariableAsString("body", ""), context)
        val customJs = VariableResolver.resolve(context.getVariableAsString("custom_js", ""), context)
        val returnVariable = VariableResolver.resolve(
            context.getVariableAsString("return_variable", ""), context
        ).trim().takeIf { it.isNotEmpty() }
        val headless = context.getVariableAsBoolean("headless") ?: true
        val timeoutSeconds = (context.getVariableAsNumber("timeout") ?: 60.0).toLong()

        val headers = when (val headerError = WebViewRequestSpec.parseHeaders(headersRaw)) {
            is HeaderParseResult.Invalid -> return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_network_webview_param_error),
                String.format(
                    appContext.getString(R.string.error_vflow_network_webview_invalid_headers),
                    headerError.detail
                )
            )
            is HeaderParseResult.Valid -> headerError.headers
        }

        val specResult = WebViewRequestSpec.parse(
            url = url,
            method = method,
            headers = headers,
            body = body,
            customJs = customJs,
            returnVariable = returnVariable,
            headless = headless,
            timeoutSeconds = timeoutSeconds
        )
        val spec = when (specResult) {
            is SpecResult.Invalid -> return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_network_webview_param_error),
                specFailureMessage(specResult)
            )
            is SpecResult.Valid -> specResult.spec
        }

        // 进度提示：加载中；确认模式额外提示等待用户确认。
        // Progress updates: loading; confirmation mode additionally waits
        // for the user to confirm.
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_webview_loading, spec.url)))
        if (!spec.headless) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_network_webview_waiting_confirm)))
        }

        val sessionId = UUID.randomUUID().toString()
        val session = WebViewSession(sessionId, spec, context.applicationContext)
        WebViewSessionManager.register(session)
        session.start()
        try {
            val outcome = if (spec.executionDeadlineMs != null) {
                withTimeoutOrNull(spec.executionDeadlineMs + DEADLINE_GRACE_MS) { session.await() }
                    ?: SessionOutcome.Failed(SessionFailure.TIMEOUT, null)
            } else {
                session.await()
            }
            return when (outcome) {
                is SessionOutcome.Completed -> ExecutionResult.Success(outcome.outputs)
                is SessionOutcome.Failed -> ExecutionResult.Failure(
                    sessionFailureTitle(outcome.failure),
                    outcome.detail ?: sessionFailureTitle(outcome.failure)
                )
            }
        } finally {
            // 取消 / 超时 / 正常结束都释放 WebView 与确认界面（幂等）。
            // Cancel / timeout / normal completion all release the WebView
            // and the confirmation screen (idempotent).
            session.cancel()
        }
    }

    private fun specFailureMessage(result: SpecResult.Invalid): String = when (result.reason) {
        InvalidReason.EMPTY_URL -> appContext.getString(R.string.error_vflow_network_webview_empty_url)
        InvalidReason.INVALID_URL -> appContext.getString(R.string.error_vflow_network_webview_invalid_url)
        InvalidReason.INVALID_METHOD -> String.format(
            appContext.getString(R.string.error_vflow_network_webview_invalid_method),
            result.detail ?: ""
        )
        InvalidReason.INVALID_RETURN_VARIABLE -> String.format(
            appContext.getString(R.string.error_vflow_network_webview_invalid_return_variable),
            result.detail ?: ""
        )
    }

    private fun sessionFailureTitle(failure: SessionFailure): String = when (failure) {
        SessionFailure.NETWORK -> appContext.getString(R.string.error_vflow_network_webview_network_error)
        SessionFailure.REDIRECT_LOOP -> appContext.getString(R.string.error_vflow_network_webview_redirect_loop)
        SessionFailure.TIMEOUT -> appContext.getString(R.string.error_vflow_network_webview_timeout)
        SessionFailure.ACTIVITY_LAUNCH_FAILED -> appContext.getString(R.string.error_vflow_network_webview_activity_launch_failed)
        SessionFailure.CANCELLED -> appContext.getString(R.string.error_vflow_network_webview_cancelled)
        SessionFailure.INTERNAL -> appContext.getString(R.string.error_vflow_network_webview_internal_error)
    }

    companion object {
        /** 截止时间之外的宽限：把 JS 收集链的收尾计入模块超时。
         *  Grace beyond the deadline: counts the tail of the JS collection
         *  chain into the module timeout. */
        private const val DEADLINE_GRACE_MS = 7_000L
    }
}
