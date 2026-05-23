package com.chaomixian.vflow.core.workflow.module.system

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRotationModuleTest {

    private val module = ScreenRotationModule()

    @Test
    fun `direction input is visible only when auto rotate is off`() {
        val directionInput = module.getInputs().first { it.id == ScreenRotationModule.INPUT_DIRECTION }

        assertTrue(directionInput.visibility?.isVisible(mapOf(ScreenRotationModule.INPUT_AUTO_ROTATE to false)) == true)
        assertFalse(directionInput.visibility?.isVisible(mapOf(ScreenRotationModule.INPUT_AUTO_ROTATE to true)) == true)
    }

    @Test
    fun `invalid direction falls back to portrait`() {
        assertEquals(ScreenRotationModule.DIRECTION_PORTRAIT, module.resolveDirection("invalid"))
        assertEquals(ScreenRotationModule.DIRECTION_PORTRAIT, module.resolveDirection(null))
    }

    @Test
    fun `canonical directions map to system rotations`() {
        assertEquals(Surface.ROTATION_0, module.toSystemRotation(ScreenRotationModule.DIRECTION_PORTRAIT))
        assertEquals(Surface.ROTATION_90, module.toSystemRotation(ScreenRotationModule.DIRECTION_LANDSCAPE_LEFT))
        assertEquals(Surface.ROTATION_180, module.toSystemRotation(ScreenRotationModule.DIRECTION_PORTRAIT_UPSIDE_DOWN))
        assertEquals(Surface.ROTATION_270, module.toSystemRotation(ScreenRotationModule.DIRECTION_LANDSCAPE_RIGHT))
    }
}
