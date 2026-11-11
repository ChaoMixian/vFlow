package com.chaomixian.vflow.api.mcp

import android.content.Context
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.recording.RecordedUiNode
import com.chaomixian.vflow.core.recording.RecordingNodeUtils
import com.chaomixian.vflow.core.recording.UiRecording
import com.chaomixian.vflow.core.recording.UiRecordingEvent
import com.chaomixian.vflow.core.recording.UiRecordingSegment
import com.chaomixian.vflow.core.recording.UiRecordingStore
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.serialization.VObjectGsonAdapter
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowReentryBehavior
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

/**
 * 从 UI 录制生成工作流的结果。
 */
internal data class UiRecordingBuildResult(
    val workflow: Workflow?,
    val json: String,
    val saved: Boolean,
    val filePath: String?,
    val stepCount: Int,
    val skipped: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val screenshotDir: String? = null,
    val error: String? = null,
    val saveWarning: String? = null,
) {
    val workflowId: String get() = workflow?.id.orEmpty()
    val skippedCount: Int get() = skipped.size
}

/**
 * 把 [UiRecording] 还原成 vFlow 工作流。
 *
 * 还原策略：
 * 1. 优先按录制事件（CLICK / TEXT_CHANGED / SCROLL）生成步骤；
 * 2. 没有事件时，对比相邻界面段落的节点差异，推断点击与输入；
 * 3. 选择器优先使用录制时生成的 GKD 表达式，缺失时用 viewId、类名、文本、
 *    描述、深度、边界等所有可用信息组合，并始终保留坐标兜底；
 * 4. 每个步骤都合并模块默认参数，保证生成的 JSON 可以直接被 vFlow 导入执行。
 */
internal class UiRecordingWorkflowBuilder(private val context: Context) {

    private companion object {
        const val MODULE_SELECTOR = "vflow.interaction.ui_selector"
        const val MODULE_CLICK = "vflow.device.click"
        const val MODULE_INPUT_TEXT = "vflow.interaction.input_text"
        const val MODULE_SCREEN_OPERATION = "vflow.interaction.screen_operation"
        const val MODULE_MANUAL_TRIGGER = "vflow.trigger.manual"

        /** 每个相邻界面段落只推断一次点击，优先选信息最完整的控件。 */
        const val MAX_INFERRED_CLICKS_PER_PAIR = 1
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(VObject::class.java, VObjectGsonAdapter())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    fun build(
        recording: UiRecording,
        name: String = "",
        description: String = "",
        save: Boolean = true,
        dryRun: Boolean = false,
    ): UiRecordingBuildResult {
        val session = BuildSession(recording)
        session.run()

        val steps = session.steps
        val screenshotDir = runCatching {
            if (recording.id.isBlank()) null
            else File(UiRecordingStore.recordingsDir, recording.id).absolutePath
        }.getOrNull()

        val baseName = recording.name.ifBlank { recording.id }.ifBlank { "UI 录制" }
        if (steps.isEmpty()) {
            return UiRecordingBuildResult(
                workflow = null,
                json = "",
                saved = false,
                filePath = null,
                stepCount = 0,
                skipped = session.skipped.ifEmpty {
                    listOf("录制中没有找到可还原的点击、输入或滚动操作。")
                },
                warnings = session.warnings,
                screenshotDir = screenshotDir,
                error = "未能从录制「$baseName」还原出可执行步骤。"
            )
        }

        val workflowName = name.ifBlank { "$baseName 工作流" }
        val shotCount = recording.segments.count { !it.screenshot.isNullOrBlank() }
        val autoDescription = buildString {
            append("由 UI 录制「").append(baseName).append("」自动生成")
            append("；录制 ID：").append(recording.id.ifBlank { "未知" })
            append("；交互事件 ").append(recording.events.size).append(" 个")
            append("，界面状态 ").append(recording.segments.size).append(" 个")
            append("，还原步骤 ").append(steps.size).append(" 个")
            if (!screenshotDir.isNullOrBlank()) {
                append("。截图目录：").append(screenshotDir)
                if (shotCount > 0) append("（").append(shotCount).append(" 张）")
            }
        }
        val workflowDescription = if (description.isBlank()) {
            autoDescription
        } else {
            description.trimEnd() + "\n\n" + autoDescription
        }

        val workflowId = "mcp_recording_${UUID.randomUUID()}"
        val workflow = Workflow(
            id = workflowId,
            name = workflowName,
            triggers = listOf(
                ActionStep(moduleId = MODULE_MANUAL_TRIGGER, parameters = emptyMap())
            ),
            steps = steps,
            isEnabled = true,
            description = workflowDescription,
            author = "vFlow MCP",
            tags = listOf("recording", "auto-export"),
            maxExecutionTime = 300,
            reentryBehavior = WorkflowReentryBehavior.STOP_CURRENT_AND_RUN_NEW
        )

        var saved = false
        var filePath: String? = null
        var saveWarning: String? = null
        var finalWorkflow = workflow
        var json = gson.toJson(workflow)

        if (save && !dryRun) {
            try {
                val manager = WorkflowManager(context)
                manager.saveWorkflow(workflow)
                saved = true
                finalWorkflow = manager.getWorkflow(workflow.id) ?: workflow
                json = gson.toJson(finalWorkflow)
            } catch (e: Exception) {
                saveWarning = "工作流已生成，但保存到 vFlow 失败：${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (!dryRun) {
            try {
                val exportsDir = File(StorageManager.rootDir, "exports").apply { mkdirs() }
                val safeName = workflowName
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .take(80)
                    .ifBlank { "workflow" }
                val file = File(exportsDir, "${safeName}_${workflowId.takeLast(8)}.json")
                file.writeText(json, Charsets.UTF_8)
                filePath = file.absolutePath
            } catch (e: Exception) {
                saveWarning = listOfNotNull(
                    saveWarning,
                    "JSON 文件写入失败：${e.message ?: e.javaClass.simpleName}"
                ).joinToString("；")
            }
        }

        return UiRecordingBuildResult(
            workflow = finalWorkflow,
            json = json,
            saved = saved,
            filePath = filePath,
            stepCount = steps.size,
            skipped = session.skipped,
            warnings = session.warnings,
            screenshotDir = screenshotDir,
            saveWarning = saveWarning
        )
    }

    private data class Point(val x: Int, val y: Int)

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val isValid: Boolean get() = right > left && bottom > top
    }

    private inner class BuildSession(private val recording: UiRecording) {

        val segments: List<UiRecordingSegment> = recording.segments.sortedBy { it.startMs }
        val steps = mutableListOf<ActionStep>()
        val skipped = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        private val usedSelectors = mutableSetOf<String>()
        private val usedInputKeys = mutableSetOf<String>()

        private var lastClickKey: String? = null
        private var lastActionWasClick = false

        private val inferredWidth: Int = recording.screenWidth.takeIf { it > 0 }
            ?: segments.flatMap { it.nodes }.maxOfOrNull { it.right } ?: 0
        private val inferredHeight: Int = recording.screenHeight.takeIf { it > 0 }
            ?: segments.flatMap { it.nodes }.maxOfOrNull { it.bottom } ?: 0

        fun run() {
            val events = normalizeEvents(recording.events)
            if (events.isNotEmpty()) {
                var index = 0
                while (index < events.size) {
                    index = when (events[index].type) {
                        UiRecordingEvent.CLICK -> {
                            handleClick(events[index])
                            index + 1
                        }
                        UiRecordingEvent.TEXT_CHANGED -> handleTextChanged(events, index)
                        UiRecordingEvent.SCROLL -> handleScroll(events, index)
                        else -> index + 1
                    }
                }
                if (steps.isEmpty()) {
                    skipped += "录制事件中没有可还原的点击、输入或滚动。"
                }
            } else {
                warnings += "录制文件没有交互事件，改为根据相邻界面状态差异推断操作。"
                inferFromSegments()
            }
        }

        // -----------------------------------------------------------------
        // 事件还原
        // -----------------------------------------------------------------

        private fun handleClick(event: UiRecordingEvent) {
            val segment = segmentAt(event.timestampMs)
            val node = findNode(segment, event)
            val selector = buildSelector(node, event, segment?.nodes.orEmpty(), textVolatile = false)
            val point = pointOf(node, event)
            val key = targetKey(node, event)
            val label = describe(node, event)

            if (selector == null && point == null) {
                skipped += "点击「$label」缺少选择器和有效坐标，已跳过。"
                return
            }
            emitClick(selector, point, node, key, fromInference = false)
            if (selector == null && point != null) {
                warnings += "点击「$label」没有可用的 GKD/控件信息，已改为坐标点击（${point.x},${point.y}）。"
            }
        }

        private fun handleTextChanged(events: List<UiRecordingEvent>, start: Int): Int {
            val event = events[start]
            val segment = segmentAt(event.timestampMs)
            val node = findTextTarget(segment, event)
            val key = targetKey(node, event)
            val label = describe(node, event)

            var text = event.text
            var lastTimestamp = event.timestampMs
            var index = start + 1
            // 输入过程会为每个字符触发一次 TEXT_CHANGED，合并同一控件的连续事件，只保留最终文本。
            while (index < events.size && events[index].type == UiRecordingEvent.TEXT_CHANGED) {
                val next = events[index]
                val nextSegment = segmentAt(next.timestampMs)
                val nextNode = findTextTarget(nextSegment, next)
                if (targetKey(nextNode, next) != key) break
                if (next.timestampMs - lastTimestamp > 2500L) break
                if (next.text.isNotBlank()) text = next.text
                lastTimestamp = next.timestampMs
                index++
            }

            if (node?.password == true) {
                skipped += "输入框「$label」是密码字段，已跳过明文输入。"
                return index
            }
            if (text.isBlank()) {
                skipped += "输入框「$label」的最终文本为空，已跳过。"
                return index
            }
            if (looksLikeTemplate(text)) {
                warnings += "输入框「$label」的文本包含变量语法，执行时可能被当作魔法变量解析，请检查。"
            }

            // 如果上一步已经点击了同一个控件，点击只是用于聚焦，不需要重复生成。
            if (!(lastActionWasClick && lastClickKey == key)) {
                val selector = buildSelector(node, event, segment?.nodes.orEmpty(), textVolatile = true)
                val point = pointOf(node, event)
                if (selector == null && point == null) {
                    skipped += "输入框「$label」无法定位，已跳过输入。"
                    return index
                }
                if (!emitClick(selector, point, node, key, fromInference = false)) {
                    skipped += "输入框「$label」的定位步骤生成失败，已跳过输入。"
                    return index
                }
            }

            addStep(MODULE_INPUT_TEXT, linkedMapOf("text" to text))
            lastActionWasClick = false
            lastClickKey = null
            return index
        }

        private fun handleScroll(events: List<UiRecordingEvent>, start: Int): Int {
            val event = events[start]
            var index = start + 1
            // 一次手势通常会连续触发多个 TYPE_VIEW_SCROLLED，合并为一次滑动。
            while (index < events.size && events[index].type == UiRecordingEvent.SCROLL) {
                val previous = events[index - 1]
                val next = events[index]
                if (next.timestampMs - previous.timestampMs > 350L) break
                if (!sameScrollArea(event, next)) break
                index++
            }

            val segment = segmentAt(event.timestampMs)
            val node = findNode(segment, event)
                ?: segment?.nodes?.firstOrNull { it.scrollable && contains(it.boundsOrNull(), event) }
            val swipe = swipePoints(node, event)
            if (swipe == null) {
                skipped += "滚动事件缺少可用的控件或屏幕边界，已跳过。"
                return index
            }

            addStep(
                MODULE_SCREEN_OPERATION,
                linkedMapOf(
                    "operation_type" to "swipe",
                    "target" to "${swipe.first.x},${swipe.first.y}",
                    "target_end" to "${swipe.second.x},${swipe.second.y}",
                    "duration" to 500
                )
            )
            warnings += "滚动事件未记录方向，按默认上滑生成（${swipe.first.x},${swipe.first.y} → " +
                "${swipe.second.x},${swipe.second.y}）。"
            lastActionWasClick = false
            lastClickKey = null
            return index
        }

        // -----------------------------------------------------------------
        // 无事件时按界面差异推断
        // -----------------------------------------------------------------

        private fun inferFromSegments() {
            if (segments.size < 2) {
                skipped += "录制中没有交互事件，且只有 ${segments.size} 个界面状态，无法推断操作。"
                return
            }

            for (pairIndex in 0 until segments.size - 1) {
                val previous = segments[pairIndex]
                val next = segments[pairIndex + 1]
                val pairLabel = "${pairIndex + 1}→${pairIndex + 2}"

                if (previous.packageName.isNotBlank() && next.packageName.isNotBlank() &&
                    previous.packageName != next.packageName
                ) {
                    skipped += "段 $pairLabel 跨越应用切换（${previous.packageName} → ${next.packageName}），未做推断。"
                    continue
                }

                // 1. 可编辑控件文本变化 → 输入
                previous.nodes
                    .filter { it.editable && !it.password && it.enabled }
                    .forEach { oldNode ->
                        val newNode = findMatchingNode(next.nodes, oldNode)
                        if (newNode == null || newNode.password) return@forEach
                        if (newNode.text.isBlank() || newNode.text == oldNode.text) return@forEach

                        val key = targetKey(oldNode, null)
                        if (!usedInputKeys.add(key)) return@forEach
                        val selector = buildSelector(oldNode, null, previous.nodes, textVolatile = true)
                        val point = pointOf(oldNode, null)
                        if (selector == null && point == null) {
                            skipped += "段 $pairLabel 的输入框「${describe(oldNode, null)}」缺少定位信息，未生成输入。"
                            return@forEach
                        }
                        emitClick(selector, point, oldNode, key, fromInference = true)
                        addStep(MODULE_INPUT_TEXT, linkedMapOf("text" to newNode.text))
                        lastActionWasClick = false
                        lastClickKey = null
                        if (looksLikeTemplate(newNode.text)) {
                            warnings += "段 $pairLabel 推断的输入文本包含变量语法，执行时可能被当作魔法变量解析，请检查。"
                        }
                        warnings += "段 $pairLabel 推断出输入「${newNode.text}」（输入框 ${describe(oldNode, null)}），请人工确认。"
                    }

                // 2. 消失或状态变化（selected/checked）的可点击控件 → 点击
                val candidates = previous.nodes
                    .filter { it.clickable && it.enabled && it.hasIdentity() }
                val changed = candidates
                    .filter { oldNode ->
                        val newNode = findMatchingNode(next.nodes, oldNode)
                        newNode == null ||
                            newNode.selected != oldNode.selected ||
                            newNode.checked != oldNode.checked
                    }
                    .sortedByDescending { infoScore(it) }

                val withoutIdentity = previous.nodes.count { it.clickable && it.enabled && !it.hasIdentity() }
                if (withoutIdentity > 0) {
                    warnings += "段 $pairLabel 有 $withoutIdentity 个可点击控件缺少 ID/文本/描述，未参与推断。"
                }

                changed.take(MAX_INFERRED_CLICKS_PER_PAIR).forEach { oldNode ->
                    val selector = buildSelector(oldNode, null, previous.nodes, textVolatile = false)
                    val point = pointOf(oldNode, null)
                    if (selector == null && point == null) {
                        skipped += "段 $pairLabel 推断点击「${describe(oldNode, null)}」失败：缺少定位信息。"
                        return@forEach
                    }
                    if (emitClick(selector, point, oldNode, targetKey(oldNode, null), fromInference = true)) {
                        warnings += "段 $pairLabel 推断出点击「${describe(oldNode, null)}」，请人工确认。"
                    }
                }
            }

            if (steps.isEmpty()) {
                skipped += "界面差异中没有找到足够可靠的点击或输入信号，未生成步骤。"
            }
        }

        // -----------------------------------------------------------------
        // 步骤构造
        // -----------------------------------------------------------------

        private fun addStep(moduleId: String, overrides: Map<String, Any?>): ActionStep {
            val defaults = runCatching {
                ModuleRegistry.getModule(moduleId)
                    ?.createSteps()
                    ?.firstOrNull()
                    ?.parameters
                    .orEmpty()
            }.getOrDefault(emptyMap())

            val parameters = LinkedHashMap<String, Any?>(defaults)
            parameters.putAll(overrides)
            val step = ActionStep(
                moduleId = moduleId,
                parameters = parameters,
                indentationLevel = 0,
                id = UUID.randomUUID().toString()
            )
            steps += step
            return step
        }

        private fun addSelectorStep(selector: String, node: RecordedUiNode?, point: Point?): ActionStep {
            val overrides = linkedMapOf<String, Any?>(
                "selector" to selector,
                "result_selection" to "first"
            )
            node?.let { overrides["recording_node"] = gson.toJson(it) }
            point?.let { overrides["fallback_target"] = "${it.x},${it.y}" }
            return addStep(MODULE_SELECTOR, overrides)
        }

        private fun emitClick(
            selector: String?,
            point: Point?,
            node: RecordedUiNode?,
            key: String,
            fromInference: Boolean,
        ): Boolean {
            if (selector == null) {
                if (point == null) return false
                addStep(MODULE_CLICK, linkedMapOf("target" to "${point.x},${point.y}"))
                lastClickKey = key
                lastActionWasClick = true
                return true
            }
            if (fromInference && !usedSelectors.add(selector)) return false
            usedSelectors += selector

            val selectorStep = addSelectorStep(selector, node, point)
            val overrides = linkedMapOf<String, Any?>(
                "target" to "{{${selectorStep.id}.element}}"
            )
            point?.let { overrides["fallback_target"] = "${it.x},${it.y}" }
            addStep(MODULE_CLICK, overrides)
            lastClickKey = key
            lastActionWasClick = true
            return true
        }

        // -----------------------------------------------------------------
        // 选择器
        // -----------------------------------------------------------------

        /**
         * 生成 GKD 选择器。
         *
         * - 有录制时生成的可用 GKD 表达式时直接使用；
         * - 输入文本场景拒绝 `[text='新文本']` 这类易失选择器，改用 vid/类名/边界；
         * - 没有 vid 时组合类名、文本、描述、可编辑/可点击状态、深度和精确边界，
         *   只有在组合仍可能命中多个控件时才追加边界/深度。
         */
        private fun buildSelector(
            node: RecordedUiNode?,
            event: UiRecordingEvent?,
            segmentNodes: List<RecordedUiNode>,
            textVolatile: Boolean,
        ): String? {
            if (node == null && event == null) return null

            val gkd = node?.gkd?.takeIf { isUsableGkd(it) }
                ?: event?.gkd?.takeIf { isUsableGkd(it) }
            if (gkd != null && (!textVolatile || gkd.contains("vid="))) {
                return gkd
            }

            val viewId = node?.viewId?.takeIf { it.isNotBlank() }
                ?: event?.viewId?.takeIf { it.isNotBlank() }
                ?: ""
            val className = node?.className?.takeIf { it.isNotBlank() }
                ?: event?.className?.takeIf { it.isNotBlank() }
                ?: ""
            val text = node?.text?.takeIf { it.isNotBlank() }
                ?: event?.text?.takeIf { it.isNotBlank() }
                ?: ""
            val description = node?.description.orEmpty()
            val bounds = boundsOf(node, event)

            val sb = StringBuilder()
            if (className.isNotBlank()) sb.append('@').append(className)

            var hasIdentity = false
            if (viewId.isNotBlank()) {
                sb.append("[vid='")
                    .append(RecordingNodeUtils.escapeGkdString(shortViewId(viewId)))
                    .append("']")
                hasIdentity = true
            } else {
                if (!textVolatile && text.isNotBlank()) {
                    sb.append("[text='")
                        .append(RecordingNodeUtils.escapeGkdString(text))
                        .append("']")
                    hasIdentity = true
                }
                if (description.isNotBlank()) {
                    sb.append("[desc='")
                        .append(RecordingNodeUtils.escapeGkdString(description))
                        .append("']")
                    hasIdentity = true
                }
                // GKD 没有 hint 属性，hint 只保存在 recording_node 中，用于人工排查。

                if (node != null && node.editable) {
                    sb.append("[editable=true]")
                }
                if (node != null && node.clickable && !textVolatile) {
                    sb.append("[clickable=true]")
                }

                if (!hasIdentity) {
                    // 只有类名/状态可用时，追加精确边界辅助定位。
                    bounds?.let {
                        appendBounds(sb, it)
                        hasIdentity = true
                    }
                } else if (segmentNodes.isNotEmpty()) {
                    val primaryCount = countPrimaryMatches(
                        nodes = segmentNodes,
                        viewId = viewId,
                        className = className,
                        text = if (textVolatile) "" else text,
                        description = description
                    )
                    if (primaryCount > 1) {
                        val uniqueBounds = bounds?.takeIf { candidate ->
                            segmentNodes.count {
                                sameBounds(it, candidate.left, candidate.top, candidate.right, candidate.bottom)
                            } == 1
                        }
                        if (uniqueBounds != null) {
                            appendBounds(sb, uniqueBounds)
                        } else if (node != null && node.depth > 0) {
                            // 运行时 transform 的 depth 以 1 为根，录制快照以 0 为根。
                            sb.append("[depth=").append(node.depth + 1).append(']')
                        }
                    }
                }
            }

            val selector = sb.toString()
            return selector.ifBlank { null }
        }

        private fun appendBounds(sb: StringBuilder, bounds: Bounds) {
            sb.append("[left=").append(bounds.left).append(']')
                .append("[top=").append(bounds.top).append(']')
                .append("[right=").append(bounds.right).append(']')
                .append("[bottom=").append(bounds.bottom).append(']')
        }

        private fun countPrimaryMatches(
            nodes: List<RecordedUiNode>,
            viewId: String,
            className: String,
            text: String,
            description: String,
        ): Int {
            return nodes.count { candidate ->
                val viewIdMatches = viewId.isBlank() ||
                    shortViewId(candidate.viewId) == shortViewId(viewId)
                val classMatches = className.isBlank() ||
                    candidate.className == className ||
                    candidate.className.endsWith(".$className")
                val textMatches = text.isBlank() || candidate.text == text
                val descriptionMatches = description.isBlank() ||
                    candidate.description == description
                viewIdMatches && classMatches && textMatches && descriptionMatches
            }
        }

        private fun isUsableGkd(gkd: String): Boolean {
            return gkd.isNotBlank() && gkd != "*"
        }

        private fun shortViewId(viewId: String): String {
            if (viewId.isBlank()) return ""
            return when {
                viewId.contains(":id/") -> viewId.substringAfter(":id/")
                viewId.contains("/id/") -> viewId.substringAfter("/id/")
                else -> viewId.substringAfterLast("/")
            }
        }

        // -----------------------------------------------------------------
        // 节点/事件工具
        // -----------------------------------------------------------------

        private fun segmentAt(timestampMs: Long): UiRecordingSegment? {
            return segments.lastOrNull { it.startMs <= timestampMs } ?: segments.firstOrNull()
        }

        private fun findNode(segment: UiRecordingSegment?, event: UiRecordingEvent): RecordedUiNode? {
            val nodes = segment?.nodes.orEmpty()
            if (nodes.isEmpty()) return null

            if (event.viewId.isNotBlank()) {
                nodes.firstOrNull {
                    it.viewId == event.viewId && it.className == event.className
                }?.let { return it }
                nodes.firstOrNull { it.viewId == event.viewId }?.let { return it }
            }

            if (hasBounds(event.left, event.top, event.right, event.bottom)) {
                val exact = nodes.filter {
                    sameBounds(it, event.left, event.top, event.right, event.bottom)
                }
                exact.firstOrNull {
                    event.className.isBlank() || it.className == event.className
                }?.let { return it }
                exact.firstOrNull()?.let { return it }
            }

            if (isUsableGkd(event.gkd)) {
                nodes.firstOrNull { it.gkd == event.gkd }?.let { return it }
            }

            if (event.text.isNotBlank()) {
                nodes.firstOrNull {
                    it.text == event.text &&
                        (event.className.isBlank() || it.className == event.className)
                }?.let { return it }
            }
            return null
        }

        /**
         * 文本变化事件的 source 在部分 ROM 上会指向根节点；此时按事件坐标、
         * 焦点状态和“唯一输入框”逐级回退，避免把文本输入到错误的控件上。
         */
        private fun findTextTarget(segment: UiRecordingSegment?, event: UiRecordingEvent): RecordedUiNode? {
            val found = findNode(segment, event)
            if (found?.editable == true) return found

            val editables = segment?.nodes.orEmpty().filter { it.editable }
            if (editables.isEmpty()) return found

            if (hasBounds(event.left, event.top, event.right, event.bottom)) {
                val cx = (event.left + event.right) / 2
                val cy = (event.top + event.bottom) / 2
                editables
                    .filter { it.hasBounds() && cx in it.left..it.right && cy in it.top..it.bottom }
                    .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
                    ?.let { return it }
            }
            editables.firstOrNull { it.focused }?.let { return it }
            if (editables.size == 1) return editables.first()
            return found
        }

        private fun findMatchingNode(
            nodes: List<RecordedUiNode>,
            target: RecordedUiNode,
        ): RecordedUiNode? {
            if (target.viewId.isNotBlank()) {
                nodes.firstOrNull {
                    it.viewId == target.viewId && it.className == target.className
                }?.let { return it }
                nodes.firstOrNull { it.viewId == target.viewId }?.let { return it }
            }
            nodes.firstOrNull { sameBounds(it, target) }?.let { return it }
            if (target.text.isNotBlank() || target.description.isNotBlank()) {
                nodes.firstOrNull {
                    it.className == target.className &&
                        it.text == target.text &&
                        it.description == target.description
                }?.let { return it }
            }
            return null
        }

        private fun targetKey(node: RecordedUiNode?, event: UiRecordingEvent?): String {
            if (node != null) {
                if (node.viewId.isNotBlank()) return "vid:${node.viewId}"
                if (node.hasBounds()) {
                    return "b:${node.className}:${node.left},${node.top},${node.right},${node.bottom}"
                }
                return "t:${node.className}:${node.text}:${node.description}"
            }
            if (event != null) {
                if (event.viewId.isNotBlank()) return "vid:${event.viewId}"
                if (hasBounds(event.left, event.top, event.right, event.bottom)) {
                    return "b:${event.className}:${event.left},${event.top},${event.right},${event.bottom}"
                }
                return "e:${event.className}:${event.text}"
            }
            return "unknown"
        }

        private fun describe(node: RecordedUiNode?, event: UiRecordingEvent?): String {
            val password = node?.password == true
            val text = if (password) {
                ""
            } else {
                node?.text?.takeIf { it.isNotBlank() }
                    ?: node?.description?.takeIf { it.isNotBlank() }
                    ?: event?.text.orEmpty()
            }
            val viewId = node?.viewId?.takeIf { it.isNotBlank() }
                ?: event?.viewId.orEmpty()
            val className = node?.className?.takeIf { it.isNotBlank() }
                ?: event?.className.orEmpty()

            return buildString {
                append(text.ifBlank { className.ifBlank { "未命名控件" } })
                if (viewId.isNotBlank()) {
                    append(" / ").append(shortViewId(viewId))
                }
            }
        }

        private fun looksLikeTemplate(text: String): Boolean {
            return text.contains("{{") || text.contains("[[") || text.contains("{%")
        }

        private fun infoScore(node: RecordedUiNode): Int {
            var score = 0
            if (node.viewId.isNotBlank()) score += 8
            if (node.description.isNotBlank()) score += 4
            if (node.text.isNotBlank()) score += 3
            if (node.className.isNotBlank()) score += 1
            if (node.hasBounds()) score += 1
            if (node.clickable) score += 1
            return score
        }

        private fun pointOf(node: RecordedUiNode?, event: UiRecordingEvent?): Point? {
            val bounds = boundsOf(node, event) ?: return null
            return Point(bounds.centerX, bounds.centerY)
        }

        private fun boundsOf(node: RecordedUiNode?, event: UiRecordingEvent?): Bounds? {
            if (node != null && node.hasBounds()) {
                return Bounds(node.left, node.top, node.right, node.bottom)
            }
            if (event != null && hasBounds(event.left, event.top, event.right, event.bottom)) {
                return Bounds(event.left, event.top, event.right, event.bottom)
            }
            return null
        }

        private fun swipePoints(node: RecordedUiNode?, event: UiRecordingEvent): Pair<Point, Point>? {
            val bounds = boundsOf(node, event) ?: run {
                if (inferredWidth <= 1 || inferredHeight <= 1) return null
                Bounds(0, 0, inferredWidth, inferredHeight)
            }
            if (bounds.height < 4) return null

            // 事件没有记录方向，默认从控件中心向上滑（内容上移）。
            val distance = maxOf(bounds.height / 3, inferredHeight / 8, 80)
            val startY = bounds.centerY.coerceIn(bounds.top + 1, bounds.bottom - 1)
            val endY = (bounds.centerY - distance).coerceIn(bounds.top + 1, bounds.bottom - 1)
            if (startY == endY) return null

            return Point(clampX(bounds.centerX), clampY(startY)) to
                Point(clampX(bounds.centerX), clampY(endY))
        }

        private fun clampX(value: Int): Int {
            return if (inferredWidth > 1) value.coerceIn(1, inferredWidth - 1) else value.coerceAtLeast(1)
        }

        private fun clampY(value: Int): Int {
            return if (inferredHeight > 1) value.coerceIn(1, inferredHeight - 1) else value.coerceAtLeast(1)
        }

        private fun sameScrollArea(first: UiRecordingEvent, second: UiRecordingEvent): Boolean {
            if (hasBounds(first.left, first.top, first.right, first.bottom) &&
                hasBounds(second.left, second.top, second.right, second.bottom)
            ) {
                return first.left == second.left && first.top == second.top &&
                    first.right == second.right && first.bottom == second.bottom
            }
            return first.className == second.className && first.packageName == second.packageName
        }

        /**
         * 录制悬浮窗自身的按钮事件不应写入生成的工作流。
         */
        private fun isRecorderOverlayEvent(event: UiRecordingEvent): Boolean {
            if (event.packageName != "com.chaomixian.vflow") return false
            val label = event.text.trim()
            if (label in setOf("开始", "暂停", "继续", "结束", "UI 录制")) return true
            return event.gkd.trim() in setOf(
                "[text='开始']",
                "[text='暂停']",
                "[text='继续']",
                "[text='结束']",
                "[text='UI 录制']"
            )
        }

        private fun normalizeEvents(raw: List<UiRecordingEvent>): List<UiRecordingEvent> {
            val result = mutableListOf<UiRecordingEvent>()
            raw.sortedBy { it.timestampMs }.forEach { event ->
                if (event.type == UiRecordingEvent.WINDOW_CHANGED) return@forEach
                if (isRecorderOverlayEvent(event)) return@forEach
                val previous = result.lastOrNull()
                if (previous != null && previous.type == event.type &&
                    event.timestampMs - previous.timestampMs <= 120L &&
                    previous.gkd == event.gkd &&
                    previous.viewId == event.viewId &&
                    previous.text == event.text &&
                    previous.left == event.left &&
                    previous.top == event.top &&
                    previous.right == event.right &&
                    previous.bottom == event.bottom
                ) {
                    return@forEach
                }
                result += event
            }
            return result
        }
    }
}

private fun RecordedUiNode.hasBounds(): Boolean = right > left && bottom > top

private fun RecordedUiNode.boundsOrNull(): BoundsLike? {
    return if (hasBounds()) BoundsLike(left, top, right, bottom) else null
}

private fun RecordedUiNode.hasIdentity(): Boolean {
    return viewId.isNotBlank() || text.isNotBlank() || description.isNotBlank()
}

private fun hasBounds(left: Int, top: Int, right: Int, bottom: Int): Boolean {
    return right > left && bottom > top
}

private fun sameBounds(node: RecordedUiNode, left: Int, top: Int, right: Int, bottom: Int): Boolean {
    return node.left == left && node.top == top && node.right == right && node.bottom == bottom
}

private fun sameBounds(node: RecordedUiNode, other: RecordedUiNode): Boolean {
    return sameBounds(node, other.left, other.top, other.right, other.bottom)
}

private fun contains(bounds: BoundsLike?, event: UiRecordingEvent): Boolean {
    if (bounds == null) return false
    val cx = (event.left + event.right) / 2
    val cy = (event.top + event.bottom) / 2
    return cx in bounds.left..bounds.right && cy in bounds.top..bounds.bottom
}

private data class BoundsLike(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
