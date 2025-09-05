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

// 文件：InputRequestActivity.kt
// 描述：一个透明的Activity，用于在工作流执行期间显示用户输入或信息查看对话框。

class InputRequestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST_TYPE = "request_type" // 'input' or 'quick_view'
        const val EXTRA_INPUT_TYPE = "input_type"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content" // 新增
    }

    // 新增：用于处理图片选择结果的 ActivityResultLauncher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // 用户成功选择了图片
            ExecutionUIService.inputCompletable?.complete(uri.toString())
        } else {
            // 用户取消了选择
            ExecutionUIService.inputCompletable?.complete(null)
        }
        finish() // 完成操作后关闭Activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val useDynamicColor = prefs.getBoolean("dynamicColorEnabled", false)
        if (useDynamicColor) {
            setTheme(R.style.Theme_vFlow_Transparent_Dynamic) // 应用动态透明主题
        } else {
            setTheme(R.style.Theme_vFlow_Transparent_Default) // 应用默认透明主题
        }

        super.onCreate(savedInstanceState)
        // 这是一个UI-host Activity，不需要设置 contentView

        val requestType = intent.getStringExtra(EXTRA_REQUEST_TYPE)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "提示"

        when (requestType) {
            "quick_view" -> {
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
                showQuickViewDialog(title, content)
            }
            "input" -> {
                val inputType = intent.getStringExtra(EXTRA_INPUT_TYPE)
                when (inputType) {
                    "时间" -> showTimePickerDialog(title)
                    "日期" -> showDatePickerDialog(title)
                    else -> showTextInputDialog(title, inputType)
                }
            }
            // 处理 "pick_image" 的分支
            "pick_image" -> {
                pickImageLauncher.launch("image/*") // 启动图片选择器
            }
            else -> finish() // 未知类型则直接关闭
        }
    }

    /**
     * 新增：显示快速查看对话框。
     */
    private fun showQuickViewDialog(title: String, content: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(content) // 将内容设置为消息体
            .setPositiveButton("关闭") { _, _ ->
                ExecutionUIService.inputCompletable?.complete(true) // 通知Service可以继续执行
                finish()
            }
            .setOnCancelListener {
                ExecutionUIService.inputCompletable?.complete(true)
                finish()
            }
            .show()
    }

    /**
     * 显示文本或数字输入对话框。
     */
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
                val result: Any? = if (type == "数字") {
                    inputText.toDoubleOrNull()
                } else {
                    inputText
                }
                ExecutionUIService.inputCompletable?.complete(result)
                finish()
            }
            .setNegativeButton("取消") { _, _ ->
                ExecutionUIService.inputCompletable?.complete(null)
                finish()
            }
            .setOnCancelListener {
                ExecutionUIService.inputCompletable?.complete(null)
                finish()
            }
            .show()
    }

    /**
     * 显示 Material 3 时间选择器对话框。
     */
    private fun showTimePickerDialog(title: String) {
        val picker =
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                .setMinute(Calendar.getInstance().get(Calendar.MINUTE))
                .setTitleText(title)
                .build()

        picker.addOnPositiveButtonClickListener {
            val timeString = String.format("%02d:%02d", picker.hour, picker.minute)
            ExecutionUIService.inputCompletable?.complete(timeString)
            finish()
        }
        picker.addOnNegativeButtonClickListener {
            ExecutionUIService.inputCompletable?.complete(null)
            finish()
        }
        picker.addOnCancelListener {
            ExecutionUIService.inputCompletable?.complete(null)
            finish()
        }

        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    /**
     * 显示 Material 3 日期选择器对话框。
     */
    private fun showDatePickerDialog(title: String) {
        val picker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(title)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

        picker.addOnPositiveButtonClickListener { selection ->
            ExecutionUIService.inputCompletable?.complete(selection)
            finish()
        }
        picker.addOnNegativeButtonClickListener {
            ExecutionUIService.inputCompletable?.complete(null)
            finish()
        }
        picker.addOnCancelListener {
            ExecutionUIService.inputCompletable?.complete(null)
            finish()
        }

        picker.show(supportFragmentManager, "DATE_PICKER")
    }
}