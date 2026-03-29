package com.chaomixian.vflow.core.workflow.module.system

import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvokeModuleTest {

    @Test
    fun `coerce intent extra keeps VString raw value`() {
        val result = coerceIntentExtraValue(VString("Boss"))

        assertEquals("Boss", result)
    }

    @Test
    fun `coerce intent extra keeps VBoolean raw value`() {
        val result = coerceIntentExtraValue(VBoolean(true))

        assertEquals(true, result)
    }

    @Test
    fun `coerce intent extra keeps VNumber integer type`() {
        val result = coerceIntentExtraValue(VNumber(10))

        assertTrue(result is Int)
        assertEquals(10, result)
    }

    @Test
    fun `coerce intent extra keeps VNumber decimal type`() {
        val result = coerceIntentExtraValue(VNumber(3.14))

        assertTrue(result is Double)
        assertEquals(3.14, result)
    }

    @Test
    fun `coerce intent extra preserves string smart inference for plain values`() {
        assertEquals(10, coerceIntentExtraValue("10"))
        assertEquals(true, coerceIntentExtraValue("true"))
        assertEquals("Boss", coerceIntentExtraValue("Boss"))
    }

    @Test
    fun `coerce intent extra maps VNull to null`() {
        val result = coerceIntentExtraValue(VNull)

        assertNull(result)
    }
}
