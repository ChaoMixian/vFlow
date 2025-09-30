// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/AppStartTriggerUIProvider.kt
// 描述: 为 AppStartTriggerModule 提供自定义UI交互逻辑。
package com.chaomixian.vflow.core.workflow.module.triggers

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
import com.chaomixian.vflow.ui.app_picker.AppPickerActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class AppStartTriggerViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button,
    val eventChipGroup: ChipGroup,
    val openChip: Chip,
    val closeChip: Chip
) : CustomEditorViewHolder(view)

class AppStartTriggerUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("event", "packageName", "activityName")

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
            view.findViewById(R.id.chip_app_close)
        )

        // Restore state
        val currentEvent = currentParameters["event"] as? String ?: "打开时"
        if (currentEvent == "打开时") holder.openChip.isChecked = true else holder.closeChip.isChecked = true

        updateSummaryText(context, holder.summaryTextView, currentParameters)

        // Listeners
        holder.pickButton.setOnClickListener {
            val intent = Intent(context, AppPickerActivity::class.java)
            onStartActivityForResult?.invoke(intent) { _, _ ->}
        }
        holder.eventChipGroup.setOnCheckedStateChangeListener { _, _ -> onParametersChanged() }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as AppStartTriggerViewHolder
        val event = if (h.openChip.isChecked) "打开时" else "关闭时"
        // 包名和Activity名在外部通过回调更新，这里只返回事件
        return mapOf("event" to event)
    }

    private fun updateSummaryText(context: Context, textView: TextView, parameters: Map<String, Any?>) {
        val packageName = parameters["packageName"] as? String
        val activityName = parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            textView.text = "尚未选择"
            return
        }

        val pm = context.packageManager
        val appName = try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // 如果找不到应用，就显示包名
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            "应用: $appName"
        } else {
            // 如果Activity名称过长，只显示类名
            val simpleActivityName = activityName.substringAfterLast('.')
            "Activity: $appName / $simpleActivityName"
        }
        textView.text = "已选择: $displayText"
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}