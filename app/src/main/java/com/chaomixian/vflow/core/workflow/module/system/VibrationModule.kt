// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/VibrationModule.kt
// 描述: 振动模块，使用Android系统自带的Vibrator API触发设备振动
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.module.InputDefinition.Companion.slider
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

/**
 * 振动模块
 * 使用Android系统自带的Vibrator API触发设备振动
 */
class VibrationModule : BaseModule() {

    override val id = "vflow.device.vibration"

    override val metadata = ActionMetadata(
        name = "振动",
        nameStringRes = R.string.module_vflow_device_vibration_name,
        description = "触发设备振动，支持设置持续时间和振动模式",
        descriptionStringRes = R.string.module_vflow_device_vibration_desc,
        iconRes = R.drawable.rounded_mobile_vibrate_24,
        category = "应用与系统"
    )

    // 振动模式常量
    companion object {
        const val MODE_ONCE = "once"           // 单次振动
        const val MODE_PATTERN = "pattern"     // 自定义模式
        const val MODE_NOTIFICATION = "notification"  // 通知模式
        const val MODE_RINGTONE = "ringtone"   // 铃声模式
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // 振动模式
        InputDefinition(
            id = "mode",
            name = "振动模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_ONCE,
            options = listOf(MODE_ONCE, MODE_NOTIFICATION, MODE_RINGTONE, MODE_PATTERN),
            optionsStringRes = listOf(
                R.string.option_vflow_device_vibration_once,
                R.string.option_vflow_device_vibration_notification,
                R.string.option_vflow_device_vibration_ringtone,
                R.string.option_vflow_device_vibration_pattern
            ),
            inputStyle = InputStyle.CHIP_GROUP,
            nameStringRes = R.string.param_vflow_device_vibration_mode_name
        ),

        // 单次振动：持续时间
        InputDefinition(
            id = "duration",
            name = "持续时间 (毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 200,
            sliderConfig = slider(10f, 5000f, 10f),
            inputStyle = InputStyle.SLIDER,
            nameStringRes = R.string.param_vflow_device_vibration_duration_name
        ),

        // 自定义模式：振动时长
        InputDefinition(
            id = "patternVibrate",
            name = "振动时长 (毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 500,
            sliderConfig = slider(10f, 5000f, 10f),
            inputStyle = InputStyle.SLIDER,
            isHidden = true,
            nameStringRes = R.string.param_vflow_device_vibration_pattern_vibrate_name
        ),

        // 自定义模式：暂停时长
        InputDefinition(
            id = "patternPause",
            name = "暂停时长 (毫秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 500,
            sliderConfig = slider(10f, 5000f, 10f),
            inputStyle = InputStyle.SLIDER,
            isHidden = true,
            nameStringRes = R.string.param_vflow_device_vibration_pattern_pause_name
        ),

        // 自定义模式：重复次数
        InputDefinition(
            id = "patternRepeat",
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 3,
            isHidden = true,
            nameStringRes = R.string.param_vflow_device_vibration_pattern_repeat_name
        ),

        // 振动强度 (仅部分设备支持)
        InputDefinition(
            id = "amplitude",
            name = "振动强度",
            staticType = ParameterType.NUMBER,
            defaultValue = 128,
            sliderConfig = slider(1f, 255f, 1f),
            inputStyle = InputStyle.SLIDER,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_vibration_amplitude_name
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val mode = step?.parameters?.get("mode") as? String ?: MODE_ONCE
        val isPatternMode = mode == MODE_PATTERN

        return getInputs().map { input ->
            when (input.id) {
                "duration" -> input.copy(isHidden = mode != MODE_ONCE)
                "patternVibrate", "patternPause", "patternRepeat" -> input.copy(isHidden = !isPatternMode)
                else -> input
            }
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_vibration_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_ONCE

        val modeText = when (mode) {
            MODE_ONCE -> {
                val duration = (step.parameters["duration"] as? Number)?.toInt() ?: 200
                context.getString(R.string.summary_vflow_device_vibration_once, duration)
            }
            MODE_NOTIFICATION -> context.getString(R.string.summary_vflow_device_vibration_notification)
            MODE_RINGTONE -> context.getString(R.string.summary_vflow_device_vibration_ringtone)
            MODE_PATTERN -> {
                val vibrate = (step.parameters["patternVibrate"] as? Number)?.toInt() ?: 500
                val pause = (step.parameters["patternPause"] as? Number)?.toInt() ?: 500
                val repeat = (step.parameters["patternRepeat"] as? Number)?.toInt() ?: 3
                context.getString(R.string.summary_vflow_device_vibration_pattern, vibrate, pause, repeat)
            }
            else -> context.getString(R.string.summary_vflow_device_vibration_once, 200)
        }

        val modePill = PillUtil.Pill(modeText, "mode", isModuleOption = true)
        return PillUtil.buildSpannable(context, metadata.getLocalizedName(context) + ": ", modePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val step = context.allSteps[context.currentStepIndex]
        val mode = step.parameters["mode"] as? String ?: MODE_ONCE

        // 获取 Vibrator 实例
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 检查设备是否支持振动
        if (!vibrator.hasVibrator()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_vibration_not_supported),
                appContext.getString(R.string.error_vflow_device_vibration_no_vibrator)
            )
        }

        // 获取振动强度
        val amplitude = (step.parameters["amplitude"] as? Number)?.toInt() ?: 128

        return try {
            when (mode) {
                MODE_ONCE -> {
                    val duration = (step.parameters["duration"] as? Number)?.toLong() ?: 200L
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_vibration_vibrating)))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Android 8.0+ 使用 VibrationEffect
                        val effect = VibrationEffect.createOneShot(duration, amplitude)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }

                    // 等待振动完成
                    delay(duration + 100)
                }

                MODE_NOTIFICATION -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_vibration_notification)))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // 通知振动模式：短-短-长
                        val timings = longArrayOf(0, 50, 50, 50, 50, 100)
                        val amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
                        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 50, 50, 50, 50, 100), -1)
                    }

                    delay(350)
                }

                MODE_RINGTONE -> {
                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_vibration_ringtone)))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // 铃声振动模式：长-短-短
                        val timings = longArrayOf(0, 200, 100, 50, 100, 50)
                        val amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
                        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 200, 100, 50, 100, 50), -1)
                    }

                    delay(550)
                }

                MODE_PATTERN -> {
                    val vibrateMs = (step.parameters["patternVibrate"] as? Number)?.toLong() ?: 500L
                    val pauseMs = (step.parameters["patternPause"] as? Number)?.toLong() ?: 500L
                    val repeatCount = (step.parameters["patternRepeat"] as? Number)?.toInt() ?: 3

                    onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_vibration_pattern)))

                    // 构建振动模式数组
                    val patternSize = (repeatCount * 2) + 1
                    val timings = LongArray(patternSize)
                    timings[0] = 0  // 延迟0ms开始

                    for (i in 0 until repeatCount) {
                        timings[i * 2 + 1] = vibrateMs   // 振动
                        timings[i * 2 + 2] = pauseMs     // 暂停
                    }

                    val totalDuration = timings.sum()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amplitudes = IntArray(patternSize)
                        amplitudes[0] = 0
                        for (i in 1 until patternSize) {
                            amplitudes[i] = if (i % 2 == 1) amplitude else 0
                        }
                        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(timings, -1)
                    }

                    delay(totalDuration + 100)
                }

                else -> {
                    return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_device_vibration_invalid_mode),
                        appContext.getString(R.string.error_vflow_device_vibration_unknown_mode, mode)
                    )
                }
            }

            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_vibration_completed), 100))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_vibration_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_device_vibration_unknown)
            )
        }
    }
}
