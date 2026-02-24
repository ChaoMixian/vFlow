// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/AppStartTriggerUIProvider.kt
// 描述: 为 AppStartTriggerModule 提供自定义UI交互逻辑。强制使用硬编码字符串以确保逻辑一致性。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.app_picker.AppPickerMode
import com.chaomixian.vflow.ui.app_picker.UnifiedAppPickerSheet
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

data class AppEntry(
    val packageName: String,
    val appName: String
)

class AppStartTriggerViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button,
    val eventToggleGroup: MaterialButtonToggleGroup,
    val openButton: MaterialButton,
    val closeButton: MaterialButton,
    val eventSectionViews: List<View>,
    val selectedAppsChipGroup: ChipGroup,
    var selectedApps: MutableList<AppEntry> = mutableListOf()
) : CustomEditorViewHolder(view)

class AppStartTriggerUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("event", "packageNames")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_app_start_trigger_editor, parent, false)
        val holder = AppStartTriggerViewHolder(
            view,
            view.findViewById(R.id.text_selected_app_summary),
            view.findViewById(R.id.button_pick_app),
            view.findViewById(R.id.cg_app_event),
            view.findViewById(R.id.chip_app_open),
            view.findViewById(R.id.chip_app_close),
            listOf(
                view.findViewById(R.id.label_event),
                view.findViewById(R.id.cg_app_event),
                view.findViewById(R.id.divider_event)
            ),
            view.findViewById(R.id.cg_selected_apps)
        )

        // 检查模块是否定义了 "event" 参数
        // AppStartTriggerModule 有，而 LaunchAppModule 没有
        val hasEventParameter = currentParameters.containsKey("event")
        holder.eventSectionViews.forEach { it.isVisible = hasEventParameter }

        // 设置事件选项的默认选中状态
        if (hasEventParameter) {
            val currentEvent = currentParameters["event"] as? String

            if (currentEvent == "关闭时") {
                holder.eventToggleGroup.check(R.id.chip_app_close)
            } else {
                holder.eventToggleGroup.check(R.id.chip_app_open)
            }

            holder.eventToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) onParametersChanged()
            }
        }

        // Restore state
        holder.selectedAppsChipGroup.removeAllViews()
        holder.selectedApps.clear()

        @Suppress("UNCHECKED_CAST")
        val packageNames = currentParameters["packageNames"] as? List<String> ?: emptyList()
        val pm = context.packageManager

        for (packageName in packageNames) {
            try {
                val appName = pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                val entry = AppEntry(packageName, appName)
                holder.selectedApps.add(entry)
                addAppChip(holder.selectedAppsChipGroup, entry, holder, onParametersChanged)
            } catch (e: Exception) {
                // 应用已卸载，跳过
            }
        }

        // Listeners
        holder.pickButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra(UnifiedAppPickerSheet.EXTRA_MODE, AppPickerMode.SELECT_APP.name)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME)
                    if (packageName != null && holder.selectedApps.none { it.packageName == packageName }) {
                        val appName = try {
                            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                        } catch (e: PackageManager.NameNotFoundException) {
                            packageName
                        }
                        val entry = AppEntry(packageName, appName)
                        holder.selectedApps.add(entry)
                        addAppChip(holder.selectedAppsChipGroup, entry, holder, onParametersChanged)
                        onParametersChanged()
                    }
                }
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as AppStartTriggerViewHolder
        val result = mutableMapOf<String, Any?>()

        if (h.eventSectionViews.first().isVisible) {
            val event = when (h.eventToggleGroup.checkedButtonId) {
                R.id.chip_app_close -> "关闭时"
                else -> "打开时" // 默认值
            }
            result["event"] = event
        }

        result["packageNames"] = h.selectedApps.map { it.packageName }
        return result
    }

    private fun addAppChip(
        chipGroup: ChipGroup,
        entry: AppEntry,
        holder: AppStartTriggerViewHolder,
        onParametersChanged: () -> Unit
    ) {
        val chip = Chip(chipGroup.context).apply {
            text = entry.appName
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                chipGroup.removeView(this)
                holder.selectedApps.remove(entry)
                onParametersChanged()
            }
        }
        chipGroup.addView(chip)
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}