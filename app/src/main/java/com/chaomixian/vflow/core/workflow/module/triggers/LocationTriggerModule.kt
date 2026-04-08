// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerModule.kt
// 描述: 位置触发器，支持进入/离开指定区域时触发工作流
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.permissions.PermissionType
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class LocationTriggerModule : BaseModule() {
    companion object {
        const val EVENT_ENTER = "enter"
        const val EVENT_EXIT = "exit"

        fun normalizeEvent(value: String?): String? {
            return when (value) {
                EVENT_ENTER, "进入时", "Enter" -> EVENT_ENTER
                EVENT_EXIT, "离开时", "Exit" -> EVENT_EXIT
                else -> null
            }
        }
    }
    override val id = "vflow.trigger.location"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_location_name,
        descriptionStringRes = R.string.module_vflow_trigger_location_desc,
        name = "位置触发器",
        description = "当进入或离开指定区域时触发工作流",
        iconRes = R.drawable.rounded_alt_route_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override val requiredPermissions = listOf(
        PermissionManager.LOCATION,
        Permission(
            id = android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            name = "后台位置访问",
            description = "需要在后台监听位置变化时使用此权限。",
            type = PermissionType.RUNTIME,
            nameStringRes = R.string.permission_name_background_location,
            descriptionStringRes = R.string.permission_desc_background_location
        )
    )
    override val uiProvider: ModuleUIProvider = LocationTriggerUIProvider()

    private val triggerEventOptions by lazy { listOf(EVENT_ENTER, EVENT_EXIT) }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "event",
            name = "事件",
            staticType = ParameterType.ENUM,
            defaultValue = EVENT_ENTER,
            options = triggerEventOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_trigger_location_event_enter,
                R.string.option_vflow_trigger_location_event_exit
            ),
            legacyValueMap = mapOf(
                appContext.getString(R.string.option_vflow_trigger_location_event_enter) to EVENT_ENTER,
                appContext.getString(R.string.option_vflow_trigger_location_event_exit) to EVENT_EXIT
            ),
            nameStringRes = R.string.param_vflow_trigger_location_event_name
        ),
        InputDefinition(
            id = "latitude",
            name = "纬度",
            staticType = ParameterType.NUMBER,
            defaultValue = 39.9042,
            nameStringRes = R.string.param_vflow_trigger_location_latitude_name
        ),
        InputDefinition(
            id = "longitude",
            name = "经度",
            staticType = ParameterType.NUMBER,
            defaultValue = 116.4074,
            nameStringRes = R.string.param_vflow_trigger_location_longitude_name
        ),
        InputDefinition(
            id = "radius",
            name = "半径",
            staticType = ParameterType.NUMBER,
            defaultValue = 500.0,
            nameStringRes = R.string.param_vflow_trigger_location_radius_name
        ),
        InputDefinition(
            id = "location_name",
            name = "位置名称",
            staticType = ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_trigger_location_location_name_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "latitude",
            name = "当前纬度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_location_latitude_name
        ),
        OutputDefinition(
            id = "longitude",
            name = "当前经度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_location_longitude_name
        ),
        OutputDefinition(
            id = "accuracy",
            name = "精度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_location_accuracy_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val event = step.parameters["event"] as? String ?: EVENT_ENTER
        val latitude = step.parameters["latitude"] as? Double ?: 0.0
        val longitude = step.parameters["longitude"] as? Double ?: 0.0
        val radius = step.parameters["radius"] as? Double ?: 500.0
        val locationName = step.parameters["location_name"] as? String ?: ""

        val eventPill = PillUtil.createPillFromParam(event, getInputs().find { it.id == "event" }, isModuleOption = true)

        // 如果有位置名称，显示位置名称，否则显示坐标
        val locationText = if (locationName.isNotEmpty()) {
            locationName
        } else {
            context.getString(
                R.string.summary_vflow_trigger_location_coordinates,
                latitude, longitude, radius.toInt()
            )
        }
        val locationPill = PillUtil.Pill(locationText, "location")

        val prefix = context.getString(R.string.summary_vflow_trigger_location_prefix)
        return PillUtil.buildSpannable(context, prefix, " ", eventPill, " ", locationPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_location_triggered)))

        // 从 triggerData 获取位置信息
        val triggerData = context.triggerData as? LocationTriggerData
            ?: LocationTriggerData(0.0, 0.0, 0f)

        return ExecutionResult.Success(outputs = mapOf(
            "latitude" to VNumber(triggerData.latitude),
            "longitude" to VNumber(triggerData.longitude),
            "accuracy" to VNumber(triggerData.accuracy.toDouble())
        ))
    }
}
