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
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.buildSpannedString

/**
 * 音量控制模块（Beta）。
 * 使用 vFlow Core 控制不同音频流的音量。
 */
class CoreVolumeModule : BaseModule() {

    override val id = "vflow.core.volume"
    override val metadata = ActionMetadata(
        name = "音量控制",
        nameStringRes = R.string.module_vflow_core_volume_name,
        description = "使用 vFlow Core 控制不同音频流的音量（音乐/通知/铃声等）。",
        descriptionStringRes = R.string.module_vflow_core_volume_desc,
        iconRes = R.drawable.rounded_volume_up_24,
        category = "Core (Beta)"
    )

    override val uiProvider: ModuleUIProvider = CoreVolumeModuleUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    private val streamTypeNames = mapOf(
        "music" to "音乐",
        "notification" to "通知",
        "ring" to "铃声",
        "system" to "系统",
        "alarm" to "闹钟"
    )

    private val streamTypeCodes = mapOf(
        "music" to 3,     // STREAM_MUSIC
        "notification" to 5, // STREAM_NOTIFICATION
        "ring" to 2,     // STREAM_RING
        "system" to 1,   // STREAM_SYSTEM
        "alarm" to 4     // STREAM_ALARM
    )

    // 音频流的最大音量（实际值）
    private val streamMaxVolumes = mapOf(
        "music" to 160,
        "notification" to 16,
        "ring" to 16,
        "system" to 16,
        "alarm" to 16
    )

    /**
     * 将百分比（0-100）转换为实际音量值
     * @param stream 音频流名称（如 "music", "notification" 等）
     * @param percent 百分比值（0-100）
     * @return 实际音量值
     */
    private fun percentToActualVolume(stream: String, percent: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return (percent * maxVolume / 100)
    }

    /**
     * 将实际音量值转换为百分比（0-100）
     * @param stream 音频流名称
     * @param actualVolume 实际音量值
     * @return 百分比值（0-100）
     */
    private fun actualVolumeToPercent(stream: String, actualVolume: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // 音乐音量
        InputDefinition("music_action", "音乐操作", ParameterType.ENUM, "keep",
            options = listOf("keep", "set", "mute", "unmute"),
            acceptsMagicVariable = false, isHidden = true),
        InputDefinition("music_value", "音乐音量", ParameterType.NUMBER, 50,
            acceptsMagicVariable = false, isHidden = true),

        // 通知音量
        InputDefinition("notification_action", "通知操作", ParameterType.ENUM, "keep",
            options = listOf("keep", "set", "mute", "unmute"),
            acceptsMagicVariable = false, isHidden = true),
        InputDefinition("notification_value", "通知音量", ParameterType.NUMBER, 50,
            acceptsMagicVariable = false, isHidden = true),

        // 铃声音量
        InputDefinition("ring_action", "铃声操作", ParameterType.ENUM, "keep",
            options = listOf("keep", "set", "mute", "unmute"),
            acceptsMagicVariable = false, isHidden = true),
        InputDefinition("ring_value", "铃声音量", ParameterType.NUMBER, 50,
            acceptsMagicVariable = false, isHidden = true),

        // 系统音量
        InputDefinition("system_action", "系统操作", ParameterType.ENUM, "keep",
            options = listOf("keep", "set", "mute", "unmute"),
            acceptsMagicVariable = false, isHidden = true),
        InputDefinition("system_value", "系统音量", ParameterType.NUMBER, 50,
            acceptsMagicVariable = false, isHidden = true),

        // 闹钟音量
        InputDefinition("alarm_action", "闹钟操作", ParameterType.ENUM, "keep",
            options = listOf("keep", "set", "mute", "unmute"),
            acceptsMagicVariable = false, isHidden = true),
        InputDefinition("alarm_value", "闹钟音量", ParameterType.NUMBER, 50,
            acceptsMagicVariable = false, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val parts = mutableListOf<String>()

        val streams = listOf("music", "notification", "ring", "system", "alarm")
        for (stream in streams) {
            val action = step.parameters["${stream}_action"] as? String ?: "keep"
            if (action != "keep") {
                val streamName = streamTypeNames[stream] ?: stream
                val actionText = when (action) {
                    "set" -> {
                        val value = step.parameters["${stream}_value"] as? Number ?: 50
                        "设为${value}"
                    }
                    "mute" -> "静音"
                    "unmute" -> "取消静音"
                    else -> ""
                }
                parts.add("$streamName$actionText")
            }
        }

        return if (parts.isEmpty()) {
            "音量控制"
        } else {
            parts.joinToString("、")
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
        val step = context.allSteps[context.currentStepIndex]
        val streams = listOf("music", "notification", "ring", "system", "alarm")
        var allSuccess = true
        val results = mutableListOf<String>()

        // 3. 对每个音频流执行操作
        for (stream in streams) {
            val action = step.parameters["${stream}_action"] as? String ?: "keep"
            if (action == "keep") continue

            val streamType = streamTypeCodes[stream] ?: continue
            val streamName = streamTypeNames[stream] ?: stream

            val success = when (action) {
                "set" -> {
                    val percent = step.parameters["${stream}_value"] as? Number ?: 50
                    val actualVolume = percentToActualVolume(stream, percent.toInt())
                    onProgress(ProgressUpdate("正在设置${streamName}音量为 $percent%..."))
                    val result = VFlowCoreBridge.setVolume(streamType, actualVolume)
                    result.first
                }
                "mute" -> {
                    onProgress(ProgressUpdate("正在静音${streamName}..."))
                    val result = VFlowCoreBridge.muteVolume(streamType, true)
                    result.first
                }
                "unmute" -> {
                    onProgress(ProgressUpdate("正在取消静音${streamName}..."))
                    val result = VFlowCoreBridge.muteVolume(streamType, false)
                    result.first
                }
                else -> false
            }

            if (success) {
                val actionText = when (action) {
                    "set" -> {
                        val value = step.parameters["${stream}_value"] as? Number ?: 50
                        "设为${value}"
                    }
                    "mute" -> "已静音"
                    "unmute" -> "已取消静音"
                    else -> ""
                }
                results.add("${streamName}$actionText")
            } else {
                allSuccess = false
            }
        }

        return if (allSuccess) {
            val summary = if (results.isEmpty()) "音量控制" else results.joinToString("、")
            onProgress(ProgressUpdate(summary))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                "执行失败",
                "部分音量操作失败。请检查：\n" +
                "1. 是否开启了勿扰模式（DND）？\n" +
                "2. 是否授予了 Shizuku 或 Root 权限？\n" +
                "3. vFlow Core 是否正常运行？"
            )
        }
    }
}
