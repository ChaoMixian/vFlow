package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.*
import com.chaomixian.vflow.core.workflow.module.triggers.RuleExecutionState
import com.chaomixian.vflow.services.ServiceStateBus
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonArray as GsonJsonArray
import com.google.gson.JsonElement as GsonJsonElement
import com.google.gson.JsonObject as GsonJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import li.songe.json5.Json5
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * GKD订阅触发器处理器
 * 解析 gkd 格式的订阅规则，监听页面变化并触发工作流
 */
class GKDTriggerHandler : ListeningTriggerHandler() {

    companion object {
        private const val TAG = "GKDTriggerHandler"
        private const val RULES_DIR = "gkd_rules"
    }

    // 存储所有工作流的状态
    private val triggerStates = CopyOnWriteArrayList<GKDTriggerState>()

    // Transform 实例
    private val transform = createTransform()

    // 监听Job
    private var listeningJob: Job? = null

    override fun getTriggerModuleId(): String = GKDTriggerModule().id

    override fun startListening(context: Context) {
        DebugLogger.d(TAG, "开始监听 GKD 订阅规则")

        // 确保 triggerStates 与 listeningWorkflows 同步
        syncTriggerStates()

        listeningJob = triggerScope.launch {
            launch {
                ServiceStateBus.windowChangeEventFlow.collect { (packageName, className) ->
                    DebugLogger.d(TAG, "收到窗口变化事件: $packageName / $className")
                    checkAndTriggerWorkflows(context, packageName, className)
                }
            }
            launch {
                ServiceStateBus.windowContentChangedFlow.collect { (packageName, className) ->
                    DebugLogger.d(TAG, "收到窗口内容变化事件: $packageName / $className")
                    checkAndTriggerWorkflows(context, packageName, className)
                }
            }
        }
    }

    override fun stopListening(context: Context) {
        DebugLogger.d(TAG, "停止监听 GKD 订阅规则")
        listeningJob?.cancel()
        listeningJob = null
        triggerStates.clear()
    }

    /**
     * 同步 triggerStates 与 listeningWorkflows
     */
    private fun syncTriggerStates() {
        val currentWorkflowIds = listeningWorkflows.map { it.id }.toSet()

        // 移除不存在的workflow的state
        triggerStates.removeAll { state ->
            !currentWorkflowIds.contains(state.workflow.id)
        }

        // 为新workflow创建state
        for (workflow in listeningWorkflows) {
            if (triggerStates.none { it.workflow.id == workflow.id }) {
                val state = createTriggerState(workflow)
                if (state != null) {
                    triggerStates.add(state)
                    DebugLogger.d(TAG, "加载工作流 '${workflow.name}' 的 GKD 规则")
                }
            }
        }
    }

    /**
     * 创建触发器状态
     */
    private fun createTriggerState(workflow: Workflow): GKDTriggerState? {
        val config = workflow.triggerConfig ?: return null

        val rules = mutableListOf<ResolvedGKDRule>()

        // 1. 从订阅 URL 下载
        val subscriptionUrl = config["subscriptionUrl"] as? String
        if (!subscriptionUrl.isNullOrBlank()) {
            DebugLogger.d(TAG, "工作流 '${workflow.name}' 从订阅 URL 加载: $subscriptionUrl")
            val parsedRules = downloadAndParseSubscription(subscriptionUrl, getRulesDir())
            DebugLogger.d(TAG, "工作流 '${workflow.name}' 解析到 ${parsedRules.size} 条规则")
            rules.addAll(parsedRules)
        }

        // 2. 从文件加载
        val subscriptionFile = config["subscriptionFile"] as? String
        if (!subscriptionFile.isNullOrBlank()) {
            val fileRules = loadRulesFromFile(subscriptionFile)
            rules.addAll(fileRules)
        }

        if (rules.isEmpty()) {
            DebugLogger.w(TAG, "工作流 '${workflow.name}' 没有有效的 GKD 规则")
            return null
        }

        return GKDTriggerState(workflow = workflow, rules = rules)
    }

    /**
     * 下载并解析远程订阅
     * 使用缓存避免重复下载
     */
    private fun downloadAndParseSubscription(url: String, rulesDir: File): List<ResolvedGKDRule> {
        // 生成缓存文件名（使用 URL 的 hash 作为文件名）
        val fileName = "subscription_${url.hashCode()}.json"
        val file = File(rulesDir, fileName)

        // 检查缓存是否存在且有效（1小时内有效）
        val cacheValid = file.exists() && (System.currentTimeMillis() - file.lastModified()) < 3600000

        if (cacheValid) {
            DebugLogger.d(TAG, "使用缓存的订阅: ${file.absolutePath}")
            return try {
                val content = file.readText()
                if (content.isNotEmpty()) {
                    parseSubscriptionContent(content)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "读取缓存失败，尝试重新下载", e)
                downloadSubscription(url, file)
            }
        }

        // 缓存不存在或已过期，下载订阅
        return downloadSubscription(url, file)
    }

    /**
     * 实际执行下载操作
     * 在后台线程执行网络请求
     */
    private fun downloadSubscription(url: String, file: File): List<ResolvedGKDRule> {
        DebugLogger.d(TAG, "开始下载订阅: $url")

        return runBlocking(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                DebugLogger.d(TAG, "响应码: ${response.code}")

                if (!response.isSuccessful) {
                    DebugLogger.e(TAG, "下载订阅失败: ${response.code}")
                    return@runBlocking emptyList<ResolvedGKDRule>()
                }

                val body = response.body
                if (body == null) {
                    DebugLogger.e(TAG, "响应体为空")
                    return@runBlocking emptyList<ResolvedGKDRule>()
                }

                val content = body.string()
                DebugLogger.d(TAG, "下载内容长度: ${content.length}")

                if (content.isEmpty()) {
                    DebugLogger.e(TAG, "订阅内容为空")
                    return@runBlocking emptyList<ResolvedGKDRule>()
                }

                // 保存到文件
                file.writeText(content)
                DebugLogger.d(TAG, "已保存订阅到: ${file.absolutePath}")

                // 解析
                parseSubscriptionContent(content)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "下载订阅失败: ${e.message}", e)
                // 如果下载失败且有缓存文件，尝试使用缓存
                if (file.exists()) {
                    DebugLogger.d(TAG, "下载失败，尝试使用旧缓存")
                    return@runBlocking try {
                        val content = file.readText()
                        if (content.isNotEmpty()) {
                            parseSubscriptionContent(content)
                        } else {
                            emptyList()
                        }
                    } catch (e2: Exception) {
                        DebugLogger.e(TAG, "读取旧缓存也失败", e2)
                        emptyList()
                    }
                }
                emptyList()
            }
        }
    }

    /**
     * 解析订阅内容
     */
    private fun parseSubscriptionContent(content: String): List<ResolvedGKDRule> {
        // 验证输入
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length < 10) {
            DebugLogger.w(TAG, "订阅内容为空或过短")
            return emptyList()
        }

        return try {
            // 使用 Json5 解析（支持单引号、注释等）
            val jsonElement = Json5.parseToJson5Element(trimmed)
            parseJsonElement(jsonElement)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "解析订阅内容失败: ${trimmed.take(100)}", e)
            emptyList()
        }
    }

    /**
     * 解析 JSON 元素
     */
    private fun parseJsonElement(jsonElement: JsonElement): List<ResolvedGKDRule> {
        val rules = mutableListOf<ResolvedGKDRule>()

        if (jsonElement !is JsonObject) {
            DebugLogger.w(TAG, "JSON 根元素不是对象")
            return rules
        }
        val rootObj = jsonElement

        DebugLogger.d(TAG, "解析根对象，字段: ${rootObj.keys}")

        // 解析 apps
        val appsElement = rootObj["apps"]
        if (appsElement is JsonArray) {
            DebugLogger.d(TAG, "apps 数组元素数量: ${appsElement.size}")
            appsElement.forEachIndexed { index, appJson ->
                if (appJson !is JsonObject) {
                    DebugLogger.w(TAG, "app[$index] 不是对象")
                    return@forEachIndexed
                }
                val appObj = appJson

                // 检查 app 级别的启用状态
                val appEnable = appObj["enable"]?.jsonPrimitive?.booleanOrNull
                if (appEnable != null && !appEnable) {
                    DebugLogger.d(TAG, "app[$index] 已禁用，跳过")
                    return@forEachIndexed
                }

                val appId = appObj["id"]?.jsonPrimitive?.content ?: run {
                    DebugLogger.w(TAG, "app[$index] 缺少 id 字段")
                    return@forEachIndexed
                }
                val appName = appObj["name"]?.jsonPrimitive?.content

                val groupsElement = appObj["groups"]
                if (groupsElement is JsonArray) {
                    groupsElement.forEachIndexed { gIndex, groupJson ->
                        if (groupJson !is JsonObject) {
                            DebugLogger.w(TAG, "app[$appId].groups[$gIndex] 不是对象")
                            return@forEachIndexed
                        }
                        val groupObj = groupJson

                        // 检查 group 级别的启用状态
                        val groupEnable = groupObj["enable"]?.jsonPrimitive?.booleanOrNull
                        if (groupEnable != null && !groupEnable) {
                            DebugLogger.d(TAG, "app[$appId].groups[$gIndex] 已禁用，跳过")
                            return@forEachIndexed
                        }

                        val groupName = groupObj["name"]?.jsonPrimitive?.content ?: "未命名"

                        val rulesElement = groupObj["rules"]
                        if (rulesElement is JsonArray) {
                            rulesElement.forEachIndexed { rIndex, ruleJson ->
                                if (ruleJson !is JsonObject) {
                                    DebugLogger.w(TAG, "app[$appId].$groupName.rules[$rIndex] 不是对象")
                                    return@forEachIndexed
                                }
                                val ruleObj = ruleJson

                                // 检查 rule 级别的启用状态
                                val ruleEnable = ruleObj["enable"]?.jsonPrimitive?.booleanOrNull
                                if (ruleEnable != null && !ruleEnable) {
                                    DebugLogger.d(TAG, "app[$appId].$groupName.rules[$rIndex] 已禁用，跳过")
                                    return@forEachIndexed
                                }

                                val rule = parseRule(ruleObj, groupName, appId)
                                if (rule != null) {
                                    rules.add(rule)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            DebugLogger.w(TAG, "apps 字段不是数组或为空: ${appsElement?.javaClass?.simpleName}")
        }

        // 解析 globalGroups
        val globalGroupsElement = rootObj["globalGroups"]
        if (globalGroupsElement is JsonArray) {
            DebugLogger.d(TAG, "globalGroups 数组元素数量: ${globalGroupsElement.size}")
            globalGroupsElement.forEachIndexed { index, groupJson ->
                if (groupJson !is JsonObject) {
                    DebugLogger.w(TAG, "globalGroups[$index] 不是对象")
                    return@forEachIndexed
                }
                val groupObj = groupJson

                // 检查 globalGroup 级别的启用状态
                val groupEnable = groupObj["enable"]?.jsonPrimitive?.booleanOrNull
                if (groupEnable != null && !groupEnable) {
                    DebugLogger.d(TAG, "globalGroups[$index] 已禁用，跳过")
                    return@forEachIndexed
                }

                val groupName = groupObj["name"]?.jsonPrimitive?.content ?: "全局"

                val rulesElement = groupObj["rules"]
                if (rulesElement is JsonArray) {
                    rulesElement.forEachIndexed { rIndex, ruleJson ->
                        if (ruleJson !is JsonObject) {
                            DebugLogger.w(TAG, "globalGroups.$groupName.rules[$rIndex] 不是对象")
                            return@forEachIndexed
                        }
                        val ruleObj = ruleJson

                        // 检查 rule 级别的启用状态
                        val ruleEnable = ruleObj["enable"]?.jsonPrimitive?.booleanOrNull
                        if (ruleEnable != null && !ruleEnable) {
                            DebugLogger.d(TAG, "globalGroups.$groupName.rules[$rIndex] 已禁用，跳过")
                            return@forEachIndexed
                        }

                        val rule = parseRule(ruleObj, groupName, null)
                        if (rule != null) {
                            rules.add(rule)
                        }
                    }
                }
            }
        }

        DebugLogger.d(TAG, "共解析到 ${rules.size} 条规则")
        return rules
    }

    /**
     * 解析规则
     */
    private fun parseRule(
        ruleObj: JsonObject,
        groupName: String,
        appId: String?
    ): ResolvedGKDRule? {
        val ruleName = ruleObj["name"]?.jsonPrimitive?.content ?: "未命名规则"
        val ruleKey = ruleObj["key"]?.jsonPrimitive?.int

        // 解析选择器
        val matches = parseSelectors(ruleObj["matches"])
        val anyMatches = parseSelectors(ruleObj["anyMatches"])
        val excludeMatches = parseSelectors(ruleObj["excludeMatches"])
        val excludeAllMatches = parseSelectors(ruleObj["excludeAllMatches"])

        // 如果没有有效的选择器，跳过
        if (matches.isEmpty() && anyMatches.isEmpty()) {
            return null
        }

        return ResolvedGKDRule(
            name = ruleName,
            groupName = groupName,
            appId = appId,
            activityIds = parseStringArray(ruleObj["activityIds"]),
            excludeActivityIds = parseStringArray(ruleObj["excludeActivityIds"]),
            matches = matches,
            anyMatches = anyMatches,
            excludeMatches = excludeMatches,
            excludeAllMatches = excludeAllMatches,
            actionCd = ruleObj["actionCd"]?.jsonPrimitive?.long,
            actionDelay = ruleObj["actionDelay"]?.jsonPrimitive?.long,
            matchDelay = ruleObj["matchDelay"]?.jsonPrimitive?.long,
            matchTime = ruleObj["matchTime"]?.jsonPrimitive?.long,
            matchRoot = ruleObj["matchRoot"]?.jsonPrimitive?.boolean,
            fastQuery = ruleObj["fastQuery"]?.jsonPrimitive?.boolean,
            actionMaximum = ruleObj["actionMaximum"]?.jsonPrimitive?.int
        )
    }

    /**
     * 解析选择器列表
     */
    private fun parseSelectors(element: JsonElement?): List<Selector> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonArray -> {
                element.mapNotNull { item ->
                    (item as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { source ->
                        try {
                            Selector.parse(source)
                        } catch (e: Exception) {
                            DebugLogger.w(TAG, "解析选择器失败: $source")
                            null
                        }
                    }
                }
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                element.content?.let { source ->
                    try {
                        listOf(Selector.parse(source))
                    } catch (e: Exception) {
                        DebugLogger.w(TAG, "解析选择器失败: $source")
                        emptyList()
                    }
                } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    /**
     * 解析字符串数组
     */
    private fun parseStringArray(element: JsonElement?): List<String>? {
        if (element == null) return null
        return when (element) {
            is JsonArray -> {
                element.mapNotNull { item -> (item as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                element.content?.let { listOf(it) }
            }
            else -> null
        }
    }

    /**
     * 从文件加载规则
     */
    private fun loadRulesFromFile(filePath: String): List<ResolvedGKDRule> {
        val file = if (filePath.startsWith("/")) {
            File(filePath)
        } else {
            File(getRulesDir(), filePath)
        }

        if (!file.exists()) {
            DebugLogger.w(TAG, "规则文件不存在: ${file.absolutePath}")
            return emptyList()
        }

        return try {
            file.readText().let { content ->
                parseSubscriptionContent(content)
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "读取规则文件失败: ${file.absolutePath}", e)
            emptyList()
        }
    }

    /**
     * 获取规则目录
     */
    private fun getRulesDir(): File {
        val dir = File(StorageManager.tempDir, RULES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 检查并触发匹配的工作流
     */
    private fun checkAndTriggerWorkflows(context: Context, packageName: String, className: String) {
        syncTriggerStates()

        val service = ServiceStateBus.getAccessibilityService() ?: return
        val rootNode = service.rootInActiveWindow ?: return

        val t = System.currentTimeMillis()
        val currentPackage = packageName
        val currentActivity = className

        for (state in triggerStates) {
            try {
                // 匹配规则
                for (rule in state.rules) {
                    val ruleState = state.getRuleState(rule)

                    // 检查规则状态
                    if (!ruleState.shouldTrigger()) {
                        continue
                    }

                    // 检查应用匹配
                    if (rule.appId != null && rule.appId != currentPackage) {
                        continue
                    }

                    // 检查 Activity 匹配
                    if (!matchActivity(rule.activityIds, rule.excludeActivityIds, currentActivity)) {
                        continue
                    }

                    // 获取匹配选项
                    val matchOption = MatchOption(fastQuery = rule.fastQuery ?: false)

                    // 查询匹配的节点 - 先尝试 anyMatches
                    var matchedNode = queryRuleSelectors(rootNode, rule.anyMatches, matchOption, false)
                        ?: queryRuleSelectors(rootNode, rule.matches, matchOption, true)

                    if (matchedNode == null) {
                        continue
                    }

                    // 检查排除条件
                    if (queryRuleSelectors(rootNode, rule.excludeMatches, matchOption, false) != null) {
                        continue
                    }
                    if (rule.excludeAllMatches.isNotEmpty()) {
                        val allExcluded = rule.excludeAllMatches.all { selector ->
                            queryRuleSelectors(rootNode, listOf(selector), matchOption, false) == null
                        }
                        if (!allExcluded) {
                            continue
                        }
                    }

                    // 记录匹配
                    ruleState.recordMatch(t)

                    // 处理触发延迟
                    if (ruleState.actionDelay > 0) {
                        ruleState.startActionDelay(t)
                        triggerScope.launch {
                            delay(ruleState.actionDelay)
                            performTrigger(context, state, rule, ruleState, matchedNode)
                        }
                    } else {
                        performTrigger(context, state, rule, ruleState, matchedNode)
                    }
                    break // 触发后退出规则循环
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "处理工作流 '${state.workflow.name}' 时出错", e)
            }
        }
    }

    /**
     * 匹配 Activity
     */
    private fun matchActivity(
        activityIds: List<String>?,
        excludeActivityIds: List<String>?,
        currentActivity: String
    ): Boolean {
        // 检查排除
        if (excludeActivityIds != null) {
            if (excludeActivityIds.any { pattern ->
                    pattern == currentActivity || pattern == "*" || currentActivity.contains(pattern)
                }) {
                return false
            }
        }

        // 如果没有指定 activityIds，则匹配所有
        if (activityIds == null) return true

        // 检查是否匹配
        return activityIds.any { pattern ->
            pattern == currentActivity || pattern == "*" || currentActivity.contains(pattern)
        }
    }

    /**
     * 查询规则选择器匹配的节点
     * @param rootNode 根节点
     * @param selectors 选择器列表
     * @param matchOption 匹配选项
     * @param requireAll 是否要求全部匹配
     * @return 匹配的节点或 null
     */
    private fun queryRuleSelectors(
        rootNode: AccessibilityNodeInfo,
        selectors: List<Selector>,
        matchOption: MatchOption,
        requireAll: Boolean
    ): AccessibilityNodeInfo? {
        if (selectors.isEmpty()) return null

        return if (requireAll) {
            // 全部必须匹配
            var resultNode: AccessibilityNodeInfo? = null
            for (selector in selectors) {
                resultNode = transform.querySelectorAll(rootNode, selector, matchOption).firstOrNull()
                    ?: return null
            }
            resultNode
        } else {
            // 任一匹配即可
            for (selector in selectors) {
                transform.querySelectorAll(rootNode, selector, matchOption).firstOrNull()?.let {
                    return it
                }
            }
            null
        }
    }

    /**
     * 查询所有匹配的节点
     */
    private fun queryAllMatchedNodes(
        rootNode: AccessibilityNodeInfo,
        rule: ResolvedGKDRule
    ): List<AccessibilityNodeInfo> {
        val matchOption = MatchOption(fastQuery = rule.fastQuery ?: false)
        val nodes = mutableSetOf<AccessibilityNodeInfo>()

        // 添加 anyMatches 匹配的节点
        for (selector in rule.anyMatches) {
            nodes.addAll(transform.querySelectorAll(rootNode, selector, matchOption))
        }

        // 添加 matches 匹配的节点
        for (selector in rule.matches) {
            nodes.addAll(transform.querySelectorAll(rootNode, selector, matchOption))
        }

        return nodes.toList()
    }

    /**
     * 执行触发
     */
    private fun performTrigger(
        context: Context,
        state: GKDTriggerState,
        rule: ResolvedGKDRule,
        ruleState: RuleExecutionState,
        node: AccessibilityNodeInfo
    ) {
        if (!ruleState.shouldTrigger()) {
            return
        }

        // 创建 VScreenElement
        val element = VScreenElement.fromAccessibilityNode(node, calculateDepth(node))

        // 查询所有匹配的节点
        val allNodes = queryAllMatchedNodes(node, rule)
        val allElements = allNodes.map { VScreenElement.fromAccessibilityNode(it, calculateDepth(it)) }

        // 调用 WorkflowExecutor
        WorkflowExecutor.execute(
            workflow = state.workflow,
            context = context.applicationContext,
            triggerData = GKDTriggerData(
                element = element,
                allElements = allElements,
                ruleName = rule.name,
                ruleGroup = rule.groupName
            )
        )

        // 更新规则状态
        ruleState.recordTrigger(System.currentTimeMillis())
    }

    /**
     * 创建 Transform 实例
     */
    private fun createTransform(): Transform<AccessibilityNodeInfo> {
        return Transform(
            getAttr = { target, name ->
                when (target) {
                    is QueryContext<*> -> when (name) {
                        "prev" -> target.prev
                        "current" -> target.current
                        else -> getNodeAttr(target.current as AccessibilityNodeInfo, name)
                    }
                    is AccessibilityNodeInfo -> getNodeAttr(target, name)
                    else -> null
                }
            },
            getInvoke = getInvoke@{ target, name, args ->
                when (target) {
                    is QueryContext<*> -> getNodeInvoke(target.current as AccessibilityNodeInfo, name, args)
                    is AccessibilityNodeInfo -> getNodeInvoke(target, name, args)
                    else -> null
                }
            },
            getName = { node -> node.className?.toString() },
            getChildren = { node ->
                sequence {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { yield(it) }
                    }
                }
            },
            getParent = { node -> node.parent },
            traverseFastQueryDescendants = { rootNode, fastQueryList ->
                fastQueryDescendants(rootNode, fastQueryList)
            }
        )
    }

    /**
     * 快速查询后代节点 - 使用 Android Accessibility API
     */
    private fun fastQueryDescendants(
        rootNode: AccessibilityNodeInfo,
        fastQueryList: List<FastQuery>
    ): Sequence<AccessibilityNodeInfo> {
        return sequence {
            for (fq in fastQueryList) {
                when (fq) {
                    is FastQuery.Text -> {
                        // 使用 findAccessibilityNodeInfosByText
                        val nodes = rootNode.findAccessibilityNodeInfosByText(fq.value)
                        for (node in nodes) {
                            // 验证节点是否是 rootNode 的后代
                            if (isDescendantOf(rootNode, node)) {
                                yield(node)
                            }
                        }
                    }
                    is FastQuery.Id -> {
                        // 使用 findAccessibilityNodeInfosByViewId
                        val nodes = rootNode.findAccessibilityNodeInfosByViewId(fq.value)
                        for (node in nodes) {
                            // 验证节点是否是 rootNode 的后代
                            if (isDescendantOf(rootNode, node)) {
                                yield(node)
                            }
                        }
                    }
                    is FastQuery.Vid -> {
                        // vid 需要拼接包名
                        val pkg = rootNode.packageName?.toString()
                        val vid = if (pkg != null && fq.value.startsWith(pkg)) {
                            fq.value
                        } else if (pkg != null) {
                            "$pkg:id/${fq.value}"
                        } else {
                            fq.value
                        }
                        val nodes = rootNode.findAccessibilityNodeInfosByViewId(vid)
                        DebugLogger.d(TAG, "fastQuery vid=$vid, 找到 ${nodes.size} 个节点")
                        for (node in nodes) {
                            // 验证节点是否是 rootNode 的后代
                            if (isDescendantOf(rootNode, node)) {
                                DebugLogger.d(TAG, "  ✓ 节点是后代，添加到候选: ${node.viewIdResourceName}")
                                yield(node)
                            } else {
                                DebugLogger.d(TAG, "  ✗ 节点不是后代，跳过: ${node.viewIdResourceName}")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查节点是否是另一个节点的后代
     */
    private fun isDescendantOf(ancestor: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Boolean {
        if (ancestor == node) return false
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            val parent = current.parent
            if (parent == ancestor) return true
            // 防止无限循环（虽然理论上不应该发生）
            if (parent == node) break
            current = parent
        }
        return false
    }

    /**
     * 获取节点属性
     */
    private fun getNodeAttr(node: AccessibilityNodeInfo, name: String): Any? {
        return when (name) {
            "id" -> node.viewIdResourceName
            "vid" -> {
                // vid 处理：提取 viewIdResourceName 中的简短形式
                val id = node.viewIdResourceName ?: return null
                val pkg = node.packageName?.toString()
                if (pkg != null && id.startsWith(pkg) && id.contains(":id/")) {
                    id.substringAfterLast(":id/")
                } else {
                    id
                }
            }

            "name", "class", "className" -> node.className?.toString()
            "text" -> node.text?.toString()
            "desc", "contentDescription" -> node.contentDescription?.toString()

            "clickable" -> node.isClickable
            "focusable" -> node.isFocusable
            "checkable" -> node.isCheckable
            "checked" -> {
                // 使用 compatChecked 逻辑
                when (node.isChecked) {
                    true -> true
                    false -> false
                    else -> null  // CHECKED_STATE_PARTIAL
                }
            }

            "editable" -> node.isEditable
            "longClickable" -> node.isLongClickable
            "visibleToUser" -> node.isVisibleToUser
            "scrollable" -> node.isScrollable
            "selected" -> node.isSelected
            "focused" -> node.isFocused

            "childCount" -> node.childCount

            "index" -> getNodeIndex(node)

            else -> null
        }
    }

    /**
     * 获取节点在父节点中的索引
     */
    private fun getNodeIndex(node: AccessibilityNodeInfo): Int {
        val parent = node.parent ?: return -1
        for (i in 0 until parent.childCount) {
            if (parent.getChild(i) == node) {
                return i
            }
        }
        return -1
    }

    private fun getNodeInvoke(node: AccessibilityNodeInfo, name: String, args: List<Any>): Any? {
        return when (name) {
            "getChild" -> {
                val index = args.firstOrNull()?.toString()?.toIntOrNull() ?: 0
                if (index in 0 until node.childCount) node.getChild(index) else null
            }
            "getParent" -> node.parent
            else -> null
        }
    }

    private fun calculateDepth(node: AccessibilityNodeInfo): Int {
        var depth = 0
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            current = current.parent
            depth++
        }
        return depth
    }
}
