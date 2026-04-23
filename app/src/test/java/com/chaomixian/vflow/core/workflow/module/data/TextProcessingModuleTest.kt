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

class TextProcessingModuleTest {

    @Test
    fun executeRegex_returnsFullMatchWhenGroupIsZero() = runBlocking {
        val module = TextProcessingModule()
        val context = createContext(
            sourceText = "A123B",
            pattern = "A(.*?)B",
            group = 0
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val output = (result as ExecutionResult.Success).outputs["result_list"] as VList
        assertEquals(listOf("A123B"), output.raw.map { it.asString() })
    }

    @Test
    fun executeRegex_returnsCapturingGroupWhenGroupIsOne() = runBlocking {
        val module = TextProcessingModule()
        val context = createContext(
            sourceText = "A123B",
            pattern = "A(.*?)B",
            group = 1
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val output = (result as ExecutionResult.Success).outputs["result_list"] as VList
        assertEquals(listOf("123"), output.raw.map { it.asString() })
    }

    private fun createContext(
        sourceText: String,
        pattern: String,
        group: Int
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(
                "operation" to VObjectFactory.from(TextProcessingModule.OP_REGEX),
                "source_text" to VObjectFactory.from(sourceText),
                "regex_pattern" to VObjectFactory.from(pattern),
                "regex_group" to VObjectFactory.from(group)
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
