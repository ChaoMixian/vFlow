package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.triggers.GravityVector
import com.chaomixian.vflow.core.workflow.module.triggers.PoseAngles
import com.chaomixian.vflow.core.workflow.module.triggers.PoseTriggerMath
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class PoseTriggerRecorderActivity : BaseActivity(), SensorEventListener {
    companion object {
        const val EXTRA_INCLUDE_GRAVITY_ACCELERATION = "pose_include_gravity_acceleration"
        const val EXTRA_RESULT_AZIMUTH = "pose_result_azimuth"
        const val EXTRA_RESULT_PITCH = "pose_result_pitch"
        const val EXTRA_RESULT_ROLL = "pose_result_roll"
        const val EXTRA_RESULT_GRAVITY_X = "pose_result_gravity_x"
        const val EXTRA_RESULT_GRAVITY_Y = "pose_result_gravity_y"
        const val EXTRA_RESULT_GRAVITY_Z = "pose_result_gravity_z"

        fun createIntent(context: Context, includeGravityAcceleration: Boolean): Intent {
            return Intent(context, PoseTriggerRecorderActivity::class.java).apply {
                putExtra(EXTRA_INCLUDE_GRAVITY_ACCELERATION, includeGravityAcceleration)
            }
        }
    }

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var gravitySensor: Sensor? = null

    private lateinit var statusText: TextView
    private lateinit var currentPoseText: TextView
    private lateinit var hintText: TextView
    private lateinit var sampleCountText: TextView
    private lateinit var cancelButton: MaterialButton

    private var includeGravityAcceleration = false
    private var latestPose: PoseAngles? = null
    private var latestGravity: GravityVector? = null
    private var isRecording = false
    private val recordedSamples = mutableListOf<PoseAngles>()
    private val recordedGravitySamples = mutableListOf<GravityVector>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pose_trigger_recorder)

        includeGravityAcceleration = intent.getBooleanExtra(EXTRA_INCLUDE_GRAVITY_ACCELERATION, false)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        statusText = findViewById(R.id.text_record_status)
        currentPoseText = findViewById(R.id.text_current_pose)
        hintText = findViewById(R.id.text_record_hint)
        sampleCountText = findViewById(R.id.text_sample_count)
        cancelButton = findViewById(R.id.button_cancel_record_pose)

        findViewById<MaterialToolbar>(R.id.toolbar_pose_recorder).setNavigationOnClickListener {
            finish()
        }
        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (rotationSensor == null) {
            Toast.makeText(this, R.string.pose_trigger_sensor_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (includeGravityAcceleration && gravitySensor == null) {
            Toast.makeText(this, R.string.pose_trigger_gravity_sensor_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        renderUi()
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (includeGravityAcceleration) {
            gravitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            handleVolumeKeyToggle()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                latestPose = PoseTriggerMath.fromRotationVector(event.values)
                if (isRecording) {
                    recordedSamples += latestPose!!
                }
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                if (!includeGravityAcceleration) return
                latestGravity = PoseTriggerMath.fromGravityValues(event.values)
                if (isRecording) {
                    recordedGravitySamples += latestGravity!!
                }
            }
            else -> return
        }
        renderUi()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleVolumeKeyToggle() {
        if (!isRecording) {
            isRecording = true
            recordedSamples.clear()
            recordedGravitySamples.clear()
            renderUi()
            return
        }

        val averagedPose = PoseTriggerMath.average(recordedSamples)
            ?: latestPose
        val averagedGravity = if (includeGravityAcceleration) {
            PoseTriggerMath.averageGravity(recordedGravitySamples) ?: latestGravity
        } else {
            null
        }
        if (averagedPose == null) {
            Toast.makeText(this, R.string.pose_trigger_record_empty, Toast.LENGTH_SHORT).show()
            isRecording = false
            renderUi()
            return
        }
        if (includeGravityAcceleration && averagedGravity == null) {
            Toast.makeText(this, R.string.pose_trigger_gravity_record_empty, Toast.LENGTH_SHORT).show()
            isRecording = false
            renderUi()
            return
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_AZIMUTH, averagedPose.azimuth)
            putExtra(EXTRA_RESULT_PITCH, averagedPose.pitch)
            putExtra(EXTRA_RESULT_ROLL, averagedPose.roll)
            if (averagedGravity != null) {
                putExtra(EXTRA_RESULT_GRAVITY_X, averagedGravity.x)
                putExtra(EXTRA_RESULT_GRAVITY_Y, averagedGravity.y)
                putExtra(EXTRA_RESULT_GRAVITY_Z, averagedGravity.z)
            }
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun renderUi() {
        statusText.text = if (isRecording) {
            getString(R.string.pose_trigger_record_status_recording)
        } else {
            getString(R.string.pose_trigger_record_status_ready)
        }
        hintText.text = if (isRecording) {
            getString(R.string.pose_trigger_record_hint_stop)
        } else {
            getString(R.string.pose_trigger_record_hint_start)
        }
        sampleCountText.text = getString(
            R.string.pose_trigger_record_sample_count,
            recordedSamples.size
        )
        currentPoseText.text = buildString {
            append(latestPose?.format() ?: getString(R.string.pose_trigger_current_pose_waiting))
            if (includeGravityAcceleration) {
                append('\n')
                append(latestGravity?.format() ?: getString(R.string.pose_trigger_current_gravity_waiting))
            }
        }
    }
}
