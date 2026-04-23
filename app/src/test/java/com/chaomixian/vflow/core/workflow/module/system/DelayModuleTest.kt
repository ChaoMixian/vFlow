package com.chaomixian.vflow.core.workflow.module.system

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class DelayModuleTest {

    @Test
    fun validate_acceptsNamedVariableReferenceForDuration() {
        val module = DelayModule()

        val result = module.validate(
            ActionStep(
                moduleId = module.id,
                parameters = mapOf("duration" to "[[delayMs]]")
            ),
            emptyList()
        )

        assertTrue(result.isValid)
    }

    @Test
    fun execute_acceptsPreResolvedNamedVariableDuration() = runBlocking {
        val module = DelayModule()

        val context = ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(
                "duration" to VObjectFactory.from("[[delayMs]]")
            ),
            magicVariables = mutableMapOf(
                "duration" to VNumber(0)
            ),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = mutableMapOf(
                "delayMs" to VNumber(0)
            ),
            workDir = File("build/test-workdir")
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
    }
}
