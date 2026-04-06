package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * 模块Handler
 */
class ModuleHandler(
    private val deps: ApiDependencies,
    rateLimiter: RateLimiter,
    gson: Gson
) : BaseHandler(rateLimiter, gson) {

    fun handle(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        // 提取模块ID - 支持 /modules/{id}, /modules/{id}/input-schema
        val moduleId = if (uri.matches(Regex("/api/v1/modules/[^/]+(/.*)?$"))) {
            getPathParameter(uri, "/api/v1/modules/")
        } else null

        val isInputSchema = uri.endsWith("/input-schema")

        return when {
            // 获取分类列表
            uri == "/api/v1/modules/categories" && method == NanoHTTPD.Method.GET -> {
                handleListCategories(tokenInfo)
            }
            // 获取模块列表
            uri == "/api/v1/modules" && method == NanoHTTPD.Method.GET -> {
                handleListModules(session, tokenInfo)
            }
            // 获取模块输入Schema
            moduleId != null && isInputSchema && method == NanoHTTPD.Method.GET -> {
                handleGetModuleInputSchema(moduleId, tokenInfo)
            }
            // 获取模块详情
            moduleId != null && method == NanoHTTPD.Method.GET -> {
                handleGetModuleDetail(moduleId, tokenInfo)
            }
            else -> errorResponse(404, "Endpoint not found")
        }
    }

    private fun handleListCategories(tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val categories = ModuleCategories.allSpecs().map { spec ->
            ModuleCategory(
                id = spec.id,
                name = spec.labelRes?.let(deps.context::getString) ?: spec.defaultLabel,
                nameEn = resolveCategoryEnglishName(spec.id),
                icon = "ic_${spec.id}",
                description = resolveCategoryDescription(spec.id, false),
                descriptionEn = resolveCategoryDescription(spec.id, true),
                order = spec.sortOrder
            )
        }

        return successResponse(mapOf("categories" to categories))
    }

    private fun handleListModules(session: NanoHTTPD.IHTTPSession, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val params = parseQueryParams(session)
        val category = params["category"]
        val search = params["search"]

        DebugLogger.d("ModuleHandler", "开始获取模块列表...")

        val modulesList = ModuleRegistry.getAllModules()
        DebugLogger.d("ModuleHandler", "获取到 ${modulesList.size} 个模块")

        val modules = modulesList.map { module ->
            ModuleSummary(
                id = module.id,
                metadata = ModuleMetadata(
                    name = module.metadata.name,
                    nameEn = module.metadata.name,
                    icon = module.metadata.iconRes.toString(),
                    category = module.metadata.getResolvedCategoryId(),
                    description = module.metadata.description,
                    descriptionEn = module.metadata.description,
                    helpUrl = null
                ),
                blockBehavior = BlockBehavior(
                    blockType = module.blockBehavior.type.name.lowercase(),
                    canStartWorkflow = true,
                    endBlockId = null
                )
            )
        }

        // 过滤掉积木块的中间和结束部分
        var filteredModules = modules.filter {
            it.blockBehavior.blockType != "block_middle" &&
            it.blockBehavior.blockType != "block_end"
        }

        // 按分类过滤
        if (category != null) {
            val normalizedCategory = ModuleCategories.resolveId(category) ?: category
            filteredModules = filteredModules.filter { it.metadata.category == normalizedCategory }
        }

        // 按搜索关键词过滤
        if (!search.isNullOrBlank()) {
            filteredModules = filteredModules.filter {
                it.metadata.name.contains(search, ignoreCase = true) ||
                it.metadata.description.contains(search, ignoreCase = true) ||
                it.id.contains(search, ignoreCase = true)
            }
        }

        DebugLogger.d("ModuleHandler", "最终返回 ${filteredModules.size} 个模块")

        return successResponse(mapOf("modules" to filteredModules))
    }

    private fun handleGetModuleDetail(moduleId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val module = ModuleRegistry.getModule(moduleId)
        if (module == null) {
            return errorResponse(2001, "Module not found")
        }

        // 构建模块详情响应
        val moduleDetail = ModuleDetail(
            id = module.id,
            metadata = ModuleMetadata(
                name = module.metadata.name,
                nameEn = module.metadata.name,
                icon = module.metadata.iconRes.toString(),
                category = module.metadata.getResolvedCategoryId(),
                description = module.metadata.description,
                descriptionEn = module.metadata.description,
                helpUrl = null
            ),
            blockBehavior = BlockBehavior(
                blockType = module.blockBehavior.type.name.lowercase(),
                canStartWorkflow = true,
                endBlockId = null
            ),
            inputs = module.getInputs().map { input ->
                ParameterDefinition(
                    id = input.id,
                    type = input.staticType.name,
                    label = input.name,
                    labelEn = input.name,
                    description = null,
                    descriptionEn = null,
                    defaultValue = input.defaultValue,
                    required = input.defaultValue == null,
                    uiType = mapInputStyleToUiType(input),
                    constraints = null
                )
            },
            outputs = module.getOutputs().map { output ->
                OutputDefinition(
                    id = output.id,
                    type = output.typeName,
                    label = output.name,
                    labelEn = output.name,
                    description = null,
                    descriptionEn = null
                )
            },
            examples = emptyList()
        )

        return successResponse(moduleDetail)
    }

    private fun handleGetModuleInputSchema(moduleId: String, tokenInfo: com.chaomixian.vflow.api.auth.TokenInfo): NanoHTTPD.Response {
        var rateLimitResponse: NanoHTTPD.Response? = null
        runBlocking {
            val rateLimitResult = checkRateLimit(tokenInfo.token, RateLimiter.RequestType.QUERY)
            if (rateLimitResult != null) rateLimitResponse = rateLimitResponse(rateLimitResult)
        }
        if (rateLimitResponse != null) return rateLimitResponse

        val module = ModuleRegistry.getModule(moduleId)
        if (module == null) {
            return errorResponse(2001, "Module not found")
        }

        // 生成UI Schema
        val schema = module.getInputs().map { input ->
            UiFieldSchema(
                key = input.id,
                type = mapInputStyleToUiType(input),
                label = input.name,
                labelEn = input.name,
                description = null,
                descriptionEn = null,
                placeholder = null,
                required = input.defaultValue == null,
                validation = null,
                autocomplete = null,
                options = if (input.options.isNotEmpty()) {
                    input.options.map { UiOption(it, it) }
                } else null,
                min = null,
                max = null,
                step = null,
                unit = null,
                keyPlaceholder = null,
                valuePlaceholder = null,
                allowVariables = input.acceptsMagicVariable,
                language = null,
                defaultValue = input.defaultValue
            )
        }

        return successResponse(mapOf("schema" to schema))
    }

    /**
     * 将输入样式映射到UI类型
     */
    private fun mapInputStyleToUiType(input: com.chaomixian.vflow.core.module.InputDefinition): String {
        return when {
            input.options.isNotEmpty() -> "dropdown"
            input.staticType == ParameterType.BOOLEAN -> "switch"
            input.staticType == ParameterType.NUMBER -> "number_slider"
            input.staticType == ParameterType.STRING -> "text_field"
            input.inputStyle == com.chaomixian.vflow.core.module.InputStyle.SWITCH -> "switch"
            input.inputStyle == com.chaomixian.vflow.core.module.InputStyle.SLIDER -> "number_slider"
            input.inputStyle == com.chaomixian.vflow.core.module.InputStyle.DROPDOWN -> "dropdown"
            else -> "text_field"
        }
    }

    private fun resolveCategoryEnglishName(categoryId: String): String = when (categoryId) {
        ModuleCategories.TRIGGER -> "Triggers"
        ModuleCategories.INTERACTION -> "Screen Interaction"
        ModuleCategories.LOGIC -> "Logic"
        ModuleCategories.DATA -> "Data Processing"
        ModuleCategories.FILE -> "File"
        ModuleCategories.NETWORK -> "Network"
        ModuleCategories.DEVICE -> "Device & System"
        ModuleCategories.CORE -> "Core (Beta)"
        ModuleCategories.SHIZUKU -> "Shizuku"
        ModuleCategories.TEMPLATE -> "Templates"
        ModuleCategories.UI -> "UI Control"
        ModuleCategories.FEISHU -> "Feishu"
        ModuleCategories.USER_MODULE -> "User Modules"
        else -> categoryId
    }

    private fun resolveCategoryDescription(categoryId: String, english: Boolean): String = when (categoryId) {
        ModuleCategories.TRIGGER -> if (english) "Workflow trigger conditions" else "工作流触发条件"
        ModuleCategories.INTERACTION -> if (english) "UI automation operations" else "界面自动化操作"
        ModuleCategories.LOGIC -> if (english) "Conditions and loops" else "条件判断与循环"
        ModuleCategories.DATA -> if (english) "Variables and data processing" else "变量与数据处理"
        ModuleCategories.FILE -> if (english) "File operations" else "文件操作"
        ModuleCategories.NETWORK -> if (english) "Network requests and integrations" else "网络请求与集成"
        ModuleCategories.DEVICE -> if (english) "System control and app management" else "系统控制与应用管理"
        ModuleCategories.CORE -> if (english) "Low-level core capabilities" else "底层核心能力"
        ModuleCategories.SHIZUKU -> if (english) "Advanced system operations" else "高级系统操作"
        ModuleCategories.TEMPLATE -> if (english) "Reusable workflow templates" else "可复用工作流模板"
        ModuleCategories.UI -> if (english) "UI construction and control" else "界面构建与控制"
        ModuleCategories.FEISHU -> if (english) "Feishu integrations" else "飞书集成"
        ModuleCategories.USER_MODULE -> if (english) "User-installed modules" else "用户安装的模块"
        else -> categoryId
    }
}
