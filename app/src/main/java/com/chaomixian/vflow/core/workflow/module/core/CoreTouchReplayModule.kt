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
        name = "触摸回放",
        description = "回放录制的触摸操作序列，可调节回放速度",
        iconRes = R.drawable.rounded_all_out_24,
        category = "Core (Beta)"
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
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("event_count", "事件数量", VTypeRegistry.NUMBER.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val recordingData = step.parameters["recording_data"] as? String
        val speed = step.parameters["speed"] as? Number ?: 1.0

        return if (recordingData.isNullOrEmpty()) {
            "触摸回放 (未录制)"
        } else {
            val data = TouchRecordingData.fromJson(recordingData)
            if (data != null) {
                "回放 ${data.events.size} 个触摸事件，速度 ${speed}x"
            } else {
                "触摸回放 (数据错误)"
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
                "Core 未连接",
                "vFlow Core 服务未运行。请确保已授予 Shizuku 或 Root 权限。"
            )
        }

        // 2. 获取参数
        val recordingData = context.variables["recording_data"] as? String
        if (recordingData.isNullOrEmpty()) {
            return ExecutionResult.Failure("未录制", "请先在编辑器中录制触摸操作")
        }

        val speed = (context.variables["speed"] as? Number)?.toFloat() ?: 1.0f
        val data = TouchRecordingData.fromJson(recordingData)
            ?: return ExecutionResult.Failure("数据错误", "录制数据已损坏")

        onProgress(ProgressUpdate("正在回放 ${data.events.size} 个触摸事件..."))

        // 3. 调用 VFlowCoreBridge 回放
        val success = VFlowCoreBridge.replayTouchSequence(recordingData, speed)

        return if (success) {
            onProgress(ProgressUpdate("回放完成"))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "event_count" to VNumber(data.events.size.toDouble())
            ))
        } else {
            ExecutionResult.Failure("回放失败", "触摸回放执行失败")
        }
    }
}
