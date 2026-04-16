package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.speech.SpeechRecognizer
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class SpeechToTextModule : BaseModule() {
    companion object {
        const val ENGINE_SYSTEM = "SYSTEM"
        const val ENGINE_SHERPA_NCNN = "SHERPA_NCNN"
        private const val SHERPA_LANGUAGE_INPUT_ID = "sherpaLanguage"
        private const val AUTO_START_INPUT_ID = "autoStart"
        private const val AUTO_SEND_INPUT_ID = "autoSend"

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

        private val SYSTEM_LANGUAGE_OPTIONS = listOf(
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
        private val SHERPA_LANGUAGE_OPTIONS = listOf(
            LANGUAGE_AUTO,
            LANGUAGE_ZH_CN,
            LANGUAGE_EN_US,
        )
        private val SYSTEM_LANGUAGE_OPTION_STRING_RES = listOf(
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
            R.string.option_vflow_device_text_to_speech_language_ar_sa,
        )
        private val SHERPA_LANGUAGE_OPTION_STRING_RES = listOf(
            R.string.option_vflow_device_text_to_speech_language_auto,
            R.string.option_vflow_device_text_to_speech_language_zh_cn,
            R.string.option_vflow_device_text_to_speech_language_en_us,
        )

        val ENGINE_INPUT_DEFINITION = InputDefinition(
            id = "engine",
            name = "识别引擎",
            nameStringRes = R.string.param_vflow_device_speech_to_text_engine_name,
            staticType = ParameterType.ENUM,
            defaultValue = ENGINE_SYSTEM,
            options = listOf(ENGINE_SYSTEM, ENGINE_SHERPA_NCNN),
            optionsStringRes = listOf(
                R.string.option_vflow_device_speech_to_text_engine_system,
                R.string.option_vflow_device_speech_to_text_engine_sherpa_ncnn,
            ),
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = false,
            legacyValueMap = mapOf(
                "系统语音识别" to ENGINE_SYSTEM,
                "System Speech Recognizer" to ENGINE_SYSTEM,
                "本地 Sherpa-ncnn" to ENGINE_SHERPA_NCNN,
                "Sherpa-ncnn" to ENGINE_SHERPA_NCNN,
            ),
        )
        val SYSTEM_LANGUAGE_INPUT_DEFINITION = InputDefinition(
            id = "language",
            name = "语言",
            nameStringRes = R.string.param_vflow_device_speech_to_text_language_name,
            staticType = ParameterType.ENUM,
            defaultValue = LANGUAGE_AUTO,
            options = SYSTEM_LANGUAGE_OPTIONS,
            optionsStringRes = SYSTEM_LANGUAGE_OPTION_STRING_RES,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            acceptsMagicVariable = false,
            visibility = InputVisibility.whenEquals("engine", ENGINE_SYSTEM),
        )
        val SHERPA_LANGUAGE_INPUT_DEFINITION = InputDefinition(
            id = SHERPA_LANGUAGE_INPUT_ID,
            name = "语言",
            nameStringRes = R.string.param_vflow_device_speech_to_text_language_name,
            staticType = ParameterType.ENUM,
            defaultValue = LANGUAGE_AUTO,
            options = SHERPA_LANGUAGE_OPTIONS,
            optionsStringRes = SHERPA_LANGUAGE_OPTION_STRING_RES,
            inputStyle = InputStyle.CHIP_GROUP,
            isFolded = true,
            acceptsMagicVariable = false,
            visibility = InputVisibility.whenEquals("engine", ENGINE_SHERPA_NCNN),
        )
        val AUTO_START_INPUT_DEFINITION = InputDefinition(
            id = AUTO_START_INPUT_ID,
            name = "自动开始",
            nameStringRes = R.string.param_vflow_device_speech_to_text_auto_start_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            acceptsMagicVariable = false,
        )
        val AUTO_SEND_INPUT_DEFINITION = InputDefinition(
            id = AUTO_SEND_INPUT_ID,
            name = "自动发送",
            nameStringRes = R.string.param_vflow_device_speech_to_text_auto_send_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            acceptsMagicVariable = false,
        )

        fun resolveRequestedLanguage(
            engine: String,
            rawLanguage: String?,
            rawSherpaLanguage: String?,
        ): String {
            return if (engine == ENGINE_SHERPA_NCNN) {
                SHERPA_LANGUAGE_INPUT_DEFINITION.normalizeEnumValueOrNull(rawSherpaLanguage) ?: LANGUAGE_AUTO
            } else {
                SYSTEM_LANGUAGE_INPUT_DEFINITION.normalizeEnumValueOrNull(rawLanguage) ?: LANGUAGE_AUTO
            }
        }
    }

    override val id = "vflow.device.speech_to_text"

    override val metadata = ActionMetadata(
        name = "语音转文字",
        nameStringRes = R.string.module_vflow_device_speech_to_text_name,
        description = "弹出按住说话的悬浮输入界面，实时将语音转成文本。",
        descriptionStringRes = R.string.module_vflow_device_speech_to_text_desc,
        iconRes = R.drawable.rounded_text_to_speech_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val requiredPermissions = listOf(
        PermissionManager.OVERLAY,
        PermissionManager.MICROPHONE
    )

    override fun getEditorActions(step: ActionStep?, allSteps: List<ActionStep>?): List<EditorAction> {
        return listOf(
            EditorAction(labelStringRes = R.string.module_editor_action_configure_sherpa_model) { context ->
                context.startActivity(
                    ModuleConfigActivity.createIntent(
                        context,
                        ModuleConfigActivity.SECTION_SHERPA
                    )
                )
            }
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        ENGINE_INPUT_DEFINITION,
        InputDefinition(
            id = "prompt",
            name = "提示信息",
            nameStringRes = R.string.param_vflow_device_speech_to_text_prompt_name,
            staticType = ParameterType.STRING,
            defaultValue = appContext.getString(R.string.param_vflow_device_speech_to_text_prompt_default),
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        ),
        SYSTEM_LANGUAGE_INPUT_DEFINITION,
        SHERPA_LANGUAGE_INPUT_DEFINITION,
        InputDefinition(
            id = "preferOffline",
            name = "离线识别",
            nameStringRes = R.string.param_vflow_device_speech_to_text_prefer_offline_name,
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            acceptsMagicVariable = false,
            visibility = InputVisibility.whenEquals("engine", ENGINE_SYSTEM),
        ),
        AUTO_START_INPUT_DEFINITION,
        AUTO_SEND_INPUT_DEFINITION,
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "text",
            name = appContext.getString(R.string.output_vflow_device_speech_to_text_text_name),
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_device_speech_to_text_text_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val promptPill = PillUtil.createPillFromParam(
            step.parameters["prompt"],
            getInputs().find { it.id == "prompt" }
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_device_speech_to_text_prefix),
            promptPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_speech_to_text_service_missing),
                appContext.getString(R.string.error_vflow_device_speech_to_text_service_missing)
            )

        val prompt = context.getVariableAsString("prompt")
            .ifBlank { appContext.getString(R.string.param_vflow_device_speech_to_text_prompt_default) }
        val rawEngine = context.getVariableAsString("engine", ENGINE_SYSTEM)
        val engine = ENGINE_INPUT_DEFINITION.normalizeEnumValueOrNull(rawEngine) ?: ENGINE_SYSTEM
        val language = resolveRequestedLanguage(
            engine = engine,
            rawLanguage = context.getVariableAsString("language", LANGUAGE_AUTO),
            rawSherpaLanguage = context.getVariableAsString(SHERPA_LANGUAGE_INPUT_ID),
        )
        val preferOffline = context.getVariableAsBoolean("preferOffline") ?: false
        val autoStart = context.getVariableAsBoolean(AUTO_START_INPUT_ID) ?: false
        val autoSend = context.getVariableAsBoolean(AUTO_SEND_INPUT_ID) ?: false

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_speech_to_text_waiting)))

        val result = uiService.requestSpeechToText(
            SpeechToTextOverlayRequest(
                title = prompt,
                languageTag = language,
                forceOffline = preferOffline,
                engine = engine,
                autoStart = autoStart,
                autoSend = autoSend,
            )
        )
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_speech_to_text_user_cancelled),
                appContext.getString(R.string.error_vflow_device_speech_to_text_user_cancelled)
            )

        if (!result.error.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_speech_to_text_failed),
                result.error
            )
        }

        val text = result.text?.trim().orEmpty()
        if (text.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_speech_to_text_empty),
                appContext.getString(R.string.error_vflow_device_speech_to_text_empty)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_speech_to_text_received)))
        return ExecutionResult.Success(mapOf("text" to VString(text)))
    }
}

data class SpeechToTextResult(
    val text: String? = null,
    val error: String? = null
)

data class SpeechToTextOverlayRequest(
    val title: String,
    val languageTag: String,
    val forceOffline: Boolean,
    val engine: String = SpeechToTextModule.ENGINE_SYSTEM,
    val autoStart: Boolean = false,
    val autoSend: Boolean = false,
)

object SpeechToTextOverlayContract {
    const val REQUEST_TYPE = "speech_to_text"
    const val EXTRA_LANGUAGE = "speech_language"
    const val EXTRA_FORCE_OFFLINE = "speech_force_offline"
    const val EXTRA_ENGINE = "speech_engine"
    const val EXTRA_AUTO_START = "speech_auto_start"
    const val EXTRA_AUTO_SEND = "speech_auto_send"

    fun fromIntent(intent: Intent, fallbackTitle: String): SpeechToTextOverlayRequest {
        return SpeechToTextOverlayRequest(
            title = intent.getStringExtra("title") ?: fallbackTitle,
            languageTag = intent.getStringExtra(EXTRA_LANGUAGE) ?: "auto",
            forceOffline = intent.getBooleanExtra(EXTRA_FORCE_OFFLINE, false),
            engine = SpeechToTextModule.ENGINE_INPUT_DEFINITION.normalizeEnumValueOrNull(
                intent.getStringExtra(EXTRA_ENGINE)
            ) ?: SpeechToTextModule.ENGINE_SYSTEM,
            autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false),
            autoSend = intent.getBooleanExtra(EXTRA_AUTO_SEND, false),
        )
    }
}

private enum class SpeechToTextStatus {
    IDLE,
    IDLE_OFFLINE,
    PREPARING_LOCAL,
    RECORDING,
    PROCESSING,
    READY,
    NO_MATCH,
    SWITCHED_OFFLINE,
    OFFLINE_PACK_MISSING,
    ERROR_AUDIO,
    ERROR_NETWORK,
    ERROR_BUSY,
    ERROR_SERVER,
    ERROR_TIMEOUT,
    ERROR_PERMISSION,
    ERROR_UNKNOWN
}

data class SpeechRecognitionStartRequest(
    val languageTag: String,
    val preferOffline: Boolean
)

data class SpeechRecognitionErrorResult(
    val shouldFinishWithError: Boolean = false
)

class SpeechToTextOverlaySession(
    private val request: SpeechToTextOverlayRequest
) {
    private var committedText = ""
    private var partialText = ""
    private var isRecording = false
    private var isAwaitingResult = false
    private var isPreparingLocalRecognizer = false
    private var shouldCommitPartialOnClientError = false
    private var offlineFallbackActivated = false
    private var attemptedOffline = false
    private var status = idleStatus()
    private var unknownErrorDetail: String? = null

    fun onOverlayShown() {
        status = idleStatus()
        unknownErrorDetail = null
    }

    fun onReadyForSpeech() {
        isPreparingLocalRecognizer = false
        status = SpeechToTextStatus.RECORDING
    }

    fun onLocalPreparationStarted() {
        isPreparingLocalRecognizer = true
        status = SpeechToTextStatus.PREPARING_LOCAL
    }

    fun onLocalPreparationFinished() {
        isPreparingLocalRecognizer = false
        unknownErrorDetail = null
        status = idleStatus()
    }

    fun startRecognition(currentEditorText: String, deviceLanguageTag: String): SpeechRecognitionStartRequest? {
        if (isRecording || isAwaitingResult || isPreparingLocalRecognizer) return null

        committedText = currentEditorText.trim()
        partialText = ""
        isRecording = true
        isAwaitingResult = false
        shouldCommitPartialOnClientError = false
        attemptedOffline = shouldUseOfflineRecognition()
        unknownErrorDetail = null
        status = SpeechToTextStatus.RECORDING

        return SpeechRecognitionStartRequest(
            languageTag = resolveLanguageTag(deviceLanguageTag),
            preferOffline = attemptedOffline
        )
    }

    fun stopRecognitionRequested(): Boolean {
        if (!isRecording) return false

        isRecording = false
        isAwaitingResult = true
        shouldCommitPartialOnClientError = true
        unknownErrorDetail = null
        status = SpeechToTextStatus.PROCESSING
        return true
    }

    fun onEndOfSpeech() {
        isRecording = false
        isAwaitingResult = true
        status = SpeechToTextStatus.PROCESSING
    }

    fun onPartialResult(result: String?) {
        partialText = result?.trim().orEmpty()
    }

    fun onResults(result: String?) {
        result?.let(::appendSpeechText)
        partialText = ""
        isRecording = false
        isAwaitingResult = false
        isPreparingLocalRecognizer = false
        shouldCommitPartialOnClientError = false
        unknownErrorDetail = null
        status = if (currentText().isBlank()) {
            SpeechToTextStatus.NO_MATCH
        } else {
            SpeechToTextStatus.READY
        }
    }

    fun onError(error: Int): SpeechRecognitionErrorResult {
        if (error == SpeechRecognizer.ERROR_CLIENT &&
            shouldCommitPartialOnClientError &&
            partialText.isNotBlank()
        ) {
            appendSpeechText(partialText)
        }

        partialText = ""
        isRecording = false
        isAwaitingResult = false
        isPreparingLocalRecognizer = false
        shouldCommitPartialOnClientError = false
        unknownErrorDetail = null

        if (shouldSwitchToOfflineFallback(error)) {
            offlineFallbackActivated = true
            status = SpeechToTextStatus.SWITCHED_OFFLINE
            return SpeechRecognitionErrorResult()
        }

        if (attemptedOffline && isLikelyOfflinePackMissing(error)) {
            status = SpeechToTextStatus.OFFLINE_PACK_MISSING
            return SpeechRecognitionErrorResult(shouldFinishWithError = true)
        }

        status = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> SpeechToTextStatus.ERROR_AUDIO
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechToTextStatus.ERROR_NETWORK
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechToTextStatus.ERROR_BUSY
            SpeechRecognizer.ERROR_SERVER -> SpeechToTextStatus.ERROR_SERVER
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechToTextStatus.ERROR_PERMISSION
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechToTextStatus.ERROR_TIMEOUT
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechToTextStatus.NO_MATCH
            else -> {
                unknownErrorDetail = error.toString()
                SpeechToTextStatus.ERROR_UNKNOWN
            }
        }

        return SpeechRecognitionErrorResult()
    }

    fun onStartFailure(detail: String) {
        isRecording = false
        isAwaitingResult = false
        isPreparingLocalRecognizer = false
        shouldCommitPartialOnClientError = false
        unknownErrorDetail = detail
        status = SpeechToTextStatus.ERROR_UNKNOWN
    }

    fun onStopFailure(detail: String) {
        isRecording = false
        isAwaitingResult = false
        isPreparingLocalRecognizer = false
        shouldCommitPartialOnClientError = false
        unknownErrorDetail = detail
        status = SpeechToTextStatus.ERROR_UNKNOWN
    }

    fun currentText(): String {
        val committed = committedText.trim()
        val partial = partialText.trim()
        return when {
            committed.isBlank() -> partial
            partial.isBlank() -> committed
            else -> "$committed $partial"
        }
    }

    fun canSend(currentEditorText: String = currentText()): Boolean {
        return currentEditorText.trim().isNotBlank() &&
            !isRecording &&
            !isAwaitingResult &&
            !isPreparingLocalRecognizer
    }

    fun shouldAutoSend(currentEditorText: String = currentText()): Boolean {
        return request.autoSend && canSend(currentEditorText)
    }

    fun isHoldButtonEnabled(): Boolean = !isAwaitingResult
        && !isPreparingLocalRecognizer

    fun holdButtonText(context: Context): String {
        return when {
            isPreparingLocalRecognizer -> context.getString(R.string.overlay_ui_speech_button_preparing)
            isRecording -> context.getString(R.string.overlay_ui_speech_button_release)
            isAwaitingResult -> context.getString(R.string.overlay_ui_speech_button_processing)
            else -> context.getString(R.string.overlay_ui_speech_button_hold)
        }
    }

    fun statusText(context: Context): String {
        return when (status) {
            SpeechToTextStatus.IDLE -> context.getString(R.string.overlay_ui_speech_status_idle)
            SpeechToTextStatus.IDLE_OFFLINE -> context.getString(R.string.overlay_ui_speech_status_idle_offline)
            SpeechToTextStatus.PREPARING_LOCAL -> context.getString(R.string.overlay_ui_speech_status_initializing_local)
            SpeechToTextStatus.RECORDING -> context.getString(R.string.overlay_ui_speech_status_recording)
            SpeechToTextStatus.PROCESSING -> context.getString(R.string.overlay_ui_speech_status_processing)
            SpeechToTextStatus.READY -> context.getString(R.string.overlay_ui_speech_status_ready)
            SpeechToTextStatus.NO_MATCH -> context.getString(R.string.overlay_ui_speech_status_no_match)
            SpeechToTextStatus.SWITCHED_OFFLINE -> context.getString(R.string.overlay_ui_speech_status_switched_offline)
            SpeechToTextStatus.OFFLINE_PACK_MISSING -> context.getString(R.string.overlay_ui_speech_offline_pack_missing_message)
            SpeechToTextStatus.ERROR_AUDIO -> context.getString(R.string.overlay_ui_speech_error_audio)
            SpeechToTextStatus.ERROR_NETWORK -> context.getString(R.string.overlay_ui_speech_error_network)
            SpeechToTextStatus.ERROR_BUSY -> context.getString(R.string.overlay_ui_speech_error_busy)
            SpeechToTextStatus.ERROR_SERVER -> context.getString(R.string.overlay_ui_speech_error_server)
            SpeechToTextStatus.ERROR_TIMEOUT -> context.getString(R.string.overlay_ui_speech_error_timeout)
            SpeechToTextStatus.ERROR_PERMISSION -> context.getString(R.string.overlay_ui_speech_permission_denied)
            SpeechToTextStatus.ERROR_UNKNOWN -> context.getString(
                R.string.overlay_ui_speech_error_unknown,
                unknownErrorDetail ?: context.getString(R.string.error_unknown_error)
            )
        }
    }

    private fun appendSpeechText(segment: String) {
        val normalized = segment.trim()
        if (normalized.isBlank()) return

        committedText = if (committedText.isBlank()) {
            normalized
        } else {
            "${committedText.trimEnd()} $normalized"
        }
    }

    private fun resolveLanguageTag(deviceLanguageTag: String): String {
        return if (request.languageTag == "auto") {
            deviceLanguageTag
        } else {
            request.languageTag
        }
    }

    private fun shouldUseOfflineRecognition(): Boolean {
        return request.forceOffline || offlineFallbackActivated
    }

    private fun shouldSwitchToOfflineFallback(error: Int): Boolean {
        return !request.forceOffline &&
            !offlineFallbackActivated &&
            !attemptedOffline &&
            (error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER)
    }

    private fun isLikelyOfflinePackMissing(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_SERVER
    }

    private fun idleStatus(): SpeechToTextStatus {
        return if (request.engine == SpeechToTextModule.ENGINE_SHERPA_NCNN || shouldUseOfflineRecognition()) {
            SpeechToTextStatus.IDLE_OFFLINE
        } else {
            SpeechToTextStatus.IDLE
        }
    }
}
