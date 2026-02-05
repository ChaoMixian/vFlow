// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/LaunchAppUIProvider.kt
// 描述: 为 LaunchAppModule 提供自定义UI交互逻辑。
//      使用 AppPickerActivity 选择单个应用及其 Activity。
package com.chaomixian.vflow.core.workflow.module.system

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

class LaunchAppViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button
) : CustomEditorViewHolder(view)

class LaunchAppUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("packageName", "activityName")

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
        updateSummaryText(context, holder.summaryTextView, currentParameters)

        // 点击按钮启动应用选择器
        holder.pickButton.setOnClickListener {
            val intent = Intent(context, AppPickerActivity::class.java)
            onStartActivityForResult?.invoke(intent) { _, _ ->
                // 结果由 WorkflowEditorActivity.handleAppPickerResult 处理
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        // 参数通过 ActivityResult 更新，此方法返回空 Map
        return emptyMap()
    }

    private fun updateSummaryText(context: Context, textView: TextView, parameters: Map<String, Any?>) {
        val packageName = parameters["packageName"] as? String
        val activityName = parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            textView.text = context.getString(R.string.text_not_selected)
            return
        }

        val pm = context.packageManager
        val appName = try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            context.getString(R.string.text_app_selected, appName)
        } else {
            val simpleActivityName = activityName.substringAfterLast('.')
            context.getString(R.string.text_activity_selected, appName, simpleActivityName)
        }
        textView.text = displayText
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
