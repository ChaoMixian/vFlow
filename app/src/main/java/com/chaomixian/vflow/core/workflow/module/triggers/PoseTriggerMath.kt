package com.chaomixian.vflow.core.workflow.module.triggers

import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PoseAngles(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float,
) {
    fun format(): String {
        return "Z ${azimuth.roundToInt()}°, X ${pitch.roundToInt()}°, Y ${roll.roundToInt()}°"
    }
}

data class GravityVector(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)

    fun isMeaningful(epsilon: Float = 0.1f): Boolean = magnitude() > epsilon

    fun format(): String {
        return "G X ${x.formatSignedOneDecimal()}, Y ${y.formatSignedOneDecimal()}, Z ${z.formatSignedOneDecimal()} m/s²"
    }

    private fun Float.formatSignedOneDecimal(): String = String.format("%.1f", this)
}

object PoseTriggerMath {
    private const val MAX_GRAVITY_VECTOR_DIFF = SensorManager.GRAVITY_EARTH * 2f

    fun fromRotationVector(values: FloatArray): PoseAngles {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        return PoseAngles(
            azimuth = normalizeUnsigned(Math.toDegrees(orientation[0].toDouble()).toFloat()),
            pitch = normalizeSigned(Math.toDegrees(orientation[1].toDouble()).toFloat()),
            roll = normalizeSigned(Math.toDegrees(orientation[2].toDouble()).toFloat()),
        )
    }

    fun fromGravityValues(values: FloatArray): GravityVector {
        return GravityVector(
            x = values.getOrElse(0) { 0f },
            y = values.getOrElse(1) { 0f },
            z = values.getOrElse(2) { 0f },
        )
    }

    fun average(samples: List<PoseAngles>): PoseAngles? {
        if (samples.isEmpty()) return null
        return PoseAngles(
            azimuth = averageAngle(samples.map { it.azimuth }, unsigned = true),
            pitch = averageAngle(samples.map { it.pitch }, unsigned = false),
            roll = averageAngle(samples.map { it.roll }, unsigned = false),
        )
    }

    fun averageGravity(samples: List<GravityVector>): GravityVector? {
        if (samples.isEmpty()) return null
        return GravityVector(
            x = samples.map { it.x }.average().toFloat(),
            y = samples.map { it.y }.average().toFloat(),
            z = samples.map { it.z }.average().toFloat(),
        )
    }

    fun calculateMatchScore(
        target: PoseAngles,
        current: PoseAngles,
        targetGravity: GravityVector? = null,
        currentGravity: GravityVector? = null,
    ): Float {
        val poseScore = calculatePoseMatchScore(target, current)
        if (targetGravity == null || currentGravity == null) {
            return poseScore
        }
        val gravityScore = calculateGravityMatchScore(targetGravity, currentGravity)
        return ((poseScore + gravityScore) / 2f).coerceIn(0f, 100f)
    }

    fun calculateGravityMatchScore(target: GravityVector, current: GravityVector): Float {
        val diffX = target.x - current.x
        val diffY = target.y - current.y
        val diffZ = target.z - current.z
        val diff = sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ)
        return ((MAX_GRAVITY_VECTOR_DIFF - diff) / MAX_GRAVITY_VECTOR_DIFF * 100f).coerceIn(0f, 100f)
    }

    private fun calculatePoseMatchScore(target: PoseAngles, current: PoseAngles): Float {
        val azimuthDiff = shortestAngleDifference(target.azimuth, current.azimuth)
        val pitchDiff = shortestAngleDifference(target.pitch, current.pitch)
        val rollDiff = shortestAngleDifference(target.roll, current.roll)
        val averageDiff = (azimuthDiff + pitchDiff + rollDiff) / 3f
        return ((180f - averageDiff) / 180f * 100f).coerceIn(0f, 100f)
    }

    fun shortestAngleDifference(first: Float, second: Float): Float {
        val diff = ((first - second + 540f) % 360f) - 180f
        return kotlin.math.abs(diff)
    }

    fun normalizeUnsigned(angle: Float): Float {
        val normalized = angle % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    fun normalizeSigned(angle: Float): Float {
        val normalized = normalizeUnsigned(angle)
        return if (normalized >= 180f) normalized - 360f else normalized
    }

    private fun averageAngle(values: List<Float>, unsigned: Boolean): Float {
        val sumSin = values.sumOf { sin(Math.toRadians(it.toDouble())) }
        val sumCos = values.sumOf { cos(Math.toRadians(it.toDouble())) }
        val degrees = Math.toDegrees(atan2(sumSin, sumCos))
        return if (unsigned) normalizeUnsigned(degrees.toFloat()) else normalizeSigned(degrees.toFloat())
    }
}
