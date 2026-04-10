package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音量控制模块（Beta）。
 * 使用 vFlow Core 控制不同音频流的音量。
 */
class CoreVolumeModule : BaseModule() {
    companion object {
        const val ACTION_KEEP = "keep"
        const val ACTION_SET = "set"
        const val ACTION_MUTE = "mute"
        const val ACTION_UNMUTE = "unmute"

        private val LEGACY_ACTION_ALIASES = mapOf(
            "保持不变" to ACTION_KEEP,
            "设置音量" to ACTION_SET,
            "静音" to ACTION_MUTE,
            "取消静音" to ACTION_UNMUTE
        )
    }

    override val id = "vflow.core.volume"
    override val metadata = ActionMetadata(
        name = "音量控制",
        nameStringRes = R.string.module_vflow_core_volume_name,
        description = "使用 vFlow Core 控制不同音频流的音量（音乐/通知/铃声等）。",
        descriptionStringRes = R.string.module_vflow_core_volume_desc,
        iconRes = R.drawable.rounded_volume_up_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override val uiProvider: ModuleUIProvider = CoreVolumeModuleUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    private val streamTypeNameResIds = mapOf(
        "music" to R.string.stream_type_music,
        "notification" to R.string.stream_type_notification,
        "ring" to R.string.stream_type_ring,
        "system" to R.string.stream_type_system,
        "alarm" to R.string.stream_type_alarm
    )

    private val streamTypeCodes = mapOf(
        "music" to 3,
        "notification" to 5,
        "ring" to 2,
        "system" to 1,
        "alarm" to 4
    )

    private val streamMaxVolumes = mapOf(
        "music" to 160,
        "notification" to 16,
        "ring" to 16,
        "system" to 16,
        "alarm" to 16
    )

    private fun percentToActualVolume(stream: String, percent: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return percent * maxVolume / 100
    }

    private fun actionInput(id: String, fallbackName: String, nameResId: Int): InputDefinition {
        return InputDefinition(
            id = id,
            name = fallbackName,
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_KEEP,
            options = listOf(ACTION_KEEP, ACTION_SET, ACTION_MUTE, ACTION_UNMUTE),
            optionsStringRes = listOf(
                R.string.volume_action_keep,
                R.string.volume_action_set,
                R.string.volume_action_mute,
                R.string.volume_action_unmute
            ),
            legacyValueMap = LEGACY_ACTION_ALIASES,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = nameResId
        )
    }

    private fun valueInput(id: String, fallbackName: String, nameResId: Int): InputDefinition {
        return InputDefinition(
            id = id,
            name = fallbackName,
            staticType = ParameterType.NUMBER,
            defaultValue = 50,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = nameResId
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        actionInput("music_action", "音乐操作", R.string.param_vflow_core_volume_music_action_name),
        valueInput("music_value", "音乐音量", R.string.param_vflow_core_volume_music_value_name),
        actionInput("notification_action", "通知操作", R.string.param_vflow_core_volume_notification_action_name),
        valueInput("notification_value", "通知音量", R.string.param_vflow_core_volume_notification_value_name),
        actionInput("ring_action", "铃声操作", R.string.param_vflow_core_volume_ring_action_name),
        valueInput("ring_value", "铃声音量", R.string.param_vflow_core_volume_ring_value_name),
        actionInput("system_action", "系统操作", R.string.param_vflow_core_volume_system_action_name),
        valueInput("system_value", "系统音量", R.string.param_vflow_core_volume_system_value_name),
        actionInput("alarm_action", "闹钟操作", R.string.param_vflow_core_volume_alarm_action_name),
        valueInput("alarm_value", "闹钟音量", R.string.param_vflow_core_volume_alarm_value_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_core_volume_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputsById = getInputs().associateBy { it.id }
        val parts = listOf("music", "notification", "ring", "system", "alarm")
            .mapNotNull { stream ->
                val actionInput = inputsById["${stream}_action"]
                val rawAction = step.parameters["${stream}_action"] as? String ?: ACTION_KEEP
                val action = actionInput?.normalizeEnumValue(rawAction) ?: rawAction
                if (action == ACTION_KEEP) {
                    null
                } else {
                    val value = (step.parameters["${stream}_value"] as? Number)?.toInt() ?: 50
                    describeAction(context, stream, action, value)
                }
            }

        return if (parts.isEmpty()) {
            context.getString(R.string.module_vflow_core_volume_name)
        } else {
            parts.joinToString(", ")
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        val step = context.allSteps[context.currentStepIndex]
        val inputsById = getInputs().associateBy { it.id }
        val streams = listOf("music", "notification", "ring", "system", "alarm")
        val results = mutableListOf<String>()
        var allSuccess = true

        for (stream in streams) {
            val actionInput = inputsById["${stream}_action"]
            val rawAction = step.parameters["${stream}_action"] as? String ?: ACTION_KEEP
            val action = actionInput?.normalizeEnumValue(rawAction) ?: rawAction
            if (action == ACTION_KEEP) continue

            val streamType = streamTypeCodes[stream] ?: continue
            val streamName = getStreamName(appContext, stream)

            val success = when (action) {
                ACTION_SET -> {
                    val percent = (step.parameters["${stream}_value"] as? Number)?.toInt() ?: 50
                    val actualVolume = percentToActualVolume(stream, percent)
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_volume_setting, streamName, percent)))
                    VFlowCoreBridge.setVolume(streamType, actualVolume).first
                }
                ACTION_MUTE -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_volume_muting, streamName)))
                    VFlowCoreBridge.muteVolume(streamType, true).first
                }
                ACTION_UNMUTE -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_volume_unmuting, streamName)))
                    VFlowCoreBridge.muteVolume(streamType, false).first
                }
                else -> false
            }

            if (success) {
                val value = (step.parameters["${stream}_value"] as? Number)?.toInt() ?: 50
                describeAction(appContext, stream, action, value)?.let(results::add)
            } else {
                allSuccess = false
            }
        }

        return if (allSuccess) {
            val summary = if (results.isEmpty()) {
                appContext.getString(R.string.module_vflow_core_volume_name)
            } else {
                results.joinToString(", ")
            }
            onProgress(ProgressUpdate(summary))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                appContext.getString(R.string.error_vflow_core_volume_partial_failed)
            )
        }
    }

    private fun getStreamName(context: Context, stream: String): String {
        val nameResId = streamTypeNameResIds[stream] ?: return stream
        return context.getString(nameResId)
    }

    private fun describeAction(context: Context, stream: String, action: String, value: Int): String? {
        val streamName = getStreamName(context, stream)
        return when (action) {
            ACTION_SET -> context.getString(R.string.summary_vflow_core_volume_action_set, streamName, value)
            ACTION_MUTE -> context.getString(R.string.summary_vflow_core_volume_action_mute, streamName)
            ACTION_UNMUTE -> context.getString(R.string.summary_vflow_core_volume_action_unmute, streamName)
            else -> null
        }
    }
}
