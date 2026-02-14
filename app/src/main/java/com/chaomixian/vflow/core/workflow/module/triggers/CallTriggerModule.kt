package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class CallTriggerModule : BaseModule() {
    override val id = "vflow.trigger.call"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_call_name,
        descriptionStringRes = R.string.module_vflow_trigger_call_desc,
        name = "电话触发",
        description = "当有来电、通话接通或挂断时触发工作流",
        iconRes = R.drawable.rounded_call_24,
        category = "触发器"
    )

    override val requiredPermissions = listOf(PermissionManager.READ_PHONE_STATE)

    private val callTypeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_trigger_call_type_any),
            appContext.getString(R.string.option_vflow_trigger_call_type_incoming),
            appContext.getString(R.string.option_vflow_trigger_call_type_answered),
            appContext.getString(R.string.option_vflow_trigger_call_type_ended)
        )
    }

    private val anyCallTypeOption get() = callTypeOptions[0]

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("call_type", "触发类型", ParameterType.ENUM,
            defaultValue = anyCallTypeOption,
            options = callTypeOptions,
            nameStringRes = R.string.param_vflow_trigger_call_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("call_state", "通话状态", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_call_state_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val callType = step.parameters["call_type"] as? String ?: anyCallTypeOption

        val callTypePill = PillUtil.Pill(callType, "call_type", isModuleOption = true)

        val prefix = context.getString(R.string.summary_vflow_trigger_call_prefix)
        val suffix = context.getString(R.string.summary_vflow_trigger_call_suffix)

        return PillUtil.buildSpannable(context, "$prefix", callTypePill, "$suffix")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_call_received)))
        val triggerData = context.triggerData as? com.chaomixian.vflow.core.types.basic.VDictionary
        val callState = triggerData?.raw?.get("call_state") as? VString ?: VString("")

        return ExecutionResult.Success(
            outputs = mapOf(
                "call_state" to callState
            )
        )
    }
}
