package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowVisualsTest {

    @Test
    fun `normalize icon falls back to default`() {
        assertEquals(
            WorkflowVisuals.DEFAULT_ICON_RES_NAME,
            WorkflowVisuals.normalizeIconResName(null)
        )
        assertEquals(
            WorkflowVisuals.DEFAULT_ICON_RES_NAME,
            WorkflowVisuals.normalizeIconResName("   ")
        )
    }

    @Test
    fun `normalize theme color returns uppercase hex`() {
        assertEquals("#5B8CFF", WorkflowVisuals.normalizeThemeColorHex("#5b8cff"))
        assertEquals(
            WorkflowVisuals.DEFAULT_THEME_COLOR_HEX,
            WorkflowVisuals.normalizeThemeColorHex("blue")
        )
    }

    @Test
    fun `random theme color always comes from palette`() {
        repeat(20) {
            assertTrue(WorkflowVisuals.randomThemeColorHex() in WorkflowVisuals.themePaletteHex)
        }
    }

    @Test
    fun `resolve icon uses registry and falls back to default`() {
        assertEquals(
            R.drawable.rounded_terminal_24,
            WorkflowVisuals.resolveIconDrawableRes("rounded_terminal_24")
        )
        assertEquals(
            R.drawable.rounded_layers_fill_24,
            WorkflowVisuals.resolveIconDrawableRes("missing_icon")
        )
    }
}
