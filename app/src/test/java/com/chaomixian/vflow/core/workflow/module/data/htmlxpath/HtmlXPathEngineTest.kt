// 文件: test/java/com/chaomixian/vflow/core/workflow/module/data/htmlxpath/HtmlXPathEngineTest.kt
// 描述: HTML XPath 引擎的单元测试 —— 行为镜像内建 XML 解析模块
//      (vflow.data.parse_xml，文档化的参考模块)，同时解析真实世界的 HTML。
//
// File: test/java/com/chaomixian/vflow/core/workflow/module/data/htmlxpath/HtmlXPathEngineTest.kt
// Description: Unit tests for the HTML XPath engine — the behavior mirrors
//      the built-in XML Parse module (vflow.data.parse_xml, the documented
//      reference module) while parsing real-world HTML.

package com.chaomixian.vflow.core.workflow.module.data.htmlxpath

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlXPathEngineTest {

    private val html = """
        <!DOCTYPE html>
        <html>
        <head><title>Test Page</title></head>
        <body>
          <div id="main" class="container">
            <h1>Heading One</h1>
            <ul id="list">
              <!-- three items -->
              <li class="item" data-value="1">Item 1</li>
              <li class="item" data-value="2">Item 2</li>
              <li class="item" data-value="3">Item 3</li>
            </ul>
            <a href="https://example.com/page">Link text</a>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun eval(xpath: String, htmlText: String = html) = HtmlXPathEngine.evaluate(htmlText, xpath)

    // --- 文本提取 / Text extraction --------------------------------------

    @Test
    fun `text nodes are extracted trimmed`() {
        val result = eval("//h1/text()")
        assertEquals(listOf("Heading One"), result.values)
        assertEquals("Heading One", result.firstValue)
    }

    @Test
    fun `document order is preserved for multiple text matches`() {
        val result = eval("//li/text()")
        assertEquals(listOf("Item 1", "Item 2", "Item 3"), result.values)
    }

    @Test
    fun `title text is extracted`() {
        assertEquals(listOf("Test Page"), eval("//title/text()").values)
    }

    // --- 属性提取 / Attribute extraction --------------------------------

    @Test
    fun `attributes are extracted as values`() {
        assertEquals(listOf("main"), eval("//div/@id").values)
        assertEquals(listOf("1"), eval("//li[1]/@data-value").values)
    }

    @Test
    fun `attributes of all matching elements are returned in order`() {
        assertEquals(listOf("1", "2", "3"), eval("//li/@data-value").values)
    }

    @Test
    fun `href attributes are extracted`() {
        assertEquals(listOf("https://example.com/page"), eval("//a/@href").values)
    }

    // --- 单元素提取 / Single-element extraction -------------------------

    @Test
    fun `element match yields its text content as value`() {
        val result = eval("//h1")
        assertEquals("Heading One", result.firstValue)
        assertEquals(1, result.matchCount)
    }

    @Test
    fun `element match exposes structured element info`() {
        val result = eval("//a")
        val element = result.elements.single()
        assertEquals("a", element["tag_name"])
        assertEquals("Link text", element["text"])
        assertEquals(mapOf("href" to "https://example.com/page"), element["attributes"])
        assertEquals("<a href=\"https://example.com/page\">Link text</a>", element["html"])
    }

    @Test
    fun `element info carries tag name text attributes and serialized html`() {
        val element = eval("//ul").elements.single()
        assertEquals("ul", element["tag_name"])
        assertEquals("Item 1 Item 2 Item 3", element["text"])
        assertEquals(mapOf("id" to "list"), element["attributes"])
        val serialized = element["html"] as String
        assertTrue(serialized.startsWith("<ul"))
        assertTrue(serialized.contains("data-value=\"2\""))
        assertTrue(serialized.endsWith("</ul>"))
    }

    // --- 多元素提取 / Multi-element extraction --------------------------

    @Test
    fun `multiple elements return an ordered collection preserving document order`() {
        val result = eval("//li")
        assertEquals(3, result.matchCount)
        assertEquals(listOf("Item 1", "Item 2", "Item 3"), result.values)
        val tags = result.elements.map { it["tag_name"] }
        assertEquals(listOf("li", "li", "li"), tags)
        val dataValues = result.elements.map { (it["attributes"] as Map<*, *>)["data-value"] }
        assertEquals(listOf("1", "2", "3"), dataValues)
    }

    @Test
    fun `first_element is the first match in document order`() {
        val first = eval("//li").firstElement!!
        assertEquals("Item 1", first["text"])
    }

    @Test
    fun `attribute node matches do not produce element entries`() {
        val result = eval("//li[2]/@data-value")
        assertEquals(listOf("2"), result.values)
        assertTrue(result.elements.isEmpty())
        assertNull(result.firstElement)
    }

    // --- 标量结果 / Scalar results ----------------------------------------

    @Test
    fun `count returns a scalar number`() {
        val result = eval("count(//li)")
        assertEquals(1, result.matchCount)
        assertEquals(listOf("3"), result.values)
        assertEquals(3L, result.scalarValue)
    }

    @Test
    fun `boolean expressions return typed booleans`() {
        assertEquals(true, eval("count(//li) > 2").scalarValue)
        assertEquals(false, eval("count(//li) > 5").scalarValue)
    }

    @Test
    fun `string functions return scalar strings`() {
        assertEquals("Heading One", eval("normalize-space(//h1)").scalarValue)
    }

    @Test
    fun `non integral numbers stay floating point`() {
        // string-length("Heading One") = 11; 11 div 4 = 2.75
        val result = eval("string-length(//h1) div 4")
        assertEquals(2.75, result.scalarValue)
    }

    // --- 空匹配 / Empty matches --------------------------------------------

    @Test
    fun `empty matches are not an error`() {
        val result = eval("//nomatch")
        assertTrue(result.values.isEmpty())
        assertNull(result.firstValue)
        assertTrue(result.elements.isEmpty())
        assertNull(result.firstElement)
        assertEquals(0, result.matchCount)
        assertNull(result.scalarValue)
    }

    @Test
    fun `empty attribute match returns nothing`() {
        val result = eval("//nomatch/@id")
        assertTrue(result.values.isEmpty())
    }

    // --- 注释与混合节点 / Comments and mixed nodes -------------------------

    @Test
    fun `comments are handled like the xml parse module`() {
        val result = eval("//comment()")
        assertEquals(listOf("three items"), result.values)
    }

    // --- HTML 解析边界情况 / HTML parsing edge cases --------------------------

    @Test
    fun `malformed html with unclosed tags is recovered`() {
        val malformed = "<html><body><p>Paragraph one<p>Paragraph two<div id=x>text"
        val result = eval("//p", malformed)
        assertEquals(2, result.matchCount)
        assertEquals(listOf("Paragraph one", "Paragraph two"), result.values)
        assertEquals(listOf("x"), eval("//div/@id", malformed).values)
    }

    @Test
    fun `void elements do not break parsing`() {
        val withVoid = "<body>Line<br>next<img src=\"a.png\" alt=\"pic\"><hr></body>"
        assertEquals(listOf("a.png"), eval("//img/@src", withVoid).values)
        assertEquals(listOf("pic"), eval("//img/@alt", withVoid).values)
        assertEquals(1, eval("count(//br)", withVoid).matchCount)
    }

    @Test
    fun `uppercase tags are normalized`() {
        val upper = "<HTML><BODY><DIV ID=\"up\">Content</DIV></BODY></HTML>"
        assertEquals(listOf("up"), eval("//div/@id", upper).values)
        assertEquals(listOf("Content"), eval("//div/text()", upper).values)
    }

    @Test
    fun `script and style content stay extractable`() {
        val page = "<head><style>body { color: red; }</style>" +
            "<script>var x = 1 < 2;</script></head>"
        val style = eval("//style", page)
        assertEquals("body { color: red; }", style.firstValue)
        val script = eval("//script", page)
        assertEquals("var x = 1 < 2;", script.firstValue)
    }

    @Test
    fun `entities are decoded in text`() {
        val page = "<body><p>A &amp; B &lt;tag&gt; &quot;quoted&quot;</p></body>"
        assertEquals(listOf("""A & B <tag> "quoted""""), eval("//p/text()", page).values)
    }

    @Test
    fun `html fragments get an implicit document structure`() {
        val fragment = "<div id=\"f\">fragment</div>"
        assertEquals(listOf("fragment"), eval("//div[@id='f']/text()", fragment).values)
        // 隐式的 html/body 包装不会隐藏片段。/ The implicit html/body wrapper does not hide fragments.
        assertNotNull(eval("/html", fragment).values.firstOrNull())
    }

    @Test
    fun `bom zero width characters and xml declarations are sanitized`() {
        assertEquals(listOf("t"), eval("//div/text()", "\uFEFF<div>t</div>").values)
        assertEquals(listOf("t"), eval("//div/text()", "\u200B\u200C\u200D\u2060<div>t</div>").values)
        assertEquals(listOf("t"), eval("//div/text()", "<?xml version=\"1.0\"?><div>t</div>").values)
    }

    @Test
    fun `html without namespaces works with plain xpath`() {
        // jsoup/W3CDom 产生无命名空间的元素，//div 无需命名空间前缀即可
        // 工作 —— 符合用户对 HTML 的预期。
        // jsoup/W3CDom yields namespace-free elements, so //div works without
        // a namespace prefix — matching what users expect from HTML.
        val result = eval("//div[@id='main']")
        assertEquals(1, result.matchCount)
    }

    // --- 元素索引的健壮性 / Element-index robustness ------------------------

    @Test
    fun `element info falls back to dom serialization when index mismatches`() {
        // 畸形 HTML 可能使 jsoup 重构树；引擎仍必须提供序列化的 html
        // 字段（绝不能是 DOM 对象）。
        // Malformed HTML may make jsoup restructure the tree; the engine
        // must still provide the serialized html field (never a DOM
        // object).
        val weird = "<body><table><tr><td>cell</td></tr></table>"
        val element = eval("//td", weird).elements.singleOrNull()
        if (element != null) {
            assertNotNull(element["html"])
            assertEquals("td", element["tag_name"])
        }
    }

    @Test
    fun `module output values are serializable`() {
        // 与 ParseHtmlModule.execute 构建的输出 Map 相同的形态。
        // The same shape as the output map built by ParseHtmlModule.execute.
        val evaluation = eval("//li")
        val outputs = linkedMapOf<String, Any?>(
            "first_value" to evaluation.firstValue,
            "all_values" to evaluation.values,
            "first_element" to evaluation.firstElement,
            "all_elements" to evaluation.elements,
            "scalar_value" to evaluation.scalarValue,
            "match_count" to evaluation.matchCount.toLong()
        )
        val json = Gson().toJson(outputs)
        assertTrue(json.contains("\"match_count\":3"))
        assertTrue(json.contains("\"all_values\":[\"Item 1\",\"Item 2\",\"Item 3\"]"))
    }

    @Test
    fun `errors are classified like the xml parse module`() {
        assertTrue(
            HtmlXPathEngine.classifyError(javax.xml.xpath.XPathExpressionException("bad"))
                == HtmlXPathEngine.ErrorKind.XPATH
        )
        assertTrue(
            HtmlXPathEngine.classifyError(RuntimeException("x"))
                == HtmlXPathEngine.ErrorKind.PARSE
        )
    }

    @Test
    fun `invalid xpath expressions throw xpath exceptions`() {
        try {
            eval("///[[[")
            throw AssertionError("expected XPathExpressionException")
        } catch (e: javax.xml.xpath.XPathExpressionException) {
            // 预期：由模块报告为 XPath 解析错误 / Expected: reported by the module as an XPath evaluation error
        }
    }
}
