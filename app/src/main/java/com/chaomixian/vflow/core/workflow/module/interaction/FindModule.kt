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
import com.chaomixian.vflow.core.module.TextVariable // 更新导入
import com.chaomixian.vflow.permissions.PermissionManager
// 项目内部的无障碍服务，避免与 Android 框架的同名类混淆
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern

/**
 * 表示屏幕上的一个UI元素。
 * @param bounds 元素在屏幕上的边界矩形。
 * @param text 元素的文本内容（可选）。
 */
@Parcelize
data class ScreenElement(
    val bounds: Rect,
    val text: String?
) : Parcelable {
    companion object {
        /** ScreenElement 类型的唯一标识符。 */
        const val TYPE_NAME = "vflow.type.screen_element"
    }
}

/**
 * 表示屏幕上的一个坐标点。
 * @param x x轴坐标。
 * @param y y轴坐标。
 */
@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable {
    companion object {
        /** Coordinate 类型的唯一标识符。 */
        const val TYPE_NAME = "vflow.type.coordinate"
    }
}

/**
 * “查找文本”模块。
 * 使用无障碍服务在当前屏幕上根据文本内容查找UI元素。
 * 支持多种匹配模式和输出格式。
 */
class FindTextModule : BaseModule() {
    // 模块的唯一ID
    override val id = "vflow.device.find.text"
    // 模块的元数据
    override val metadata = ActionMetadata("查找文本", "在屏幕上查找元素", R.drawable.rounded_feature_search_24, "界面交互") // 更新分类
    // 此模块需要的权限
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    // 定义匹配模式的选项
    private val matchModeOptions = listOf("完全匹配", "包含", "正则")
    // 定义输出格式的选项
    private val outputFormatOptions = listOf("元素", "坐标", "视图ID")

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "matchMode",
            name = "匹配模式",
            staticType = ParameterType.ENUM,
            defaultValue = "完全匹配",
            options = matchModeOptions,
            acceptsMagicVariable = false // 匹配模式通常是静态选择
        ),
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true, // 目标文本可以来自魔法变量
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        ),
        InputDefinition(
            id = "outputFormat",
            name = "输出格式",
            staticType = ParameterType.ENUM,
            defaultValue = "元素",
            options = outputFormatOptions,
            acceptsMagicVariable = false // 输出格式通常是静态选择
        )
    )

    /**
     * 根据选择的输出格式，动态定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val format = step?.parameters?.get("outputFormat") as? String ?: "元素"
        // 定义通用的条件分支选项（存在/不存在）
        val conditions = listOf(
            ConditionalOption("存在", "存在"),
            ConditionalOption("不存在", "不存在")
        )
        // 根据输出格式确定输出参数的类型和名称
        return when (format) {
            "坐标" -> listOf(OutputDefinition("result", "坐标", Coordinate.TYPE_NAME, conditions))
            "视图ID" -> listOf(OutputDefinition("result", "视图ID", TextVariable.TYPE_NAME, conditions))
            else -> listOf(OutputDefinition("result", "找到的元素", ScreenElement.TYPE_NAME, conditions)) // 默认为元素
        }
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
            "使用 ",
            modePill,
            " 模式查找文本 ",
            targetPill,
            " 并输出 ",
            formatPill
        )
    }

    /**
     * 执行查找文本的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取无障碍服务实例
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "查找文本需要无障碍服务，但该服务当前未运行。")

        // 获取目标文本，优先从魔法变量，其次从静态变量
        val targetText = (context.magicVariables["targetText"] as? TextVariable)?.value
            ?: context.variables["targetText"] as? String

        // 校验目标文本是否为空
        if (targetText.isNullOrBlank()) {
            return ExecutionResult.Failure("参数缺失", "目标文本不能为空。")
        }
        // 获取当前活动窗口的根节点
        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure("服务错误", "无法获取到当前窗口的根节点。")

        try {
            val matchModeStr = context.variables["matchMode"] as? String ?: "完全匹配"
            onProgress(ProgressUpdate("正在以 [${matchModeStr}] 模式查找文本: '$targetText'"))
            // 查找匹配文本的节点列表
            val nodes = findNodesByText(rootNode, targetText, matchModeStr)

            if (nodes.isEmpty()) {
                onProgress(ProgressUpdate("未在屏幕上找到匹配的文本。"))
                return ExecutionResult.Success() // 未找到则成功返回，但不包含 result 输出
            }

            // 如果找到多个节点，默认使用第一个
            val foundNode = nodes.first()
            val bounds = Rect() // 用于存储找到节点的边界
            foundNode.getBoundsInScreen(bounds)

            // 根据选择的输出格式，构造输出结果
            val outputFormat = context.variables["outputFormat"] as? String ?: "元素"
            val output: Parcelable = when (outputFormat) {
                "坐标" -> Coordinate(bounds.centerX(), bounds.centerY()) // 输出中心坐标
                "视图ID" -> TextVariable(foundNode.viewIdResourceName ?: "") // 输出视图ID，如果不存在则为空字符串
                else -> ScreenElement( // 默认输出屏幕元素对象
                    bounds = bounds,
                    text = foundNode.text?.toString() ?: foundNode.contentDescription?.toString()
                )
            }

            // 回收所有在 findNodesByText 中获取的节点副本
            nodes.forEach { it.recycle() }

            return ExecutionResult.Success(outputs = mapOf("result" to output))
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
                    "包含" -> source.contains(text, ignoreCase = true)
                    "正则" -> try { Pattern.compile(text).matcher(source).find() } catch (e: Exception) { false }
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
}