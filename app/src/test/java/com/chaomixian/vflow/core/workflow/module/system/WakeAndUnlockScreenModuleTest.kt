package com.chaomixian.vflow.core.workflow.module.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAndUnlockScreenModuleTest {

    private val module = WakeAndUnlockScreenModule()

    @Test
    fun `digit password uses keyevents and enter`() {
        val commands = module.buildUnlockCommandSequence("1209")

        assertEquals(
            listOf(
                "input keyevent 8",
                "input keyevent 9",
                "input keyevent 7",
                "input keyevent 16",
                "input keyevent 66"
            ),
            commands
        )
    }

    @Test
    fun `text password uses escaped input text command`() {
        val commands = module.buildUnlockCommandSequence("A b$\"`")

        assertEquals("input text \"A%sb\\$\\\"\\`\"", commands.first())
        assertEquals("input keyevent 66", commands.last())
    }

    @Test
    fun `wm size parser extracts physical size`() {
        assertEquals(1080 to 2400, module.parseDisplaySize("Physical size: 1080x2400"))
        assertEquals(720 to 1600, module.parseDisplaySize("Override size: 720x1600"))
        assertEquals(null, module.parseDisplaySize("Error: permission denied"))
    }

    @Test
    fun `ascii password validation rejects unicode`() {
        assertTrue(module.isAsciiUnlockPassword("P@55w0rd"))
        assertFalse(module.isAsciiUnlockPassword("密码123"))
    }
}
