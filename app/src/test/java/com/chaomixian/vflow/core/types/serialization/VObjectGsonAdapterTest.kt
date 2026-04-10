package com.chaomixian.vflow.core.types.serialization

import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.complex.VNotification
import com.chaomixian.vflow.core.types.complex.VUiComponent
import com.chaomixian.vflow.core.workflow.module.notification.NotificationObject
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VObjectGsonAdapterTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(VObject::class.java, VObjectGsonAdapter())
        .create()

    @Test
    fun `round trip preserves nested notification wrappers inside list`() {
        val source: VObject = VList(
            listOf(
                VNotification(NotificationObject("n1", "pkg.demo", "Title", "Content"))
            )
        )

        val json = gson.toJson(source, VObject::class.java)
        val restored = gson.fromJson(json, VObject::class.java) as VList

        assertEquals(1, restored.raw.size)
        val item = restored.raw.first()
        assertTrue(item is VNotification)
        assertEquals("n1", (item as VNotification).notification.id)
    }

    @Test
    fun `round trip preserves nested ui component wrappers inside dictionary`() {
        val source: VObject = VDictionary(
            mapOf(
                "component" to VUiComponent(
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

        val json = gson.toJson(source, VObject::class.java)
        val restored = gson.fromJson(json, VObject::class.java) as VDictionary

        val component = restored.raw["component"]
        assertTrue(component is VUiComponent)
        assertEquals("btn_submit", (component as VUiComponent).element.id)
        assertEquals("Submit", component.element.label)
    }
}
