package com.chaomixian.vflow.core.workflow.module.data

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class CreateVariableModuleTest {

    @Test
    fun execute_resolvesNestedCoordinateVariableReferences() = runBlocking {
        val module = CreateVariableModule()
        val context = ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(
                "type" to VObjectFactory.from(CreateVariableModule.TYPE_COORDINATE),
                "variableName" to VObjectFactory.from("点击坐标"),
                "value" to VObjectFactory.from(
                    mapOf(
                        "x" to "{{randomX.randomVariable.int}}",
                        "y" to "{{textY.variable}}"
                    )
                )
            ),
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(
                "randomX" to mapOf("randomVariable" to VNumber(999)),
                "textY" to mapOf("variable" to VString("1101"))
            ),
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = File("build/test-workdir")
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)

        val coordinate = (result as ExecutionResult.Success).outputs["variable"] as VCoordinate
        assertEquals(VCoordinate(999, 1101), coordinate)
        assertEquals(VCoordinate(999, 1101), context.namedVariables["点击坐标"])
    }

    @Test
    fun execute_preservesTypedDictionaryInput() = runBlocking {
        val module = CreateVariableModule()
        val context = createContext(
            variables = mutableMapOf(
                "type" to VObjectFactory.from(CreateVariableModule.TYPE_DICTIONARY),
                "value" to VObjectFactory.from(
                    mapOf(
                        "name" to "demo",
                        "count" to 3
                    )
                )
            )
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val dictionary = (result as ExecutionResult.Success).outputs["variable"] as? VDictionary
        assertNotNull(dictionary)
        assertEquals("demo", dictionary?.raw?.get("name")?.asString())
        assertEquals(3.0, dictionary?.raw?.get("count")?.asNumber())
    }

    @Test
    fun execute_resolvesCoordinateFromTypedListInput() = runBlocking {
        val module = CreateVariableModule()
        val context = createContext(
            variables = mutableMapOf(
                "type" to VObjectFactory.from(CreateVariableModule.TYPE_COORDINATE),
                "value" to VObjectFactory.from(
                    listOf("{{randomX.randomVariable.int}}", 256)
                )
            ),
            stepOutputs = mutableMapOf(
                "randomX" to mapOf("randomVariable" to VNumber(128))
            )
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val coordinate = (result as ExecutionResult.Success).outputs["variable"] as? VCoordinate
        assertEquals(VCoordinate(128, 256), coordinate)
    }

    @Test
    fun execute_preservesTypedListInput() = runBlocking {
        val module = CreateVariableModule()
        val context = createContext(
            variables = mutableMapOf(
                "type" to VObjectFactory.from(CreateVariableModule.TYPE_LIST),
                "value" to VObjectFactory.from(listOf("a", 2, true))
            )
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val list = (result as ExecutionResult.Success).outputs["variable"] as? VList
        assertNotNull(list)
        assertEquals(listOf("a", "2", "true"), list?.raw?.map { it.asString() })
    }

    private fun createContext(
        variables: MutableMap<String, com.chaomixian.vflow.core.types.VObject>,
        stepOutputs: MutableMap<String, Map<String, com.chaomixian.vflow.core.types.VObject>> = mutableMapOf()
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = variables,
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = stepOutputs,
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = File("build/test-workdir")
        )
    }
}
