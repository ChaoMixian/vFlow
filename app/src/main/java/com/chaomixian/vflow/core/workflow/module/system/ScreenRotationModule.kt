package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.provider.Settings
import android.view.Surface
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.core.workflow.model.ActionStep

class ScreenRotationModule : BaseModule() {

    companion object {
        const val INPUT_AUTO_ROTATE = "auto_rotate"
        const val INPUT_DIRECTION = "direction"

        const val DIRECTION_PORTRAIT = "portrait"
        const val DIRECTION_LANDSCAPE_LEFT = "landscape_left"
        const val DIRECTION_PORTRAIT_UPSIDE_DOWN = "portrait_upside_down"
        const val DIRECTION_LANDSCAPE_RIGHT = "landscape_right"

        private val DIRECTION_OPTIONS = listOf(
            DIRECTION_PORTRAIT,
            DIRECTION_LANDSCAPE_LEFT,
            DIRECTION_PORTRAIT_UPSIDE_DOWN,
            DIRECTION_LANDSCAPE_RIGHT
        )
    }

    override val id = "vflow.system.screen_rotation"
    override val metadata = ActionMetadata(
        name = "屏幕旋转",
        nameStringRes = R.string.module_vflow_system_screen_rotation_name,
        description = "控制自动旋转或锁定屏幕方向。",
        descriptionStringRes = R.string.module_vflow_system_screen_rotation_desc,
        iconRes = R.drawable.rounded_rotate_90_degrees_cw_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Control Android screen rotation. Enable auto-rotate or lock orientation to portrait, landscape left, upside-down portrait, or landscape right.",
        workflowStepDescription = "Change screen rotation settings.",
        inputHints = mapOf(
            INPUT_AUTO_ROTATE to "Enable this to turn on system auto-rotate. Leave it off to lock a specific direction.",
            INPUT_DIRECTION to "Use canonical values portrait, landscape_left, portrait_upside_down, or landscape_right when auto-rotate is off."
        ),
        requiredInputIds = setOf(INPUT_AUTO_ROTATE)
    )

    override val requiredPermissions = listOf(PermissionManager.WRITE_SETTINGS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = INPUT_AUTO_ROTATE,
            name = "自动旋转",
            staticType = com.chaomixian.vflow.core.module.ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            inputStyle = InputStyle.SWITCH,
            nameStringRes = R.string.param_vflow_system_screen_rotation_auto_rotate_name
        ),
        InputDefinition(
            id = INPUT_DIRECTION,
            name = "屏幕方向",
            staticType = com.chaomixian.vflow.core.module.ParameterType.ENUM,
            defaultValue = DIRECTION_PORTRAIT,
            options = DIRECTION_OPTIONS,
            optionsStringRes = listOf(
                R.string.option_vflow_system_screen_rotation_portrait,
                R.string.option_vflow_system_screen_rotation_landscape_left,
                R.string.option_vflow_system_screen_rotation_portrait_upside_down,
                R.string.option_vflow_system_screen_rotation_landscape_right
            ),
            acceptsMagicVariable = false,
            inputStyle = InputStyle.DROPDOWN,
            visibility = InputVisibility.whenFalse(INPUT_AUTO_ROTATE),
            nameStringRes = R.string.param_vflow_system_screen_rotation_direction_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_screen_rotation_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val autoRotate = step.parameters[INPUT_AUTO_ROTATE] as? Boolean ?: false
        if (autoRotate) {
            return context.getString(R.string.summary_vflow_system_screen_rotation_auto)
        }

        val direction = resolveDirection(step.parameters[INPUT_DIRECTION] as? String)
        val directionLabel = getDirectionLabel(context, direction)
        val directionPill = PillUtil.Pill(directionLabel, INPUT_DIRECTION, isModuleOption = true)
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_system_screen_rotation_prefix),
            directionPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val autoRotate = context.getVariableAsBoolean(INPUT_AUTO_ROTATE) ?: false
        val resolver = context.applicationContext.contentResolver

        return try {
            if (autoRotate) {
                onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_screen_rotation_enabling_auto)))
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            } else {
                val direction = resolveDirection(context.getVariableAsString(INPUT_DIRECTION, DIRECTION_PORTRAIT))
                val rotation = toSystemRotation(direction)
                    ?: return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_system_screen_rotation_invalid_direction),
                        appContext.getString(R.string.error_vflow_system_screen_rotation_invalid_direction_detail, direction)
                    )

                onProgress(
                    ProgressUpdate(
                        appContext.getString(
                            R.string.msg_vflow_system_screen_rotation_locking,
                            getDirectionLabel(appContext, direction)
                        )
                    )
                )
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, rotation)
            }

            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_screen_rotation_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_system_screen_rotation_failed_detail)
            )
        }
    }

    fun resolveDirection(rawDirection: String?): String {
        return rawDirection?.takeIf { it in DIRECTION_OPTIONS } ?: DIRECTION_PORTRAIT
    }

    fun toSystemRotation(direction: String): Int? {
        return when (direction) {
            DIRECTION_PORTRAIT -> Surface.ROTATION_0
            DIRECTION_LANDSCAPE_LEFT -> Surface.ROTATION_90
            DIRECTION_PORTRAIT_UPSIDE_DOWN -> Surface.ROTATION_180
            DIRECTION_LANDSCAPE_RIGHT -> Surface.ROTATION_270
            else -> null
        }
    }

    private fun getDirectionLabel(context: Context, direction: String): String {
        return when (direction) {
            DIRECTION_LANDSCAPE_LEFT -> context.getString(R.string.option_vflow_system_screen_rotation_landscape_left)
            DIRECTION_PORTRAIT_UPSIDE_DOWN -> context.getString(R.string.option_vflow_system_screen_rotation_portrait_upside_down)
            DIRECTION_LANDSCAPE_RIGHT -> context.getString(R.string.option_vflow_system_screen_rotation_landscape_right)
            else -> context.getString(R.string.option_vflow_system_screen_rotation_portrait)
        }
    }
}
