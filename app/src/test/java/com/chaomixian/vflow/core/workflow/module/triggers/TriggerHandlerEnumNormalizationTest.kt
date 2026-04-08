package com.chaomixian.vflow.core.workflow.module.triggers

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerHandlerEnumNormalizationTest {

    @Test
    fun `normalizes wifi trigger values`() {
        assertEquals(
            WifiTriggerModule.TRIGGER_TYPE_CONNECTION,
            WifiTriggerModule.normalizeTriggerType("网络连接")
        )
        assertEquals(
            WifiTriggerModule.TRIGGER_TYPE_STATE,
            WifiTriggerModule.normalizeTriggerType("Wi-Fi State")
        )
        assertEquals(
            WifiTriggerModule.CONNECTION_EVENT_CONNECTED,
            WifiTriggerModule.normalizeConnectionEvent("Connect to")
        )
        assertEquals(
            WifiTriggerModule.STATE_EVENT_OFF,
            WifiTriggerModule.normalizeStateEvent("关闭时")
        )
    }

    @Test
    fun `normalizes bluetooth trigger values`() {
        assertEquals(
            BluetoothTriggerModule.TRIGGER_TYPE_STATE,
            BluetoothTriggerModule.normalizeTriggerType("Bluetooth State")
        )
        assertEquals(
            BluetoothTriggerModule.TRIGGER_TYPE_DEVICE,
            BluetoothTriggerModule.normalizeTriggerType("设备连接")
        )
        assertEquals(
            BluetoothTriggerModule.STATE_EVENT_ON,
            BluetoothTriggerModule.normalizeStateEvent("Turned On")
        )
        assertEquals(
            BluetoothTriggerModule.DEVICE_EVENT_DISCONNECTED,
            BluetoothTriggerModule.normalizeDeviceEvent("断开时")
        )
    }

    @Test
    fun `normalizes call trigger values`() {
        assertEquals(CallTriggerModule.TYPE_ANY, CallTriggerModule.normalizeCallType("任意"))
        assertEquals(CallTriggerModule.TYPE_INCOMING, CallTriggerModule.normalizeCallType("Incoming"))
        assertEquals(CallTriggerModule.TYPE_ANSWERED, CallTriggerModule.normalizeCallType("接通"))
        assertEquals(CallTriggerModule.TYPE_ENDED, CallTriggerModule.normalizeCallType("Ended"))
    }

    @Test
    fun `normalizes sms trigger values`() {
        assertEquals(
            SmsTriggerModule.SENDER_ANY,
            SmsTriggerModule.normalizeSenderFilterType("Any Number")
        )
        assertEquals(
            SmsTriggerModule.SENDER_NOT_CONTAINS,
            SmsTriggerModule.normalizeSenderFilterType("号码不包含")
        )
        assertEquals(
            SmsTriggerModule.CONTENT_CODE,
            SmsTriggerModule.normalizeContentFilterType("识别验证码")
        )
        assertEquals(
            SmsTriggerModule.CONTENT_REGEX,
            SmsTriggerModule.normalizeContentFilterType("Regex Match")
        )
    }

    @Test
    fun `normalizes location trigger values`() {
        assertEquals(LocationTriggerModule.EVENT_ENTER, LocationTriggerModule.normalizeEvent("进入时"))
        assertEquals(LocationTriggerModule.EVENT_EXIT, LocationTriggerModule.normalizeEvent("Exit"))
    }
}
