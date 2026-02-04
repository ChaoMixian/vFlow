package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音量状态读取模块（Beta）。
 * 使用 vFlow Core 读取不同音频流的当前音量。
 */
class CoreVolumeStateModule : BaseModule() {

    override val id = "vflow.core.volume_state"
    override val metadata = ActionMetadata(
        name = "读取音量",
        nameStringRes = R.string.module_vflow_core_volume_state_name,
        description = "使用 vFlow Core 读取不同音频流的当前音量。",
        descriptionStringRes = R.string.module_vflow_core_volume_state_desc,
        iconRes = R.drawable.rounded_volume_up_24,
        category = "Core (Beta)"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    // 音频流的最大音量（实际值）
    private val streamMaxVolumes = mapOf(
        "music" to 160,
        "notification" to 16,
        "ring" to 16,
        "system" to 16,
        "alarm" to 16,
        "call" to 5
    )

    /**
     * 将实际音量值转换为百分比（0-100）
     */
    private fun actualVolumeToPercent(stream: String, actualVolume: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0
    }

    override fun getInputs(): List<InputDefinition> = listOf()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("musicLevel", "音乐音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("notificationLevel", "通知音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("ringLevel", "铃声音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("systemLevel", "系统音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("alarmLevel", "闹钟音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("callLevel", "通话音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("musicMax", "音乐最大音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("notificationMax", "通知最大音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("ringMax", "铃声最大音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("systemMax", "系统最大音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("alarmMax", "闹钟最大音量", VTypeRegistry.NUMBER.id),
        OutputDefinition("callMax", "通话最大音量", VTypeRegistry.NUMBER.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_core_volume_state)
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

        // 2. 读取所有音量
        onProgress(ProgressUpdate("正在读取音量状态..."))
        val volumes = VFlowCoreBridge.getAllVolumes()

        return if (volumes != null) {
            onProgress(ProgressUpdate("成功读取音量状态"))
            ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "musicLevel" to VNumber(actualVolumeToPercent("music", volumes.musicCurrent).toLong()),
                "musicMax" to VNumber(100),
                "notificationLevel" to VNumber(actualVolumeToPercent("notification", volumes.notificationCurrent).toLong()),
                "notificationMax" to VNumber(100),
                "ringLevel" to VNumber(actualVolumeToPercent("ring", volumes.ringCurrent).toLong()),
                "ringMax" to VNumber(100),
                "systemLevel" to VNumber(actualVolumeToPercent("system", volumes.systemCurrent).toLong()),
                "systemMax" to VNumber(100),
                "alarmLevel" to VNumber(actualVolumeToPercent("alarm", volumes.alarmCurrent).toLong()),
                "alarmMax" to VNumber(100),
                "callLevel" to VNumber(actualVolumeToPercent("call", volumes.callCurrent).toLong()),
                "callMax" to VNumber(100)
            ))
        } else {
            ExecutionResult.Failure("执行失败", "vFlow Core 读取音量失败")
        }
    }
}
