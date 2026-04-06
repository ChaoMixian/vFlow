// 文件: app/src/main/java/com/chaomixian/vflow/core/workflow/module/core/CoreTouchReplayModule.kt

package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.TouchRecordingData
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 触摸回放模块（Beta）。
 * 录制触摸操作序列并回放，可调节回放速度。
 */
class CoreTouchReplayModule : BaseModule() {

    override val id = "vflow.core.touch_replay"
    override val metadata = ActionMetadata(
        name = "触摸回放",  // Fallback
        nameStringRes = R.string.module_vflow_core_touch_replay_name,
        description = "回放录制的触摸操作序列，可调节回放速度",  // Fallback
        descriptionStringRes = R.string.module_vflow_core_touch_replay_desc,
        iconRes = R.drawable.rounded_all_out_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override val uiProvider = CoreTouchReplayModuleUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "speed",
            name = "回放速度",
            staticType = ParameterType.NUMBER,
            defaultValue = 1.0,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_core_touch_replay_speed_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_touch_replay_success_name),
        OutputDefinition("event_count", "事件数量", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_core_touch_replay_event_count_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val recordingData = step.parameters["recording_data"] as? String
        val speed = step.parameters["speed"] as? Number ?: 1.0
        val speedValue = speed.toDouble()

        return if (recordingData.isNullOrEmpty()) {
            context.getString(R.string.summary_vflow_core_touch_replay_not_recorded)
        } else {
            val data = TouchRecordingData.fromJson(recordingData)
            if (data != null) {
                context.getString(R.string.summary_vflow_core_touch_replay_with_data, data.events.size, speedValue)
            } else {
                context.getString(R.string.summary_vflow_core_touch_replay_error)
            }
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 确保 Core 连接
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(context.applicationContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_touch_replay_not_connected),
                appContext.getString(R.string.error_vflow_core_touch_replay_service_not_running)
            )
        }

        // 2. 获取参数
        val recordingData = context.getVariableAsString("recording_data", "")
        if (recordingData.isNullOrEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_touch_replay_not_recorded),
                appContext.getString(R.string.error_vflow_core_touch_replay_no_data)
            )
        }

        // 现在 variables 是 Map<String, VObject>，使用 getVariableAsNumber 获取
        val speed = context.getVariableAsNumber("speed")?.toFloat() ?: 1.0f
        val data = TouchRecordingData.fromJson(recordingData)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_touch_replay_data_error),
                appContext.getString(R.string.error_vflow_core_touch_replay_corrupted)
            )

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_touch_replay_replaying, data.events.size)))

        // 3. 调用 VFlowCoreBridge 回放
        val success = VFlowCoreBridge.replayTouchSequence(recordingData, speed)

        return if (success) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_touch_replay_completed)))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "event_count" to VNumber(data.events.size.toDouble())
            ))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_touch_replay_failed),
                appContext.getString(R.string.error_vflow_core_touch_replay_execution_failed)
            )
        }
    }
}
