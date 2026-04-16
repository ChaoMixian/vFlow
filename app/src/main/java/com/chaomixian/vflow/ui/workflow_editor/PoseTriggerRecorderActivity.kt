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
import com.chaomixian.vflow.core.workflow.module.triggers.PoseAngles
import com.chaomixian.vflow.core.workflow.module.triggers.PoseTriggerMath
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class PoseTriggerRecorderActivity : BaseActivity(), SensorEventListener {
    companion object {
        const val EXTRA_RESULT_AZIMUTH = "pose_result_azimuth"
        const val EXTRA_RESULT_PITCH = "pose_result_pitch"
        const val EXTRA_RESULT_ROLL = "pose_result_roll"

        fun createIntent(context: Context): Intent {
            return Intent(context, PoseTriggerRecorderActivity::class.java)
        }
    }

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private lateinit var statusText: TextView
    private lateinit var currentPoseText: TextView
    private lateinit var hintText: TextView
    private lateinit var sampleCountText: TextView
    private lateinit var cancelButton: MaterialButton

    private var latestPose: PoseAngles? = null
    private var isRecording = false
    private val recordedSamples = mutableListOf<PoseAngles>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pose_trigger_recorder)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

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

        renderUi()
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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
        latestPose = PoseTriggerMath.fromRotationVector(event.values)
        if (isRecording) {
            recordedSamples += latestPose!!
        }
        renderUi()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleVolumeKeyToggle() {
        if (!isRecording) {
            isRecording = true
            recordedSamples.clear()
            renderUi()
            return
        }

        val averagedPose = PoseTriggerMath.average(recordedSamples)
            ?: latestPose
        if (averagedPose == null) {
            Toast.makeText(this, R.string.pose_trigger_record_empty, Toast.LENGTH_SHORT).show()
            isRecording = false
            renderUi()
            return
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_AZIMUTH, averagedPose.azimuth)
            putExtra(EXTRA_RESULT_PITCH, averagedPose.pitch)
            putExtra(EXTRA_RESULT_ROLL, averagedPose.roll)
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
        currentPoseText.text = latestPose?.format()
            ?: getString(R.string.pose_trigger_current_pose_waiting)
    }
}
