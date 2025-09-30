// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/TimeTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class TimeTriggerViewHolder(
    view: View,
    val timeTextView: TextView,
    val pickTimeButton: Button,
    val dayChips: Map<Int, Chip>
) : CustomEditorViewHolder(view)

class TimeTriggerUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("time", "days")

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_time_trigger_editor, parent, false)
        val timeTextView = view.findViewById<TextView>(R.id.text_selected_time)
        val pickTimeButton = view.findViewById<Button>(R.id.button_pick_time)
        val dayChips = mapOf(
            Calendar.SUNDAY to view.findViewById<Chip>(R.id.chip_sun),
            Calendar.MONDAY to view.findViewById<Chip>(R.id.chip_mon),
            Calendar.TUESDAY to view.findViewById<Chip>(R.id.chip_tue),
            Calendar.WEDNESDAY to view.findViewById<Chip>(R.id.chip_wed),
            Calendar.THURSDAY to view.findViewById<Chip>(R.id.chip_thu),
            Calendar.FRIDAY to view.findViewById<Chip>(R.id.chip_fri),
            Calendar.SATURDAY to view.findViewById<Chip>(R.id.chip_sat)
        )
        val holder = TimeTriggerViewHolder(view, timeTextView, pickTimeButton, dayChips)

        // 恢复已有参数
        val currentTime = currentParameters["time"] as? String ?: "09:00"
        timeTextView.text = currentTime

        // 兼容从JSON加载的 Double 列表和默认的 Int 列表
        val daysAny = currentParameters["days"]
        val currentDays = when (daysAny) {
            is List<*> -> daysAny.mapNotNull { (it as? Number)?.toInt() }
            else -> emptyList()
        }
        dayChips.forEach { (dayInt, chip) ->
            chip.isChecked = currentDays.contains(dayInt)
        }

        // 设置时间选择器
        pickTimeButton.setOnClickListener {
            val (hour, minute) = currentTime.split(":").map { it.toInt() }
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText("选择触发时间")
                .build()

            picker.addOnPositiveButtonClickListener {
                val newTime = String.format("%02d:%02d", picker.hour, picker.minute)
                holder.timeTextView.text = newTime
                onParametersChanged()
            }
            // 需要 FragmentManager 来显示 picker
            (context as? FragmentActivity)?.supportFragmentManager?.let {
                picker.show(it, "TIME_PICKER")
            }
        }

        // 为星期选择Chips添加监听
        dayChips.values.forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ -> onParametersChanged() }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as TimeTriggerViewHolder
        val time = h.timeTextView.text.toString()
        val days = h.dayChips.filter { it.value.isChecked }.keys.toList()
        return mapOf("time" to time, "days" to days)
    }
}