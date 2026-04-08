package com.chaomixian.vflow.core.workflow.module.data

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
