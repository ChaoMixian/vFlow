package com.chaomixian.vflow.core.workflow.module.data

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class ParseXmlModuleTest {

    @Test
    fun execute_returnsAllMatchedNodeValues() = runBlocking {
        val module = ParseXmlModule()
        val context = createContext(
            xml = """
                <root>
                    <item>alpha</item>
                    <item>beta</item>
                </root>
            """.trimIndent(),
            xpath = "//item"
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertEquals("alpha", success.outputs["first_value"])
        val output = success.outputs["all_values"] as VList
        assertEquals(listOf("alpha", "beta"), output.raw.map { it.asString() })
    }

    @Test
    fun execute_returnsScalarXPathResultWhenNoNodesMatched() = runBlocking {
        val module = ParseXmlModule()
        val context = createContext(
            xml = """
                <root>
                    <item />
                    <item />
                </root>
            """.trimIndent(),
            xpath = "count(//item)"
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertEquals("2", success.outputs["first_value"])
        val output = success.outputs["all_values"] as VList
        assertEquals(listOf("2"), output.raw.map { it.asString() })
    }

    @Test
    fun execute_acceptsXmlDeclarationAndRootXPath() = runBlocking {
        val module = ParseXmlModule()
        val context = createContext(
            xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bookstore>
                    <book category="cooking">
                        <title lang="en">Everyday Italian</title>
                        <author>Giada De Laurentiis</author>
                        <year>2005</year>
                        <price>30.00</price>
                    </book>
                    <book category="children">
                        <title lang="en">Harry Potter</title>
                        <author>J.K. Rowling</author>
                        <year>2005</year>
                        <price>29.99</price>
                    </book>
                    <book category="web">
                        <title lang="zh">Learning XML</title>
                        <author>Erik T. Ray</author>
                        <year>2003</year>
                        <price>39.95</price>
                    </book>
                </bookstore>
            """.trimIndent(),
            xpath = "/bookstore"
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertTrue((success.outputs["first_value"] as String).contains("Everyday Italian"))
    }

    @Test
    fun execute_ignoresLeadingBomBeforeXmlDeclaration() = runBlocking {
        val module = ParseXmlModule()
        val context = createContext(
            xml = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookstore><book><title>OK</title></book></bookstore>",
            xpath = "/bookstore"
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertTrue((success.outputs["first_value"] as String).contains("OK"))
    }

    @Test
    fun execute_ignoresXmlDeclarationOnStringInput() = runBlocking {
        val module = ParseXmlModule()
        val context = createContext(
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookstore><book><title>Plain</title></book></bookstore>",
            xpath = "/bookstore"
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertTrue((success.outputs["first_value"] as String).contains("Plain"))
    }

    private fun createContext(xml: String, xpath: String): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(
                "xml" to VObjectFactory.from(xml),
                "xpath" to VObjectFactory.from(xpath)
            ),
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = File("build/test-workdir")
        )
    }
}
