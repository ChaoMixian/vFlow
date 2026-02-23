// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/CallPhoneModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionType
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallPhoneModule : BaseModule() {

    override val id = "vflow.device.call_phone"

    // 模块的元数据
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_device_call_phone_name,
        descriptionStringRes = R.string.module_vflow_device_call_phone_desc,
        name = "拨打电话",
        description = "直接拨打指定的电话号码",
        iconRes = R.drawable.rounded_call_to_action_24,
        category = "应用与系统"
    )

    // 定义通话权限
    private val CALL_PHONE_PERMISSION = Permission(
        id = Manifest.permission.CALL_PHONE,
        name = "拨打电话",
        description = "允许应用直接拨打电话号码。",
        type = PermissionType.RUNTIME,
        runtimePermissions = listOf(Manifest.permission.CALL_PHONE),
        nameStringRes = R.string.permission_name_call_phone,
        descriptionStringRes = R.string.permission_desc_call_phone
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(CALL_PHONE_PERMISSION)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "phone_number",
            name = "电话号码",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_device_call_phone_number_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_call_phone_success_name
        ),
        OutputDefinition(
            id = "phone_number",
            name = "拨打的电话号码",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_device_call_phone_number_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["phone_number"]?.toString() ?: ""

        if (VariableResolver.isComplex(rawText)) {
            return metadata.getLocalizedName(context)
        } else {
            val phonePill = PillUtil.createPillFromParam(
                step.parameters["phone_number"],
                getInputs().find { it.id == "phone_number" }
            )
            return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", phonePill)
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val phoneNumber = step.parameters["phone_number"]?.toString()
        if (phoneNumber.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_device_call_phone_empty)
            )
        }
        return ValidationResult(isValid = true)
    }

    /**
     * 执行拨打电话的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val phoneNumberObj = context.getVariable("phone_number")
        val phoneNumber = phoneNumberObj.asString()

        if (phoneNumber.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_call_phone_empty),
                appContext.getString(R.string.error_vflow_device_call_phone_missing)
            )
        }

        val resolvedNumber = VariableResolver.resolve(phoneNumber, context)

        onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_device_call_phone_preparing), resolvedNumber)))

        return try {
            val success = withContext(Dispatchers.Main) {
                makePhoneCall(context.applicationContext, resolvedNumber)
            }

            if (success) {
                ExecutionResult.Success(outputs = mapOf(
                    "success" to VBoolean(true),
                    "phone_number" to VString(resolvedNumber)
                ))
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_device_call_phone_failed),
                    appContext.getString(R.string.error_vflow_device_call_phone_failed_desc)
                )
            }
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_call_phone_exception),
                e.message ?: appContext.getString(R.string.error_vflow_device_call_phone_exception_desc)
            )
        }
    }

    private fun makePhoneCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            // 如果没有权限，尝试使用ACTION_DIAL（只打开拨号界面，不直接拨打）
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                true
            } catch (e2: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
