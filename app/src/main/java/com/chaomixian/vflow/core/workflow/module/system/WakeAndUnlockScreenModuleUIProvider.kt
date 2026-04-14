package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.textfield.TextInputEditText

class WakeAndUnlockScreenModuleUIProvider : ModuleUIProvider {

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val unlockPasswordEdit: TextInputEditText = view.findViewById(R.id.et_unlock_password)
    }

    override fun getHandledInputIds(): Set<String> = setOf(WakeAndUnlockScreenModule.INPUT_UNLOCK_PASSWORD)

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_wake_and_unlock_screen_editor, parent, false)
        val holder = ViewHolder(view)

        holder.unlockPasswordEdit.setText(
            currentParameters[WakeAndUnlockScreenModule.INPUT_UNLOCK_PASSWORD] as? String ?: ""
        )
        holder.unlockPasswordEdit.doAfterTextChanged {
            onParametersChanged()
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val viewHolder = holder as ViewHolder
        return mapOf(
            WakeAndUnlockScreenModule.INPUT_UNLOCK_PASSWORD to viewHolder.unlockPasswordEdit.text?.toString().orEmpty()
        )
    }
}
