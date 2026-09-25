package com.chaomixian.vflow.core.workflow.module.data

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
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.module.data.htmlxpath.HtmlXPathEngine
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import javax.xml.xpath.XPathExpressionException

// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/ParseHtmlModule.kt
// 描述: 解析 HTML (XPath) 模块。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/data/ParseHtmlModule.kt
// Description: The Parse HTML (XPath) module.

/**
 * 解析 HTML (XPath) 模块。
 *
 * 以内建 XML 解析模块 (vflow.data.parse_xml) —— 文档化的结构、输入输出、
 * XPath 行为、错误处理与工作流集成参考 —— 为蓝本实现，但解析的是
 * HTML 而不是严格 XML，并在参考输出契约之上暴露结构化的元素结果。
 * 典型管道：打开网页 (WebView) 模块的 response_body -> 本模块的 html 输入。
 *
 * The Parse HTML (XPath) module.
 *
 * Modeled on the built-in XML Parse module (vflow.data.parse_xml) — its
 * documented structure, inputs/outputs, XPath behavior, error handling and
 * workflow integration — but parsing HTML instead of strict XML, and
 * exposing structured element results on top of the reference output
 * contract. Typical pipeline: the Open Web Page (WebView) module's
 * response_body -> this module's html input.
 */
class ParseHtmlModule : BaseModule() {

    override val id = "vflow.data.parse_html"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_parse_html_name,
        descriptionStringRes = R.string.module_vflow_data_parse_html_desc,
        name = "解析 HTML (XPath)",  // Fallback / 备用名称
        description = "从 HTML 文本中通过 XPath 提取数据",  // Fallback / 备用描述
        iconRes = R.drawable.rounded_html_24,
        category = "数据",
        categoryId = "data"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Parse HTML text and extract values with an XPath expression.",
        inputHints = mapOf(
            "html" to "HTML text to parse, usually the response_body of the WebView or HTTP module, file content, or a variable.",
            "xpath" to "XPath expression such as //li/text(), //a/@href, //div[@id='main'], or count(//item)."
        ),
        requiredInputIds = setOf("html", "xpath")
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "html",
            name = "HTML 文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_data_parse_html_html_name
        ),
        InputDefinition(
            id = "xpath",
            name = "XPath",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_data_parse_html_xpath_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "first_value",
            name = "第一个匹配值",
            typeName = VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_data_parse_html_first_value_name
        ),
        OutputDefinition(
            id = "all_values",
            name = "所有匹配值",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_data_parse_html_all_values_name
        ),
        OutputDefinition(
            id = "first_element",
            name = "第一个匹配元素",
            typeName = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_data_parse_html_first_element_name
        ),
        OutputDefinition(
            id = "all_elements",
            name = "所有匹配元素",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_data_parse_html_all_elements_name
        ),
        OutputDefinition(
            id = "scalar_value",
            name = "标量结果",
            typeName = VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_data_parse_html_scalar_value_name
        ),
        OutputDefinition(
            id = "match_count",
            name = "匹配数量",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_data_parse_html_match_count_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val rawHtml = step.parameters["html"]?.toString() ?: ""
        val xpathPill = PillUtil.createPillFromParam(
            step.parameters["xpath"],
            inputs.find { it.id == "xpath" }
        )

        if (VariableResolver.isComplex(rawHtml)) {
            return PillUtil.buildSpannable(context, "使用", xpathPill, "解析 HTML", PillUtil.richTextPreview(rawHtml))
        }

        val htmlPill = PillUtil.createPillFromParam(
            step.parameters["html"],
            inputs.find { it.id == "html" }
        )
        return PillUtil.buildSpannable(context, "使用", xpathPill, "解析 HTML", htmlPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawHtml = context.getVariableAsString("html", "")
        val html = HtmlXPathEngine.sanitizeHtmlInput(VariableResolver.resolve(rawHtml, context))
        val rawXpath = context.getVariableAsString("xpath", "")
        val xpathExpression = VariableResolver.resolve(rawXpath, context)

        if (html.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_html_param_error),
                appContext.getString(R.string.error_vflow_data_parse_html_empty)
            )
        }

        if (xpathExpression.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_html_param_error),
                appContext.getString(R.string.error_vflow_data_parse_html_xpath_empty)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_data_parse_html_parsing)))

        return try {
            val evaluation = HtmlXPathEngine.evaluate(html, xpathExpression)

            onProgress(ProgressUpdate(
                appContext.getString(R.string.msg_vflow_data_parse_html_done, evaluation.matchCount)
            ))

            ExecutionResult.Success(
                mapOf(
                    "first_value" to evaluation.firstValue,
                    "all_values" to evaluation.values,
                    "first_element" to evaluation.firstElement,
                    "all_elements" to evaluation.elements,
                    "scalar_value" to evaluation.scalarValue,
                    "match_count" to evaluation.matchCount.toLong()
                )
            )
        } catch (e: XPathExpressionException) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_html_xpath_error),
                e.message ?: appContext.getString(R.string.error_vflow_data_parse_html_xpath_error)
            )
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_html_parse_error),
                e.message ?: appContext.getString(R.string.error_vflow_data_parse_html_parse_error)
            )
        }
    }
}
