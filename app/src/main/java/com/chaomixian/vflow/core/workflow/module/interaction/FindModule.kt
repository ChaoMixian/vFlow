// 文件：FindModule.kt
// 描述：定义了屏幕查找相关的模块（FindTextModule）以及支持的数据结构（ScreenElement, Coordinate）。

package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.permissions.PermissionManager
// 项目内部的无障碍服务，避免与 Android 框架的同名类混淆
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern

/**
 * "查找文本"模块。
 * 使用无障碍服务在当前屏幕上根据文本内容查找UI元素。
 * 支持多种匹配模式和输出格式。
 */
class FindTextModule : BaseModule() {
    companion object {
        private const val MATCH_EXACT = "exact"
        private const val MATCH_CONTAINS = "contains"
        private const val MATCH_REGEX = "regex"
        private const val OUTPUT_ELEMENT = "element"
        private const val OUTPUT_COORDINATE = "coordinate"
        private const val OUTPUT_VIEW_ID = "view_id"
    }
    // 模块的唯一ID
    override val id = "vflow.device.find.text"
    // 模块的元数据
    override val metadata = ActionMetadata(
        name = "查找文本",
        description = "在屏幕上查找元素",
        nameStringRes = R.string.module_vflow_device_find_text_name,
        descriptionStringRes = R.string.module_vflow_device_find_text_desc,
        iconRes = R.drawable.rounded_feature_search_24,
        category = "界面交互",
        categoryId = "interaction"
    ) // 更新分类
    // 此模块需要的权限
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 定义匹配模式的选项
    private val matchModeOptions = listOf(MATCH_EXACT, MATCH_CONTAINS, MATCH_REGEX)
    // 定义输出格式的选项
    private val outputFormatOptions = listOf(OUTPUT_ELEMENT, OUTPUT_COORDINATE, OUTPUT_VIEW_ID)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "matchMode",
            name = "匹配模式",
            nameStringRes = R.string.param_vflow_device_find_text_matchMode_name,
            staticType = ParameterType.ENUM,
            defaultValue = MATCH_EXACT,
            options = matchModeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_device_find_text_match_exact,
                R.string.option_vflow_device_find_text_match_contains,
                R.string.option_vflow_device_find_text_match_regex
            ),
            legacyValueMap = mapOf(
                "完全匹配" to MATCH_EXACT,
                "Exact Match" to MATCH_EXACT,
                "包含" to MATCH_CONTAINS,
                "Contains" to MATCH_CONTAINS,
                "正则" to MATCH_REGEX,
                "Regex" to MATCH_REGEX
            ),
            acceptsMagicVariable = false // 匹配模式通常是静态选择
        ),
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            nameStringRes = R.string.param_vflow_device_find_text_targetText_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true, // 目标文本可以来自魔法变量
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        ),
        InputDefinition(
            id = "outputFormat",
            name = "输出格式",
            nameStringRes = R.string.param_vflow_device_find_text_outputFormat_name,
            staticType = ParameterType.ENUM,
            defaultValue = OUTPUT_ELEMENT,
            options = outputFormatOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_device_find_text_output_element,
                R.string.option_vflow_device_find_text_output_coordinate,
                R.string.option_vflow_device_find_text_output_viewid
            ),
            legacyValueMap = mapOf(
                "元素" to OUTPUT_ELEMENT,
                "Element" to OUTPUT_ELEMENT,
                "坐标" to OUTPUT_COORDINATE,
                "Coordinate" to OUTPUT_COORDINATE,
                "视图ID" to OUTPUT_VIEW_ID,
                "View ID" to OUTPUT_VIEW_ID
            ),
            acceptsMagicVariable = false // 输出格式通常是静态选择
        )
    )

    /**
     * 根据选择的输出格式，动态定义模块的输出参数。
     * 新增“数量”和“所有结果”输出，并将原始结果重命名为“第一个结果”。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val outputFormatInput = getInputs().first { it.id == "outputFormat" }
        val rawFormat = step?.parameters?.get("outputFormat") as? String ?: OUTPUT_ELEMENT
        val format = outputFormatInput.normalizeEnumValue(rawFormat) ?: rawFormat
        // 定义通用的条件分支选项（存在/不存在）
        val conditions = listOf(
            ConditionalOption("存在", "存在"),
            ConditionalOption("不存在", "不存在")
        )

        // 根据输出格式确定主结果的类型
        val resultTypeName = when (format) {
            OUTPUT_COORDINATE -> VTypeRegistry.COORDINATE.id
            OUTPUT_VIEW_ID -> VTypeRegistry.STRING.id
            else -> VTypeRegistry.SCREEN_ELEMENT.id  // 使用新的 VScreenElement 类型 ID
        }

        // 定义所有输出
        return listOf(
            OutputDefinition("first_result", "第一个结果", resultTypeName, conditions, nameStringRes = R.string.output_vflow_device_find_text_first_result_name),
            OutputDefinition("all_results", "所有结果", VTypeRegistry.LIST.id, conditions, nameStringRes = R.string.output_vflow_device_find_text_all_results_name),
            OutputDefinition("count", "结果数量", VTypeRegistry.NUMBER.id, conditions, nameStringRes = R.string.output_vflow_device_find_text_count_name)
        )
    }


    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如：“使用 [包含] 模式查找文本 [搜索词] 并输出 [坐标]”
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val modePill = PillUtil.createPillFromParam(
            step.parameters["matchMode"],
            inputs.find { it.id == "matchMode" },
            isModuleOption = true
        )
        val targetPill = PillUtil.createPillFromParam(
            step.parameters["targetText"],
            inputs.find { it.id == "targetText" }
        )
        val formatPill = PillUtil.createPillFromParam(
            step.parameters["outputFormat"],
            inputs.find { it.id == "outputFormat" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_find_text_prefix),
            modePill,
            context.getString(R.string.summary_find_text_middle),
            targetPill,
            context.getString(R.string.summary_find_text_suffix),
            formatPill
        )
    }

    /**
     * 执行查找文本的核心逻辑。
     * 现在会同时输出第一个结果、所有结果的列表以及结果数量。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取无障碍服务实例
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "查找文本需要无障碍服务，但该服务当前未运行。")

        // 获取目标文本，优先从魔法变量，其次从静态变量
        val targetText = context.getVariableAsString("targetText")

        // 校验目标文本是否为空
        if (targetText.isNullOrBlank()) {
            return ExecutionResult.Failure("参数缺失", "目标文本不能为空。")
        }
        // 获取当前活动窗口的根节点
        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure("服务错误", "无法获取到当前窗口的根节点。")

        try {
            val inputsById = getInputs().associateBy { it.id }
            val rawMatchMode = context.getVariableAsString("matchMode", MATCH_EXACT)
            val matchModeStr = inputsById["matchMode"]?.normalizeEnumValue(rawMatchMode) ?: rawMatchMode
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_find_text_searching, getMatchModeDisplayName(matchModeStr), targetText)))

            val nodes = findNodesByText(rootNode, targetText, matchModeStr)
            val count = nodes.size
            val rawOutputFormat = context.getVariableAsString("outputFormat", OUTPUT_ELEMENT)
            val outputFormat = inputsById["outputFormat"]?.normalizeEnumValue(rawOutputFormat) ?: rawOutputFormat
            val outputs = mutableMapOf<String, Any?>()

            outputs["count"] = VNumber(count.toDouble())

            if (nodes.isEmpty()) {
                // 返回 Failure，让用户通过"异常处理策略"选择行为
                // 用户可以选择：重试（UI 可能还在加载）、忽略错误继续、停止工作流
                return ExecutionResult.Failure(
                    "未找到文本",
                    "未在屏幕上找到匹配的文本: '$targetText' (匹配模式: $matchModeStr)",
                    // 提供 partialOutputs，让"跳过此步骤继续"时有语义化的默认值
                    partialOutputs = mapOf(
                        "count" to VNumber(0.0),              // 找到 0 个
                        "all_results" to emptyList<Any>(),   // 空列表
                        "first_result" to VNull             // 没有"第一个"
                    )
                )
            }

            // 转换所有节点为所需的输出格式（使用新的 VObject 类型）
            val allResultsList = nodes.map { node ->
                when (outputFormat) {
                    OUTPUT_COORDINATE -> {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        VCoordinate(bounds.centerX(), bounds.centerY())
                    }
                    OUTPUT_VIEW_ID -> VString(node.viewIdResourceName ?: "")
                    else -> VScreenElement.fromAccessibilityNode(node)
                }
            }

            outputs["all_results"] = allResultsList
            outputs["first_result"] = allResultsList.firstOrNull() ?: VNull  // 使用安全访问

            // 回收所有在 findNodesByText 中获取的节点副本
            nodes.forEach { it.recycle() }

            return ExecutionResult.Success(outputs)
        } catch (e: Exception) {
            return ExecutionResult.Failure("执行异常", e.localizedMessage ?: "发生了未知错误")
        } finally {
            // 确保回收通过 rootInActiveWindow 获取的根节点
            rootNode.recycle()
        }
    }


    /**
     * 根据文本内容和匹配模式在给定的根节点下查找 AccessibilityNodeInfo 节点列表。
     * @param rootNode 开始搜索的根节点。
     * @param text 要查找的文本。
     * @param matchModeStr 匹配模式 (\"完全匹配\", \"包含\", \"正则\")。
     * @return 匹配到的节点列表。返回的列表中的节点是副本，需要调用者回收。
     */
    private fun findNodesByText(rootNode: AccessibilityNodeInfo, text: String, matchModeStr: String): List<AccessibilityNodeInfo> {
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>() // 用于广度优先搜索的队列

        // 将根节点（的副本）加入队列开始遍历，确保原始 rootNode 不被意外回收
        queue.add(AccessibilityNodeInfo.obtain(rootNode))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst() // 取出当前待处理节点（此为副本，使用后需回收）
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()

            // 定义文本匹配检查的局部函数
            val checkMatch = { source: String ->
                when (matchModeStr) {
                    MATCH_CONTAINS -> source.contains(text, ignoreCase = true)
                    MATCH_REGEX -> try { Pattern.compile(text).matcher(source).find() } catch (e: Exception) { false }
                    else -> source == text // 默认为 \"完全匹配\"
                }
            }

            var matchedThisNode = false // 标记当前节点是否已因文本匹配而被添加
            // 检查节点文本是否匹配
            if (nodeText != null && checkMatch(nodeText)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node)) // 添加节点的副本到结果列表
                matchedThisNode = true
            }
            // 如果文本未匹配，则检查节点的内容描述是否匹配
            if (!matchedThisNode && nodeDesc != null && checkMatch(nodeDesc)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node)) // 添加节点的副本到结果列表
            }

            // 将子节点加入队列继续搜索
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    // 直接添加子节点，它们在从队列中取出并处理时会被创建副本并回收
                    queue.add(child)
                }
            }
            node.recycle() // 回收当前处理的节点副本
        }
        return matchedNodes // 返回包含所有匹配节点副本的列表
    }

    private fun getMatchModeDisplayName(matchMode: String): String {
        return when (matchMode) {
            MATCH_CONTAINS -> appContext.getString(R.string.option_vflow_device_find_text_match_contains)
            MATCH_REGEX -> appContext.getString(R.string.option_vflow_device_find_text_match_regex)
            else -> appContext.getString(R.string.option_vflow_device_find_text_match_exact)
        }
    }
}
