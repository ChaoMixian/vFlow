// 文件: PickerHandler.kt
// 描述: 处理各种 PickerType 的选择逻辑
package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.PickerType
import com.chaomixian.vflow.ui.app_picker.AppPickerActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Picker 类型处理Handler
 */
class PickerHandler(
    private val activity: AppCompatActivity,
    private val appPickerLauncher: ActivityResultLauncher<Intent>,
    private val filePickerLauncher: ActivityResultLauncher<Array<String>>,
    private val mediaPickerLauncher: ActivityResultLauncher<Array<String>>,
    private val onUpdateParameters: (Map<String, Any?>) -> Unit
) {
    // 当前正在处理的输入定义，用于结果回调
    private var currentInputDef: InputDefinition? = null

    // 文件选择器的 pending 输入定义（防止被其他操作清空）
    private var pendingFileInputDef: InputDefinition? = null

    /**
     * 处理 Picker 请求
     */
    fun handle(inputDef: InputDefinition) {
        currentInputDef = inputDef
        when (inputDef.pickerType) {
            PickerType.APP -> handleAppPicker()
            PickerType.ACTIVITY -> handleActivityPicker()
            PickerType.DATE -> handleDatePicker(inputDef)
            PickerType.TIME -> handleTimePicker(inputDef)
            PickerType.DATETIME -> handleDateTimePicker(inputDef)
            PickerType.FILE -> handleFilePicker(inputDef)
            PickerType.MEDIA -> handleMediaPicker(inputDef)
            PickerType.NONE -> { currentInputDef = null }
        }
    }

    /**
     * 处理应用选择结果
     */
    fun handleAppPickerResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val packageName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE_NAME)
            val activityName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_ACTIVITY_NAME)

            when (currentInputDef?.pickerType) {
                PickerType.ACTIVITY -> {
                    if (packageName != null && activityName != null) {
                        val value = "$packageName/$activityName"
                        onUpdateParameters(mapOf(currentInputDef!!.id to value))
                    }
                }
                PickerType.APP -> {
                    if (packageName != null) {
                        onUpdateParameters(mapOf(currentInputDef!!.id to packageName))
                    }
                }
                else -> {}
            }
        }
        currentInputDef = null
    }

    /**
     * 处理文件选择结果
     */
    fun handleFilePickerResult(uri: Uri?) {
        // 优先使用 currentInputDef，如果为空则使用 pendingFileInputDef
        val inputDef = currentInputDef ?: pendingFileInputDef
        if (uri != null && inputDef != null) {
            val value = uri.toString()
            onUpdateParameters(mapOf(inputDef.id to value))
        } else if (uri == null) {
            android.util.Log.d("PickerHandler", "文件选择已取消或失败")
        } else if (inputDef == null) {
            android.util.Log.e("PickerHandler", "无法找到输入定义来处理文件选择结果")
        }
        // 清理状态
        currentInputDef = null
        pendingFileInputDef = null
    }

    /**
     * 处理媒体选择结果
     */
    fun handleMediaPickerResult(uri: Uri?) {
        // 优先使用 currentInputDef，如果为空则使用 pendingFileInputDef
        val inputDef = currentInputDef ?: pendingFileInputDef
        if (uri != null && inputDef != null) {
            val value = uri.toString()
            onUpdateParameters(mapOf(inputDef.id to value))
        } else if (uri == null) {
            android.util.Log.d("PickerHandler", "媒体选择已取消或失败")
        } else if (inputDef == null) {
            android.util.Log.e("PickerHandler", "无法找到输入定义来处理媒体选择结果")
        }
        // 清理状态
        currentInputDef = null
        pendingFileInputDef = null
    }

    private fun handleAppPicker() {
        val intent = Intent(activity, AppPickerActivity::class.java)
        appPickerLauncher.launch(intent)
    }

    private fun handleActivityPicker() {
        val intent = Intent(activity, AppPickerActivity::class.java)
        appPickerLauncher.launch(intent)
    }

    private fun handleDatePicker(inputDef: InputDefinition) {
        val currentDate = parseDate(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(activity.getString(R.string.picker_select_date))
            .setSelection(currentDate?.toEpochDay()?.times(24 * 60 * 60 * 1000))
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = java.time.Instant.ofEpochMilli(selection)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            onUpdateParameters(mapOf(inputDef.id to date.toString()))
        }

        datePicker.show(fragmentManager, "DatePicker")
    }

    private fun handleTimePicker(inputDef: InputDefinition) {
        val currentTime = parseTime(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val timePicker = MaterialTimePicker.Builder()
            .setHour(currentTime?.hour ?: 12)
            .setMinute(currentTime?.minute ?: 0)
            .setTitleText(activity.getString(R.string.picker_select_time))
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val time = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            onUpdateParameters(mapOf(inputDef.id to time))
        }

        timePicker.show(fragmentManager, "TimePicker")
    }

    private fun handleDateTimePicker(inputDef: InputDefinition) {
        val currentDateTime = parseDateTime(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(activity.getString(R.string.picker_select_datetime))
            .setSelection(
                (currentDateTime?.toLocalDate()?.toEpochDay() ?: LocalDate.now().toEpochDay())
                    .times(24 * 60 * 60 * 1000)
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            val localDate = java.time.Instant.ofEpochMilli(dateSelection)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val timePicker = MaterialTimePicker.Builder()
                .setHour(currentDateTime?.hour ?: 12)
                .setMinute(currentDateTime?.minute ?: 0)
                .setTitleText(activity.getString(R.string.picker_select_time))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val dateTime = localDate.atTime(timePicker.hour, timePicker.minute)
                onUpdateParameters(mapOf(inputDef.id to dateTime.toString()))
            }

            timePicker.show(fragmentManager, "TimePicker")
        }

        datePicker.show(fragmentManager, "DatePicker")
    }

    private fun handleFilePicker(inputDef: InputDefinition) {
        // 保存 pending 输入定义，防止在文件选择过程中被其他操作清空
        pendingFileInputDef = inputDef
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun handleMediaPicker(inputDef: InputDefinition) {
        // 保存 pending 输入定义，防止在媒体选择过程中被其他操作清空
        pendingFileInputDef = inputDef
        mediaPickerLauncher.launch(arrayOf("image/*"))
    }

    private var getCurrentValue: (InputDefinition) -> Any? = { null }

    fun setGetCurrentValueCallback(callback: (InputDefinition) -> Any?) {
        getCurrentValue = callback
    }

    private fun parseDate(value: Any?): LocalDate? {
        return try {
            (value as? String)?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(value: Any?): LocalTime? {
        return try {
            (value as? String)?.let { LocalTime.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDateTime(value: Any?): LocalDateTime? {
        return try {
            (value as? String)?.let { LocalDateTime.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val REQUEST_FILE_PICKER = 1001
        const val REQUEST_MEDIA_PICKER = 1002
    }
}
