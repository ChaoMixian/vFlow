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

    override val id = "vflow.system.get_usage_stats"
    override val metadata = ActionMetadata(
        name = "获取应用使用情况",
        description = "获取设备上应用的屏幕使用时间统计。",
        iconRes = R.drawable.rounded_data_object_24,
        category = "应用与系统"
    )

    override val requiredPermissions = listOf(PermissionManager.USAGE_STATS)

    private val intervalOptions = listOf("今天", "过去24小时", "本周", "本月", "本年")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "interval",
            name = "时间范围",
            staticType = ParameterType.ENUM,
            defaultValue = "今天",
            options = intervalOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "max_results",
            name = "最大结果数",
            staticType = ParameterType.NUMBER,
            defaultValue = 10.0, // 默认前10个
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("stats_list", "统计列表", VTypeRegistry.LIST.id),
        OutputDefinition("most_used_app", "最常使用的应用", VTypeRegistry.STRING.id), // 包名
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val intervalPill = PillUtil.createPillFromParam(
            step.parameters["interval"],
            getInputs().find { it.id == "interval" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, "获取 ", intervalPill, " 的应用使用排行")
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val intervalStr = context.variables["interval"] as? String ?: "今天"
        val maxResults = ((context.magicVariables["max_results"] as? VNumber)?.raw
            ?: (context.variables["max_results"] as? Number)?.toDouble()
            ?: 10.0).toInt()

        val usageStatsManager = context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.applicationContext.packageManager

        // 计算时间范围
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime = when (intervalStr) {
            "今天" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            "过去24小时" -> endTime - 24 * 60 * 60 * 1000
            "本周" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.timeInMillis
            }
            "本月" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.timeInMillis
            }
            "本年" -> {
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

        val mostUsed = sortedStats.first()["package_name"] as String
        val mostUsedName = sortedStats.first()["app_name"] as String

        onProgress(ProgressUpdate("最常使用: $mostUsedName"))

        return ExecutionResult.Success(mapOf(
            "stats_list" to VList(sortedStats.map { VObjectFactory.from(it) }),
            "most_used_app" to VString(mostUsed),
            "success" to VBoolean(true)
        ))
    }
}