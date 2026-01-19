// 文件: BrightnessModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.provider.Settings
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class BrightnessModule : BaseModule() {

    override val id = "vflow.system.brightness"
    override val metadata = ActionMetadata(
        name = "屏幕亮度设置",
        description = "设置屏幕的亮度值。",
        iconRes = R.drawable.rounded_brightness_5_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.WRITE_SETTINGS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "brightness_level",
            name = "亮度值 (0-255)",
            staticType = ParameterType.NUMBER,
            defaultValue = 128.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val levelPill = PillUtil.createPillFromParam(
            step.parameters["brightness_level"],
            getInputs().find { it.id == "brightness_level" }
        )
        return PillUtil.buildSpannable(context, "设置屏幕亮度为 ", levelPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val levelValue = context.magicVariables["brightness_level"] ?: context.variables["brightness_level"]

        val level = when (levelValue) {
            is VNumber -> levelValue.raw.toInt()
            is Number -> levelValue.toInt()
            is String -> levelValue.toIntOrNull()
            else -> null
        }

        if (level == null || level !in 0..255) {
            return ExecutionResult.Failure("参数错误", "亮度值必须是 0 到 255 之间的数字。")
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
            ExecutionResult.Failure("设置失败", e.localizedMessage ?: "发生未知错误")
        }
    }
}