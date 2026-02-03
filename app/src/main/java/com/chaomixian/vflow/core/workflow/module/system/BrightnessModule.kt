// 文件: BrightnessModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.provider.Settings
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BrightnessModule : BaseModule() {

    override val id = "vflow.system.brightness"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_brightness_name,
        descriptionStringRes = R.string.module_vflow_system_brightness_desc,
        name = "屏幕亮度设置",  // Fallback
        description = "设置屏幕的亮度值",  // Fallback
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.WRITE_SETTINGS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "brightness_level",
            name = "亮度值 (0-255)",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 128.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val levelPill = PillUtil.createPillFromParam(
            step.parameters["brightness_level"],
            getInputs().find { it.id == "brightness_level" }
        )

        val prefix = context.getString(R.string.summary_vflow_system_brightness_prefix)

        return PillUtil.buildSpannable(context, "$prefix ", levelPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val level = context.getVariableAsInt("brightness_level")

        if (level == null) {
            val rawValue = context.getVariable("brightness_level")
            val rawValueStr = when (rawValue) {
                is VString -> rawValue.raw
                is VNull -> "空值"
                is VNumber -> rawValue.raw.toString()
                else -> rawValue?.toString() ?: "未知"
            }
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_brightness_invalid_level),
                "无法将 '$rawValueStr' 解析为有效的亮度值 (0-255)。"
            )
        }

        if (level !in 0..255) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_brightness_invalid_level),
                appContext.getString(R.string.error_vflow_system_brightness_invalid_level_detail)
            )
        }

        onProgress(ProgressUpdate("正在设置屏幕亮度为 $level..."))

        return try {
            val resolver = context.applicationContext.contentResolver
            // 在设置亮度值之前，先将亮度模式设为手动
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            // 设置亮度值
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_brightness_set_failed),
                e.localizedMessage ?: "发生未知错误"
            )
        }
    }
}