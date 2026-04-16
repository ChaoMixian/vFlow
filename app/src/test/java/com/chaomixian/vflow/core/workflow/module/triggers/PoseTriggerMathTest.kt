package com.chaomixian.vflow.core.workflow.module.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseTriggerMathTest {
    @Test
    fun `shortest angle difference wraps around zero`() {
        assertEquals(20f, PoseTriggerMath.shortestAngleDifference(350f, 10f), 0.001f)
        assertEquals(15f, PoseTriggerMath.shortestAngleDifference(-175f, 170f), 0.001f)
    }

    @Test
    fun `average handles azimuth wrap around`() {
        val average = PoseTriggerMath.average(
            listOf(
                PoseAngles(azimuth = 350f, pitch = 10f, roll = -10f),
                PoseAngles(azimuth = 10f, pitch = 20f, roll = -20f),
            )
        )!!

        assertTrue(average.azimuth <= 20f || average.azimuth >= 340f)
        assertEquals(15f, average.pitch, 0.5f)
        assertEquals(-15f, average.roll, 0.5f)
    }

    @Test
    fun `match score prefers close poses`() {
        val target = PoseAngles(azimuth = 5f, pitch = 10f, roll = -20f)
        val close = PoseAngles(azimuth = 358f, pitch = 14f, roll = -18f)
        val far = PoseAngles(azimuth = 160f, pitch = -80f, roll = 110f)

        val closeScore = PoseTriggerMath.calculateMatchScore(target, close)
        val farScore = PoseTriggerMath.calculateMatchScore(target, far)

        assertTrue(closeScore > 95f)
        assertTrue(farScore < 50f)
    }

    @Test
    fun `gravity average keeps stable center`() {
        val average = PoseTriggerMath.averageGravity(
            listOf(
                GravityVector(x = 0f, y = 9.6f, z = 0.4f),
                GravityVector(x = 0.2f, y = 9.8f, z = 0.2f),
            )
        )!!

        assertEquals(0.1f, average.x, 0.001f)
        assertEquals(9.7f, average.y, 0.001f)
        assertEquals(0.3f, average.z, 0.001f)
    }

    @Test
    fun `combined match score gets stricter with gravity mismatch`() {
        val targetPose = PoseAngles(azimuth = 20f, pitch = 15f, roll = -5f)
        val currentPose = PoseAngles(azimuth = 22f, pitch = 16f, roll = -4f)
        val targetGravity = GravityVector(x = 0f, y = 9.8f, z = 0f)
        val closeGravity = GravityVector(x = 0.1f, y = 9.7f, z = 0.1f)
        val farGravity = GravityVector(x = 9.8f, y = 0f, z = 0f)

        val closeScore = PoseTriggerMath.calculateMatchScore(
            target = targetPose,
            current = currentPose,
            targetGravity = targetGravity,
            currentGravity = closeGravity,
        )
        val farScore = PoseTriggerMath.calculateMatchScore(
            target = targetPose,
            current = currentPose,
            targetGravity = targetGravity,
            currentGravity = farGravity,
        )

        assertTrue(closeScore > 95f)
        assertTrue(farScore < 80f)
    }
}
