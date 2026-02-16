package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus

/**
 * 获取当前活动模块
 * 获取当前前台 Activity 的信息，包括 Activity 名称、包名、应用名称等
 * 需要无障碍服务权限
 */
class GetCurrentActivityModule : BaseModule() {

    override val id = "vflow.interaction.get_current_activity"

    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取当前活动",
        nameStringRes = R.string.module_vflow_interaction_get_current_activity_name,
        description = "获取当前前台 Activity 的信息",
        descriptionStringRes = R.string.module_vflow_interaction_get_current_activity_desc,
        iconRes = R.drawable.rounded_preview_24,
        category = "界面交互"
    )

    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    /**
     * 无输入参数
     */
    override fun getInputs(): List<InputDefinition> = emptyList()

    /**
     * 定义输出参数
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "activity_info",
            name = "活动信息",
            nameStringRes = R.string.output_vflow_interaction_get_current_activity_activity_info_name,
            typeName = VTypeRegistry.DICTIONARY.id
        ),
        OutputDefinition(
            id = "package_name",
            name = "包名",
            nameStringRes = R.string.output_vflow_interaction_get_current_activity_package_name_name,
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "class_name",
            name = "类名",
            nameStringRes = R.string.output_vflow_interaction_get_current_activity_class_name_name,
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "app_name",
            name = "应用名称",
            nameStringRes = R.string.output_vflow_interaction_get_current_activity_app_name_name,
            typeName = VTypeRegistry.STRING.id
        ),
        OutputDefinition(
            id = "is_foreground",
            name = "是否前台",
            nameStringRes = R.string.output_vflow_interaction_get_current_activity_is_foreground_name,
            typeName = VTypeRegistry.BOOLEAN.id
        )
    )

    /**
     * 执行模块的核心逻辑
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 检查无障碍服务是否正在运行
        if (!ServiceStateBus.isAccessibilityServiceRunning()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_get_current_activity_service_not_running),
                "获取当前活动需要无障碍服务，请确保无障碍服务已开启。"
            )
        }

        val packageName = ServiceStateBus.lastWindowPackageName
        val className = ServiceStateBus.lastWindowClassName

        if (packageName.isNullOrBlank() || className.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_get_current_activity_not_available),
                "无法获取当前活动信息。请确保无障碍服务已正确连接，并尝试切换到其他应用后再试。"
            )
        }

        // 获取应用信息
        val appInfo = try {
            appContext.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val appName = appInfo?.let {
            appContext.packageManager.getApplicationLabel(it).toString()
        } ?: ""

        // 获取 Activity 简短类名
        val shortClassName = className.substringAfterLast('.')

        // 构建活动信息字典
        val activityInfo = VDictionary(
            mapOf(
                "package_name" to VString(packageName),
                "class_name" to VString(className),
                "short_class_name" to VString(shortClassName),
                "app_name" to VString(appName),
                "is_foreground" to VBoolean(true)
            )
        )

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_interaction_get_current_activity_current, shortClassName, appName)))

        return ExecutionResult.Success(
            mapOf(
                "activity_info" to activityInfo,
                "package_name" to VString(packageName),
                "class_name" to VString(className),
                "app_name" to VString(appName),
                "is_foreground" to VBoolean(true)
            )
        )
    }

    /**
     * 验证模块参数的有效性
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    /**
     * 创建此模块对应的默认动作步骤列表
     */
    override fun createSteps(): List<ActionStep> = listOf(
        ActionStep(moduleId = this.id, parameters = emptyMap())
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_interaction_get_current_activity)
    }
}
