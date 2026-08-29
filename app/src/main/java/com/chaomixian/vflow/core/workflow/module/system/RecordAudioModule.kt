// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/RecordAudioModule.kt
// 描述: 录音模块，使用Android MediaRecorder通过麦克风录制指定时长的音频并保存为文件
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VFile
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 录音模块
 * 使用Android MediaRecorder录制指定时长的音频并保存为文件
 */
class RecordAudioModule : BaseModule() {

    override val id = "vflow.device.record_audio"

    override val metadata = ActionMetadata(
        name = "录音",
        nameStringRes = R.string.module_vflow_device_record_audio_name,
        description = "使用麦克风录制指定时长的音频并保存为文件。",
        descriptionStringRes = R.string.module_vflow_device_record_audio_desc,
        iconRes = R.drawable.rounded_mic_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Record audio from the device microphone for a specified duration and save it to a file.",
        workflowStepDescription = "Record audio from the microphone for a given duration.",
        inputHints = mapOf(
            "duration" to "Recording duration in seconds (e.g. 5, 10).",
            "save_path" to "Optional output file path. If empty, saves to workflow temporary directory.",
            "audio_source" to "Audio source: 'mic', 'camcorder', or 'voice_recognition'.",
            "audio_format" to "Audio format: 'm4a' (AAC) or '3gp' (AMR)."
        ),
        requiredInputIds = setOf("duration")
    )

    companion object {
        const val SOURCE_MIC = "mic"
        const val SOURCE_CAMCORDER = "camcorder"
        const val SOURCE_VOICE_RECOGNITION = "voice_recognition"

        const val FORMAT_M4A = "m4a"
        const val FORMAT_3GP = "3gp"

        private val SOURCE_OPTIONS = listOf(SOURCE_MIC, SOURCE_CAMCORDER, SOURCE_VOICE_RECOGNITION)
        private val FORMAT_OPTIONS = listOf(FORMAT_M4A, FORMAT_3GP)

        private val SOURCE_OPTION_RES = listOf(
            R.string.option_vflow_device_record_audio_source_mic,
            R.string.option_vflow_device_record_audio_source_camcorder,
            R.string.option_vflow_device_record_audio_source_voice_recognition
        )

        private val FORMAT_OPTION_RES = listOf(
            R.string.option_vflow_device_record_audio_format_m4a,
            R.string.option_vflow_device_record_audio_format_3gp
        )

        private val SOURCE_LEGACY_MAP = mapOf(
            "麦克风" to SOURCE_MIC,
            "摄像机" to SOURCE_CAMCORDER,
            "摄像机麦克风" to SOURCE_CAMCORDER,
            "语音识别" to SOURCE_VOICE_RECOGNITION,
            "语音识别源" to SOURCE_VOICE_RECOGNITION
        )

        private val FORMAT_LEGACY_MAP = mapOf(
            "M4A" to FORMAT_M4A,
            "M4A (AAC)" to FORMAT_M4A,
            "3GP" to FORMAT_3GP,
            "3GP (AMR)" to FORMAT_3GP
        )
    }

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.MICROPHONE)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "duration",
            name = "录音时长 (秒)",
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id, VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_record_audio_duration_name
        ),
        InputDefinition(
            id = "save_path",
            name = "保存路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.FILE.id),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_record_audio_save_path_name
        ),
        InputDefinition(
            id = "audio_source",
            name = "音频源",
            staticType = ParameterType.ENUM,
            defaultValue = SOURCE_MIC,
            options = SOURCE_OPTIONS,
            optionsStringRes = SOURCE_OPTION_RES,
            legacyValueMap = SOURCE_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_record_audio_source_name
        ),
        InputDefinition(
            id = "audio_format",
            name = "音频格式",
            staticType = ParameterType.ENUM,
            defaultValue = FORMAT_M4A,
            options = FORMAT_OPTIONS,
            optionsStringRes = FORMAT_OPTION_RES,
            legacyValueMap = FORMAT_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_record_audio_format_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_device_record_audio_success_name),
        OutputDefinition("file", "文件对象", VTypeRegistry.FILE.id, nameStringRes = R.string.output_vflow_device_record_audio_file_name),
        OutputDefinition("path", "文件路径", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_device_record_audio_path_name),
        OutputDefinition("duration", "实际录制时长", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_device_record_audio_duration_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val duration = step.parameters["duration"]?.toString() ?: "5"
        return context.getString(R.string.summary_vflow_device_record_audio, duration)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val inputs = getInputs()

        val durationRaw = context.getVariableAsNumber("duration") ?: 5.0
        val durationSeconds = durationRaw.coerceIn(0.5, 3600.0)
        val durationMs = (durationSeconds * 1000).toLong()

        val audioSourceStr = inputs.normalizeEnumValue("audio_source", context.getVariableAsString("audio_source", SOURCE_MIC)) ?: SOURCE_MIC
        val audioFormatStr = inputs.normalizeEnumValue("audio_format", context.getVariableAsString("audio_format", FORMAT_M4A)) ?: FORMAT_M4A
        val customSavePathRaw = VariableResolver.resolve(context.getVariableAsString("save_path", ""), context).trim()

        val fileExtension = if (audioFormatStr == FORMAT_3GP) "3gp" else "m4a"
        val mimeType = if (audioFormatStr == FORMAT_3GP) "audio/3gpp" else "audio/mp4"

        val targetFile = if (customSavePathRaw.isNotEmpty()) {
            val file = File(customSavePathRaw)
            if (customSavePathRaw.endsWith("/") || file.isDirectory) {
                file.mkdirs()
                File(file, "recording_${System.currentTimeMillis()}.$fileExtension")
            } else {
                file.parentFile?.mkdirs()
                file
            }
        } else {
            val workDir = context.workDir
            workDir.mkdirs()
            File(workDir, "recording_${System.currentTimeMillis()}.$fileExtension")
        }

        val recorder: MediaRecorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (e: Exception) {
            return@withContext ExecutionResult.Failure("初始化录音器失败", e.localizedMessage ?: "无法创建 MediaRecorder 实例。")
        }

        val audioSource = when (audioSourceStr) {
            SOURCE_CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
            SOURCE_VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.MIC
        }

        try {
            recorder.setAudioSource(audioSource)
            if (audioFormatStr == FORMAT_3GP) {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            }
            recorder.setOutputFile(targetFile.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            try { recorder.reset() } catch (_: Exception) {}
            try { recorder.release() } catch (_: Exception) {}
            return@withContext ExecutionResult.Failure("启动录音失败", e.localizedMessage ?: "配置或启动 MediaRecorder 失败。")
        }

        val startTime = System.currentTimeMillis()
        var actualDurationSeconds = 0.0

        try {
            val updateIntervalMs = 500L
            var elapsedMs = 0L

            while (elapsedMs < durationMs) {
                val currentSeconds = (elapsedMs / 1000).toInt() + 1
                val totalSeconds = (durationMs / 1000).toInt()
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_record_audio_recording, currentSeconds, totalSeconds)))

                val sleepTime = minOf(updateIntervalMs, durationMs - elapsedMs)
                delay(sleepTime)
                elapsedMs = System.currentTimeMillis() - startTime
            }
        } finally {
            try {
                recorder.stop()
            } catch (e: Exception) {
                // Ignore stop exceptions if cancelled or stopped early
            }
            try {
                recorder.reset()
            } catch (_: Exception) {}
            try {
                recorder.release()
            } catch (_: Exception) {}
            actualDurationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        }

        if (!targetFile.exists() || targetFile.length() == 0L) {
            return@withContext ExecutionResult.Failure("录音失败", "录音文件为空或未成功生成。")
        }

        if (customSavePathRaw.isNotEmpty() && !customSavePathRaw.contains("/.") && !customSavePathRaw.contains("/Android/data/")) {
            try {
                android.media.MediaScannerConnection.scanFile(appContext, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            } catch (_: Exception) {}
        }

        val fileUri = Uri.fromFile(targetFile)
        val fileObj = VFile(fileUri.toString(), mimeType)

        ExecutionResult.Success(
            mapOf(
                "success" to VBoolean(true),
                "file" to fileObj,
                "path" to VString(targetFile.absolutePath),
                "duration" to VNumber(actualDurationSeconds)
            )
        )
    }
}
