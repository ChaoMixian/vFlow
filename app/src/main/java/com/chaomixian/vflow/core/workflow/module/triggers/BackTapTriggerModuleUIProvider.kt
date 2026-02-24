// main/java/com/chaomixian/vflow/core/workflow/module/triggers/BackTapTriggerModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity

class BackTapTriggerModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val openConfigButton: Button = view.findViewById(R.id.button_open_module_config)
    }

    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_backtap_trigger_editor, parent, false)
        val holder = ViewHolder(view)

        holder.openConfigButton.setOnClickListener {
            val intent = Intent(context, ModuleConfigActivity::class.java)
            context.startActivity(intent)
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        return emptyMap()
    }

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null
}
