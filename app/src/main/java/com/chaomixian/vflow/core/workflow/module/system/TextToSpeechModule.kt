// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/TextToSpeechModule.kt
// 描述: TTS文本转语音模块，使用Android系统自带的TextToSpeech API朗读文本
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * TTS文本转语音模块
 * 使用Android系统自带的TextToSpeech API将文本转换为语音并朗读
 */
class TextToSpeechModule : BaseModule() {

    override val id = "vflow.device.text_to_speech"

    override val metadata = ActionMetadata(
        name = "朗读文本",
        nameStringRes = R.string.module_vflow_device_text_to_speech_name,
        description = "使用Android系统自带的TTS引擎将文本转换为语音并朗读",
        descriptionStringRes = R.string.module_vflow_device_text_to_speech_desc,
        iconRes = R.drawable.rounded_text_to_speech_24,
        category = "应用与系统"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        // 基础参数：要朗读的文本
        InputDefinition(
            id = "text",
            name = "要朗读的文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            hint = "输入要朗读的文本内容",
            supportsRichText = true,
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_text_to_speech_text_name
        ),

        // 高级设置：语言
        InputDefinition(
            id = "language",
            name = "语言",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = listOf(
                "自动",
                "中文（中国）",
                "英语（美国）",
                "英语（英国）",
                "日语",
                "韩语",
                "法语",
                "德语",
                "西班牙语",
                "意大利语",
                "俄语",
                "泰语",
                "阿拉伯语"
            ),
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_language_name
        ),

        // 高级设置：语速
        InputDefinition(
            id = "speechRate",
            name = "语速",
            staticType = ParameterType.NUMBER,
            defaultValue = 1.0,
            inputStyle = InputStyle.SLIDER,
            sliderConfig = InputDefinition.Companion.slider(0.5f, 2.0f, 0.1f),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_speech_rate_name
        ),

        // 高级设置：语调
        InputDefinition(
            id = "pitch",
            name = "语调",
            staticType = ParameterType.NUMBER,
            defaultValue = 1.0,
            inputStyle = InputStyle.SLIDER,
            sliderConfig = InputDefinition.Companion.slider(0.5f, 2.0f, 0.1f),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_pitch_name
        ),

        // 高级设置：排队模式
        InputDefinition(
            id = "queueMode",
            name = "排队模式",
            staticType = ParameterType.ENUM,
            defaultValue = "flush",
            options = listOf("flush", "add"),
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_queue_mode_name
        ),

        // 高级设置：等待完成
        InputDefinition(
            id = "awaitCompletion",
            name = "等待朗读完成",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_await_completion_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_text_to_speech_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val textPill = PillUtil.createPillFromParam(
            step.parameters["text"],
            getInputs().find { it.id == "text" }
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_device_text_to_speech_prefix),
            textPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val step = context.allSteps[context.currentStepIndex]
        val textObj = context.getVariable("text")
        val text = textObj.asString()

        if (text.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_text_to_speech_no_text),
                appContext.getString(R.string.error_vflow_device_text_to_speech_text_required)
            )
        }

        // 解析变量
        val resolvedText = VariableResolver.resolve(text, context)

        // 获取高级设置参数
        val language = step.parameters["language"] as? String ?: "自动"
        val speechRate = (step.parameters["speechRate"] as? Number)?.toDouble() ?: 1.0
        val pitch = (step.parameters["pitch"] as? Number)?.toDouble() ?: 1.0
        val queueMode = step.parameters["queueMode"] as? String ?: "flush"
        val awaitCompletion = step.parameters["awaitCompletion"] as? Boolean ?: true

        // 验证参数范围
        if (speechRate < 0.5 || speechRate > 2.0) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_text_to_speech_invalid_speech_rate),
                appContext.getString(R.string.error_vflow_device_text_to_speech_speech_rate_range)
            )
        }

        if (pitch < 0.5 || pitch > 2.0) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_text_to_speech_invalid_pitch),
                appContext.getString(R.string.error_vflow_device_text_to_speech_pitch_range)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_text_to_speech_initializing)))

        // 初始化TTS
        val tts = TextToSpeech(appContext) { status ->
            // 初始化完成回调
        }

        // 等待TTS初始化完成
        kotlinx.coroutines.delay(500)

        // 注意：如果TTS初始化失败，后续的speak()会返回错误

        return try {
            // 设置语言
            if (language != "自动") {
                val locale = when (language) {
                    "中文（中国）" -> Locale.CHINA
                    "英语（美国）" -> Locale.US
                    "英语（英国）" -> Locale.UK
                    "日语" -> Locale.JAPAN
                    "韩语" -> Locale.KOREA
                    "法语" -> Locale.FRANCE
                    "德语" -> Locale.GERMANY
                    "西班牙语" -> Locale("es", "ES")
                    "意大利语" -> Locale.ITALY
                    "俄语" -> Locale("ru", "RU")
                    "泰语" -> Locale("th", "TH")
                    "阿拉伯语" -> Locale("ar", "SA")
                    else -> Locale.getDefault()
                }

                val langResult = tts.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 语言不支持，使用默认语言
                    tts.language = Locale.getDefault()
                }
            }

            // 设置语速和语调
            tts.setSpeechRate(speechRate.toFloat())
            tts.setPitch(pitch.toFloat())

            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_text_to_speech_speaking)))

            // 执行朗读
            if (awaitCompletion) {
                // 等待朗读完成
                val utteranceId = "vflow_tts_${System.currentTimeMillis()}"

                // 动态计算超时时间：根据文本长度和语速估算
                // 假设平均语速为每分钟150字（正常语速1.0）
                val estimatedDurationMs = ((resolvedText.length / 150.0) * 60000 / speechRate).toLong()
                val timeoutMs = max(estimatedDurationMs + 5000, 10000) // 至少等待10秒，加上5秒缓冲

                try {
                    val speakResult = withTimeout(timeoutMs) {
                        suspendCancellableCoroutine<Boolean> { continuation ->
                            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                    if (interrupted) {
                                        // 被中断时也视为完成
                                        continuation.resume(true)
                                    }
                                }
                                override fun onDone(utteranceId: String?) {
                                    continuation.resume(true)
                                }
                                override fun onError(utteranceId: String?) {
                                    continuation.resume(false)
                                }
                            })

                            val result = tts.speak(
                                resolvedText,
                                if (queueMode == "flush") TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                null,
                                utteranceId
                            )

                            if (result != TextToSpeech.SUCCESS) {
                                continuation.resume(false)
                            }
                        }
                    }

                    tts.shutdown()

                    if (speakResult) {
                        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_text_to_speech_completed), 100))
                        ExecutionResult.Success(mapOf("success" to VBoolean(true)))
                    } else {
                        ExecutionResult.Failure(
                            appContext.getString(R.string.error_vflow_device_text_to_speech_failed),
                            appContext.getString(R.string.error_vflow_device_text_to_speech_unknown)
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 工作流被取消，停止TTS并释放资源
                    tts.stop()
                    tts.shutdown()
                    throw e
                }
            } else {
                // 不等待朗读完成，在后台播放
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        tts.shutdown()
                    }
                    override fun onDone(utteranceId: String?) {
                        tts.shutdown()
                    }
                    override fun onError(utteranceId: String?) {
                        tts.shutdown()
                    }
                })

                tts.speak(
                    resolvedText,
                    if (queueMode == "flush") TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    null,
                    "vflow_tts_${System.currentTimeMillis()}"
                )

                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_text_to_speech_started), 100))
                ExecutionResult.Success(mapOf("success" to VBoolean(true)))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            tts.shutdown()
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_text_to_speech_timeout),
                appContext.getString(R.string.error_vflow_device_text_to_speech_timeout_details)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 工作流被取消，确保TTS资源被释放
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
                // 忽略关闭错误
            }
            throw e // 重新抛出，让工作流引擎正确处理取消
        } catch (e: Exception) {
            tts.shutdown()
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_text_to_speech_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_device_text_to_speech_unknown)
            )
        }
    }
}
