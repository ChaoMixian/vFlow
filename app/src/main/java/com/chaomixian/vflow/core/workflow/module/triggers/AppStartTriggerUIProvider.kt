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
import androidx.core.view.isVisible
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
    val closeChip: Chip,
    val eventSectionViews: List<View>
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
            view.findViewById(R.id.chip_app_close),
            listOf(
                view.findViewById(R.id.label_event),
                view.findViewById(R.id.cg_app_event),
                view.findViewById(R.id.divider_event)
            )
        )

        // 检查模块是否定义了 "event" 参数
        // AppStartTriggerModule 有，而 LaunchAppModule 没有
        val hasEventParameter = currentParameters.containsKey("event")
        holder.eventSectionViews.forEach { it.isVisible = hasEventParameter }


        // 仅当事件部分可见时，才处理其状态恢复和监听
        if (hasEventParameter) {
            val currentEvent = currentParameters["event"] as? String ?: "打开时"
            if (currentEvent == "打开时") holder.openChip.isChecked = true else holder.closeChip.isChecked = true
            holder.eventChipGroup.setOnCheckedStateChangeListener { _, _ -> onParametersChanged() }
        }


        // Restore state
        updateSummaryText(context, holder.summaryTextView, currentParameters)

        // Listeners
        holder.pickButton.setOnClickListener {
            val intent = Intent(context, AppPickerActivity::class.java)
            onStartActivityForResult?.invoke(intent) { _, _ ->}
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as AppStartTriggerViewHolder
        // 仅当事件部分可见时，才读取其值
        if (h.eventSectionViews.first().isVisible) {
            val event = if (h.openChip.isChecked) "打开时" else "关闭时"
            return mapOf("event" to event)
        }
        // 对于 LaunchAppModule，此方法返回空Map，因为它的参数是通过 ActivityResult 更新的
        return emptyMap()
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