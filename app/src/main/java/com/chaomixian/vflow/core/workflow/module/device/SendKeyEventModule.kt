// 文件: SendKeyEventModule.kt
// 描述: 定义了通过无障碍服务发送模拟按键事件的模块。

package com.chaomixian.vflow.core.workflow.module.device

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “输入按键”模块。
 * 模拟系统级的全局操作事件，如返回、主屏幕等。
 */
class SendKeyEventModule : BaseModule() {

    override val id = "vflow.device.send_key_event"
    override val metadata = ActionMetadata(
        name = "执行全局操作", // 名称更新以反映真实功能
        description = "执行系统级操作，如返回、回到主屏幕、打开通知等。",
        iconRes = R.drawable.rounded_keyboard_24,
        category = "设备"
    )

    // 需要无障碍服务权限来发送全局操作
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 定义支持的全局操作及其对应的系统常量
    // 修正：只保留 performGlobalAction 支持的常量
    private val keyOptions = mapOf(
        "返回" to AccessibilityService.GLOBAL_ACTION_BACK,
        "主屏幕" to AccessibilityService.GLOBAL_ACTION_HOME,
        "最近任务" to AccessibilityService.GLOBAL_ACTION_RECENTS,
        "通知中心" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        "快速设置" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        "电源菜单" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
        // 移除了不支持的按键，如回车、删除、音量控制等
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "key_action",
            name = "全局操作", // 名称更新
            staticType = ParameterType.ENUM,
            defaultValue = "返回", // 默认值更改为常用且支持的操作
            options = keyOptions.keys.toList(),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val key = step.parameters["key_action"]?.toString() ?: "返回"
        return PillUtil.buildSpannable(context,
            "执行全局操作 ",
            PillUtil.Pill(key, false, "key_action", isModuleOption = true)
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未连接", "执行此操作需要无障碍服务。")

        val keyName = context.variables["key_action"] as? String ?: "返回"
        val action = keyOptions[keyName]
            ?: return ExecutionResult.Failure("参数错误", "无效的操作名称: $keyName")

        onProgress(ProgressUpdate("正在执行操作: $keyName"))

        val success = service.performGlobalAction(action)

        if (!success) {
            onProgress(ProgressUpdate("执行操作失败: $keyName"))
            return ExecutionResult.Failure("操作失败", "系统未能成功执行该全局操作。")
        }

        return ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
    }
}