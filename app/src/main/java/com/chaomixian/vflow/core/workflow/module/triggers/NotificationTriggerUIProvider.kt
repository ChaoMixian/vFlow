// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/NotificationTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.google.android.material.textfield.TextInputEditText

class NotificationTriggerUIProvider : ModuleUIProvider {
    data class SelectedAppEntry(
        val packageName: String,
        val appName: String
    )

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val appSummary: TextView = view.findViewById(R.id.text_selected_app_summary)
        val pickAppButton: Button = view.findViewById(R.id.button_pick_app)
        val selectedAppsChipGroup: ChipGroup = view.findViewById(R.id.cg_selected_apps)
        val appFilterTypeToggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.cg_notification_app_filter_type)
        val appIncludeButton: MaterialButton = view.findViewById(R.id.chip_notification_app_include)
        val appExcludeButton: MaterialButton = view.findViewById(R.id.chip_notification_app_exclude)
        val titleFilterTypeToggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.cg_notification_title_filter_type)
        val titleIncludeButton: MaterialButton = view.findViewById(R.id.chip_notification_title_include)
        val titleExcludeButton: MaterialButton = view.findViewById(R.id.chip_notification_title_exclude)
        val titleFilter: TextInputEditText = view.findViewById(R.id.et_title_filter)
        val contentFilterTypeToggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.cg_notification_content_filter_type)
        val contentIncludeButton: MaterialButton = view.findViewById(R.id.chip_notification_content_include)
        val contentExcludeButton: MaterialButton = view.findViewById(R.id.chip_notification_content_exclude)
        val contentFilter: TextInputEditText = view.findViewById(R.id.et_content_filter)
        val selectedApps: MutableList<SelectedAppEntry> = mutableListOf()
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "app_filter",
        "app_filter_type",
        "packageNames",
        "title_filter_type",
        "title_filter",
        "content_filter_type",
        "content_filter"
    )

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_notification_trigger_editor, parent, false)
        val holder = ViewHolder(view)

        bindToggleGroup(
            holder.appFilterTypeToggleGroup,
            currentParameters["app_filter_type"] as? String,
            holder.appIncludeButton.id,
            holder.appExcludeButton.id,
            NotificationTriggerModule.APP_FILTER_EXCLUDE
        )
        bindToggleGroup(
            holder.titleFilterTypeToggleGroup,
            currentParameters["title_filter_type"] as? String,
            holder.titleIncludeButton.id,
            holder.titleExcludeButton.id,
            NotificationTriggerModule.TEXT_FILTER_EXCLUDE
        )
        bindToggleGroup(
            holder.contentFilterTypeToggleGroup,
            currentParameters["content_filter_type"] as? String,
            holder.contentIncludeButton.id,
            holder.contentExcludeButton.id,
            NotificationTriggerModule.TEXT_FILTER_EXCLUDE
        )

        @Suppress("UNCHECKED_CAST")
        val packageNames = currentParameters["packageNames"] as? List<String>
        val legacyAppFilter = currentParameters["app_filter"] as? String
        val initialPackages = if (!packageNames.isNullOrEmpty()) packageNames else listOfNotNull(legacyAppFilter?.takeIf { it.isNotBlank() })
        holder.selectedAppsChipGroup.removeAllViews()
        holder.selectedApps.clear()
        initialPackages.forEach { packageName ->
            val entry = SelectedAppEntry(packageName, resolveAppName(context, packageName))
            holder.selectedApps.add(entry)
            addAppChip(holder, entry, onParametersChanged)
        }
        updateAppSummary(context, holder)

        holder.titleFilter.setText(currentParameters["title_filter"] as? String ?: "")
        holder.contentFilter.setText(currentParameters["content_filter"] as? String ?: "")

        holder.appFilterTypeToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) onParametersChanged()
        }
        holder.titleFilterTypeToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) onParametersChanged()
        }
        holder.contentFilterTypeToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) onParametersChanged()
        }

        holder.pickAppButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra(UnifiedAppPickerSheet.EXTRA_MODE, AppPickerMode.SELECT_APP.name)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode != Activity.RESULT_OK || data == null) return@invoke
                val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME) ?: return@invoke
                if (holder.selectedApps.any { it.packageName == packageName }) return@invoke

                val entry = SelectedAppEntry(packageName, resolveAppName(context, packageName))
                holder.selectedApps.add(entry)
                addAppChip(holder, entry, onParametersChanged)
                updateAppSummary(context, holder)
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "app_filter_type" to when (h.appFilterTypeToggleGroup.checkedButtonId) {
                h.appExcludeButton.id -> NotificationTriggerModule.APP_FILTER_EXCLUDE
                else -> NotificationTriggerModule.APP_FILTER_INCLUDE
            },
            "packageNames" to h.selectedApps.map { it.packageName },
            "app_filter" to "",
            "title_filter_type" to when (h.titleFilterTypeToggleGroup.checkedButtonId) {
                h.titleExcludeButton.id -> NotificationTriggerModule.TEXT_FILTER_EXCLUDE
                else -> NotificationTriggerModule.TEXT_FILTER_INCLUDE
            },
            "title_filter" to h.titleFilter.text.toString(),
            "content_filter_type" to when (h.contentFilterTypeToggleGroup.checkedButtonId) {
                h.contentExcludeButton.id -> NotificationTriggerModule.TEXT_FILTER_EXCLUDE
                else -> NotificationTriggerModule.TEXT_FILTER_INCLUDE
            },
            "content_filter" to h.contentFilter.text.toString()
        )
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    private fun bindToggleGroup(
        group: MaterialButtonToggleGroup,
        rawValue: String?,
        includeButtonId: Int,
        excludeButtonId: Int,
        excludeValue: String
    ) {
        group.check(if (rawValue == excludeValue) excludeButtonId else includeButtonId)
    }

    private fun resolveAppName(context: Context, packageName: String): String {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(context.packageManager)
                .toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun addAppChip(
        holder: ViewHolder,
        entry: SelectedAppEntry,
        onParametersChanged: () -> Unit
    ) {
        val chip = Chip(holder.selectedAppsChipGroup.context).apply {
            text = entry.appName
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                holder.selectedAppsChipGroup.removeView(this)
                holder.selectedApps.remove(entry)
                updateAppSummary(holder.selectedAppsChipGroup.context, holder)
                onParametersChanged()
            }
        }
        holder.selectedAppsChipGroup.addView(chip)
    }

    private fun updateAppSummary(context: Context, holder: ViewHolder) {
        holder.appSummary.isVisible = true
        holder.appSummary.text = if (holder.selectedApps.isEmpty()) {
            context.getString(R.string.summary_vflow_trigger_app_package_picker_any_app)
        } else {
            context.getString(
                R.string.summary_vflow_trigger_app_package_picker_selected,
                holder.selectedApps.joinToString("、") { it.appName }
            )
        }
    }
}
