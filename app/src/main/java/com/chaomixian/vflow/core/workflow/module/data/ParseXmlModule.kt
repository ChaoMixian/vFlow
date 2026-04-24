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
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

class ParseXmlModule : BaseModule() {

    override val id = "vflow.data.parse_xml"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_parse_xml_name,
        descriptionStringRes = R.string.module_vflow_data_parse_xml_desc,
        name = "解析 XML",
        description = "从 XML 文本中通过 XPath 提取数据",
        iconRes = R.drawable.rounded_data_object_24,
        category = "数据",
        categoryId = "data"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Parse XML text and extract values with an XPath expression.",
        inputHints = mapOf(
            "xml" to "XML text to parse, usually from an HTTP response, file content, or a variable.",
            "xpath" to "XPath expression such as //item/title/text(), //book/@id, or count(//item)."
        ),
        requiredInputIds = setOf("xml", "xpath")
    )

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("xml")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "xml",
            name = "XML 文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_data_parse_xml_xml_name
        ),
        InputDefinition(
            id = "xpath",
            name = "XPath",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_data_parse_xml_xpath_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "first_value",
            name = "第一个匹配值",
            typeName = VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_data_parse_xml_first_value_name
        ),
        OutputDefinition(
            id = "all_values",
            name = "所有匹配值",
            typeName = VTypeRegistry.LIST.id,
            nameStringRes = R.string.output_vflow_data_parse_xml_all_values_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val rawXml = step.parameters["xml"]?.toString() ?: ""
        val xpathPill = PillUtil.createPillFromParam(
            step.parameters["xpath"],
            inputs.find { it.id == "xpath" }
        )

        if (VariableResolver.isComplex(rawXml)) {
            return PillUtil.buildSpannable(context, "使用", xpathPill, "解析 XML")
        }

        val xmlPill = PillUtil.createPillFromParam(
            step.parameters["xml"],
            inputs.find { it.id == "xml" }
        )
        return PillUtil.buildSpannable(context, "使用", xpathPill, "解析 XML", xmlPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawXml = context.getVariableAsString("xml", "")
        val xml = sanitizeXmlInput(VariableResolver.resolve(rawXml, context))
        val rawXpath = context.getVariableAsString("xpath", "")
        val xpathExpression = VariableResolver.resolve(rawXpath, context)

        if (xml.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_xml_param_error),
                appContext.getString(R.string.error_vflow_data_parse_xml_empty)
            )
        }

        if (xpathExpression.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_xml_param_error),
                appContext.getString(R.string.error_vflow_data_parse_xml_xpath_empty)
            )
        }

        onProgress(ProgressUpdate("正在解析 XML..."))

        return try {
            val document = parseXmlSecurely(xml)
            val xpath = XPathFactory.newInstance().newXPath()
            val matchedValues = extractValues(xpath, document, xpathExpression)

            onProgress(ProgressUpdate("解析完成，找到 ${matchedValues.size} 个匹配项"))

            ExecutionResult.Success(
                mapOf(
                    "first_value" to matchedValues.firstOrNull(),
                    "all_values" to VList(matchedValues.map { com.chaomixian.vflow.core.types.VObjectFactory.from(it) })
                )
            )
        } catch (e: XPathExpressionException) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_xml_xpath_error),
                e.message ?: appContext.getString(R.string.error_vflow_data_parse_xml_xpath_error)
            )
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_parse_xml_parse_error),
                e.message ?: appContext.getString(R.string.error_vflow_data_parse_xml_parse_error)
            )
        }
    }

    private fun extractValues(xpath: javax.xml.xpath.XPath, document: org.w3c.dom.Document, xpathExpression: String): List<String> {
        val nodeList = runCatching {
            xpath.evaluate(xpathExpression, document, XPathConstants.NODESET) as? NodeList
        }.getOrNull()

        if (nodeList != null && nodeList.length > 0) {
            return (0 until nodeList.length).map { index ->
                nodeToValue(nodeList.item(index))
            }
        }

        val scalar = xpath.evaluate(xpathExpression, document)
        return if (scalar.isNullOrEmpty()) emptyList() else listOf(scalar)
    }

    private fun nodeToValue(node: Node?): String {
        if (node == null) return ""
        val rawValue = when (node.nodeType) {
            Node.ATTRIBUTE_NODE,
            Node.TEXT_NODE,
            Node.CDATA_SECTION_NODE,
            Node.COMMENT_NODE,
            Node.PROCESSING_INSTRUCTION_NODE -> node.nodeValue
            else -> node.textContent ?: node.nodeValue
        }
        return rawValue?.trim().orEmpty()
    }

    private fun parseXmlSecurely(xml: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setNamespaceAwareSafely(true)
            setXIncludeAwareSafely(false)
            setExpandEntityReferencesSafely(false)
            setSafeFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setSafeFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setSafeFeature("http://xml.org/sax/features/external-general-entities", false)
            setSafeFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setSafeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }

        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).apply {
            documentElement?.normalize()
        }
    }

    private fun sanitizeXmlInput(xml: String): String {
        return xml
            .removePrefix("\uFEFF")
            .trimStart { it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u2060' || it == '\uFEFF' }
            .replaceFirst(XML_DECLARATION_REGEX, "")
    }

    private fun DocumentBuilderFactory.setSafeFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun DocumentBuilderFactory.setNamespaceAwareSafely(value: Boolean) {
        runCatching { isNamespaceAware = value }
    }

    private fun DocumentBuilderFactory.setXIncludeAwareSafely(value: Boolean) {
        runCatching { isXIncludeAware = value }
    }

    private fun DocumentBuilderFactory.setExpandEntityReferencesSafely(value: Boolean) {
        runCatching { isExpandEntityReferences = value }
    }

    companion object {
        private val XML_DECLARATION_REGEX = Regex("^\\s*<\\?xml\\s+[^>]*\\?>\\s*", RegexOption.IGNORE_CASE)
    }
}
