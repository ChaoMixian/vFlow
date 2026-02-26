package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.auth.RateLimiter
import com.chaomixian.vflow.api.model.*
import com.chaomixian.vflow.api.server.ApiDependencies
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ActionModule
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

        val categories = listOf(
            ModuleCategory("trigger", "触发器", "Triggers", "ic_trigger", "工作流触发条件", "Workflow trigger conditions", 0),
            ModuleCategory("ui_interaction", "界面交互", "UI Interaction", "ic_ui", "UI自动化操作", "UI automation operations", 1),
            ModuleCategory("logic", "逻辑控制", "Logic Control", "ic_logic", "条件判断与循环", "Conditions and loops", 2),
            ModuleCategory("data", "数据", "Data", "ic_data", "变量与数据处理", "Variables and data processing", 3),
            ModuleCategory("file", "文件", "File", "ic_file", "文件操作", "File operations", 4),
            ModuleCategory("network", "网络", "Network", "ic_network", "网络请求", "Network requests", 5),
            ModuleCategory("system", "应用与系统", "App & System", "ic_system", "系统控制与应用管理", "System control and app management", 6),
            ModuleCategory("shizuku", "Shizuku", "Shizuku", "ic_shizuku", "高级系统操作", "Advanced system operations", 7)
        )

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
                    category = module.metadata.category,
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
            filteredModules = filteredModules.filter { it.metadata.category == category }
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
                category = module.metadata.category,
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
}
