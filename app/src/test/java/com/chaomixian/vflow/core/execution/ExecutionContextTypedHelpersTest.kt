package com.chaomixian.vflow.core.execution

import android.content.ContextWrapper
import com.chaomixian.vflow.core.types.BaseVObject
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.types.complex.VUiComponent
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class ExecutionContextTypedHelpersTest {

    @Test
    fun `getVariableAsNotification returns wrapper for wrapped notification`() {
        val notification = NotificationObject("n1", "pkg.demo", "Title", "Content")
        val context = createContext(
            namedVariables = mutableMapOf("target" to VObjectFactory.from(notification))
        )

        val result = context.getVariableAsNotification("target")

        assertNotNull(result)
        assertEquals("n1", result?.notification?.id)
        assertEquals("pkg.demo", result?.notification?.packageName)
    }

    @Test
    fun `getVariableAsNotificationList unwraps notification list`() {
        val notifications = listOf(
            NotificationObject("n1", "pkg.demo", "Title 1", "Content 1"),
            NotificationObject("n2", "pkg.demo", "Title 2", "Content 2")
        )
        val context = createContext(
            namedVariables = mutableMapOf("target" to VObjectFactory.from(notifications))
        )

        val result = context.getVariableAsNotificationList("target")

        assertEquals(2, result.size)
        assertEquals(listOf("n1", "n2"), result.map { it.notification.id })
    }

    @Test
    fun `getVariableAsUiComponentList unwraps ui element list`() {
        val elements = listOf(
            UiElement(
                id = "btn_submit",
                type = UiElementType.BUTTON,
                label = "Submit",
                defaultValue = "",
                placeholder = "",
                isRequired = false
            )
        )
        val context = createContext(
            namedVariables = mutableMapOf("_internal_ui_elements_list" to VObjectFactory.from(elements))
        )

        val components = context.getVariableAsUiComponentList("_internal_ui_elements_list")
        val rawElements = context.getVariableAsUiElementList("_internal_ui_elements_list")

        assertEquals(1, components.size)
        assertEquals("btn_submit", components.first().element.id)
        assertEquals("btn_submit", rawElements.first().id)
    }

    @Test
    fun `getVariableAsNotification rejects raw notification host`() {
        val notification = NotificationObject("n1", "pkg.demo", "Title", "Content")
        val context = createContext(
            namedVariables = mutableMapOf("target" to FakeRawVObject(notification))
        )

        val result = context.getVariableAsNotification("target")

        assertEquals(null, result)
    }

    @Test
    fun `getVariableAsNotificationList rejects raw notification list host`() {
        val notifications = VList(
            listOf(
                FakeRawVObject(NotificationObject("n1", "pkg.demo", "Title 1", "Content 1")),
                FakeRawVObject(NotificationObject("n2", "pkg.demo", "Title 2", "Content 2"))
            )
        )
        val context = createContext(
            namedVariables = mutableMapOf("target" to notifications)
        )

        val result = context.getVariableAsNotificationList("target")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getVariableAsUiComponent rejects raw ui element host`() {
        val element = UiElement(
            id = "btn_submit",
            type = UiElementType.BUTTON,
            label = "Submit",
            defaultValue = "",
            placeholder = "",
            isRequired = false
        )
        val context = createContext(
            namedVariables = mutableMapOf("target" to FakeRawVObject(element))
        )

        val result = context.getVariableAsUiComponent("target")

        assertEquals(null, result)
    }

    @Test
    fun `getVariableAsUiComponentList rejects raw ui element list host`() {
        val elements = VList(
            listOf(
                FakeRawVObject(
                    UiElement(
                        id = "btn_submit",
                        type = UiElementType.BUTTON,
                        label = "Submit",
                        defaultValue = "",
                        placeholder = "",
                        isRequired = false
                    )
                )
            )
        )
        val context = createContext(
            namedVariables = mutableMapOf("_internal_ui_elements_list" to elements)
        )

        val result = context.getVariableAsUiComponentList("_internal_ui_elements_list")

        assertTrue(result.isEmpty())
    }

    private fun createContext(
        variables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf(),
        namedVariables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf()
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = variables,
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = namedVariables,
            workDir = File("build/test-workdir")
        )
    }

    private class FakeRawVObject(
        override val raw: Any?
    ) : BaseVObject() {
        override val type = VTypeRegistry.ANY

        override fun getProperty(propertyName: String): VObject? = null

        override fun asString(): String = raw?.toString() ?: ""

        override fun asNumber(): Double? = null

        override fun asBoolean(): Boolean = raw != null
    }
}
