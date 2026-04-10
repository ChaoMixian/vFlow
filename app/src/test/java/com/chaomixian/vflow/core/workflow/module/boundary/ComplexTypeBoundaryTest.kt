package com.chaomixian.vflow.core.workflow.module.boundary

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.types.complex.VUiComponent
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.NotificationTriggerModule
import com.chaomixian.vflow.core.workflow.module.ui.blocks.GetComponentValueModule
import com.chaomixian.vflow.core.workflow.module.ui.blocks.KEY_UI_ELEMENTS_LIST
import com.chaomixian.vflow.core.workflow.module.ui.blocks.KEY_CURRENT_EVENT
import com.chaomixian.vflow.core.workflow.module.ui.blocks.KEY_UI_SESSION_ID
import com.chaomixian.vflow.core.workflow.module.ui.blocks.OnUiEventModule
import com.chaomixian.vflow.core.workflow.module.ui.blocks.UpdateUiComponentModule
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class ComplexTypeBoundaryTest {

    @Test
    fun `notification trigger emits VNotification output`() = runBlocking {
        val module = NotificationTriggerModule()
        val triggerData = VDictionary(
            mapOf(
                "id" to VString("notif-1"),
                "package_name" to VString("pkg.demo"),
                "title" to VString("Demo"),
                "content" to VString("Body")
            )
        )
        val context = createContext(triggerData = triggerData)

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        val notification = success.outputs["notification_object"] as? VNotification
        assertTrue(notification != null)
        assertEquals("notif-1", notification?.notification?.id)
        assertEquals("pkg.demo", notification?.notification?.packageName)
    }

    @Test
    fun `get component value emits VUiComponent output`() = runBlocking {
        val module = GetComponentValueModule()
        val element = UiElement(
            id = "btn_submit",
            type = UiElementType.BUTTON,
            label = "Submit",
            defaultValue = "",
            placeholder = "",
            isRequired = false
        )
        val context = createContext(
            magicVariables = mutableMapOf("component_id" to VString("btn_submit")),
            namedVariables = mutableMapOf(
                KEY_UI_ELEMENTS_LIST to VObjectFactory.from(listOf(element))
            )
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        val component = success.outputs["component"] as? VUiComponent
        assertTrue(component != null)
        assertEquals("btn_submit", component?.element?.id)
    }

    @Test
    fun `on ui event accepts VUiComponent target input`() = runBlocking {
        val module = OnUiEventModule()
        val element = UiElement(
            id = "btn_submit",
            type = UiElementType.BUTTON,
            label = "Submit",
            defaultValue = "",
            placeholder = "",
            isRequired = false
        )
        val event = UiEvent(
            sessionId = "session-1",
            elementId = "btn_submit",
            type = "click",
            value = "clicked"
        )
        val context = createContext(
            magicVariables = mutableMapOf("target_id" to VObjectFactory.from(element)),
            namedVariables = mutableMapOf(KEY_CURRENT_EVENT to VObjectFactory.from(event))
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertEquals("clicked", success.outputs["value"])
    }

    @Test
    fun `update ui component accepts VUiComponent target input`() = runBlocking {
        val sessionId = "session-update"
        UiSessionBus.registerSession(sessionId)
        try {
            val module = UpdateUiComponentModule()
            val element = UiElement(
                id = "btn_submit",
                type = UiElementType.BUTTON,
                label = "Submit",
                defaultValue = "",
                placeholder = "",
                isRequired = false
            )
            val commandDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(1000) {
                    UiSessionBus.getCommandFlow(sessionId)!!.first()
                }
            }
            val context = createContext(
                variables = mutableMapOf("text" to VString("Updated label")),
                magicVariables = mutableMapOf("target_id" to VObjectFactory.from(element)),
                namedVariables = mutableMapOf(KEY_UI_SESSION_ID to VString(sessionId))
            )

            val result = module.execute(context) { }
            val command = commandDeferred.await()

            assertTrue(result is ExecutionResult.Success)
            val success = result as ExecutionResult.Success
            val successFlag = success.outputs["success"] as? VBoolean
            assertFalse(command.targetId.isNullOrEmpty())
            assertEquals("btn_submit", command.targetId)
            assertEquals("Updated label", command.payload["text"])
            assertEquals(true, successFlag?.raw)
        } finally {
            UiSessionBus.unregisterSession(sessionId)
        }
    }

    private fun createContext(
        variables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf(),
        magicVariables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf(),
        namedVariables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf(),
        triggerData: VDictionary? = null
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = variables,
            magicVariables = magicVariables,
            services = ExecutionServices(),
            allSteps = listOf(ActionStep(moduleId = "test.module", parameters = emptyMap())),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            triggerData = triggerData,
            namedVariables = namedVariables,
            workDir = File("build/test-workdir")
        )
    }
}
