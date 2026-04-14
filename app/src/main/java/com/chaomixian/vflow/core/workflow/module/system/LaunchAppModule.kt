// 文件: LaunchAppModule.kt
// 描述: 定义了启动指定应用或Activity的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.app_picker.AppUserSupport
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LaunchAppModule : BaseModule() {

    override val id = "vflow.system.launch_app"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_launch_app_name,
        descriptionStringRes = R.string.module_vflow_system_launch_app_desc,
        name = "启动应用/活动",  // Fallback
        description = "启动一个指定的应用程序或其内部的某个页面(Activity)",  // Fallback
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "应用与系统",
        categoryId = "device"
    )

    // 使用专门的 LaunchAppUIProvider
    override val uiProvider: ModuleUIProvider = LaunchAppUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "packageName",
            name = "应用包名",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_launch_app_packageName_name
        ),
        InputDefinition(
            id = "activityName",
            name = "Activity 名称",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "LAUNCH", // "LAUNCH" 是一个特殊值，代表仅启动应用
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_launch_app_activityName_name
        ),
        InputDefinition(
            id = "userId",
            name = "用户 ID",
            staticType = ParameterType.NUMBER,
            defaultValue = null,
            acceptsMagicVariable = false,
            isHidden = true
        ),
        InputDefinition(
            id = "useAdb",
            name = "使用 Shell 命令",  // Fallback
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            isFolded = true,
            nameStringRes = R.string.param_vflow_system_launch_app_useAdb_name
        )
    )

    // 动态声明权限：根据是否使用 Shell 命令来决定权限
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val useAdb = step?.parameters?.get("useAdb") as? Boolean ?: false
        return if (useAdb) {
            ShellManager.getRequiredPermissions(LogManager.applicationContext)
        } else {
            emptyList()
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_launch_app_success_name
        )
    )


    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val packageName = step.parameters["packageName"] as? String
        val activityName = step.parameters["activityName"] as? String
        val userId = (step.parameters["userId"] as? Number)?.toInt()

        if (packageName.isNullOrEmpty()) {
            return context.getString(R.string.summary_vflow_system_launch_app_select)
        }

        val appName = AppUserSupport.loadAppLabel(context, packageName, userId) ?: packageName
        val appNameWithUser = if (userId != null && userId != AppUserSupport.getCurrentUserId()) {
            "$appName (${AppUserSupport.getUserLabel(context, userId)})"
        } else {
            appName
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            appNameWithUser
        } else {
            // 如果Activity名称过长，只显示类名
            "${activityName.substringAfterLast('.')} · $appNameWithUser"
        }

        val prefix = context.getString(R.string.summary_vflow_system_launch_app_prefix)

        return PillUtil.buildSpannable(context,
            "$prefix ",
            // 创建一个药丸，让用户可以点击它来重新选择应用/Activity
            PillUtil.Pill(displayText, "packageName")
        )
    }
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 现在 variables 是 Map<String, VObject>，需要使用 getVariableAsString 获取
        val packageName = context.getVariableAsString("packageName", "")
        val activityName = context.getVariableAsString("activityName", "LAUNCH")
        val userId = context.getVariableAsInt("userId")
        val useAdb = context.getVariableAsBoolean("useAdb") ?: false

        if (packageName.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_empty_package),
                appContext.getString(R.string.error_vflow_system_launch_app_package_required)
            )
        }

        return if (useAdb) {
            // 使用 Shell 命令启动
            launchWithShell(packageName, activityName, userId, onProgress)
        } else {
            // 使用 Intent / LauncherApps 启动；无法直接跨用户时自动回退到 Shell
            launchWithSystemApi(packageName, activityName, userId, onProgress)
        }
    }

    private suspend fun launchWithShell(
        packageName: String,
        activityName: String,
        userId: Int?,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val userArg = if (userId != null) "--user $userId " else ""
            val command = if (activityName == "LAUNCH" || activityName.isNullOrBlank()) {
                val resolvedComponent = resolveLauncherComponentWithShell(packageName, userId)
                if (resolvedComponent != null) {
                    "am start ${userArg}-n $resolvedComponent"
                } else {
                    "am start ${userArg}-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName"
                }
            } else {
                // 启动指定 Activity
                "am start ${userArg}-n $packageName/$activityName"
            }

            val result = ShellManager.execShellCommand(appContext, command)

            // 检查 ShellManager 返回的错误
            if (result.startsWith("Error:")) {
                return@withContext ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                    result.removePrefix("Error: ")
                )
            }

            // 检查 am start 命令输出中的错误信息
            val output = result.lowercase()
            val hasError = output.contains("error:") ||
                          output.contains("permission denial") ||
                          output.contains("activity does not exist") ||
                          output.contains("unable to resolve") ||
                          output.contains("android.content.ActivityNotFoundException")

            if (hasError) {
                return@withContext ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                    result.trim()
                )
            }

            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_system_launch_app_launched), packageName)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_exception),
                e.localizedMessage ?: "发生了未知错误"
            )
        }
    }

    private suspend fun resolveLauncherComponentWithShell(
        packageName: String,
        userId: Int?
    ): String? = withContext(Dispatchers.IO) {
        val userArg = if (userId != null) "--user $userId " else ""
        val command = buildString {
            append("cmd package resolve-activity --brief ")
            append(userArg)
            append("-a android.intent.action.MAIN ")
            append("-c android.intent.category.LAUNCHER ")
            append("-p ")
            append(packageName)
        }

        val result = ShellManager.execShellCommand(appContext, command)
        if (result.startsWith("Error:", ignoreCase = true)) {
            return@withContext null
        }

        result.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.contains('/') && !it.startsWith("priority=") }
    }

    private suspend fun launchWithSystemApi(
        packageName: String,
        activityName: String,
        userId: Int?,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val targetUserId = userId ?: AppUserSupport.getCurrentUserId()

        if (targetUserId != AppUserSupport.getCurrentUserId()) {
            val userHandle = AppUserSupport.findUserHandle(appContext, targetUserId)
            return if (userHandle != null) {
                launchWithLauncherApps(packageName, activityName, targetUserId, onProgress)
            } else if (AppUserSupport.isShellAvailable(appContext)) {
                launchWithShell(packageName, activityName, targetUserId, onProgress)
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                    "该用户不是系统可见 profile，需启用 Shizuku 或 Root 才能启动"
                )
            }
        }

        val intent = if (activityName == "LAUNCH" || activityName.isNullOrBlank()) {
            appContext.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent().apply {
                component = ComponentName(packageName, activityName)
            }
        }

        if (intent == null) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                appContext.getString(R.string.error_vflow_system_launch_app_intent_not_found)
            )
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            appContext.startActivity(intent)

            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_system_launch_app_launched), packageName)))

            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_exception),
                e.localizedMessage ?: "发生了未知错误"
            )
        }
    }

    private suspend fun launchWithLauncherApps(
        packageName: String,
        activityName: String,
        userId: Int,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val userHandle = AppUserSupport.findUserHandle(appContext, userId)
            ?: return@withContext ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                "找不到用户 ${userId}，请重新选择应用"
            )

        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
            ?: return@withContext ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                "系统未提供跨用户启动能力"
            )

        val launcherActivities = try {
            launcherApps.getActivityList(packageName, userHandle)
        } catch (e: Exception) {
            return@withContext ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                e.localizedMessage ?: "无法读取目标用户下的应用入口"
            )
        }

        val targetActivity = if (activityName == "LAUNCH" || activityName.isBlank()) {
            launcherActivities.firstOrNull()
        } else {
            launcherActivities.firstOrNull { it.componentName.className == activityName }
        }

        if (targetActivity == null) {
            return@withContext ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_launch_failed),
                "目标用户下未找到可启动入口；如需启动非桌面 Activity，请启用 Shell 命令"
            )
        }

        return@withContext try {
            launcherApps.startMainActivity(targetActivity.componentName, userHandle, null, null)
            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_system_launch_app_launched), packageName)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_app_exception),
                e.localizedMessage ?: "发生了未知错误"
            )
        }
    }
}
