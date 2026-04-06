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
    companion object {
        const val TYPE_ANY = "any"
        const val TYPE_INCOMING = "incoming"
        const val TYPE_ANSWERED = "answered"
        const val TYPE_ENDED = "ended"
    }
    override val id = "vflow.trigger.call"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_call_name,
        descriptionStringRes = R.string.module_vflow_trigger_call_desc,
        name = "电话触发",
        description = "当有来电、通话接通或挂断时触发工作流",
        iconRes = R.drawable.rounded_call_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions = listOf(PermissionManager.READ_PHONE_STATE)

    private val callTypeOptions by lazy { listOf(TYPE_ANY, TYPE_INCOMING, TYPE_ANSWERED, TYPE_ENDED) }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("call_type", "触发类型", ParameterType.ENUM,
            defaultValue = TYPE_ANY,
            options = callTypeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_call_type_any,
                R.string.option_vflow_trigger_call_type_incoming,
                R.string.option_vflow_trigger_call_type_answered,
                R.string.option_vflow_trigger_call_type_ended
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_call_type_any) to TYPE_ANY,
                appContext.getString(R.string.option_vflow_trigger_call_type_incoming) to TYPE_INCOMING,
                appContext.getString(R.string.option_vflow_trigger_call_type_answered) to TYPE_ANSWERED,
                appContext.getString(R.string.option_vflow_trigger_call_type_ended) to TYPE_ENDED
            ),
            nameStringRes = R.string.param_vflow_trigger_call_type_name,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("call_state", "通话状态", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_trigger_call_state_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val callType = step.parameters["call_type"] as? String ?: TYPE_ANY
        val callTypePill = PillUtil.createPillFromParam(callType, getInputs().find { it.id == "call_type" }, isModuleOption = true)

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
