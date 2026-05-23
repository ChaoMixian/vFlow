package com.chaomixian.vflow.core.workflow.module.system

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetBatteryStatusModuleTest {

    private val module = GetBatteryStatusModule()

    @Test
    fun `readBatteryLevelPercent returns percentage when level and scale are valid`() {
        assertEquals(45, module.readBatteryLevelPercent(45, 100))
    }

    @Test
    fun `readBatteryLevelPercent returns null when scale is invalid`() {
        assertNull(module.readBatteryLevelPercent(45, 0))
    }

    @Test
    fun `readChargingState returns true for charging and full statuses`() {
        assertTrue(module.readChargingState(BatteryManager.BATTERY_STATUS_CHARGING))
        assertTrue(module.readChargingState(BatteryManager.BATTERY_STATUS_FULL))
    }

    @Test
    fun `readChargingState returns false for non charging status`() {
        assertFalse(module.readChargingState(BatteryManager.BATTERY_STATUS_DISCHARGING))
    }

    @Test
    fun `readTemperatureCelsius converts tenths of celsius`() {
        assertEquals(31.5f, module.readTemperatureCelsius(315))
    }

    @Test
    fun `readTemperatureCelsius returns null for invalid value`() {
        assertNull(module.readTemperatureCelsius(-1))
    }
}
