// 文件: main/java/com/chaomixian/vflow/core/workflow/module/device/FindModule.kt

package com.chaomixian.vflow.modules.device

import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.TextVariable
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern

@Parcelize
data class ScreenElement(
    val bounds: Rect,
    val text: String?
) : Parcelable {
    companion object {
        const val TYPE_NAME = "vflow.type.screen_element"
    }
}

@Parcelize
data class Coordinate(val x: Int, val y: Int) : Parcelable {
    companion object {
        const val TYPE_NAME = "vflow.type.coordinate"
    }
}

class FindTextModule : BaseModule() {
    override val id = "vflow.device.find.text"
    override val metadata = ActionMetadata("查找文本", "在屏幕上查找元素", R.drawable.ic_node_search, "设备")
    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    private val matchModeOptions = listOf("完全匹配", "包含", "正则")
    private val outputFormatOptions = listOf("元素", "坐标", "视图ID")

    // --- 核心修改：移除了 uiProvider 的重写 ---

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "matchMode",
            name = "匹配模式",
            staticType = ParameterType.ENUM, // 标准的 ENUM 类型
            defaultValue = "完全匹配",
            options = matchModeOptions,
            acceptsMagicVariable = false // 不接受变量，通用编辑器会正确处理
        ),
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        ),
        InputDefinition(
            id = "outputFormat",
            name = "输出格式",
            staticType = ParameterType.ENUM, // 标准的 ENUM 类型
            defaultValue = "元素",
            options = outputFormatOptions,
            acceptsMagicVariable = false // 不接受变量，通用编辑器会正确处理
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val format = step?.parameters?.get("outputFormat") as? String ?: "元素"
        val conditions = listOf(
            ConditionalOption("存在", "存在"),
            ConditionalOption("不存在", "不存在")
        )
        return when (format) {
            "坐标" -> listOf(OutputDefinition("result", "坐标", Coordinate.TYPE_NAME, conditions))
            "视图ID" -> listOf(OutputDefinition("result", "视图ID", TextVariable.TYPE_NAME, conditions))
            else -> listOf(OutputDefinition("result", "找到的元素", ScreenElement.TYPE_NAME, conditions))
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["matchMode"]?.toString() ?: "完全匹配"
        val target = step.parameters["targetText"]?.toString() ?: "..."
        val format = step.parameters["outputFormat"]?.toString() ?: "元素"
        val isVariable = target.startsWith("{{")

        return PillUtil.buildSpannable(
            context,
            "使用 ",
            PillUtil.Pill(mode, false, parameterId = "matchMode", isModuleOption = true),
            " 模式查找文本 ",
            PillUtil.Pill(target, isVariable, parameterId = "targetText"),
            " 并输出 ",
            PillUtil.Pill(format, false, parameterId = "outputFormat", isModuleOption = true)
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val service = context.services.get(AccessibilityService::class)
            ?: return ExecutionResult.Failure("服务未运行", "查找文本需要无障碍服务，但该服务当前未运行。")

        val targetText = (context.magicVariables["targetText"] as? TextVariable)?.value
            ?: context.variables["targetText"] as? String

        if (targetText.isNullOrBlank()) {
            return ExecutionResult.Failure("参数缺失", "目标文本不能为空。")
        }
        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure("服务错误", "无法获取到当前窗口的根节点。")

        try {
            val matchModeStr = context.variables["matchMode"] as? String ?: "完全匹配"
            onProgress(ProgressUpdate("正在以 [${matchModeStr}] 模式查找文本: '$targetText'"))
            val nodes = findNodesByText(rootNode, targetText, matchModeStr)

            if (nodes.isEmpty()) {
                onProgress(ProgressUpdate("未在屏幕上找到匹配的文本。"))
                return ExecutionResult.Success()
            }

            val foundNode = nodes.first()
            val bounds = Rect()
            foundNode.getBoundsInScreen(bounds)

            val outputFormat = context.variables["outputFormat"] as? String ?: "元素"
            val output: Parcelable = when (outputFormat) {
                "坐标" -> Coordinate(bounds.centerX(), bounds.centerY())
                "视图ID" -> TextVariable(foundNode.viewIdResourceName ?: "")
                else -> ScreenElement(
                    bounds = bounds,
                    text = foundNode.text?.toString() ?: foundNode.contentDescription?.toString()
                )
            }

            nodes.forEach { it.recycle() }

            return ExecutionResult.Success(outputs = mapOf("result" to output))
        } catch (e: Exception) {
            return ExecutionResult.Failure("执行异常", e.localizedMessage ?: "发生了未知错误")
        } finally {
            rootNode.recycle()
        }
    }

    private fun findNodesByText(rootNode: AccessibilityNodeInfo, text: String, matchModeStr: String): List<AccessibilityNodeInfo> {
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(rootNode))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()

            val checkMatch = { source: String ->
                when (matchModeStr) {
                    "包含" -> source.contains(text, ignoreCase = true)
                    "正则" -> try { Pattern.compile(text).matcher(source).find() } catch (e: Exception) { false }
                    else -> source == text
                }
            }

            if (nodeText != null && checkMatch(nodeText)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
            } else if (nodeDesc != null && checkMatch(nodeDesc)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }
        return matchedNodes
    }
}