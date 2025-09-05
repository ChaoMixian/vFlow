package com.chaomixian.vflow.ui.common

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.R
import com.chaomixian.vflow.services.ExecutionUIService
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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
                @Suppress("UNCHECKED_CAST")
                val workflows = intent.getSerializableExtra("workflow_list") as? Map<String, String>
                workflows?.let { showWorkflowChooserDialog(it) } ?: finishWithError()
            }
            else -> finishWithError()
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

    private fun showQuickViewDialog(title: String, content: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("关闭") { _, _ -> complete(true) }
            .setOnCancelListener { complete(true) }
            .show()
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