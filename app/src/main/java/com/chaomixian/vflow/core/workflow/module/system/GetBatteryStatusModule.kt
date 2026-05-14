package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.AiModuleMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.AiModuleUsageScope
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class GetBatteryStatusModule : BaseModule() {
    companion object {
        private const val BATTERY_LEVEL = "level"
        private const val IS_CHARGING = "is_charging"
        private const val TEMPERATURE = "temperature"
    }

    // 模块的唯一标识符
    override val id = "vflow.system.get_battery_status"

    // 模块的元数据，定义其在编辑器中的显示名称、描述、图标和分类
    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取电池状态",  // Fallback
        nameStringRes = R.string.module_vflow_system_get_battery_status_name,
        description = "获取电池和充电器接入状态。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_get_battery_status_desc,
        iconRes = R.drawable.rounded_battery_android_frame_full_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Read information about the battery and any charger connected to the device such as battery level, charging status, or temperature.",
        requiredInputIds = setOf("statusType"),
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "statusType",
            name = "状态类型",
            nameStringRes = R.string.param_vflow_system_systeminfo_type_name,
            staticType = ParameterType.ENUM,
            options = listOf(
                BATTERY_LEVEL,
                IS_CHARGING,
                TEMPERATURE
            ),
            optionsStringRes = listOf(
                R.string.option_vflow_system_get_battery_status_level,
                R.string.option_vflow_system_get_battery_status_is_charging,
                R.string.option_vflow_system_get_battery_status_temperature
            ),
            defaultValue = BATTERY_LEVEL,
            acceptsMagicVariable = false
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "battery_status",
            name = "电池状态",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_battery_status_value_name
        )
    )

    /**
     * 执行模块的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val statusTypeInput = getInputs().first { it.id == "statusType" }
        val rawstatusType = context.getVariableAsString("statusType", BATTERY_LEVEL)
        val statusType = statusTypeInput.normalizeEnumValue(rawstatusType) ?: rawstatusType
        val batteryIntent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (batteryIntent == null) {
            return ExecutionResult.Failure("获取失败", "无法获取电池信息")
        }
        val resultValue : Number = when (statusType) {
            "level" -> {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale
            }
            "is_charging" -> {
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
            }
            "temperature" -> {
                val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                temperature / 10.0f
            }
            else -> return ExecutionResult.Failure("获取失败", "无效的输入")
        }
        return ExecutionResult.Success(mapOf("battery_status" to VNumber(resultValue)))
    }

    /**
     * 验证模块参数的有效性。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    /**
     * 创建此模块对应的默认动作步骤列表。
     */
    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = this.id, parameters = emptyMap()))

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()

        val pillstatusType = PillUtil.createPillFromParam(
            step.parameters["statusType"],
            inputs.find { it.id == "statusType" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_get_battery_status_prefix), pillstatusType)
    }
}