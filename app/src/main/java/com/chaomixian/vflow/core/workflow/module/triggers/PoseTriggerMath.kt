package com.chaomixian.vflow.core.workflow.module.triggers

import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class PoseAngles(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float,
) {
    fun format(): String {
        return "Z ${azimuth.roundToInt()}°, X ${pitch.roundToInt()}°, Y ${roll.roundToInt()}°"
    }
}

object PoseTriggerMath {
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

    fun average(samples: List<PoseAngles>): PoseAngles? {
        if (samples.isEmpty()) return null
        return PoseAngles(
            azimuth = averageAngle(samples.map { it.azimuth }, unsigned = true),
            pitch = averageAngle(samples.map { it.pitch }, unsigned = false),
            roll = averageAngle(samples.map { it.roll }, unsigned = false),
        )
    }

    fun calculateMatchScore(target: PoseAngles, current: PoseAngles): Float {
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
