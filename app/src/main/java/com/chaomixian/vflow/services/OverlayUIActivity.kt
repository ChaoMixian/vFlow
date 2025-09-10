// 文件：OverlayUIActivity.kt
package com.chaomixian.vflow.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.load
import com.chaomixian.vflow.R
import com.chaomixian.vflow.services.ExecutionUIService
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

class OverlayUIActivity : AppCompatActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ExecutionUIService.inputCompletable?.complete(uri.toString())
        } else {
            ExecutionUIService.inputCompletable?.complete(null)
        }
        finish()
    }

    // 用于JSON反序列化
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
                showQuickViewDialog(title ?: "快速查看", content)
            }
            "quick_view_image" -> {
                val imageUri = intent.getStringExtra("content")
                if (imageUri != null) {
                    showQuickViewImageDialog(title ?: "图片预览", imageUri)
                } else {
                    finishWithError()
                }
            }
            "input" -> {
                val inputType = intent.getStringExtra("input_type")
                when (inputType) {
                    "时间" -> showTimePickerDialog(title ?: "请选择时间")
                    "日期" -> showDatePickerDialog(title ?: "请选择日期")
                    else -> showTextInputDialog(title ?: "请输入", inputType)
                }
            }
            "pick_image" -> pickImageLauncher.launch("image/*")
            "workflow_chooser" -> {
                // [核心修改] 优先尝试读取 Serializable，失败则回退到读取 JSON 字符串
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
            "share" -> {
                handleShareRequest()
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
            .setPositiveButton("关闭") { _, _ -> complete(true) }
            .setNeutralButton("复制", null)
            .setNegativeButton("分享", null)
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
            Toast.makeText(this, "没有可分享的内容", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "分享图片失败: ${e.message}", Toast.LENGTH_LONG).show()
                    return
                }
            }
            else -> {
                Toast.makeText(this, "不支持的分享类型", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val chooser = Intent.createChooser(shareIntent, "分享内容")
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
            val clip = ClipData.newUri(contentResolver, "vFlow Image", safeUri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "图片已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "复制图片失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun showWorkflowChooserDialog(workflows: Map<String, String>) {
        val items = workflows.values.toTypedArray()
        val itemIds = workflows.keys.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("选择要执行的工作流")
            .setItems(items) { _, which ->
                complete(itemIds[which])
            }
            .setOnCancelListener { cancel() }
            .show()
    }

    /**
     * 更新：为文本快速查看对话框添加复制和分享按钮，并重写行为
     */
    private fun showQuickViewDialog(title: String, content: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("关闭") { _, _ -> complete(true) }
            .setNeutralButton("复制", null)
            .setNegativeButton("分享", null)
            .setOnCancelListener { cancel() }
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("vFlow Text", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            shareContent("text", content)
        }
    }


    private fun showTextInputDialog(title: String, type: String?) {
        val editText = EditText(this).apply {
            inputType = if (type == "数字") {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val inputText = editText.text.toString()
                val result: Any? = if (type == "数字") inputText.toDoubleOrNull() else inputText
                complete(result)
            }
            .setNegativeButton("取消") { _, _ -> cancel() }
            .setOnCancelListener { cancel() }
            .show()
    }

    private fun showTimePickerDialog(title: String) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            .setMinute(Calendar.getInstance().get(Calendar.MINUTE))
            .setTitleText(title)
            .build()

        picker.addOnPositiveButtonClickListener {
            complete(String.format("%02d:%02d", picker.hour, picker.minute))
        }
        picker.addOnNegativeButtonClickListener { cancel() }
        picker.addOnCancelListener { cancel() }
        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun showDatePickerDialog(title: String) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            complete(selection)
        }
        picker.addOnNegativeButtonClickListener { cancel() }
        picker.addOnCancelListener { cancel() }
        picker.show(supportFragmentManager, "DATE_PICKER")
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
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val useDynamicColor = prefs.getBoolean("dynamicColorEnabled", false)
        if (useDynamicColor) {
            setTheme(R.style.Theme_vFlow_Transparent_Dynamic)
        } else {
            setTheme(R.style.Theme_vFlow_Transparent_Default)
        }
    }
}