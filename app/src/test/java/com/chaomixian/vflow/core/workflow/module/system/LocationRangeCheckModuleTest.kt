package com.chaomixian.vflow.core.workflow.module.system

import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRangeCheckModuleTest {

    private val module = LocationRangeCheckModule()

    @Test
    fun `distanceMeters returns zero for same coordinate`() {
        val distance = LocationRangeMath.distanceMeters(
            startLatitude = 39.9042,
            startLongitude = 116.4074,
            endLatitude = 39.9042,
            endLongitude = 116.4074
        )

        assertEquals(0.0, distance, 0.0001)
    }

    @Test
    fun `distanceMeters matches expected scale`() {
        val distance = LocationRangeMath.distanceMeters(
            startLatitude = 0.0,
            startLongitude = 0.0,
            endLatitude = 1.0,
            endLongitude = 0.0
        )

        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun `validate rejects invalid latitude`() {
        val result = module.validate(
            ActionStep(
                moduleId = module.id,
                parameters = mapOf(
                    "latitude" to 91.0,
                    "longitude" to 116.4074,
                    "radius" to 500.0
                )
            ),
            emptyList()
        )

        assertFalse(result.isValid)
    }

    @Test
    fun `validate accepts valid coordinate range`() {
        val result = module.validate(
            ActionStep(
                moduleId = module.id,
                parameters = mapOf(
                    "latitude" to 39.9042,
                    "longitude" to 116.4074,
                    "radius" to 500.0
                )
            ),
            emptyList()
        )

        assertTrue(result.isValid)
    }
}
