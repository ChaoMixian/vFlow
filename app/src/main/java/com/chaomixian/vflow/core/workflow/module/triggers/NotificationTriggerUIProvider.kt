// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/NotificationTriggerUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.textfield.TextInputEditText

class NotificationTriggerUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val appFilter: TextInputEditText = view.findViewById(R.id.et_app_filter)
        val titleFilter: TextInputEditText = view.findViewById(R.id.et_title_filter)
        val contentFilter: TextInputEditText = view.findViewById(R.id.et_content_filter)
    }

    override fun getHandledInputIds(): Set<String> = setOf("app_filter", "title_filter", "content_filter")

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

        // 恢复参数
        holder.appFilter.setText(currentParameters["app_filter"] as? String ?: "")
        holder.titleFilter.setText(currentParameters["title_filter"] as? String ?: "")
        holder.contentFilter.setText(currentParameters["content_filter"] as? String ?: "")

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        return mapOf(
            "app_filter" to h.appFilter.text.toString(),
            "title_filter" to h.titleFilter.text.toString(),
            "content_filter" to h.contentFilter.text.toString()
        )
    }

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}