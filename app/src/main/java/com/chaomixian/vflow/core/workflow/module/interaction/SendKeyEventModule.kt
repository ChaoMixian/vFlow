// 文件: SendKeyEventModule.kt
// 描述: 定义了通过无障碍服务发送模拟按键事件的模块。

package com.chaomixian.vflow.core.workflow.module.interaction

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “输入按键”模块。
 * 模拟物理或虚拟按键的按下事件，如回车、返回、删除等。
 */
class SendKeyEventModule : BaseModule() {
    companion object {
        private const val ACTION_BACK = "back"
        private const val ACTION_HOME = "home"
        private const val ACTION_RECENTS = "recents"
        private const val ACTION_NOTIFICATIONS = "notifications"
        private const val ACTION_QUICK_SETTINGS = "quick_settings"
        private const val ACTION_POWER_DIALOG = "power_dialog"
    }

    override val id = "vflow.device.send_key_event"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_device_send_key_event_name,
        descriptionStringRes = R.string.module_vflow_device_send_key_event_desc,
        name = "执行全局操作",
        description = "执行系统级操作，如返回、回到主屏幕、打开通知等。",
        iconRes = R.drawable.rounded_keyboard_24,
        category = "界面交互",
        categoryId = "interaction"
    )

    // 需要无障碍服务权限来发送全局按键事件
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 定义支持的按键及其对应的系统常量
    private val keyOptions = mapOf(
        ACTION_BACK to AccessibilityService.GLOBAL_ACTION_BACK,
        ACTION_HOME to AccessibilityService.GLOBAL_ACTION_HOME,
        ACTION_RECENTS to AccessibilityService.GLOBAL_ACTION_RECENTS,
        ACTION_NOTIFICATIONS to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        ACTION_QUICK_SETTINGS to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        ACTION_POWER_DIALOG to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "key_action",
            name = "全局操作", // 名称更新
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_BACK,
            options = keyOptions.keys.toList(),
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_device_send_key_event_action_name,
            optionsStringRes = listOf(
                R.string.option_vflow_device_send_key_event_back,
                R.string.option_vflow_device_send_key_event_home,
                R.string.option_vflow_device_send_key_event_recents,
                R.string.option_vflow_device_send_key_event_notifications,
                R.string.option_vflow_device_send_key_event_quick_settings,
                R.string.option_vflow_device_send_key_event_power_dialog
            ),
            legacyValueMap = mapOf(
                "返回" to ACTION_BACK,
                "Back" to ACTION_BACK,
                "主屏幕" to ACTION_HOME,
                "Home" to ACTION_HOME,
                "最近任务" to ACTION_RECENTS,
                "Recents" to ACTION_RECENTS,
                "通知中心" to ACTION_NOTIFICATIONS,
                "Notifications" to ACTION_NOTIFICATIONS,
                "快速设置" to ACTION_QUICK_SETTINGS,
                "Quick Settings" to ACTION_QUICK_SETTINGS,
                "电源菜单" to ACTION_POWER_DIALOG,
                "Power Dialog" to ACTION_POWER_DIALOG
            )
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_device_send_key_event_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val keyPill = PillUtil.createPillFromParam(
            step.parameters["key_action"],
            getInputs().find { it.id == "key_action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context,
            context.getString(R.string.summary_vflow_device_send_key_event_prefix),
            keyPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未连接", "执行此操作需要无障碍服务。")

        val keyName = context.getVariableAsString("key_action", ACTION_BACK)
        val action = keyOptions[keyName]
            ?: return ExecutionResult.Failure("参数错误", "无效的操作名称: $keyName")

        onProgress(ProgressUpdate("正在执行操作: $keyName"))

        val success = service.performGlobalAction(action)

        if (!success) {
            onProgress(ProgressUpdate("执行操作失败: $keyName"))
            return ExecutionResult.Failure("操作失败", "系统未能成功执行该全局操作。")
        }

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
