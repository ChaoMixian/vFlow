package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.google.android.material.button.MaterialButton

private class VoiceTriggerViewHolder(
    view: View,
    val templateStatus: TextView,
    val openConfigButton: MaterialButton,
) : CustomEditorViewHolder(view)

class VoiceTriggerUIProvider : ModuleUIProvider {
    override fun getHandledInputIds(): Set<String> = emptySet()

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?,
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_voice_trigger_editor, parent, false)
        val holder = VoiceTriggerViewHolder(
            view = view,
            templateStatus = view.findViewById(R.id.text_voice_template_status),
            openConfigButton = view.findViewById(R.id.button_record_voice_templates),
        )

        renderTemplateStatus(context, holder)

        holder.openConfigButton.setOnClickListener {
            context.startActivity(
                ModuleConfigActivity.createIntent(
                    context,
                    ModuleConfigActivity.SECTION_VOICE_TRIGGER
                )
            )
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> = emptyMap()

    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?,
    ): View? = null

    private fun renderTemplateStatus(context: Context, holder: VoiceTriggerViewHolder) {
        val count = VoiceTriggerConfig.recordedTemplateCount(context)
        holder.templateStatus.text = if (count == 3) {
            context.getString(R.string.voice_trigger_templates_ready)
        } else {
            context.getString(R.string.voice_trigger_templates_progress, count, 3)
        }
    }
}
