// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/GetAppUsageStatsModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.Calendar

class GetAppUsageStatsModule : BaseModule() {
    companion object {
        private const val INTERVAL_TODAY = "today"
        private const val INTERVAL_24H = "last_24_hours"
        private const val INTERVAL_WEEK = "this_week"
        private const val INTERVAL_MONTH = "this_month"
        private const val INTERVAL_YEAR = "this_year"
    }

    override val id = "vflow.system.get_usage_stats"
    override val metadata = ActionMetadata(
        name = "获取应用使用情况",  // Fallback
        nameStringRes = R.string.module_vflow_system_get_usage_stats_name,
        description = "获取设备上应用的屏幕使用时间统计。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_get_usage_stats_desc,
        iconRes = R.drawable.rounded_data_object_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Read app usage statistics for a time interval and return the most-used apps.",
        inputHints = mapOf(
            "interval" to "Time range such as today, last_24_hours, this_week, this_month, or this_year.",
            "max_results" to "Maximum number of apps to return.",
        ),
        requiredInputIds = setOf("interval"),
    )

    override val requiredPermissions = listOf(PermissionManager.USAGE_STATS)

    private val intervalOptions = listOf(INTERVAL_TODAY, INTERVAL_24H, INTERVAL_WEEK, INTERVAL_MONTH, INTERVAL_YEAR)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "interval",
            name = "时间范围",
            staticType = ParameterType.ENUM,
            defaultValue = INTERVAL_TODAY,
            options = intervalOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_system_get_usage_stats_interval_name,
            optionsStringRes = listOf(
                R.string.option_vflow_system_get_usage_stats_interval_today,
                R.string.option_vflow_system_get_usage_stats_interval_24h,
                R.string.option_vflow_system_get_usage_stats_interval_week,
                R.string.option_vflow_system_get_usage_stats_interval_month,
                R.string.option_vflow_system_get_usage_stats_interval_year
            ),
            legacyValueMap = mapOf(
                "今天" to INTERVAL_TODAY,
                "Today" to INTERVAL_TODAY,
                "过去24小时" to INTERVAL_24H,
                "最近24小时" to INTERVAL_24H,
                "Last 24 Hours" to INTERVAL_24H,
                "本周" to INTERVAL_WEEK,
                "This Week" to INTERVAL_WEEK,
                "本月" to INTERVAL_MONTH,
                "This Month" to INTERVAL_MONTH,
                "本年" to INTERVAL_YEAR,
                "今年" to INTERVAL_YEAR,
                "This Year" to INTERVAL_YEAR
            )
        ),
        InputDefinition(
            id = "max_results",
            name = "最大结果数",
            staticType = ParameterType.NUMBER,
            defaultValue = 10.0, // 默认前10个
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_system_get_usage_stats_max_results_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("stats_list", "统计列表", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.DICTIONARY.id, nameStringRes = R.string.output_vflow_system_get_usage_stats_stats_list_name),
        OutputDefinition("most_used_app", "最常使用的应用", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_system_get_usage_stats_most_used_app_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_system_get_usage_stats_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val intervalPill = PillUtil.createPillFromParam(
            step.parameters["interval"],
            getInputs().find { it.id == "interval" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_get_usage_prefix), intervalPill, context.getString(R.string.summary_vflow_system_get_usage_suffix))
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val intervalInput = getInputs().first { it.id == "interval" }
        val rawIntervalStr = context.getVariableAsString("interval", INTERVAL_TODAY)
        val intervalStr = intervalInput.normalizeEnumValue(rawIntervalStr) ?: rawIntervalStr
        // 现在 variables 是 Map<String, VObject>，使用 getVariableAsInt 获取
        val maxResults = context.getVariableAsInt("max_results") ?: 10

        val usageStatsManager = context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.applicationContext.packageManager

        // 计算时间范围
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime = when (intervalStr) {
            INTERVAL_TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            INTERVAL_24H -> endTime - 24 * 60 * 60 * 1000
            INTERVAL_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.timeInMillis
            }
            INTERVAL_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.timeInMillis
            }
            INTERVAL_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.timeInMillis
            }
            else -> endTime - 24 * 60 * 60 * 1000
        }

        onProgress(ProgressUpdate("正在查询 $intervalStr 的使用记录..."))

        // 查询并聚合数据
        // queryAndAggregateUsageStats 返回的是 Map<String, UsageStats>
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

        if (statsMap.isEmpty()) {
            // 如果返回为空，可能是权限没给，或者系统未记录
            return ExecutionResult.Failure("获取失败", "未获取到使用数据，请确保“使用情况访问权限”已授予且系统已记录数据。")
        }

        // 过滤和排序
        val sortedStats = statsMap.values
            .filter { it.totalTimeInForeground > 0 } // 只保留有使用记录的
            .sortedByDescending { it.totalTimeInForeground }
            .take(maxResults)
            .map { usageStats ->
                val packageName = usageStats.packageName
                val appName = try {
                    pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                } catch (e: Exception) {
                    packageName
                }

                // 转换为字典格式
                mapOf(
                    "package_name" to packageName,
                    "app_name" to appName,
                    "total_time_ms" to usageStats.totalTimeInForeground,
                    "total_time_minutes" to (usageStats.totalTimeInForeground / 1000 / 60), // 分钟
                    "last_time_used" to usageStats.lastTimeUsed
                )
            }

        if (sortedStats.isEmpty()) {
            onProgress(ProgressUpdate("指定时间段内没有应用使用记录"))
            return ExecutionResult.Success(mapOf(
                "stats_list" to VList(emptyList()),
                "most_used_app" to VString(""),
                "success" to VBoolean(true)
            ))
        }

        // 使用安全的访问方式，代码意图更清晰
        val mostUsed = sortedStats.firstOrNull()?.get("package_name") as? String ?: ""
        val mostUsedName = sortedStats.firstOrNull()?.get("app_name") as? String ?: ""

        onProgress(ProgressUpdate("最常使用: $mostUsedName"))

        return ExecutionResult.Success(mapOf(
            "stats_list" to VList(sortedStats.map { VObjectFactory.from(it) }),
            "most_used_app" to VString(mostUsed),
            "success" to VBoolean(true)
        ))
    }
}
