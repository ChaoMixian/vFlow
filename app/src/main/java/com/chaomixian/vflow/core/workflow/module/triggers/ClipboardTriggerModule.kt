package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ClipboardTriggerModule : BaseModule() {
    companion object {
        const val MODE_STANDARD = "standard"
        const val MODE_CORE = "core"

        private val MODE_OPTIONS = listOf(MODE_STANDARD, MODE_CORE)
        private val MODE_INPUT = InputDefinition(
            id = "mode",
            name = "监听模式",
            nameStringRes = R.string.param_vflow_trigger_clipboard_mode_name,
            staticType = ParameterType.ENUM,
            defaultValue = MODE_STANDARD,
            options = MODE_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_clipboard_mode_standard,
                R.string.option_vflow_trigger_clipboard_mode_core
            ),
            inputStyle = InputStyle.CHIP_GROUP,
            acceptsMagicVariable = false
        )
    }

    override val id = "vflow.trigger.clipboard"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_clipboard_name,
        descriptionStringRes = R.string.module_vflow_trigger_clipboard_desc,
        name = "剪贴板变更",
        description = "当剪贴板内容发生变化时触发工作流",
        iconRes = R.drawable.rounded_content_paste_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = MODE_INPUT.normalizeEnumValueOrNull(step?.parameters?.get("mode") as? String) ?: MODE_STANDARD
        return if (mode == MODE_CORE) listOf(PermissionManager.CORE) else emptyList()
    }

    override fun getInputs(): List<InputDefinition> = listOf(MODE_INPUT)

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "text_content",
            name = "剪贴板文本",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_trigger_clipboard_text_content_name
        ),
        OutputDefinition(
            id = "image_content",
            name = "剪贴板图片",
            typeName = VTypeRegistry.IMAGE.id,
            nameStringRes = R.string.output_vflow_trigger_clipboard_image_content_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = MODE_INPUT.normalizeEnumValueOrNull(step.parameters["mode"] as? String) ?: MODE_STANDARD
        val modePill = PillUtil.createPillFromParam(mode, MODE_INPUT, isModuleOption = true)
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_clipboard),
            " ",
            modePill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_clipboard_triggered)))

        val triggerData = context.triggerData as? VDictionary
        val outputs = mutableMapOf<String, Any>(
            "text_content" to ((triggerData?.raw?.get("text_content") as? VString) ?: VString(""))
        )

        val imageContent = triggerData?.raw?.get("image_content") as? VImage
        if (imageContent != null) {
            outputs["image_content"] = imageContent
        }

        return ExecutionResult.Success(outputs)
    }
}
