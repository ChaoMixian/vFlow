// 文件：main/java/com/chaomixian/vflow/services/OverlayUIActivity.kt
// 添加了对 MediaProjection 的支持
package com.chaomixian.vflow.ui.common

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import coil.load
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.workflow.module.system.InputModule
import com.chaomixian.vflow.core.workflow.module.system.SpeechRecognitionErrorResult
import com.chaomixian.vflow.core.workflow.module.system.SpeechRecognitionStartRequest
import com.chaomixian.vflow.core.workflow.module.system.SpeechToTextOverlayContract
import com.chaomixian.vflow.core.workflow.module.system.SpeechToTextOverlayRequest
import com.chaomixian.vflow.core.workflow.module.system.SpeechToTextOverlaySession
import com.chaomixian.vflow.core.workflow.module.system.SpeechToTextResult
import com.chaomixian.vflow.core.workflow.module.system.SpeechToTextModule
import com.chaomixian.vflow.speech.SherpaNcnnModelManager
import com.chaomixian.vflow.speech.SherpaNcnnStreamingRecognizer
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.common.OverlayUiPreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverlayUIActivity : AppCompatActivity() {
    private var pendingSpeechRequest: SpeechToTextOverlayRequest? = null
    private var speechSession: SpeechToTextOverlaySession? = null
    private var speechAutoStartPending = false
    private var speechDialog: AlertDialog? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechTitleView: TextView? = null
    private var speechStatusView: TextView? = null
    private var speechResultEditText: TextInputEditText? = null
    private var speechHoldButton: MaterialButton? = null
    private var speechSendButton: MaterialButton? = null
    private var sherpaRecognizer: SherpaNcnnStreamingRecognizer? = null
    private var sherpaPreparationJob: Job? = null
    private val sherpaModelManager by lazy { SherpaNcnnModelManager(this) }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ExecutionUIService.inputCompletable?.complete(uri.toString())
        } else {
            ExecutionUIService.inputCompletable?.complete(null)
        }
        finish()
    }

    // MediaProjection Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 将结果传递回去
            ExecutionUIService.inputCompletable?.complete(result.data)
        } else {
            ExecutionUIService.inputCompletable?.complete(null)
        }
        finish()
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            continueSpeechToTextRequest()
        } else {
            finishWithSpeechError(getString(R.string.overlay_ui_speech_permission_denied))
        }
    }

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDynamicTheme()
        configureWindowForLockScreen()
        handleIntent()
    }

    private fun configureWindowForLockScreen() {
        val allowShowOnLockScreen = OverlayUiPreferences.isShowOnLockScreenAllowed(this)
        setShowWhenLocked(allowShowOnLockScreen)
        setTurnScreenOn(allowShowOnLockScreen)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleIntent() {
        val requestType = intent.getStringExtra("request_type")
        val title = intent.getStringExtra("title")

        when (requestType) {
            "quick_view" -> {
                val content = intent.getStringExtra("content") ?: ""
                showQuickViewDialog(title ?: getString(R.string.overlay_ui_quick_view_title), content)
            }
            "quick_view_image" -> {
                val imageUri = intent.getStringExtra("content")
                if (imageUri != null) {
                    showQuickViewImageDialog(title ?: getString(R.string.overlay_ui_image_preview_title), imageUri)
                } else {
                    finishWithError()
                }
            }
            "input" -> {
                val inputType = intent.getStringExtra("input_type")
                when {
                    isTimeInputType(inputType) -> showTimePickerDialog(title ?: getString(R.string.overlay_ui_input_time_title))
                    isDateInputType(inputType) -> showDatePickerDialog(title ?: getString(R.string.overlay_ui_input_date_title))
                    else -> showTextInputDialog(title ?: getString(R.string.overlay_ui_input_text_title), inputType)
                }
            }
            SpeechToTextOverlayContract.REQUEST_TYPE -> handleSpeechToTextRequest(title)
            "pick_image" -> pickImageLauncher.launch("image/*")
            "media_projection" -> {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            }
            "workflow_chooser" -> {
                @Suppress("UNCHECKED_CAST")
                var workflows = intent.getSerializableExtra("workflow_list") as? Map<String, String>
                if (workflows == null) {
                    val jsonString = intent.getStringExtra("workflow_list_json")
                    if (jsonString != null) {
                        try {
                            val type = object : TypeToken<Map<String, String>>() {}.type
                            workflows = gson.fromJson(jsonString, type)
                        } catch (e: Exception) {
                            finishWithError()
                            return
                        }
                    }
                }
                workflows?.let { showWorkflowChooserDialog(it) } ?: finishWithError()
            }
            "share" -> handleShareRequest()
            "error_dialog" -> {
                val workflowName = intent.getStringExtra("workflow_name")
                    ?: getString(R.string.summary_unknown_workflow)
                val moduleName = intent.getStringExtra("module_name")
                    ?: getString(R.string.ui_inspector_unknown)
                val errorMessage = intent.getStringExtra("error_message")
                    ?: getString(R.string.error_unknown_error)
                showErrorDialog(workflowName, moduleName, errorMessage)
            }
            else -> finishWithError()
        }
    }

    private fun handleSpeechToTextRequest(title: String?) {
        val fallbackTitle = title ?: getString(R.string.overlay_ui_speech_default_title)
        pendingSpeechRequest = SpeechToTextOverlayContract.fromIntent(intent, fallbackTitle)
        if (usesSystemSpeechRecognizer() && !SpeechRecognizer.isRecognitionAvailable(this)) {
            finishWithSpeechError(getString(R.string.overlay_ui_speech_not_available))
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        continueSpeechToTextRequest()
    }

    private fun continueSpeechToTextRequest() {
        val request = pendingSpeechRequest
            ?: return finishWithSpeechError(getString(R.string.overlay_ui_speech_not_available))
        if (request.engine == SpeechToTextModule.ENGINE_SHERPA_NCNN) {
            val languageTag = resolvedLocalLanguageTag(request)
            if (!sherpaModelManager.isModelInstalled(languageTag)) {
                finishWithSpeechError(sherpaModelMissingMessage())
                return
            }
        }
        showSpeechToTextOverlay(request)
    }

    private fun showSpeechToTextOverlay(request: SpeechToTextOverlayRequest) {
        pendingSpeechRequest = request
        speechAutoStartPending = request.autoStart
        speechSession = SpeechToTextOverlaySession(request).also { it.onOverlayShown() }
        releaseSherpaRecognizer()

        dismissSpeechDialog()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_overlay_speech_to_text, null)
        speechDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setOnCancelListener { cancel() }
            .show()

        speechTitleView = dialogView.findViewById<TextView>(R.id.text_speech_title).apply {
            text = request.title
        }
        speechStatusView = dialogView.findViewById(R.id.text_speech_status)
        speechResultEditText = dialogView.findViewById(R.id.edit_speech_result)
        speechHoldButton = dialogView.findViewById(R.id.button_hold_to_record)
        speechSendButton = dialogView.findViewById(R.id.button_speech_send)
        speechResultEditText?.doAfterTextChanged {
            updateSpeechSendButtonState()
        }

        dialogView.findViewById<MaterialButton>(R.id.button_speech_cancel).setOnClickListener {
            cancel()
        }
        speechSendButton?.setOnClickListener {
            val text = speechResultEditText?.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && speechSession?.canSend(text) == true) {
                complete(SpeechToTextResult(text = text))
            }
        }
        speechHoldButton?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startSpeechRecognition()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopSpeechRecognition()
                    true
                }
                else -> true
            }
        }

        if (usesSystemSpeechRecognizer()) {
            initializeSpeechRecognizer()
        } else {
            ensureSherpaRecognizerPrepared()
        }
        renderSpeechUi()
        maybeAutoStartSpeechRecognition()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    speechSession?.onReadyForSpeech()
                    renderSpeechUi()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    speechSession?.onEndOfSpeech()
                    renderSpeechUi()
                }

                override fun onError(error: Int) {
                    val result: SpeechRecognitionErrorResult = speechSession?.onError(error) ?: return
                    if (result.shouldFinishWithError) {
                        finishWithSpeechError(speechSession?.statusText(this@OverlayUIActivity).orEmpty())
                        return
                    }
                    renderSpeechUi()
                }

                override fun onResults(results: Bundle?) {
                    speechSession?.onResults(extractBestSpeechResult(results))
                    renderSpeechUi()
                    maybeAutoSendSpeechResult()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    speechSession?.onPartialResult(extractBestSpeechResult(partialResults))
                    renderSpeechUi()
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startSpeechRecognition(): Boolean {
        val session = speechSession ?: return false
        if (!usesSystemSpeechRecognizer()) {
            return startLocalSpeechRecognition(session)
        }
        if (speechRecognizer == null) return false

        val request: SpeechRecognitionStartRequest = session.startRecognition(
            currentEditorText = speechResultEditText?.text?.toString().orEmpty(),
            deviceLanguageTag = Locale.getDefault().toLanguageTag()
        ) ?: return false

        renderSpeechUi()

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.preferOffline)
        }

        try {
            speechRecognizer?.startListening(recognizerIntent)
            return true
        } catch (e: Exception) {
            session.onStartFailure(e.message ?: getString(R.string.error_unknown_error))
            renderSpeechUi()
            return false
        }
    }

    private fun stopSpeechRecognition() {
        val session = speechSession ?: return
        if (!session.stopRecognitionRequested()) return

        renderSpeechUi()

        if (!usesSystemSpeechRecognizer()) {
            lifecycleScope.launch {
                try {
                    val text = sherpaRecognizer?.stopListening().orEmpty()
                    speechSession?.onResults(text)
                } catch (e: Exception) {
                    session.onStopFailure(e.message ?: getString(R.string.error_unknown_error))
                }
                renderSpeechUi()
                maybeAutoSendSpeechResult()
            }
            return
        }

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            session.onStopFailure(e.message ?: getString(R.string.error_unknown_error))
            renderSpeechUi()
        }
    }

    private fun extractBestSpeechResult(bundle: Bundle?): String? {
        return bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun renderSpeechUi() {
        speechStatusView?.text = speechSession?.statusText(this).orEmpty()

        val editText = speechResultEditText ?: return
        val currentText = speechSession?.currentText().orEmpty()
        if (editText.text?.toString() != currentText) {
            editText.setText(currentText)
            editText.setSelection(currentText.length)
        }
        speechHoldButton?.text = speechSession?.holdButtonText(this)
        speechHoldButton?.isEnabled = speechSession?.isHoldButtonEnabled() == true
        updateSpeechSendButtonState()
    }

    private fun startLocalSpeechRecognition(session: SpeechToTextOverlaySession): Boolean {
        val localRecognizer = sherpaRecognizer
        if (localRecognizer == null || !localRecognizer.isPrepared) {
            ensureSherpaRecognizerPrepared()
            return false
        }

        session.startRecognition(
            currentEditorText = speechResultEditText?.text?.toString().orEmpty(),
            deviceLanguageTag = Locale.getDefault().toLanguageTag(),
        ) ?: return false

        renderSpeechUi()
        lifecycleScope.launch {
            try {
                localRecognizer.startListening(
                    onPartialResult = { partialText ->
                        runOnUiThread {
                            speechSession?.onPartialResult(partialText)
                            renderSpeechUi()
                        }
                    },
                    onEndpoint = {
                        runOnUiThread {
                            if (pendingSpeechRequest?.autoStart == true) {
                                stopSpeechRecognition()
                            }
                        }
                    }
                )
                session.onReadyForSpeech()
            } catch (e: Exception) {
                session.onStartFailure(e.message ?: getString(R.string.error_unknown_error))
            }
            renderSpeechUi()
        }
        return true
    }

    private fun ensureSherpaRecognizerPrepared() {
        val request = pendingSpeechRequest ?: return
        if (request.engine != SpeechToTextModule.ENGINE_SHERPA_NCNN) return
        val existingRecognizer = sherpaRecognizer
        if (existingRecognizer != null && existingRecognizer.isPrepared) {
            speechSession?.onLocalPreparationFinished()
            renderSpeechUi()
            return
        }
        if (sherpaPreparationJob?.isActive == true) return

        val resolvedLanguageTag = resolvedLocalLanguageTag(request)
        if (sherpaRecognizer == null) {
            sherpaRecognizer = SherpaNcnnStreamingRecognizer(this)
        }

        speechSession?.onLocalPreparationStarted()
        renderSpeechUi()

        sherpaPreparationJob = lifecycleScope.launch {
            try {
                sherpaRecognizer?.prepare(resolvedLanguageTag)
                speechSession?.onLocalPreparationFinished()
            } catch (e: Exception) {
                speechSession?.onStartFailure(e.message ?: getString(R.string.error_unknown_error))
            }
            renderSpeechUi()
            maybeAutoStartSpeechRecognition()
        }
    }

    private fun usesSystemSpeechRecognizer(): Boolean {
        return pendingSpeechRequest?.engine != SpeechToTextModule.ENGINE_SHERPA_NCNN
    }

    private fun resolvedLocalLanguageTag(request: SpeechToTextOverlayRequest): String {
        return if (request.languageTag == "auto") {
            Locale.getDefault().toLanguageTag()
        } else {
            request.languageTag
        }
    }

    private fun sherpaModelMissingMessage(): String {
        return getString(R.string.overlay_ui_sherpa_model_missing)
    }

    private fun releaseSherpaRecognizer() {
        sherpaPreparationJob?.cancel()
        sherpaPreparationJob = null
        val recognizer = sherpaRecognizer ?: return
        sherpaRecognizer = null
        lifecycleScope.launch {
            recognizer.release()
        }
    }

    private fun updateSpeechSendButtonState() {
        val currentEditorText = speechResultEditText?.text?.toString().orEmpty()
        speechSendButton?.isEnabled = speechSession?.canSend(currentEditorText) == true
    }

    private fun maybeAutoStartSpeechRecognition() {
        if (!speechAutoStartPending) return
        if (startSpeechRecognition()) {
            speechAutoStartPending = false
        }
    }

    private fun maybeAutoSendSpeechResult() {
        val text = speechResultEditText?.text?.toString()?.trim().orEmpty()
        if (speechSession?.shouldAutoSend(text) == true) {
            complete(SpeechToTextResult(text = text))
        }
    }

    /**
     * 更新：显示包含图片的对话框，并重写按钮行为以防止意外关闭。
     */
    private fun showQuickViewImageDialog(title: String, imageUriString: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_view, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.image_view_preview)

        val imageUri = Uri.parse(imageUriString)
        imageView.load(imageUri) {
            crossfade(true)
            placeholder(R.drawable.rounded_cached_24)
            error(R.drawable.rounded_close_small_24)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.common_close) { _, _ -> complete(true) }
            .setNeutralButton(R.string.common_copy, null)
            .setNegativeButton(R.string.common_share, null)
            .setOnCancelListener { cancel() }
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            copyImageToClipboard(imageUriString)
            // 这里不调用 dialog.dismiss()，所以对话框会保持打开
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            shareContent("image", imageUriString)
            // 这里也不调用 dialog.dismiss()
        }
    }

    private fun handleShareRequest() {
        val shareType = intent.getStringExtra("share_type")
        val shareContent = intent.getStringExtra("share_content")
        shareContent(shareType, shareContent)
        // 启动分享后，我们认为任务已完成，可以立即返回
        complete(true)
    }

    /**
     * 抽离出通用的分享逻辑
     */
    private fun shareContent(shareType: String?, shareContent: String?) {
        if (shareContent.isNullOrEmpty()) {
            Toast.makeText(this, R.string.overlay_ui_share_no_content, Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND)

        when(shareType) {
            "text" -> {
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent)
            }
            "image" -> {
                try {
                    val imageFile = File(java.net.URI(shareContent))
                    val authority = "$packageName.provider"
                    val safeUri = FileProvider.getUriForFile(this, authority, imageFile)
                    shareIntent.type = contentResolver.getType(safeUri)
                    shareIntent.putExtra(Intent.EXTRA_STREAM, safeUri)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        this,
                        getString(R.string.overlay_ui_share_image_failed, e.message ?: getString(R.string.error_unknown_error)),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
            else -> {
                Toast.makeText(this, R.string.overlay_ui_share_type_unsupported, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val chooser = Intent.createChooser(shareIntent, getString(R.string.overlay_ui_share_chooser_title))
        startActivity(chooser)
    }

    /**
     * 将图片URI复制到剪贴板的逻辑
     */
    private fun copyImageToClipboard(imageUriString: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val imageFile = File(java.net.URI(imageUriString))
            val authority = "$packageName.provider"
            val safeUri = FileProvider.getUriForFile(this, authority, imageFile)
            val clip = ClipData.newUri(contentResolver, getString(R.string.overlay_ui_clip_label_image), safeUri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.overlay_ui_image_copied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.overlay_ui_copy_image_failed, e.message ?: getString(R.string.error_unknown_error)),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showWorkflowChooserDialog(workflows: Map<String, String>) {
        val items = workflows.values.toTypedArray()
        val itemIds = workflows.keys.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.overlay_ui_workflow_chooser_title)
            .setItems(items) { _, which -> complete(itemIds[which]) }
            .setOnCancelListener { cancel() }
            .show()
    }

    /**
     * 为文本快速查看对话框添加复制和分享按钮，并重写行为
     * 使用自定义布局以支持长按选择文本
     */
    private fun showQuickViewDialog(title: String, content: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quick_view_text, null)
        val textView = dialogView.findViewById<TextView>(R.id.text_view_content)
        textView.text = content

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.common_close) { _, _ -> complete(true) }
            .setNeutralButton(R.string.common_copy, null)
            .setNegativeButton(R.string.common_share, null)
            .setOnCancelListener { cancel() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.overlay_ui_clip_label_text), content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            shareContent("text", content)
        }
    }

    private fun showTextInputDialog(title: String, type: String?) {
        val editText = EditText(this).apply {
            inputType = if (isNumberInputType(type)) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                val inputText = editText.text.toString()
                val result: Any? = if (isNumberInputType(type)) inputText.toDoubleOrNull() else inputText
                complete(result)
            }
            .setNegativeButton(R.string.common_cancel) { _, _ -> cancel() }
            .setOnCancelListener { cancel() }
            .show()
    }

    private fun isNumberInputType(type: String?): Boolean {
        val inputType = InputModule.INPUT_TYPE_DEFINITION.normalizeEnumValueOrNull(type) ?: InputModule.TYPE_TEXT
        return inputType == InputModule.TYPE_NUMBER
    }

    private fun isTimeInputType(type: String?): Boolean {
        val inputType = InputModule.INPUT_TYPE_DEFINITION.normalizeEnumValueOrNull(type) ?: InputModule.TYPE_TEXT
        return inputType == InputModule.TYPE_TIME
    }

    private fun isDateInputType(type: String?): Boolean {
        val inputType = InputModule.INPUT_TYPE_DEFINITION.normalizeEnumValueOrNull(type) ?: InputModule.TYPE_TEXT
        return inputType == InputModule.TYPE_DATE
    }

    private fun showTimePickerDialog(title: String) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            .setMinute(Calendar.getInstance().get(Calendar.MINUTE))
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { complete(String.format("%02d:%02d", picker.hour, picker.minute)) }
        picker.addOnNegativeButtonClickListener { cancel() }
        picker.addOnCancelListener { cancel() }
        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun showDatePickerDialog(title: String) {
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection -> complete(selection) }
        picker.addOnNegativeButtonClickListener { cancel() }
        picker.addOnCancelListener { cancel() }
        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    /**
     * 显示错误详情弹窗
     */
    private fun showErrorDialog(workflowName: String, moduleName: String, errorMessage: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_execution_error, null)

        val workflowText = dialogView.findViewById<TextView>(R.id.text_workflow_name)
        val moduleText = dialogView.findViewById<TextView>(R.id.text_module_name)
        val messageText = dialogView.findViewById<TextView>(R.id.text_error_message)

        workflowText.text = getString(R.string.execution_error_workflow_name, workflowName)
        moduleText.text = getString(R.string.execution_error_module_name, moduleName)
        messageText.text = errorMessage

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok) { _, _ -> complete(true) }
            .setOnCancelListener { cancel() }
            .show()
    }

    private fun complete(result: Any?) {
        dismissSpeechDialog()
        ExecutionUIService.inputCompletable?.complete(result)
        finish()
    }

    private fun cancel() {
        dismissSpeechDialog()
        ExecutionUIService.inputCompletable?.complete(null)
        finish()
    }

    private fun finishWithError() {
        dismissSpeechDialog()
        ExecutionUIService.inputCompletable?.complete(null)
        finish()
    }

    private fun finishWithSpeechError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        complete(SpeechToTextResult(error = message))
    }

    private fun applyDynamicTheme() {
        val themeResId = ThemeUtils.getThemeResId(this, transparent = true)
        setTheme(themeResId)
    }

    override fun onDestroy() {
        dismissSpeechDialog()
        speechRecognizer?.destroy()
        speechRecognizer = null
        releaseSherpaRecognizer()
        super.onDestroy()
    }

    private fun dismissSpeechDialog() {
        val dialog = speechDialog ?: return
        speechDialog = null
        dialog.setOnCancelListener(null)
        if (dialog.isShowing) {
            dialog.dismiss()
        }
        speechTitleView = null
        speechStatusView = null
        speechResultEditText = null
        speechHoldButton = null
        speechSendButton = null
    }
}
