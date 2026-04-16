package com.chaomixian.vflow.core.workflow.module.triggers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PoseTriggerRecorderActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

private class PoseTriggerViewHolder(
    view: View,
    val recordButton: MaterialButton,
    val poseValue: TextView,
    val includeGravitySwitch: MaterialSwitch,
    val thresholdValue: TextView,
    val thresholdSlider: Slider,
) : CustomEditorViewHolder(view) {
    var recordedPose: PoseAngles? = null
    var recordedGravity: GravityVector? = null
}

class PoseTriggerUIProvider : ModuleUIProvider {
    override fun getHandledInputIds(): Set<String> {
        return setOf(
            "poseRecorded",
            "targetAzimuth",
            "targetPitch",
            "targetRoll",
            "matchThreshold",
            "includeGravityAcceleration",
            "gravityRecorded",
            "targetGravityX",
            "targetGravityY",
            "targetGravityZ",
        )
    }

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?,
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_pose_trigger_editor, parent, false)
        val holder = PoseTriggerViewHolder(
            view = view,
            recordButton = view.findViewById(R.id.button_record_pose),
            poseValue = view.findViewById(R.id.text_pose_value),
            includeGravitySwitch = view.findViewById(R.id.switch_include_gravity_acceleration),
            thresholdValue = view.findViewById(R.id.text_match_threshold),
            thresholdSlider = view.findViewById(R.id.slider_match_threshold),
        )

        val poseRecorded = currentParameters["poseRecorded"] as? Boolean ?: PoseTriggerModule.DEFAULT_POSE_RECORDED
        if (poseRecorded) {
            holder.recordedPose = PoseAngles(
                azimuth = (currentParameters["targetAzimuth"] as? Number)?.toFloat() ?: 0f,
                pitch = (currentParameters["targetPitch"] as? Number)?.toFloat() ?: 0f,
                roll = (currentParameters["targetRoll"] as? Number)?.toFloat() ?: 0f,
            )
        }
        val includeGravityAcceleration =
            currentParameters["includeGravityAcceleration"] as? Boolean
                ?: PoseTriggerModule.DEFAULT_INCLUDE_GRAVITY_ACCELERATION
        holder.includeGravitySwitch.isChecked = includeGravityAcceleration
        val gravityRecorded = currentParameters["gravityRecorded"] as? Boolean ?: PoseTriggerModule.DEFAULT_GRAVITY_RECORDED
        if (gravityRecorded) {
            holder.recordedGravity = GravityVector(
                x = (currentParameters["targetGravityX"] as? Number)?.toFloat() ?: 0f,
                y = (currentParameters["targetGravityY"] as? Number)?.toFloat() ?: 0f,
                z = (currentParameters["targetGravityZ"] as? Number)?.toFloat() ?: 0f,
            ).takeIf { it.isMeaningful() }
        }
        renderRecordedPose(context, holder)

        val threshold = ((currentParameters["matchThreshold"] as? Number)?.toFloat()
            ?: PoseTriggerModule.DEFAULT_MATCH_THRESHOLD.toFloat()).coerceIn(60f, 100f)
        holder.thresholdSlider.value = threshold
        renderThreshold(context, holder, threshold)

        holder.thresholdSlider.addOnChangeListener { _, value, _ ->
            renderThreshold(context, holder, value)
            onParametersChanged()
        }

        holder.includeGravitySwitch.setOnCheckedChangeListener { _, _ ->
            renderRecordedPose(context, holder)
            onParametersChanged()
        }

        holder.recordButton.setOnClickListener {
            if (onStartActivityForResult == null) return@setOnClickListener
            onStartActivityForResult(
                PoseTriggerRecorderActivity.createIntent(
                    context = context,
                    includeGravityAcceleration = holder.includeGravitySwitch.isChecked,
                )
            ) callback@{ resultCode, data ->
                if (resultCode != Activity.RESULT_OK || data == null) return@callback
                holder.recordedPose = PoseAngles(
                    azimuth = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_AZIMUTH, 0f),
                    pitch = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_PITCH, 0f),
                    roll = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_ROLL, 0f),
                )
                holder.recordedGravity = if (data.hasExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_GRAVITY_X)) {
                    GravityVector(
                        x = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_GRAVITY_X, 0f),
                        y = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_GRAVITY_Y, 0f),
                        z = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_GRAVITY_Z, 0f),
                    ).takeIf { it.isMeaningful() }
                } else {
                    null
                }
                renderRecordedPose(context, holder)
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val viewHolder = holder as PoseTriggerViewHolder
        val pose = viewHolder.recordedPose
        val gravity = viewHolder.recordedGravity
        return mapOf(
            "poseRecorded" to (pose != null),
            "targetAzimuth" to (pose?.azimuth?.toDouble() ?: 0.0),
            "targetPitch" to (pose?.pitch?.toDouble() ?: 0.0),
            "targetRoll" to (pose?.roll?.toDouble() ?: 0.0),
            "matchThreshold" to viewHolder.thresholdSlider.value.toDouble(),
            "includeGravityAcceleration" to viewHolder.includeGravitySwitch.isChecked,
            "gravityRecorded" to (gravity != null),
            "targetGravityX" to (gravity?.x?.toDouble() ?: 0.0),
            "targetGravityY" to (gravity?.y?.toDouble() ?: 0.0),
            "targetGravityZ" to (gravity?.z?.toDouble() ?: 0.0),
        )
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?,
    ): View? = null

    private fun renderRecordedPose(context: Context, holder: PoseTriggerViewHolder) {
        val poseText = holder.recordedPose?.format()
            ?: context.getString(R.string.pose_trigger_pose_not_recorded)
        if (!holder.includeGravitySwitch.isChecked) {
            holder.poseValue.text = poseText
            return
        }
        val gravityText = holder.recordedGravity?.format()
            ?: context.getString(R.string.pose_trigger_gravity_not_recorded)
        holder.poseValue.text = "$poseText\n$gravityText"
    }

    private fun renderThreshold(context: Context, holder: PoseTriggerViewHolder, value: Float) {
        holder.thresholdValue.text = context.getString(
            R.string.pose_trigger_match_threshold_value,
            value.toInt()
        )
    }
}
