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
import com.google.android.material.slider.Slider

private class PoseTriggerViewHolder(
    view: View,
    val recordButton: MaterialButton,
    val poseValue: TextView,
    val thresholdValue: TextView,
    val thresholdSlider: Slider,
) : CustomEditorViewHolder(view) {
    var recordedPose: PoseAngles? = null
}

class PoseTriggerUIProvider : ModuleUIProvider {
    override fun getHandledInputIds(): Set<String> {
        return setOf("poseRecorded", "targetAzimuth", "targetPitch", "targetRoll", "matchThreshold")
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
        renderRecordedPose(context, holder)

        val threshold = ((currentParameters["matchThreshold"] as? Number)?.toFloat()
            ?: PoseTriggerModule.DEFAULT_MATCH_THRESHOLD.toFloat()).coerceIn(60f, 100f)
        holder.thresholdSlider.value = threshold
        renderThreshold(context, holder, threshold)

        holder.thresholdSlider.addOnChangeListener { _, value, _ ->
            renderThreshold(context, holder, value)
            onParametersChanged()
        }

        holder.recordButton.setOnClickListener {
            if (onStartActivityForResult == null) return@setOnClickListener
            onStartActivityForResult(PoseTriggerRecorderActivity.createIntent(context)) callback@{ resultCode, data ->
                if (resultCode != Activity.RESULT_OK || data == null) return@callback
                holder.recordedPose = PoseAngles(
                    azimuth = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_AZIMUTH, 0f),
                    pitch = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_PITCH, 0f),
                    roll = data.getFloatExtra(PoseTriggerRecorderActivity.EXTRA_RESULT_ROLL, 0f),
                )
                renderRecordedPose(context, holder)
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val viewHolder = holder as PoseTriggerViewHolder
        val pose = viewHolder.recordedPose
        return mapOf(
            "poseRecorded" to (pose != null),
            "targetAzimuth" to (pose?.azimuth?.toDouble() ?: 0.0),
            "targetPitch" to (pose?.pitch?.toDouble() ?: 0.0),
            "targetRoll" to (pose?.roll?.toDouble() ?: 0.0),
            "matchThreshold" to viewHolder.thresholdSlider.value.toDouble(),
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
        holder.poseValue.text = holder.recordedPose?.format()
            ?: context.getString(R.string.pose_trigger_pose_not_recorded)
    }

    private fun renderThreshold(context: Context, holder: PoseTriggerViewHolder, value: Float) {
        holder.thresholdValue.text = context.getString(
            R.string.pose_trigger_match_threshold_value,
            value.toInt()
        )
    }
}
