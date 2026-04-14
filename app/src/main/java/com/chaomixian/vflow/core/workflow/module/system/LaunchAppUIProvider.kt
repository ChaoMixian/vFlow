// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/LaunchAppUIProvider.kt
// 描述: 为 LaunchAppModule 提供自定义UI交互逻辑。
//      使用 UnifiedAppPickerSheet 选择单个应用及其 Activity。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
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
import com.chaomixian.vflow.ui.app_picker.AppUserSupport
import com.chaomixian.vflow.ui.app_picker.UnifiedAppPickerSheet

class LaunchAppViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button
) : CustomEditorViewHolder(view) {
    var selectedPackageName: String? = null
    var selectedActivityName: String? = null
    var selectedUserId: Int? = null
}

class LaunchAppUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("packageName", "activityName", "userId")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_launch_app_editor, parent, false)
        val holder = LaunchAppViewHolder(
            view,
            view.findViewById(R.id.text_selected_app_summary),
            view.findViewById(R.id.button_pick_app)
        )

        // 恢复已选择的应用状态
        holder.selectedPackageName = currentParameters["packageName"] as? String
        holder.selectedActivityName = currentParameters["activityName"] as? String
        holder.selectedUserId = (currentParameters["userId"] as? Number)?.toInt()
        updateSummaryText(context, holder.summaryTextView, currentParameters)

        // 点击按钮启动应用选择器
        holder.pickButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra(UnifiedAppPickerSheet.EXTRA_MODE, AppPickerMode.SELECT_ACTIVITY.name)
            }
            onStartActivityForResult?.invoke(intent) { resultCode, data ->
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME)
                    val activityName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_ACTIVITY_NAME)
                    val selectedUserId = if (data.hasExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_USER_ID)) {
                        data.getIntExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_USER_ID, AppUserSupport.getCurrentUserId())
                    } else {
                        null
                    }
                    if (packageName != null && activityName != null) {
                        holder.selectedPackageName = packageName
                        holder.selectedActivityName = activityName
                        holder.selectedUserId = selectedUserId
                        updateSummaryText(context, holder.summaryTextView, mapOf(
                            "packageName" to packageName,
                            "activityName" to activityName,
                            "userId" to selectedUserId
                        ))
                        onParametersChanged()
                    }
                }
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val launchAppHolder = holder as? LaunchAppViewHolder ?: return emptyMap()
        return mapOf(
            "packageName" to (launchAppHolder.selectedPackageName as Any?),
            "activityName" to (launchAppHolder.selectedActivityName as Any?),
            "userId" to (launchAppHolder.selectedUserId as Any?)
        ).filterValues { it != null }
    }

    private fun updateSummaryText(context: Context, textView: TextView, parameters: Map<String, Any?>) {
        val packageName = parameters["packageName"] as? String
        val activityName = parameters["activityName"] as? String
        val userId = (parameters["userId"] as? Number)?.toInt()

        if (packageName.isNullOrEmpty()) {
            textView.text = context.getString(R.string.text_not_selected)
            return
        }

        val appName = AppUserSupport.loadAppLabel(context, packageName, userId) ?: packageName
        val appNameWithUser = if (userId != null && userId != AppUserSupport.getCurrentUserId()) {
            "$appName (${AppUserSupport.getUserLabel(context, userId)})"
        } else {
            appName
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            context.getString(R.string.text_app_selected, appNameWithUser)
        } else {
            val simpleActivityName = activityName.substringAfterLast('.')
            context.getString(R.string.text_activity_selected, appNameWithUser, simpleActivityName)
        }
        textView.text = displayText
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
