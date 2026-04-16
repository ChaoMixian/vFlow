package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.PoseAngles
import com.chaomixian.vflow.core.workflow.module.triggers.PoseTriggerData
import com.chaomixian.vflow.core.workflow.module.triggers.PoseTriggerMath
import java.util.concurrent.CopyOnWriteArrayList

class PoseTriggerHandler : BaseTriggerHandler(), SensorEventListener {
    companion object {
        private const val TAG = "PoseTriggerHandler"
        private const val RELEASE_MARGIN = 2f
        private const val MAX_RECENT_SAMPLES = 6
    }

    private data class ResolvedPoseTrigger(
        val trigger: TriggerSpec,
        val targetPose: PoseAngles,
        val threshold: Float,
        var isWithinMatchWindow: Boolean = false,
    )

    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private val activeTriggers = CopyOnWriteArrayList<ResolvedPoseTrigger>()
    private val recentSamples = ArrayDeque<PoseAngles>()
    private var isListening = false

    override fun start(context: Context) {
        super.start(context)
        appContext = context.applicationContext
        sensorManager = appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            DebugLogger.w(TAG, "Rotation vector sensor unavailable")
        }
        reloadTriggers()
    }

    override fun stop(context: Context) {
        stopListening()
        activeTriggers.clear()
        recentSamples.clear()
        appContext = null
        sensorManager = null
        rotationSensor = null
        super.stop(context)
    }

    override fun addTrigger(context: Context, trigger: TriggerSpec) {
        activeTriggers.removeAll { it.trigger.triggerId == trigger.triggerId }
        resolveTrigger(trigger)?.let(activeTriggers::add)
        reloadTriggers()
    }

    override fun removeTrigger(context: Context, triggerId: String) {
        activeTriggers.removeAll { it.trigger.triggerId == triggerId }
        reloadTriggers()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return
        val pose = PoseTriggerMath.fromRotationVector(event.values)
        if (recentSamples.size >= MAX_RECENT_SAMPLES) {
            recentSamples.removeFirst()
        }
        recentSamples.addLast(pose)
        val smoothedPose = PoseTriggerMath.average(recentSamples.toList()) ?: pose
        val context = appContext ?: return

        activeTriggers.forEach { resolved ->
            val score = PoseTriggerMath.calculateMatchScore(resolved.targetPose, smoothedPose)
            val isMatch = score >= resolved.threshold
            if (isMatch && !resolved.isWithinMatchWindow) {
                resolved.isWithinMatchWindow = true
                DebugLogger.d(TAG, "Pose trigger matched: ${resolved.trigger.triggerId}, score=$score")
                executeTrigger(
                    context = context,
                    trigger = resolved.trigger,
                    triggerData = PoseTriggerData(
                        azimuth = smoothedPose.azimuth,
                        pitch = smoothedPose.pitch,
                        roll = smoothedPose.roll,
                        matchScore = score,
                    )
                )
            } else if (!isMatch && score < (resolved.threshold - RELEASE_MARGIN)) {
                resolved.isWithinMatchWindow = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun reloadTriggers() {
        if (activeTriggers.isEmpty() || rotationSensor == null) {
            stopListening()
            return
        }
        startListening()
    }

    private fun startListening() {
        if (isListening) return
        val sensor = rotationSensor ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    private fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        recentSamples.clear()
    }

    private fun resolveTrigger(trigger: TriggerSpec): ResolvedPoseTrigger? {
        val poseRecorded = trigger.parameters["poseRecorded"] as? Boolean ?: false
        if (!poseRecorded) return null
        val targetPose = PoseAngles(
            azimuth = numberParameter(trigger.parameters["targetAzimuth"]),
            pitch = numberParameter(trigger.parameters["targetPitch"]),
            roll = numberParameter(trigger.parameters["targetRoll"]),
        )
        val threshold = numberParameter(trigger.parameters["matchThreshold"], 90f).coerceIn(0f, 100f)
        return ResolvedPoseTrigger(
            trigger = trigger,
            targetPose = targetPose,
            threshold = threshold,
        )
    }

    private fun numberParameter(value: Any?, fallback: Float = 0f): Float {
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: fallback
            else -> fallback
        }
    }
}
