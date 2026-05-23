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
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.app_picker.AppPickerMode
import com.chaomixian.vflow.ui.app_picker.UnifiedAppPickerSheet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

private class AppSwitchTriggerViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button,
    val excludedAppsChipGroup: ChipGroup,
    var excludedApps: MutableList<AppEntry> = mutableListOf()
) : CustomEditorViewHolder(view)

class AppSwitchTriggerUIProvider : ModuleUIProvider {
    override fun getHandledInputIds(): Set<String> = setOf("excludedPackageNames")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_app_switch_trigger_editor, parent, false)
        val holder = AppSwitchTriggerViewHolder(
            view,
            view.findViewById(R.id.text_excluded_app_summary),
            view.findViewById(R.id.button_pick_excluded_app),
            view.findViewById(R.id.cg_excluded_apps)
        )
        val pm = context.packageManager

        holder.excludedAppsChipGroup.removeAllViews()
        holder.excludedApps.clear()
        @Suppress("UNCHECKED_CAST")
        val excludedPackageNames = currentParameters["excludedPackageNames"] as? List<String> ?: emptyList()
        excludedPackageNames.forEach { packageName ->
            val entry = AppEntry(packageName, resolveAppName(pm, packageName))
            holder.excludedApps.add(entry)
            addAppChip(holder.excludedAppsChipGroup, entry, holder, onParametersChanged)
        }
        updateSummary(context, holder)

        holder.pickButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra(UnifiedAppPickerSheet.EXTRA_MODE, AppPickerMode.SELECT_APP.name)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode != Activity.RESULT_OK || data == null) return@invoke
                val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME) ?: return@invoke
                if (holder.excludedApps.any { it.packageName == packageName }) return@invoke

                val entry = AppEntry(packageName, resolveAppName(pm, packageName))
                holder.excludedApps.add(entry)
                addAppChip(holder.excludedAppsChipGroup, entry, holder, onParametersChanged)
                updateSummary(context, holder)
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as AppSwitchTriggerViewHolder
        return mapOf("excludedPackageNames" to h.excludedApps.map { it.packageName })
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    private fun addAppChip(
        chipGroup: ChipGroup,
        entry: AppEntry,
        holder: AppSwitchTriggerViewHolder,
        onParametersChanged: () -> Unit
    ) {
        val chip = Chip(chipGroup.context).apply {
            text = entry.appName
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                chipGroup.removeView(this)
                holder.excludedApps.remove(entry)
                updateSummary(chipGroup.context, holder)
                onParametersChanged()
            }
        }
        chipGroup.addView(chip)
    }

    private fun updateSummary(context: Context, holder: AppSwitchTriggerViewHolder) {
        holder.summaryTextView.text = if (holder.excludedApps.isEmpty()) {
            context.getString(R.string.summary_vflow_trigger_app_switch_picker_no_exclusions)
        } else {
            context.getString(
                R.string.summary_vflow_trigger_app_switch_picker_selected,
                holder.excludedApps.joinToString("、") { it.appName }
            )
        }
    }

    private fun resolveAppName(packageManager: PackageManager, packageName: String): String {
        return try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
