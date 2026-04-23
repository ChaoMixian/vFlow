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
    companion object {
        private const val LANGUAGE_AUTO = "auto"
        private const val LANGUAGE_ZH_CN = "zh-CN"
        private const val LANGUAGE_EN_US = "en-US"
        private const val LANGUAGE_EN_GB = "en-GB"
        private const val LANGUAGE_JA_JP = "ja-JP"
        private const val LANGUAGE_KO_KR = "ko-KR"
        private const val LANGUAGE_FR_FR = "fr-FR"
        private const val LANGUAGE_DE_DE = "de-DE"
        private const val LANGUAGE_ES_ES = "es-ES"
        private const val LANGUAGE_IT_IT = "it-IT"
        private const val LANGUAGE_RU_RU = "ru-RU"
        private const val LANGUAGE_TH_TH = "th-TH"
        private const val LANGUAGE_AR_SA = "ar-SA"

        private const val QUEUE_FLUSH = "flush"
        private const val QUEUE_ADD = "add"

        private val LANGUAGE_OPTIONS = listOf(
            LANGUAGE_AUTO,
            LANGUAGE_ZH_CN,
            LANGUAGE_EN_US,
            LANGUAGE_EN_GB,
            LANGUAGE_JA_JP,
            LANGUAGE_KO_KR,
            LANGUAGE_FR_FR,
            LANGUAGE_DE_DE,
            LANGUAGE_ES_ES,
            LANGUAGE_IT_IT,
            LANGUAGE_RU_RU,
            LANGUAGE_TH_TH,
            LANGUAGE_AR_SA
        )

        private val QUEUE_OPTIONS = listOf(QUEUE_FLUSH, QUEUE_ADD)

        private val LANGUAGE_LEGACY_MAP = mapOf(
            "自动" to LANGUAGE_AUTO,
            "中文（中国）" to LANGUAGE_ZH_CN,
            "英语（美国）" to LANGUAGE_EN_US,
            "英语（英国）" to LANGUAGE_EN_GB,
            "日语" to LANGUAGE_JA_JP,
            "韩语" to LANGUAGE_KO_KR,
            "法语" to LANGUAGE_FR_FR,
            "德语" to LANGUAGE_DE_DE,
            "西班牙语" to LANGUAGE_ES_ES,
            "意大利语" to LANGUAGE_IT_IT,
            "俄语" to LANGUAGE_RU_RU,
            "泰语" to LANGUAGE_TH_TH,
            "阿拉伯语" to LANGUAGE_AR_SA
        )

        private val QUEUE_LEGACY_MAP = mapOf(
            "替换" to QUEUE_FLUSH,
            "追加" to QUEUE_ADD
        )
    }

    override val id = "vflow.device.text_to_speech"

    override val metadata = ActionMetadata(
        name = "朗读文本",
        nameStringRes = R.string.module_vflow_device_text_to_speech_name,
        description = "使用Android系统自带的TTS引擎将文本转换为语音并朗读",
        descriptionStringRes = R.string.module_vflow_device_text_to_speech_desc,
        iconRes = R.drawable.rounded_text_to_speech_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Speak text aloud with Android text-to-speech.",
        workflowStepDescription = "Speak text aloud with Android text-to-speech, with optional language, rate, pitch, and queue settings.",
        inputHints = mapOf(
            "text" to "Text content to read aloud.",
            "language" to "Optional canonical language code such as auto, zh-CN, or en-US.",
            "speechRate" to "Optional speaking speed multiplier, usually around 1.0.",
            "pitch" to "Optional voice pitch multiplier, usually around 1.0.",
            "queueMode" to "Use flush to replace current speech or add to queue.",
            "awaitCompletion" to "Set true if later steps should wait for playback to finish."
        ),
        requiredInputIds = setOf("text")
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
            defaultValue = LANGUAGE_AUTO,
            options = LANGUAGE_OPTIONS,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_language_name,
            optionsStringRes = listOf(
                R.string.option_vflow_device_text_to_speech_language_auto,
                R.string.option_vflow_device_text_to_speech_language_zh_cn,
                R.string.option_vflow_device_text_to_speech_language_en_us,
                R.string.option_vflow_device_text_to_speech_language_en_gb,
                R.string.option_vflow_device_text_to_speech_language_ja_jp,
                R.string.option_vflow_device_text_to_speech_language_ko_kr,
                R.string.option_vflow_device_text_to_speech_language_fr_fr,
                R.string.option_vflow_device_text_to_speech_language_de_de,
                R.string.option_vflow_device_text_to_speech_language_es_es,
                R.string.option_vflow_device_text_to_speech_language_it_it,
                R.string.option_vflow_device_text_to_speech_language_ru_ru,
                R.string.option_vflow_device_text_to_speech_language_th_th,
                R.string.option_vflow_device_text_to_speech_language_ar_sa
            ),
            legacyValueMap = LANGUAGE_LEGACY_MAP
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
            defaultValue = QUEUE_FLUSH,
            options = QUEUE_OPTIONS,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_text_to_speech_queue_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_device_text_to_speech_queue_flush,
                R.string.option_vflow_device_text_to_speech_queue_add
            ),
            legacyValueMap = QUEUE_LEGACY_MAP
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
        val inputsById = getInputs().associateBy { it.id }
        val rawLanguage = context.getVariableAsString("language", LANGUAGE_AUTO)
        val language = inputsById["language"]?.normalizeEnumValue(rawLanguage) ?: rawLanguage
        val speechRate = context.getVariableAsNumber("speechRate") ?: 1.0
        val pitch = context.getVariableAsNumber("pitch") ?: 1.0
        val rawQueueMode = context.getVariableAsString("queueMode", QUEUE_FLUSH)
        val queueMode = inputsById["queueMode"]?.normalizeEnumValue(rawQueueMode) ?: rawQueueMode
        val awaitCompletion = context.getVariableAsBoolean("awaitCompletion") ?: true

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
            if (language != LANGUAGE_AUTO) {
                val locale = when (language) {
                    LANGUAGE_ZH_CN -> Locale.CHINA
                    LANGUAGE_EN_US -> Locale.US
                    LANGUAGE_EN_GB -> Locale.UK
                    LANGUAGE_JA_JP -> Locale.JAPAN
                    LANGUAGE_KO_KR -> Locale.KOREA
                    LANGUAGE_FR_FR -> Locale.FRANCE
                    LANGUAGE_DE_DE -> Locale.GERMANY
                    LANGUAGE_ES_ES -> Locale("es", "ES")
                    LANGUAGE_IT_IT -> Locale.ITALY
                    LANGUAGE_RU_RU -> Locale("ru", "RU")
                    LANGUAGE_TH_TH -> Locale("th", "TH")
                    LANGUAGE_AR_SA -> Locale("ar", "SA")
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
                                if (queueMode == QUEUE_FLUSH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
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
                    if (queueMode == QUEUE_FLUSH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
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
