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
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep

class GetBatteryStatusModule : BaseModule() {
    companion object {
        private const val OUTPUT_BATTERY_LEVEL = "battery_level"
        private const val OUTPUT_IS_CHARGING = "is_charging"
        private const val OUTPUT_TEMPERATURE = "temperature_celsius"
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
        workflowStepDescription = "Read battery information such as battery level, charging status, and temperature.",
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs() = emptyList<InputDefinition>()

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = OUTPUT_BATTERY_LEVEL,
            name = "电池电量",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_battery_level_name
        ),
        OutputDefinition(
            id = OUTPUT_IS_CHARGING,
            name = "是否正在充电",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_battery_is_charging_name
        ),
        OutputDefinition(
            id = OUTPUT_TEMPERATURE,
            name = "电池温度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_battery_temperature_name
        )
    )

    /**
     * 执行模块的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val batteryIntent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (batteryIntent == null) {
            return ExecutionResult.Failure("获取失败", "无法获取电池信息")
        }
        val batteryLevel = readBatteryLevelPercent(batteryIntent)
            ?: return ExecutionResult.Failure("获取失败", "无法获取有效的电池电量")
        val charging = readChargingState(batteryIntent)
        val temperatureCelsius = readTemperatureCelsius(batteryIntent)
            ?: return ExecutionResult.Failure("获取失败", "无法获取有效的电池温度")
        return ExecutionResult.Success(
            mapOf(
                OUTPUT_BATTERY_LEVEL to VNumber(batteryLevel),
                OUTPUT_IS_CHARGING to VBoolean(charging),
                OUTPUT_TEMPERATURE to VNumber(temperatureCelsius)
            )
        )
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
        return context.getString(R.string.summary_vflow_system_get_battery_status_prefix)
    }

    internal fun readBatteryLevelPercent(batteryIntent: Intent): Int? {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return readBatteryLevelPercent(level, scale)
    }

    internal fun readBatteryLevelPercent(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }

    internal fun readChargingState(batteryIntent: Intent): Boolean {
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return readChargingState(status)
    }

    internal fun readChargingState(status: Int): Boolean {
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    internal fun readTemperatureCelsius(batteryIntent: Intent): Float? {
        val temperatureTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return readTemperatureCelsius(temperatureTenths)
    }

    internal fun readTemperatureCelsius(temperatureTenths: Int): Float? {
        if (temperatureTenths == Int.MIN_VALUE || temperatureTenths < 0) return null
        return temperatureTenths / 10.0f
    }
}
