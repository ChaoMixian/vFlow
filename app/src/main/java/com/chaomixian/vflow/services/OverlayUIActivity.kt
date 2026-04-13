// 文件：main/java/com/chaomixian/vflow/services/OverlayUIActivity.kt
// 添加了对 MediaProjection 的支持
package com.chaomixian.vflow.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.load
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.workflow.module.system.InputModule
import com.chaomixian.vflow.services.ExecutionUIService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

class OverlayUIActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val context = LocaleManager.applyLanguage(newBase, languageCode)
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

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDynamicTheme()
        handleIntent()
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
        ExecutionUIService.inputCompletable?.complete(result)
        finish()
    }

    private fun cancel() {
        ExecutionUIService.inputCompletable?.complete(null)
        finish()
    }

    private fun finishWithError() {
        ExecutionUIService.inputCompletable?.complete(null)
        finish()
    }

    private fun applyDynamicTheme() {
        val themeResId = ThemeUtils.getThemeResId(this, transparent = true)
        setTheme(themeResId)
    }
}
