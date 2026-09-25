// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/htmlxpath/HtmlXPathEngine.kt
// 描述: HTML + XPath 求值引擎。作为 vFlow 内建数据模块的一部分提供。
//
// 行为镜像 vFlow 内建的 XML 解析模块 (vflow.data.parse_xml)：
//  - 输入先被净化（BOM / 零宽字符 / XML 声明）；
//  - XPath 表达式先按 NODESET 求值；每个匹配节点都通过与内建模块相同的
//    nodeToValue 映射转换为字符串（属性 / 文本 / CDATA / 注释 / PI ->
//    nodeValue，其余 -> textContent，再 trim）；
//  - 节点集为空时按标量求值；
//  - 空匹配不是错误：first_value 为 null、all_values 为空列表；
//  - XPathExpressionException 报告为 XPath 错误，其余报告为解析错误。
//
// HTML 需要的差异：解析使用 jsoup（宽松恢复畸形标记），元素匹配额外以
// 结构化字典暴露（标签名、文本、属性与 jsoup 序列化的 HTML，绝不是
// DOM 对象）。
//
// File: main/java/com/chaomixian/vflow/core/workflow/module/data/htmlxpath/HtmlXPathEngine.kt
// Description: The HTML + XPath evaluation engine, provided as part of
//      vFlow's built-in data modules.
//
// Behavior mirrors vFlow's built-in XML Parse module
// (vflow.data.parse_xml):
//  - the input is sanitized first (BOM / zero-width characters / XML
//    declaration);
//  - the XPath expression is evaluated as a NODESET first; every matched
//    node is stringified through the same nodeToValue mapping as the
//    built-in module (attribute / text / CDATA / comment / PI ->
//    nodeValue, otherwise -> textContent, then trim);
//  - an empty node set falls back to scalar evaluation;
//  - an empty match is not an error: first_value is null and all_values
//    is an empty list;
//  - XPathExpressionException is reported as an XPath error, everything
//    else as a parse error.
//
// What HTML needs differently: parsing uses jsoup (lenient recovery of
// malformed markup), and element matches are additionally exposed as
// structured dictionaries (tag name, text, attributes and the jsoup-
// serialized HTML — never DOM objects).

package com.chaomixian.vflow.core.workflow.module.data.htmlxpath

import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

/**
 * HTML + XPath 求值引擎（见文件头说明）。
 *
 * The HTML + XPath evaluation engine (see the file header).
 */
object HtmlXPathEngine {

    private val xpathFactory = XPathFactory.newInstance()

    /**
     * 一次成功求值的结果。
     *
     * The result of one successful evaluation.
     */
    data class Evaluation(
        /** 所有匹配节点 / 标量的字符串值，按文档顺序。
         *  String values of all matched nodes / the scalar, in document
         *  order. */
        val values: List<String>,
        /** 每个匹配的 *元素* 节点的结构化信息，按文档顺序。
         *  Structured info of every matched *element* node, in document
         *  order. */
        val elements: List<Map<String, Any?>>,
        /** 标量结果的类型化视图（String / Long / Double / Boolean），非标量时为 null。
         *  Typed view of the scalar result (String / Long / Double /
         *  Boolean); null when the result is not a scalar. */
        val scalarValue: Any?,
        /** 匹配节点数（标量结果为 1，无匹配为 0）。
         *  Number of matched nodes (1 for a scalar result, 0 for no
         *  match). */
        val matchCount: Int
    ) {
        val firstValue: String? get() = values.firstOrNull()
        val firstElement: Map<String, Any?>? get() = elements.firstOrNull()
    }

    /** 对 [html] 求值 [xpathExpression]。输入无效时抛出异常。
     *  Evaluates [xpathExpression] against [html]. Throws on invalid
     *  input. */
    fun evaluate(html: String, xpathExpression: String): Evaluation {
        val sanitizedHtml = sanitizeHtmlInput(html)
        val jsoupDocument = Jsoup.parse(sanitizedHtml)
        val document = W3CDom().fromJsoup(jsoupDocument)
        // jsoup 的 W3CDom 会给元素赋予 XHTML 命名空间，而 XPath 1.0 只把
        // 无前缀的名称匹配到“无命名空间”。形如 //div[@id='x'] 的普通 HTML
        // 表达式必须可用，因此剥离命名空间（实践中 HTML 并不按命名空间驱动）。
        // jsoup's W3CDom assigns elements an XHTML namespace while XPath 1.0
        // matches unprefixed names only against the "no namespace". Plain
        // HTML expressions such as //div[@id='x'] must work, so the
        // namespaces are stripped (in practice HTML is not namespace
        // driven).
        stripNamespaces(document)
        val xpath = xpathFactory.newXPath()

        val nodeSet = runCatching {
            xpath.evaluate(xpathExpression, document, XPathConstants.NODESET) as? org.w3c.dom.NodeList
        }.getOrNull()

        if (nodeSet != null && nodeSet.length > 0) {
            val jsoupIndex = buildElementIndex(jsoupDocument, document)
            val values = ArrayList<String>(nodeSet.length)
            val elements = ArrayList<Map<String, Any?>>(nodeSet.length)
            for (i in 0 until nodeSet.length) {
                val node = nodeSet.item(i)
                values.add(nodeToValue(node))
                val info = elementInfo(node, jsoupIndex)
                if (info != null) elements.add(info)
            }
            return Evaluation(
                values = values,
                elements = elements,
                scalarValue = null,
                matchCount = nodeSet.length
            )
        }

        // 标量回退（count(...)、string(...)、布尔表达式等）。
        // Scalar fallback (count(...), string(...), boolean expressions,
        // etc.).
        val scalar = xpath.evaluate(xpathExpression, document)
        if (scalar.isNullOrEmpty()) {
            return Evaluation(values = emptyList(), elements = emptyList(), scalarValue = null, matchCount = 0)
        }
        return Evaluation(
            values = listOf(scalar),
            elements = emptyList(),
            scalarValue = typedScalar(scalar),
            matchCount = 1
        )
    }

    /**
     * 用 jsoup 宽松地解析 HTML 并转换为 W3C DOM，使标准的
     * javax.xml.xpath 引擎（与内建 XML 解析模块相同的引擎）可以求值；
     * 元素命名空间被剥离以匹配普通 HTML 表达式。
     *
     * Leniently parses HTML with jsoup and converts it to a W3C DOM so
     * the standard javax.xml.xpath engine (the same engine the built-in
     * XML Parse module uses) can evaluate it; element namespaces are
     * stripped to match plain HTML expressions.
     */
    internal fun parseHtml(html: String): Document =
        W3CDom().fromJsoup(Jsoup.parse(html)).also { stripNamespaces(it) }

    /**
     * 把每个元素移出其命名空间（重命名为无命名空间、保留本地名）。
     * 节点身份保持不变，因此此前或此后构建的映射仍然有效。
     *
     * Moves every element out of its namespace (renamed to no-namespace,
     * local name kept). Node identity is preserved, so mappings built
     * before or after remain valid.
     */
    internal fun stripNamespaces(document: Document) {
        val elements = document.getElementsByTagName("*")
        val nodes = ArrayList<Node>(elements.length)
        for (i in 0 until elements.length) nodes.add(elements.item(i))
        for (node in nodes) {
            if (node.namespaceURI != null) {
                runCatching { document.renameNode(node, null, node.nodeName) }
            }
        }
    }

    /**
     * 与内建 XML 解析模块相同的 nodeToValue 转换，
     * 保持 XPath 行为与参考模块一致。
     *
     * The same nodeToValue conversion as the built-in XML Parse module,
     * keeping XPath behavior consistent with the reference module.
     */
    internal fun nodeToValue(node: Node?): String {
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

    /**
     * 把匹配到的 W3C *元素* 节点映射为可序列化的字典。
     * 属性与文本匹配是纯值结果，返回 null。
     *
     * Maps a matched W3C *element* node to a serializable dictionary.
     * Attribute and text matches are plain value results and return
     * null.
     */
    internal fun elementInfo(node: Node, index: ElementIndex?): Map<String, Any?>? {
        if (node.nodeType != Node.ELEMENT_NODE) return null
        val element = node as? Element ?: return null
        val jsoupElement = index?.lookup(element)
        val attributes = LinkedHashMap<String, Any?>()
        val namedNodeMap = element.attributes
        for (i in 0 until namedNodeMap.length) {
            val attr = namedNodeMap.item(i)
            if (!attr.nodeName.startsWith("xmlns")) {
                attributes[attr.nodeName] = attr.nodeValue
            }
        }
        // jsoup 的 text() 规范化空白并跳过注释；DOM 回退手动规范化空白
        // （textContent 保留注释）。
        // jsoup's text() normalizes whitespace and skips comments; the DOM
        // fallback normalizes whitespace manually (textContent keeps
        // comments).
        val text = jsoupElement?.text()
            ?: (element.textContent ?: "").replace(Regex("\\s+"), " ").trim()
        val serialized = jsoupElement?.outerHtml()
            ?: serializeDomElement(element)
        return linkedMapOf(
            "tag_name" to element.nodeName.lowercase(),
            "text" to text,
            "html" to serialized,
            "attributes" to attributes
        )
    }

    /** 用标准转换器序列化 DOM 元素（回退路径）。
     *  Serializes a DOM element with the standard transformer (fallback
     *  path). */
    private fun serializeDomElement(element: Element): String = try {
        val sw = java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance()
            .newTransformer()
            .transform(javax.xml.transform.dom.DOMSource(element), javax.xml.transform.stream.StreamResult(sw))
        sw.toString()
    } catch (_: Exception) {
        element.textContent?.trim().orEmpty()
    }

    /**
     * 关联 W3C 元素与 jsoup 元素，使 `html` 字段携带真实的 HTML 序列化。
     * 两棵树按文档顺序遍历；jsoup 的 document 节点本身被跳过。
     *
     * Correlates W3C elements with jsoup elements so the `html` field
     * carries a real HTML serialization. Both trees are walked in
     * document order; jsoup's document node itself is skipped.
     */
    internal fun buildElementIndex(
        jsoupDocument: org.jsoup.nodes.Document,
        w3cDocument: Document
    ): ElementIndex {
        return try {
            val jsoupElements = jsoupDocument.allElements.drop(1) // 跳过 document 节点 / skip the document node
            val w3cElements = w3cDocument.getElementsByTagName("*")
            if (jsoupElements.size != w3cElements.length) {
                return ElementIndex(emptyMap())
            }
            val map = HashMap<Node, org.jsoup.nodes.Element>(jsoupElements.size)
            for (i in 0 until w3cElements.length) {
                map[w3cElements.item(i)] = jsoupElements[i]
            }
            ElementIndex(map)
        } catch (_: Exception) {
            ElementIndex(emptyMap())
        }
    }

    class ElementIndex internal constructor(private val map: Map<Node, org.jsoup.nodes.Element>) {
        internal fun lookup(node: Node): org.jsoup.nodes.Element? = map[node]
    }

    /**
     * 标量 XPath 结果的类型化："true"/"false" 转换为布尔，数字字符串转换
     * 为数字（整数值保持整数，使工作流收到 JSON 整数）。仅作用于标量结果
     * —— 节点值总是字符串，与内建 XML 解析模块完全一致。
     *
     * Typing of scalar XPath results: "true"/"false" become booleans and
     * numeric strings become numbers (integral values stay integral so
     * the workflow receives JSON integers). Applies to scalar results
     * only — node values are always strings, exactly like the built-in
     * XML Parse module.
     */
    internal fun typedScalar(scalar: String): Any = when {
        scalar == "true" -> true
        scalar == "false" -> false
        NUMBER_PATTERN.matches(scalar) -> {
            val d = scalar.toDouble()
            if (d.isFinite() && d == Math.floor(d) && Math.abs(d) <= 9.007199254740992E15) d.toLong() else d
        }
        else -> scalar
    }

    /** 与 ParseXmlModule.sanitizeXmlInput 相同的净化。
     *  The same sanitization as ParseXmlModule.sanitizeXmlInput. */
    internal fun sanitizeHtmlInput(html: String): String {
        return html
            .removePrefix("\uFEFF")
            .trimStart { it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u2060' || it == '\uFEFF' }
            .replaceFirst(XML_DECLARATION_REGEX, "")
    }

    private val NUMBER_PATTERN = Regex("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$")

    private val XML_DECLARATION_REGEX = Regex("^\\s*<\\?xml\\s+[^>]*\\?>\\s*", RegexOption.IGNORE_CASE)

    /** 区分 XPath 错误与解析错误，与内建模块一致。
     *  Distinguishes XPath errors from parse errors, like the built-in
     *  module. */
    fun classifyError(e: Exception): ErrorKind =
        if (e is XPathExpressionException) ErrorKind.XPATH else ErrorKind.PARSE

    enum class ErrorKind { XPATH, PARSE }
}
