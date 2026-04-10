package com.chaomixian.vflow.core.workflow.module.triggers

import com.chaomixian.vflow.core.module.normalizeEnumValueOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerHandlerEnumNormalizationTest {

    @Test
    fun `normalizes wifi trigger values`() {
        val module = WifiTriggerModule()
        val triggerTypeInput = module.getInputs().first { it.id == "trigger_type" }
        val connectionEventInput = module.getInputs().first { it.id == "connection_event" }
        val stateEventInput = module.getInputs().first { it.id == "state_event" }

        assertEquals(
            WifiTriggerModule.TRIGGER_TYPE_CONNECTION,
            triggerTypeInput.normalizeEnumValueOrNull("网络连接")
        )
        assertEquals(
            WifiTriggerModule.CONNECTION_EVENT_CONNECTED,
            connectionEventInput.normalizeEnumValueOrNull("连接到")
        )
        assertEquals(
            WifiTriggerModule.STATE_EVENT_OFF,
            stateEventInput.normalizeEnumValueOrNull("关闭时")
        )
    }

    @Test
    fun `normalizes bluetooth trigger values`() {
        val module = BluetoothTriggerModule()
        val triggerTypeInput = module.getInputs().first { it.id == "trigger_type" }
        val stateEventInput = module.getInputs().first { it.id == "state_event" }
        val deviceEventInput = module.getInputs().first { it.id == "device_event" }

        assertEquals(
            BluetoothTriggerModule.TRIGGER_TYPE_DEVICE,
            triggerTypeInput.normalizeEnumValueOrNull("设备连接")
        )
        assertEquals(
            BluetoothTriggerModule.STATE_EVENT_ON,
            stateEventInput.normalizeEnumValueOrNull("开启时")
        )
        assertEquals(
            BluetoothTriggerModule.DEVICE_EVENT_DISCONNECTED,
            deviceEventInput.normalizeEnumValueOrNull("断开时")
        )
    }

    @Test
    fun `normalizes call trigger values`() {
        val input = CallTriggerModule().getInputs().first { it.id == "call_type" }

        assertEquals(CallTriggerModule.TYPE_ANY, input.normalizeEnumValueOrNull("任意"))
        assertEquals(CallTriggerModule.TYPE_ANSWERED, input.normalizeEnumValueOrNull("接通"))
        assertEquals(CallTriggerModule.TYPE_ENDED, input.normalizeEnumValueOrNull("挂断"))
    }

    @Test
    fun `normalizes key event trigger english action values`() {
        val input = KeyEventTriggerModule().getInputs().first { it.id == "action_type" }

        assertEquals(
            KeyEventTriggerModule.ACTION_SINGLE_CLICK,
            input.normalizeEnumValueOrNull("Single Click")
        )
        assertEquals(
            KeyEventTriggerModule.ACTION_SHORT_PRESS,
            input.normalizeEnumValueOrNull("Short Press (Immediate)")
        )
    }

    @Test
    fun `normalizes sms trigger values`() {
        val senderInput = SmsTriggerModule().getInputs().first { it.id == "sender_filter_type" }
        val contentInput = SmsTriggerModule().getInputs().first { it.id == "content_filter_type" }

        assertEquals(
            SmsTriggerModule.SENDER_NOT_CONTAINS,
            senderInput.normalizeEnumValueOrNull("号码不包含")
        )
        assertEquals(
            SmsTriggerModule.CONTENT_CODE,
            contentInput.normalizeEnumValueOrNull("识别验证码")
        )
        assertEquals(
            SmsTriggerModule.CONTENT_REGEX,
            contentInput.normalizeEnumValueOrNull("正则匹配")
        )
    }

    @Test
    fun `normalizes location trigger values`() {
        val input = LocationTriggerModule().getInputs().first { it.id == "event" }

        assertEquals(LocationTriggerModule.EVENT_ENTER, input.normalizeEnumValueOrNull("进入时"))
        assertEquals(LocationTriggerModule.EVENT_EXIT, input.normalizeEnumValueOrNull("离开时"))
    }

    @Test
    fun `normalizes receive share english values`() {
        val input = ReceiveShareTriggerModule().getInputs().first { it.id == "acceptedType" }

        assertEquals("any", input.normalizeEnumValueOrNull("Any"))
        assertEquals("text", input.normalizeEnumValueOrNull("Text"))
        assertEquals("link", input.normalizeEnumValueOrNull("Link"))
        assertEquals("image", input.normalizeEnumValueOrNull("Image"))
        assertEquals("file", input.normalizeEnumValueOrNull("File"))
    }
}
