package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlin.math.roundToInt

class PoseTriggerModule : BaseModule() {
    companion object {
        const val DEFAULT_MATCH_THRESHOLD = 90.0
        const val DEFAULT_POSE_RECORDED = false
    }

    override val id = "vflow.trigger.pose"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_pose_name,
        descriptionStringRes = R.string.module_vflow_trigger_pose_desc,
        name = "姿态触发器",
        description = "当设备姿态接近录制的三轴姿态时触发工作流",
        iconRes = R.drawable.rounded_auto_awesome_motion_24,
        category = "触发器",
        categoryId = "trigger",
    )

    override val uiProvider = PoseTriggerUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "poseRecorded",
            name = "已录制姿态",
            staticType = ParameterType.BOOLEAN,
            defaultValue = DEFAULT_POSE_RECORDED,
            isHidden = true,
        ),
        InputDefinition(
            id = "targetAzimuth",
            name = "目标 Z 轴",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            isHidden = true,
        ),
        InputDefinition(
            id = "targetPitch",
            name = "目标 X 轴",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            isHidden = true,
        ),
        InputDefinition(
            id = "targetRoll",
            name = "目标 Y 轴",
            staticType = ParameterType.NUMBER,
            defaultValue = 0.0,
            isHidden = true,
        ),
        InputDefinition(
            id = "matchThreshold",
            name = "姿态匹配度",
            staticType = ParameterType.NUMBER,
            defaultValue = DEFAULT_MATCH_THRESHOLD,
            inputStyle = InputStyle.SLIDER,
            sliderConfig = InputDefinition.slider(60f, 100f, 1f),
            isHidden = true,
            nameStringRes = R.string.param_vflow_trigger_pose_match_threshold_name,
        ),
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "azimuth",
            name = "当前 Z 轴",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_pose_azimuth_name,
        ),
        OutputDefinition(
            id = "pitch",
            name = "当前 X 轴",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_pose_pitch_name,
        ),
        OutputDefinition(
            id = "roll",
            name = "当前 Y 轴",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_pose_roll_name,
        ),
        OutputDefinition(
            id = "matchScore",
            name = "匹配度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_pose_match_score_name,
        ),
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val poseRecorded = step.parameters["poseRecorded"] as? Boolean ?: DEFAULT_POSE_RECORDED
        if (!poseRecorded) {
            return context.getString(R.string.summary_vflow_trigger_pose_not_recorded)
        }

        val targetPose = PoseAngles(
            azimuth = (step.parameters["targetAzimuth"] as? Number)?.toFloat() ?: 0f,
            pitch = (step.parameters["targetPitch"] as? Number)?.toFloat() ?: 0f,
            roll = (step.parameters["targetRoll"] as? Number)?.toFloat() ?: 0f,
        )
        val threshold = ((step.parameters["matchThreshold"] as? Number)?.toDouble()
            ?: DEFAULT_MATCH_THRESHOLD).roundToInt()
        val thresholdPill = PillUtil.Pill("$threshold%", "matchThreshold")
        val posePill = PillUtil.Pill(targetPose.format(), "pose")
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_pose_prefix),
            " ",
            posePill,
            " · ",
            thresholdPill,
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit,
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_pose_triggered)))
        val triggerData = context.triggerData as? PoseTriggerData
            ?: PoseTriggerData(azimuth = 0f, pitch = 0f, roll = 0f, matchScore = 0f)
        return ExecutionResult.Success(
            outputs = mapOf(
                "azimuth" to VNumber(triggerData.azimuth.toDouble()),
                "pitch" to VNumber(triggerData.pitch.toDouble()),
                "roll" to VNumber(triggerData.roll.toDouble()),
                "matchScore" to VNumber(triggerData.matchScore.toDouble()),
            )
        )
    }
}
