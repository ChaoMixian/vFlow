// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/FindElementModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinateRegion
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern

/**
 * 查找屏幕控件模块
 * 支持通过多种属性组合查找控件
 */
class FindElementModule : BaseModule() {

    override val id = "vflow.interaction.find_element"
    override val metadata = ActionMetadata(
        name = "查找控件",  // Fallback
        nameStringRes = R.string.module_vflow_interaction_find_element_name,
        description = "通过文本、ID、区域等属性查找屏幕控件，支持多属性组合过滤",  // Fallback
        descriptionStringRes = R.string.module_vflow_interaction_find_element_desc,
        iconRes = R.drawable.rounded_search_24,
        category = "界面交互"
    )

    companion object {
        val matchModeOptions = listOf("包含", "完全匹配", "开头是", "正则表达式")
        val resultSelectionOptions = listOf("第一个", "最后一个", "最接近中心", "最接近顶部")
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        // === 文本匹配 ===
        InputDefinition("text", "控件文本", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = false, hint = "如：设置"),
        InputDefinition("text_match_mode", "文本匹配模式", ParameterType.ENUM, "包含", options = FindElementModule.matchModeOptions, acceptsMagicVariable = false, isFolded = true),

        // === ID 匹配 ===
        InputDefinition("view_id", "控件ID", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = false, hint = "如：com.android:id/button"),
        InputDefinition("id_match_mode", "ID匹配模式", ParameterType.ENUM, "包含", options = matchModeOptions, acceptsMagicVariable = false, isFolded = true),

        // === 类名匹配 ===
        InputDefinition("class_name", "类名", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = false, hint = "如：android.widget.Button"),
        InputDefinition("class_match_mode", "类名匹配模式", ParameterType.ENUM, "完全匹配", options = matchModeOptions, acceptsMagicVariable = false, isFolded = true),

        // === 区域限制 ===
        InputDefinition("search_region", "搜索区域", ParameterType.ANY, "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE_REGION.id), hint = "如：100,200,500,600 留空则搜索全屏"),

        // === 输出过滤 ===
        InputDefinition("only_leaf_nodes", "仅保留叶子节点", ParameterType.BOOLEAN, false, acceptsMagicVariable = false, hint = "只返回没有子节点匹配的控件"),

        // === 交互状态过滤 ===
        InputDefinition("clickable", "可点击", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("enabled", "已启用", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("checkable", "可勾选", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("checked", "已勾选", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("editable", "可编辑", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("focusable", "可聚焦", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),
        InputDefinition("scrollable", "可滚动", ParameterType.STRING, "", acceptsMagicVariable = true, hint = "留空、true 或 false", isFolded = true),

        // === 其他选项 ===
        InputDefinition("depth_limit", "最大深度", ParameterType.NUMBER, 50, acceptsMagicVariable = true, hint = "默认 50，范围 1-200", isFolded = true),
        InputDefinition("result_selection", "结果选择", ParameterType.ENUM, "第一个", options = resultSelectionOptions, acceptsMagicVariable = false, isFolded = true),
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        return getInputs()
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("found", "是否找到", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("count", "找到数量", VTypeRegistry.NUMBER.id),
        OutputDefinition("element", "选中的控件", VTypeRegistry.SCREEN_ELEMENT.id),
        OutputDefinition("all_elements", "所有控件", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.SCREEN_ELEMENT.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val text = step.parameters["text"]?.toString() ?: ""
        val viewId = step.parameters["view_id"]?.toString() ?: ""
        val className = step.parameters["class_name"]?.toString() ?: ""

        // 构建条件列表，使用 PillUtil.createPillFromParam 创建 pill
        val parts = mutableListOf<Any>()
        parts.add(context.getString(R.string.summary_vflow_interaction_find_element_prefix))

        // 如果有查找条件，添加到摘要中
        val hasCondition = text.isNotEmpty() || viewId.isNotEmpty() || className.isNotEmpty()

        if (hasCondition) {
            parts.add(": ")

            val conditions = mutableListOf<Any>()
            var first = true

            if (text.isNotEmpty()) {
                val textPill = PillUtil.createPillFromParam(
                    step.parameters["text"],
                    getInputs().find { it.id == "text" }
                )
                conditions.add(context.getString(R.string.summary_vflow_interaction_find_element_text_equals))
                conditions.add(textPill)
                first = false
            }

            if (viewId.isNotEmpty()) {
                if (!first) conditions.add(", ")
                val idPill = PillUtil.createPillFromParam(
                    step.parameters["view_id"],
                    getInputs().find { it.id == "view_id" }
                )
                conditions.add(context.getString(R.string.summary_vflow_interaction_find_element_id_equals))
                conditions.add(idPill)
                first = false
            }

            if (className.isNotEmpty()) {
                if (!first) conditions.add(", ")
                val classPill = PillUtil.createPillFromParam(
                    step.parameters["class_name"],
                    getInputs().find { it.id == "class_name" }
                )
                conditions.add(context.getString(R.string.summary_vflow_interaction_find_element_class_equals))
                conditions.add(classPill)
            }

            parts.addAll(conditions)
        }

        return PillUtil.buildSpannable(context, *parts.toTypedArray())
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取服务
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("服务不可用", "无障碍服务未启动")

        // === 解析参数 ===
        val text = context.getVariableAsString("text", "")
        val textMatchMode = context.getVariableAsString("text_match_mode", "包含")

        val viewId = context.getVariableAsString("view_id", "")
        val idMatchMode = context.getVariableAsString("id_match_mode", "包含")

        val className = context.getVariableAsString("class_name", "")
        val classMatchMode = context.getVariableAsString("class_match_mode", "完全匹配")

        // 现在 variables 是 Map<String, VObject>，使用 getVariable 获取并检查类型
        val searchRegion = parseSearchRegion(context.getVariable("search_region"))

        val clickable = parseBooleanFilter(context.getVariableAsString("clickable", ""))
        val enabled = parseBooleanFilter(context.getVariableAsString("enabled", ""))
        val checkable = parseBooleanFilter(context.getVariableAsString("checkable", ""))
        val checked = parseBooleanFilter(context.getVariableAsString("checked", ""))
        val editable = parseBooleanFilter(context.getVariableAsString("editable", ""))
        val focusable = parseBooleanFilter(context.getVariableAsString("focusable", ""))
        val scrollable = parseBooleanFilter(context.getVariableAsString("scrollable", ""))

        // 使用 getVariableAsInt 获取数字类型
        val depthLimit = context.getVariableAsInt("depth_limit") ?: 50
        val resultSelection = context.getVariableAsString("result_selection", "第一个")
        val onlyLeafNodes = context.getVariableAsBoolean("only_leaf_nodes") ?: false

        // === 检查是否至少有一个查找条件 ===
        // 注意：search_region 的默认值是空字符串，不是 null，所以需要特别处理
        val hasTextCondition = !text.isNullOrEmpty()
        val hasIdCondition = !viewId.isNullOrEmpty()
        val hasClassCondition = !className.isNullOrEmpty()
        val hasRegionCondition = searchRegion != null
        val hasBooleanCondition = clickable != null || enabled != null ||
                checkable != null || checked != null || editable != null ||
                focusable != null || scrollable != null

        // 检查是否有任何非空、非默认值的参数
        // 排除有默认值的参数（depth_limit, only_leaf_nodes, text_match_mode, id_match_mode, class_match_mode, result_selection）
        val hasValidParameter = context.variables.entries.any { (key, value) ->
            value !is com.chaomixian.vflow.core.types.basic.VNull &&
            key !in setOf("depth_limit", "only_leaf_nodes", "text_match_mode", "id_match_mode", "class_match_mode", "result_selection")
        }

        if (!hasTextCondition && !hasIdCondition && !hasClassCondition &&
            !hasRegionCondition && !hasBooleanCondition && !hasValidParameter) {
            return ExecutionResult.Failure("参数错误", "请至少设置一个查找条件")
        }

        // === 验证正则表达式 ===
        if (textMatchMode == "正则表达式" && !text.isNullOrEmpty()) {
            try {
                Pattern.compile(text, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                return ExecutionResult.Failure(
                    "正则表达式错误",
                    "文本匹配的正则表达式无效: ${e.localizedMessage}",
                    partialOutputs = mapOf(
                        "success" to VBoolean(false),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),
                        "element" to VNull,
                        "all_elements" to emptyList<VScreenElement>()
                    )
                )
            }
        }

        if (idMatchMode == "正则表达式" && !viewId.isNullOrEmpty()) {
            try {
                Pattern.compile(viewId, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                return ExecutionResult.Failure(
                    "正则表达式错误",
                    "ID匹配的正则表达式无效: ${e.localizedMessage}",
                    partialOutputs = mapOf(
                        "success" to VBoolean(false),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),
                        "element" to VNull,
                        "all_elements" to emptyList<VScreenElement>()
                    )
                )
            }
        }

        if (classMatchMode == "正则表达式" && !className.isNullOrEmpty()) {
            try {
                Pattern.compile(className, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                return ExecutionResult.Failure(
                    "正则表达式错误",
                    "类名匹配的正则表达式无效: ${e.localizedMessage}",
                    partialOutputs = mapOf(
                        "success" to VBoolean(false),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),
                        "element" to VNull,
                        "all_elements" to emptyList<VScreenElement>()
                    )
                )
            }
        }

        onProgress(ProgressUpdate("正在查找控件..."))

        // === 查找控件 ===
        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure("无法访问屏幕", "无法获取当前界面")

        try {
            val allElements = mutableListOf<VScreenElement>()

            // 遍历无障碍树
            fun traverseNode(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > depthLimit) return

                // 检查当前节点是否匹配所有条件
                if (matchesAllConditions(
                        node,
                        text, textMatchMode,
                        viewId, idMatchMode,
                        className, classMatchMode,
                        searchRegion,
                        clickable, enabled, checkable, checked,
                        editable, focusable, scrollable
                    )) {
                    allElements.add(VScreenElement.fromAccessibilityNode(node, depth))
                }

                // 递归遍历子节点
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    traverseNode(child, depth + 1)
                    child.recycle()
                }
            }

            traverseNode(rootNode, 0)

            // === 过滤叶子节点 ===
            val finalElements = if (onlyLeafNodes) {
                // 过滤掉有任何子节点也在匹配列表中的节点
                allElements.filterNot { parent ->
                    allElements.any { child ->
                        // 判断 child 是否是 parent 的子孙节点
                        child.depth > parent.depth &&
                        parent.bounds.contains(child.bounds)
                    }
                }
            } else {
                allElements
            }

            // === 检查结果 ===
            if (finalElements.isEmpty()) {
                return ExecutionResult.Failure(
                    "未找到控件",
                    "没有匹配的控件",
                    partialOutputs = mapOf(
                        "success" to VBoolean(false),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),
                        "element" to VNull,
                        "all_elements" to emptyList<VScreenElement>()
                    )
                )
            }

            // === 选择结果 ===
            val selectedElement = when (resultSelection) {
                "最后一个" -> finalElements.last()
                "最接近中心" -> {
                    val screenBounds = Rect()
                    rootNode.getBoundsInScreen(screenBounds)
                    val centerX = searchRegion?.centerX ?: screenBounds.centerX()
                    val centerY = searchRegion?.centerY ?: screenBounds.centerY()

                    finalElements.minByOrNull { element ->
                        val dx = element.centerX - centerX
                        val dy = element.centerY - centerY
                        dx * dx + dy * dy
                    } ?: finalElements.first()
                }
                "最接近顶部" -> finalElements.minByOrNull { it.bounds.top } ?: finalElements.first()
                else -> finalElements.first() // "第一个"
            }

            onProgress(ProgressUpdate("找到 ${finalElements.size} 个控件"))

            return ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "found" to VBoolean(true),
                "count" to VNumber(finalElements.size.toDouble()),
                "element" to selectedElement,
                "all_elements" to finalElements
            ))

        } catch (e: Exception) {
            return ExecutionResult.Failure("查找失败", e.localizedMessage ?: "发生了未知错误")
        }
    }

    /**
     * 检查节点是否匹配所有条件
     */
    private fun matchesAllConditions(
        node: AccessibilityNodeInfo,
        text: String?, textMatchMode: String,
        viewId: String?, idMatchMode: String,
        className: String?, classMatchMode: String,
        searchRegion: com.chaomixian.vflow.core.types.complex.VCoordinateRegion?,
        clickable: Boolean?, enabled: Boolean?,
        checkable: Boolean?, checked: Boolean?,
        editable: Boolean?, focusable: Boolean?,
        scrollable: Boolean?
    ): Boolean {
        // === 文本匹配 ===
        if (!text.isNullOrEmpty()) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
            if (nodeText == null || !matchesString(nodeText, text, textMatchMode)) {
                return false
            }
        }

        // === ID 匹配 ===
        if (!viewId.isNullOrEmpty()) {
            val nodeId = node.viewIdResourceName
            if (nodeId == null || !matchesString(nodeId, viewId, idMatchMode)) {
                return false
            }
        }

        // === 类名匹配 ===
        if (!className.isNullOrEmpty()) {
            val nodeClassName = node.className?.toString()
            if (nodeClassName == null || !matchesString(nodeClassName, className, classMatchMode)) {
                return false
            }
        }

        // === 区域匹配 ===
        if (searchRegion != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // 检查控件是否完全在搜索区域内
            if (bounds.left < searchRegion.left || bounds.right > searchRegion.right ||
                bounds.top < searchRegion.top || bounds.bottom > searchRegion.bottom) {
                return false
            }
        }

        // === 交互状态匹配 ===
        if (clickable != null && node.isClickable != clickable) return false
        if (enabled != null && node.isEnabled != enabled) return false
        if (checkable != null && node.isCheckable != checkable) return false
        // 只有当控件可勾选时，才检查其checked状态
        if (checked != null && node.isCheckable && node.isChecked != checked) return false
        if (editable != null && node.isEditable != editable) return false
        if (focusable != null && node.isFocusable != focusable) return false
        if (scrollable != null && node.isScrollable != scrollable) return false

        return true
    }

    /**
     * 字符串匹配
     */
    private fun matchesString(text: String, pattern: String, mode: String): Boolean {
        return when (mode) {
            "完全匹配" -> text == pattern
            "包含" -> text.contains(pattern, ignoreCase = true)
            "开头是" -> text.startsWith(pattern, ignoreCase = true)
            "正则表达式" -> {
                try {
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()
                } catch (e: Exception) {
                    false
                }
            }
            else -> text.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * 解析布尔过滤器
     * "" -> null (不限制)
     * "true"/"1" -> true
     * "false"/"0" -> false
     */
    private fun parseBooleanFilter(value: String?): Boolean? {
        if (value.isNullOrEmpty()) return null
        return when (value.lowercase()) {
            "true", "1", "是" -> true
            "false", "0", "否" -> false
            else -> null
        }
    }

    /**
     * 解析搜索区域
     * 支持 VCoordinateRegion 对象或 "left,top,right,bottom" 格式的字符串
     */
    private fun parseSearchRegion(value: com.chaomixian.vflow.core.types.VObject): VCoordinateRegion? {
        return when (value) {
            is VCoordinateRegion -> value
            is VString -> {
                // 尝试解析 "left,top,right,bottom" 格式
                val parts = value.raw.split(",")
                if (parts.size == 4) {
                    try {
                        val left = parts[0].trim().toInt()
                        val top = parts[1].trim().toInt()
                        val right = parts[2].trim().toInt()
                        val bottom = parts[3].trim().toInt()
                        VCoordinateRegion(left, top, right, bottom)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
