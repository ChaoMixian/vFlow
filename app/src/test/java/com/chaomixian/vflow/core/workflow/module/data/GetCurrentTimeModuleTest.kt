package com.chaomixian.vflow.core.workflow.module.data

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VNumber
import java.io.File
import java.util.Stack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCurrentTimeModuleTest {

    @Test
    fun execute_exposesTimestampSecondsOutput() = runBlocking {
        val module = GetCurrentTimeModule()
        val context = ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(
                "format" to VObjectFactory.from("timestamp_seconds"),
                "timezone" to VObjectFactory.from("")
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

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val outputs = (result as ExecutionResult.Success).outputs
        val timestampMillis = (outputs["timestamp"] as VNumber).raw.toLong()
        val timestampSeconds = (outputs["timestamp_seconds"] as VNumber).raw.toLong()
        val timeString = (outputs["time"] as VString).raw

        assertEquals(timestampMillis / 1000, timestampSeconds)
        assertEquals(timestampSeconds.toString(), timeString)
    }
}
