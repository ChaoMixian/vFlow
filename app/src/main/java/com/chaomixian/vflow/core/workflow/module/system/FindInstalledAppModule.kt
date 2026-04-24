package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.app_picker.AppUserSupport
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class FindInstalledAppModule : BaseModule() {

    override val id = "vflow.system.find_installed_app"
    override val metadata = ActionMetadata(
        name = "查找本机应用",
        nameStringRes = R.string.module_vflow_system_find_installed_app_name,
        description = "按应用名称或包名查询本机已安装应用，返回最匹配的包名",
        descriptionStringRes = R.string.module_vflow_system_find_installed_app_desc,
        iconRes = R.drawable.rounded_search_24,
        category = "应用与系统",
        categoryId = "device",
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Search installed local apps by app name or package name and return the best-matching package id. Use this before launch_app or close_app when the user names an app like IT之家, 微信, Chrome, or a brand name instead of an Android package.",
        workflowStepDescription = "Search installed local apps by app name or package name.",
        inputHints = mapOf(
            "query" to "The app label, brand name, or package fragment to search for.",
            "launchableOnly" to "Leave true for app open tasks so results prefer apps with a launcher entry.",
            "maxResults" to "Maximum number of candidate matches to return.",
        ),
        requiredInputIds = setOf("query"),
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "query",
            name = "应用名称或包名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_system_find_installed_app_query_name,
        ),
        InputDefinition(
            id = "launchableOnly",
            name = "仅查找可启动应用",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_system_find_installed_app_launchable_only_name,
        ),
        InputDefinition(
            id = "userId",
            name = "用户 ID",
            staticType = ParameterType.NUMBER,
            defaultValue = null,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_system_find_installed_app_user_id_name,
        ),
        InputDefinition(
            id = "maxResults",
            name = "最大结果数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_system_find_installed_app_max_results_name,
        ),
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_success_name,
        ),
        OutputDefinition(
            id = "found",
            name = "是否找到匹配",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_found_name,
        ),
        OutputDefinition(
            id = "query",
            name = "搜索词",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_query_name,
        ),
        OutputDefinition(
            id = "package_name",
            name = "包名",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_package_name_name,
        ),
        OutputDefinition(
            id = "app_name",
            name = "应用名称",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_app_name_name,
        ),
        OutputDefinition(
            id = "activity_name",
            name = "Activity 名称",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_activity_name_name,
        ),
        OutputDefinition(
            id = "user_id",
            name = "用户 ID",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_user_id_name,
        ),
        OutputDefinition(
            id = "match_count",
            name = "匹配数量",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_match_count_name,
        ),
        OutputDefinition(
            id = "exact_match",
            name = "是否精确匹配",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_exact_match_name,
        ),
        OutputDefinition(
            id = "best_match",
            name = "最佳匹配",
            typeName = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_best_match_name,
        ),
        OutputDefinition(
            id = "all_matches",
            name = "全部匹配",
            typeName = VTypeRegistry.LIST.id,
            listElementType = VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_system_find_installed_app_all_matches_name,
        ),
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit,
    ) = run {
        val query = context.getVariableAsString("query", "").trim()
        val searchUserId = context.getVariableAsInt("userId") ?: AppUserSupport.getCurrentUserId()
        val launchableOnly = context.getVariableAsBoolean("launchableOnly") ?: true
        val maxResults = (context.getVariableAsInt("maxResults") ?: 5).coerceIn(1, 10)

        if (query.isBlank()) {
            return@run com.chaomixian.vflow.core.module.ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_find_installed_app_empty_query),
                appContext.getString(R.string.error_vflow_system_find_installed_app_query_required),
            )
        }

        onProgress(
            ProgressUpdate(
                appContext.getString(R.string.msg_vflow_system_find_installed_app_searching, query)
            )
        )

        val matches = InstalledAppSearchSupport.searchApps(
            context = appContext,
            query = query,
            userId = searchUserId,
            launchableOnly = launchableOnly,
            maxResults = maxResults,
        )
        val bestMatch = matches.firstOrNull()

        if (bestMatch != null) {
            onProgress(
                ProgressUpdate(
                    appContext.getString(
                        R.string.msg_vflow_system_find_installed_app_found,
                        bestMatch.candidate.appName,
                        bestMatch.candidate.packageName,
                    )
                )
            )
        } else {
            onProgress(
                ProgressUpdate(
                    appContext.getString(R.string.msg_vflow_system_find_installed_app_not_found, query)
                )
            )
        }

        val bestDictionary = bestMatch?.toDictionary() ?: VDictionary(emptyMap())
        val allMatches = VList(matches.map { it.toDictionary() })

        return@run com.chaomixian.vflow.core.module.ExecutionResult.Success(
            mapOf(
                "success" to VBoolean(true),
                "found" to VBoolean(bestMatch != null),
                "query" to VString(query),
                "package_name" to VString(bestMatch?.candidate?.packageName.orEmpty()),
                "app_name" to VString(bestMatch?.candidate?.appName.orEmpty()),
                "activity_name" to VString(bestMatch?.candidate?.activityName ?: "LAUNCH"),
                "user_id" to VNumber((bestMatch?.candidate?.userId ?: searchUserId).toDouble()),
                "match_count" to VNumber(matches.size.toDouble()),
                "exact_match" to VBoolean(bestMatch?.isExactMatch == true),
                "best_match" to bestDictionary,
                "all_matches" to allMatches,
            )
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val queryPill = PillUtil.createPillFromParam(
            step.parameters["query"],
            getInputs().firstOrNull { it.id == "query" },
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_system_find_installed_app_prefix),
            " ",
            queryPill,
        )
    }

    private fun InstalledAppSearchMatch.toDictionary(): VDictionary {
        return VDictionary(
            mapOf(
                "app_name" to VString(candidate.appName),
                "package_name" to VString(candidate.packageName),
                "activity_name" to VString(candidate.activityName),
                "user_id" to VNumber(candidate.userId.toDouble()),
                "user_label" to VString(candidate.userLabel),
                "launchable" to VBoolean(candidate.isLaunchable),
                "score" to VNumber(score.toDouble()),
                "exact_match" to VBoolean(isExactMatch),
            )
        )
    }
}
