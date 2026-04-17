package com.chaomixian.vflow.core.workflow.module.triggers

import android.app.Activity
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

private data class PackageTriggerAppEntry(
    val packageName: String,
    val appName: String
)

private class AppPackageTriggerViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button,
    val eventToggleGroup: MaterialButtonToggleGroup,
    val installButton: MaterialButton,
    val removeButton: MaterialButton,
    val updateButton: MaterialButton,
    val selectedAppsChipGroup: ChipGroup,
    var selectedApps: MutableList<PackageTriggerAppEntry> = mutableListOf()
) : CustomEditorViewHolder(view)

class AppPackageTriggerUIProvider : ModuleUIProvider {

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
        val view = LayoutInflater.from(context).inflate(R.layout.partial_app_package_trigger_editor, parent, false)
        val holder = AppPackageTriggerViewHolder(
            view,
            view.findViewById(R.id.text_selected_app_summary),
            view.findViewById(R.id.button_pick_app),
            view.findViewById(R.id.cg_app_package_event),
            view.findViewById(R.id.chip_app_installed),
            view.findViewById(R.id.chip_app_removed),
            view.findViewById(R.id.chip_app_updated),
            view.findViewById(R.id.cg_selected_apps)
        )

        val currentEvent = AppPackageTriggerModule().getInputs()
            .first { it.id == "event" }
            .normalizeEnumValueOrNull(currentParameters["event"] as? String)
            ?: AppPackageTriggerModule.EVENT_INSTALLED

        when (currentEvent) {
            AppPackageTriggerModule.EVENT_REMOVED -> holder.eventToggleGroup.check(R.id.chip_app_removed)
            AppPackageTriggerModule.EVENT_UPDATED -> holder.eventToggleGroup.check(R.id.chip_app_updated)
            else -> holder.eventToggleGroup.check(R.id.chip_app_installed)
        }

        holder.selectedAppsChipGroup.removeAllViews()
        holder.selectedApps.clear()
        @Suppress("UNCHECKED_CAST")
        val packageNames = currentParameters["packageNames"] as? List<String> ?: emptyList()
        packageNames.forEach { packageName ->
            val entry = PackageTriggerAppEntry(packageName, resolveAppName(context.packageManager, packageName))
            holder.selectedApps.add(entry)
            addAppChip(holder.selectedAppsChipGroup, entry, holder, onParametersChanged)
        }
        updateSummary(context, holder)

        holder.eventToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) onParametersChanged()
        }

        holder.pickButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra(UnifiedAppPickerSheet.EXTRA_MODE, AppPickerMode.SELECT_APP.name)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode != Activity.RESULT_OK || data == null) return@invoke
                val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME) ?: return@invoke
                if (holder.selectedApps.any { it.packageName == packageName }) return@invoke

                val entry = PackageTriggerAppEntry(packageName, resolveAppName(context.packageManager, packageName))
                holder.selectedApps.add(entry)
                addAppChip(holder.selectedAppsChipGroup, entry, holder, onParametersChanged)
                updateSummary(context, holder)
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val viewHolder = holder as AppPackageTriggerViewHolder
        val event = when (viewHolder.eventToggleGroup.checkedButtonId) {
            R.id.chip_app_removed -> AppPackageTriggerModule.EVENT_REMOVED
            R.id.chip_app_updated -> AppPackageTriggerModule.EVENT_UPDATED
            else -> AppPackageTriggerModule.EVENT_INSTALLED
        }
        return mapOf(
            "event" to event,
            "packageNames" to viewHolder.selectedApps.map { it.packageName }
        )
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    private fun resolveAppName(packageManager: PackageManager, packageName: String): String {
        return try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun addAppChip(
        chipGroup: ChipGroup,
        entry: PackageTriggerAppEntry,
        holder: AppPackageTriggerViewHolder,
        onParametersChanged: () -> Unit
    ) {
        val chip = Chip(chipGroup.context).apply {
            text = entry.appName
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                chipGroup.removeView(this)
                holder.selectedApps.remove(entry)
                updateSummary(chipGroup.context, holder)
                onParametersChanged()
            }
        }
        chipGroup.addView(chip)
    }

    private fun updateSummary(context: Context, holder: AppPackageTriggerViewHolder) {
        holder.summaryTextView.isVisible = true
        holder.summaryTextView.text = if (holder.selectedApps.isEmpty()) {
            context.getString(R.string.summary_vflow_trigger_app_package_picker_any_app)
        } else {
            context.getString(
                R.string.summary_vflow_trigger_app_package_picker_selected,
                holder.selectedApps.joinToString("、") { it.appName }
            )
        }
    }
}
