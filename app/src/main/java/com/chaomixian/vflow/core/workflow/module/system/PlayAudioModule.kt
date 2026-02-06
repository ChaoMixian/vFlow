// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/PlayAudioModule.kt
// 描述: 播放音频模块，支持播放系统自带音频和本地音频文件。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayAudioModule : BaseModule() {

    override val id = "vflow.device.play_audio"

    override val metadata = ActionMetadata(
        name = "播放音频",
        nameStringRes = R.string.module_vflow_device_play_audio_name,
        description = "播放系统自带的音频或本地音频文件，支持设置音量。",
        descriptionStringRes = R.string.module_vflow_device_play_audio_desc,
        iconRes = R.drawable.rounded_volume_up_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider = PlayAudioUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "audioType",
            name = "音频类型",
            staticType = ParameterType.ENUM,
            defaultValue = "system",
            options = listOf("system", "local"),
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_device_play_audio_type_name
        ),
        InputDefinition(
            id = "systemSound",
            name = "系统音频",
            staticType = ParameterType.ENUM,
            defaultValue = "notification",
            options = listOf(
                "notification", "alarm", "ringtone",
                "notification_2", "notification_3", "notification_4",
                "notification_5"
            ),
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_device_play_audio_system_sound_name
        ),
        InputDefinition(
            id = "localFile",
            name = "本地文件路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            isHidden = true,
            nameStringRes = R.string.param_vflow_device_play_audio_local_file_name
        ),
        InputDefinition(
            id = "volume",
            name = "音量 (%)",
            staticType = ParameterType.NUMBER,
            defaultValue = 100,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            nameStringRes = R.string.param_vflow_device_play_audio_volume_name
        ),
        InputDefinition(
            id = "await",
            name = "等待播放完成",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_device_play_audio_await_name
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        val audioType = step?.parameters?.get("audioType") as? String ?: "system"
        val isSystemAudio = audioType == "system"

        return getInputs().map { input ->
            when (input.id) {
                "systemSound" -> input.copy(isHidden = !isSystemAudio)
                "localFile" -> input.copy(isHidden = isSystemAudio)
                else -> input
            }
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_play_audio_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val audioType = step.parameters["audioType"] as? String ?: "system"
        val volume = step.parameters["volume"] as? Number ?: 100

        val audioSourceText = when (audioType) {
            "local" -> {
                val filePath = step.parameters["localFile"] as? String ?: ""
                if (filePath.isNotBlank()) {
                    val fileName = File(filePath).name
                    fileName
                } else {
                    context.getString(R.string.text_not_selected)
                }
            }
            else -> {
                val soundType = step.parameters["systemSound"] as? String ?: "notification"
                getSystemSoundDisplayName(context, soundType)
            }
        }

        val volumeText = if (volume.toInt() != 100) "音量：${volume}%" else ""

        val result = StringBuilder()
        result.append(context.getString(R.string.module_vflow_device_play_audio_name))
        result.append(": ")
        result.append(audioSourceText)

        if (volumeText.isNotEmpty()) {
            result.append(" ")
            result.append(volumeText)
        }

        return result.toString()
    }

    private fun getSystemSoundDisplayName(context: Context, soundType: String): CharSequence {
        val soundNames = mapOf(
            "notification" to context.getString(R.string.sound_notification),
            "alarm" to context.getString(R.string.sound_alarm),
            "ringtone" to context.getString(R.string.sound_ringtone),
            "notification_2" to context.getString(R.string.sound_notification_2),
            "notification_3" to context.getString(R.string.sound_notification_3),
            "notification_4" to context.getString(R.string.sound_notification_4),
            "notification_5" to context.getString(R.string.sound_notification_5)
        )
        return soundNames[soundType] ?: soundType
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val step = context.allSteps[context.currentStepIndex]
        val audioType = step.parameters["audioType"] as? String ?: "system"
        val volumePercent = step.parameters["volume"] as? Number ?: 100
        val awaitComplete = step.parameters["await"] as? Boolean ?: true

        val mediaPlayer = MediaPlayer()

        return try {
            val audioUri: Uri? = when (audioType) {
                "local" -> {
                    val filePath = step.parameters["localFile"] as? String ?: ""
                    if (filePath.isBlank()) {
                        mediaPlayer.release()
                        return ExecutionResult.Failure(
                            appContext.getString(R.string.error_vflow_device_play_audio_no_file),
                            appContext.getString(R.string.error_vflow_device_play_audio_file_required)
                        )
                    }
                    val file = File(filePath)
                    if (!file.exists()) {
                        mediaPlayer.release()
                        return ExecutionResult.Failure(
                            appContext.getString(R.string.error_vflow_device_play_audio_file_not_found),
                            appContext.getString(R.string.error_vflow_device_play_audio_file_missing, filePath)
                        )
                    }
                    Uri.fromFile(file)
                }
                else -> getSystemSoundUri(audioType, step.parameters["systemSound"] as? String ?: "notification")
            }

            if (audioUri == null) {
                mediaPlayer.release()
                return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_device_play_audio_invalid_source),
                    appContext.getString(R.string.error_vflow_device_play_audio_source_error)
                )
            }

            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_play_audio_preparing)))

            withContext(Dispatchers.IO) {
                mediaPlayer.setDataSource(appContext, audioUri)
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mediaPlayer.prepare()
                mediaPlayer.setVolume(volumePercent.toFloat() / 100f, volumePercent.toFloat() / 100f)
            }

            // start() 必须在主线程调用
            withContext(Dispatchers.Main) {
                mediaPlayer.start()
            }

            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_play_audio_playing)))

            // 如果需要等待播放完成
            if (awaitComplete) {
                try {
                    withContext(Dispatchers.IO) {
                        while (mediaPlayer.isPlaying) {
                            kotlinx.coroutines.delay(100)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    mediaPlayer.stop()
                    mediaPlayer.release()
                    throw e
                }
                mediaPlayer.release()
            } else {
                // 不等待播放完成，在后台自动释放
                // 注意：不能在这里释放，需要异步等待播放完后释放
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        while (mediaPlayer.isPlaying) {
                            kotlinx.coroutines.delay(100)
                        }
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // 被取消时停止播放
                        try {
                            mediaPlayer.stop()
                        } catch (_: Exception) {}
                    } finally {
                        mediaPlayer.release()
                    }
                }
            }

            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            mediaPlayer.release()
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_play_audio_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_device_play_audio_unknown)
            )
        }
    }

    private fun getSystemSoundUri(type: String, soundType: String): Uri? {
        val soundTypeCode = when (soundType) {
            "alarm" -> RingtoneManager.TYPE_ALARM
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "notification" -> RingtoneManager.TYPE_NOTIFICATION
            "notification_2" -> RingtoneManager.TYPE_NOTIFICATION
            "notification_3" -> RingtoneManager.TYPE_NOTIFICATION
            "notification_4" -> RingtoneManager.TYPE_NOTIFICATION
            "notification_5" -> RingtoneManager.TYPE_NOTIFICATION
            else -> RingtoneManager.TYPE_NOTIFICATION
        }

        val ringtoneManager = RingtoneManager(appContext)
        ringtoneManager.setType(soundTypeCode)

        return try {
            val cursor = ringtoneManager.cursor
            val total = cursor.count

            if (total == 0) {
                // 如果没有找到，直接获取默认铃声
                when (soundTypeCode) {
                    RingtoneManager.TYPE_ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    RingtoneManager.TYPE_RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            } else {
                // 根据 soundType 选择不同的通知音
                val index = when (soundType) {
                    "notification" -> 0
                    "notification_2" -> 1
                    "notification_3" -> 2
                    "notification_4" -> 3
                    "notification_5" -> 4.coerceAtMost(total - 1)
                    else -> 0
                }

                if (index < total) {
                    cursor.moveToPosition(index)
                    ringtoneManager.getRingtoneUri(index)
                } else {
                    ringtoneManager.getRingtoneUri(0)
                }
            }
        } catch (e: Exception) {
            // 发生异常时返回默认铃声
            when (soundTypeCode) {
                RingtoneManager.TYPE_ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                RingtoneManager.TYPE_RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }
    }
}
