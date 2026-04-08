package com.chaomixian.vflow.core.workflow.module.network

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.Stack

class HttpRequestModuleTest {

    @Test
    fun `normalizeBodyType maps legacy and internal values`() {
        assertEquals("json", HttpRequestModule.normalizeBodyType("json"))
        assertEquals("json", HttpRequestModule.normalizeBodyType("JSON"))
        assertEquals("form", HttpRequestModule.normalizeBodyType("表单"))
        assertEquals("raw", HttpRequestModule.normalizeBodyType("Raw Text"))
        assertEquals("file", HttpRequestModule.normalizeBodyType("文件"))
        assertEquals("none", HttpRequestModule.normalizeBodyType(null))
    }

    @Test
    fun `createRequestBody builds json body for internal and legacy body types`() {
        val module = HttpRequestModule()
        val context = createExecutionContext()
        val bodyData = linkedMapOf("message" to "hello")

        val internalBody = module.invokeCreateRequestBody(context, "json", bodyData)
        val legacyBody = module.invokeCreateRequestBody(context, "JSON", bodyData)

        assertJsonRequestBody(internalBody)
        assertJsonRequestBody(legacyBody)
    }

    private fun createExecutionContext(): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(),
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

    private fun HttpRequestModule.invokeCreateRequestBody(
        context: ExecutionContext,
        bodyType: String,
        bodyData: Any?
    ): RequestBody? {
        val method = javaClass.getDeclaredMethod(
            "createRequestBody",
            ExecutionContext::class.java,
            String::class.java,
            Any::class.java
        )
        method.isAccessible = true
        return method.invoke(this, context, bodyType, bodyData) as RequestBody?
    }

    private fun assertJsonRequestBody(body: RequestBody?) {
        assertNotNull(body)
        assertEquals("application/json; charset=utf-8", body?.contentType().toString())

        val buffer = Buffer()
        body!!.writeTo(buffer)
        assertEquals("{\"message\":\"hello\"}", buffer.readUtf8())
    }
}
