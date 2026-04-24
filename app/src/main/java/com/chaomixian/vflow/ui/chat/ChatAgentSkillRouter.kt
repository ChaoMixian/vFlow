package com.chaomixian.vflow.ui.chat

import java.util.Locale

internal data class ChatAgentSkillDefinition(
    val id: String,
    val title: String,
    val description: String,
    val instructions: String,
    val relatedSkillIds: Set<String> = emptySet(),
    val toolNames: Set<String> = emptySet(),
    val moduleIds: Set<String> = emptySet(),
)

internal data class ChatAgentSkillSelection(
    val skills: List<ChatAgentSkillDefinition>,
    val availableTools: List<ChatAgentToolDefinition>,
) {
    companion object {
        val EMPTY = ChatAgentSkillSelection(
            skills = emptyList(),
            availableTools = emptyList(),
        )
    }
}

internal object ChatAgentSkillRouter {
    fun selectSkills(
        history: List<ChatMessage>,
        availableTools: List<ChatAgentToolDefinition>,
    ): ChatAgentSkillSelection {
        if (availableTools.isEmpty()) return ChatAgentSkillSelection.EMPTY
        val alwaysExposedTools = availableTools.filter(::isAlwaysExposedNativeHelper)

        val latestUserText = history.lastOrNull { it.role == ChatMessageRole.USER }
            ?.content
            .orEmpty()
        val normalizedText = latestUserText.normalizeForSkillRouting()
        if (normalizedText.isBlank()) {
            return ChatAgentSkillSelection(
                skills = emptyList(),
                availableTools = alwaysExposedTools,
            )
        }

        val selectedSkillIds = linkedSetOf<String>()
        selectedSkillIds += selectExplicitSkillIds(
            text = normalizedText,
            availableTools = availableTools,
        )

        if (selectedSkillIds.isEmpty() && shouldContinuePriorSkills(normalizedText)) {
            selectedSkillIds += findContinuationSkillIds(history, availableTools)
        }

        val expandedSkillIds = expandSkillIds(selectedSkillIds)
        val skillsById = SKILL_CATALOG
            .map { it.definition }
            .associateBy { it.id }
        val selectedSkills = expandedSkillIds.mapNotNull(skillsById::get)
        val selectedTools = (
            alwaysExposedTools +
                availableTools.filter { tool ->
                    selectedSkills.any { skill -> skill.matchesTool(tool) }
                }
            ).distinctBy { it.name }

        return ChatAgentSkillSelection(
            skills = selectedSkills,
            availableTools = selectedTools,
        )
    }

    fun buildSystemPrompt(
        basePrompt: String,
        skillSelection: ChatAgentSkillSelection,
    ): String {
        val trimmedBasePrompt = basePrompt.trim()
        if (skillSelection.skills.isEmpty() && skillSelection.availableTools.isEmpty()) return trimmedBasePrompt

        val skillPrompt = buildString {
            appendLine("You are the vFlow chat agent inside an Android automation app.")
            appendLine("Only use the tools exposed below.")
            appendLine("Prefer the narrowest direct tool that can complete the request safely.")
            appendLine("For screen observation and on-screen interaction, prefer the agent-native `vflow_agent_*` helper tools first. Treat human-oriented vFlow action modules as supplemental fallbacks.")
            appendLine("Treat the accessibility/UI node tree as the primary source of truth. Keep screenshots, OCR, and other visual tools as explicit fallback paths so future multimodal models can use them without making them the default.")
            appendLine("If one direct tool can complete a simple request such as dark mode, flashlight, wifi, brightness, clipboard, volume, or app launch, call that direct tool instead of navigating system UI or building a workflow.")
            appendLine("Use canonical module parameters and step IDs; never invent localized parameter keys.")
            appendLine("Ask one concise clarification only when a missing target, time, account, or condition would make the action ambiguous or risky.")
            appendLine("Never claim a tool succeeded until you receive the tool result.")
            appendLine("If a tool result includes artifact:// handles, preserve and reuse them in later tool arguments when needed.")
            appendLine("Prefer small deterministic tool calls over speculative multi-step jumps. Observe, act once, then verify.")
            appendLine("Keep tool usage token-efficient: rely on concise summaries and artifact handles instead of asking tools to dump raw data unless you truly need it.")
            appendLine("When a tool returns an error or guardrail message, use that recovery guidance to self-heal. Do not repeat the same failing call unchanged.")
            appendLine("If the latest user turn is conceptual or explanatory, answer normally instead of forcing a tool call.")
            appendLine("Before chaining screen interactions, first make a fresh read-only observation of the current UI with `vflow_agent_observe_ui` or another accessibility-first helper, and prefer returned ScreenElement handles or verified ids instead of guessed text.")
            appendLine("Do not issue blind repeated swipes. If a top-ranked content target that matches the task is already visible, tap or verify it before scrolling.")
            appendLine("On feed/list screens, treat the returned primary content targets as ordered in the current viewport from top to bottom. For requests like first/latest visible item, use that visible order before considering any scroll.")
            appendLine("After any tap that is supposed to open content, re-observe once before deciding to swipe. Treat an activity change or a detail-like screen role as a strong navigation-success signal.")
            appendLine("Never do two same-direction swipes in a row without a fresh observation in between.")
            appendLine("When you need article text or page content for summarization, prefer `vflow_agent_read_page_content`, which reads from the current visible UI node tree and never scrolls on its own.")
            appendLine("If `vflow_agent_read_page_content` says the screen still looks like a feed/list, go back to target selection instead of continuing to scroll or summarize the feed.")
            appendLine("If more content is needed after a read, decide explicitly whether to swipe, then re-observe or read again. Do not hide scrolling inside a read request.")
            appendLine("On detail/article screens, summarize from the currently visible node-tree text first. Only continue scrolling when the visible text is clearly insufficient, and avoid long downward swipe chains that skip past正文.")
            appendLine("Before you say a screen-based task is complete, perform a final read-only verification step. If you could not verify the final state, say that it is not yet verified.")
            val alwaysOnNativeTools = skillSelection.availableTools.filter(::isAlwaysExposedNativeHelper)
            if (alwaysOnNativeTools.isNotEmpty()) {
                appendLine()
                appendLine("Always-available agent-native helpers:")
                append("- ")
                appendLine(alwaysOnNativeTools.joinToString(separator = ", ") { it.name })
                appendLine("These helpers stay available even when no specialized skill is selected.")
            }
            if (skillSelection.skills.isNotEmpty()) {
                appendLine()
                appendLine("Active skills:")
                skillSelection.skills.forEach { skill ->
                    append("- ")
                    append(skill.title)
                    append(" (")
                    append(skill.id)
                    append("): ")
                    appendLine(skill.description)
                    appendLine(skill.instructions.trim())
                    appendLine()
                }
            }
        }.trim()

        return listOf(trimmedBasePrompt, skillPrompt)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun isAlwaysExposedNativeHelper(tool: ChatAgentToolDefinition): Boolean {
        return tool.backend == ChatAgentToolBackend.NATIVE_HELPER &&
            tool.nativeHelperId in ALWAYS_EXPOSED_NATIVE_HELPERS
    }

    private fun selectExplicitSkillIds(
        text: String,
        availableTools: List<ChatAgentToolDefinition>,
    ): LinkedHashSet<String> {
        val selectedSkillIds = linkedSetOf<String>()
        val operationalRequest = looksLikeOperationalRequest(text)

        if (needsSavedWorkflowSkill(text)) {
            selectedSkillIds += savedWorkflowSkill.id
        }
        if (needsTemporaryWorkflowSkill(text)) {
            selectedSkillIds += temporaryWorkflowSkill.id
        }

        if (selectedSkillIds.isNotEmpty()) return selectedSkillIds

        SKILL_CATALOG.forEach { skill ->
            if (skill.matches(text, operationalRequest)) {
                selectedSkillIds += skill.definition.id
            }
        }

        if (selectedSkillIds.isEmpty() && operationalRequest) {
            selectedSkillIds += selectSkillIdsFromToolMetadata(text, availableTools)
        }

        if (selectedSkillIds.isEmpty() && operationalRequest && looksLikeAppLifecycleRequest(text)) {
            selectedSkillIds += appLifecycleSkill.definition.id
        }

        if (selectedSkillIds.isEmpty() && operationalRequest) {
            selectedSkillIds += fallbackInteractionSkill.id
        }

        return selectedSkillIds
    }

    private fun selectSkillIdsFromToolMetadata(
        text: String,
        availableTools: List<ChatAgentToolDefinition>,
    ): LinkedHashSet<String> {
        val selectedSkillIds = linkedSetOf<String>()
        val candidateTools = availableTools.filter { tool ->
            tool.usageScopes.contains(ChatAgentToolUsageScope.DIRECT_TOOL) &&
                tool.routingHints.any { hint -> hint.isNotBlank() && text.contains(hint) }
        }
        if (candidateTools.isEmpty()) return selectedSkillIds

        candidateTools.forEach { tool ->
            SKILL_CATALOG.forEach { skill ->
                if (skill.definition.id != fallbackInteractionSkill.id && skill.definition.matchesTool(tool)) {
                    selectedSkillIds += skill.definition.id
                }
            }
        }
        return selectedSkillIds
    }

    private fun expandSkillIds(seedSkillIds: LinkedHashSet<String>): LinkedHashSet<String> {
        if (seedSkillIds.isEmpty()) return linkedSetOf()

        val expanded = LinkedHashSet(seedSkillIds)
        var changed = true
        while (changed) {
            changed = false
            expanded.toList().forEach { skillId ->
                val related = SKILL_CATALOG.firstOrNull { it.definition.id == skillId }
                    ?.definition
                    ?.relatedSkillIds
                    .orEmpty()
                related.forEach { relatedId ->
                    if (expanded.add(relatedId)) {
                        changed = true
                    }
                }
            }
        }
        return expanded
    }

    private fun findContinuationSkillIds(
        history: List<ChatMessage>,
        availableTools: List<ChatAgentToolDefinition>,
    ): LinkedHashSet<String> {
        val lastToolCallMessage = history.asReversed()
            .firstOrNull { it.role == ChatMessageRole.ASSISTANT && it.toolCalls.isNotEmpty() }
            ?: return linkedSetOf()
        val toolsByName = availableTools.associateBy { it.name }
        return lastToolCallMessage.toolCalls.fold(linkedSetOf()) { selectedSkillIds, toolCall ->
            val moduleId = toolsByName[toolCall.name]?.moduleId
            SKILL_CATALOG.forEach { skill ->
                if (skill.definition.matchesTool(toolName = toolCall.name, moduleId = moduleId)) {
                    selectedSkillIds += skill.definition.id
                }
            }
            selectedSkillIds
        }
    }

    private fun needsSavedWorkflowSkill(text: String): Boolean {
        if (text.isBlank()) return false
        val hasWorkflowNoun = WORKFLOW_NOUNS.any { it in text }
        val hasCreateVerb = WORKFLOW_CREATE_VERBS.any { it in text } ||
            Regex("""\b(create|save|build|generate)\b.*\b(workflow|automation)\b""")
                .containsMatchIn(text)
        val hasAutoTrigger = WORKFLOW_TRIGGER_SIGNALS.any { it in text }
        return (hasWorkflowNoun && hasCreateVerb) || (hasAutoTrigger && looksLikeOperationalRequest(text))
    }

    private fun needsTemporaryWorkflowSkill(text: String): Boolean {
        if (text.isBlank() || needsSavedWorkflowSkill(text)) return false
        val explicitWorkflowRequest = TEMPORARY_WORKFLOW_KEYWORDS.any { it in text }
        val repeatedOrStrongSequence = TEMPORARY_SEQUENCE_SIGNALS.any { it in text } ||
            Regex("""\b\d+\s*(次|遍|times)\b""").containsMatchIn(text)
        val connectiveSequence = Regex(
            """(打开|关闭|点击|长按|输入|滑动|等待|返回|启动|停止|复制|粘贴|设置|read|open|close|tap|click|type|input|swipe|wait|launch|stop).*(然后|接着|then|and then).*(打开|关闭|点击|长按|输入|滑动|等待|返回|启动|停止|复制|粘贴|设置|read|open|close|tap|click|type|input|swipe|wait|launch|stop)"""
        ).containsMatchIn(text)
        return explicitWorkflowRequest || ((repeatedOrStrongSequence || connectiveSequence) && looksLikeOperationalRequest(text))
    }

    private fun shouldContinuePriorSkills(text: String): Boolean {
        if (text.isBlank()) return false
        if (looksLikeKnowledgeQuestion(text)) return false
        return CONTINUATION_SIGNALS.any { it in text } ||
            Regex("""\b(again|continue|same|instead|change|update|retry|that one|that workflow)\b""")
                .containsMatchIn(text)
    }

    private fun looksLikeKnowledgeQuestion(text: String): Boolean {
        return KNOWLEDGE_QUESTION_SIGNALS.any { it in text } ||
            Regex("""\b(what is|why|how does|design|architecture|difference|explain)\b""")
                .containsMatchIn(text)
    }

    private fun looksLikeOperationalRequest(text: String): Boolean {
        if (text.isBlank()) return false
        return OPERATIONAL_SIGNALS.any { it in text } ||
            Regex("""截.{0,4}图""").containsMatchIn(text) ||
            Regex("""\b(ocr|screenshot|screen shot)\b""").containsMatchIn(text) ||
            Regex("""(当前|这个)?(页面|界面|屏幕).*(控件|元素|内容|状态)""").containsMatchIn(text) ||
            Regex("""\b(current|this)\s+(page|screen|ui).*(controls|elements|content|state)\b""")
                .containsMatchIn(text) ||
            Regex(
                """\b(open|close|turn on|turn off|set|read|get|send|find|tap|click|type|launch|save|create|run|capture|scan|play|call|share|copy|paste|toggle|transcribe)\b"""
            ).containsMatchIn(text)
    }

    private fun looksLikeAppLifecycleRequest(text: String): Boolean {
        if (text.isBlank()) return false
        return Regex("""^(打开|启动|关闭|停止)[^，。！？,.]{1,32}""").containsMatchIn(text) ||
            Regex("""\b(open|launch|close|stop)\b\s+.{1,40}""").containsMatchIn(text)
    }

    private fun String.normalizeForSkillRouting(): String {
        return lowercase(Locale.ROOT)
            .replace("wi-fi", "wifi")
            .replace("蓝芽", "蓝牙")
            .trim()
    }

    private fun ChatAgentSkillDefinition.matchesTool(tool: ChatAgentToolDefinition): Boolean {
        return matchesTool(toolName = tool.name, moduleId = tool.moduleId)
    }

    private fun ChatAgentSkillDefinition.matchesTool(
        toolName: String,
        moduleId: String?,
    ): Boolean {
        return toolName in toolNames || (moduleId != null && moduleId in moduleIds)
    }

    private data class SkillRule(
        val definition: ChatAgentSkillDefinition,
        val keywords: List<String> = emptyList(),
        val regexes: List<Regex> = emptyList(),
        val requiresOperationalIntent: Boolean = true,
    ) {
        fun matches(text: String, operationalRequest: Boolean): Boolean {
            if (requiresOperationalIntent && !operationalRequest) return false
            return keywords.any { it in text } || regexes.any { it.containsMatchIn(text) }
        }
    }

    private val temporaryWorkflowSkill = ChatAgentSkillDefinition(
        id = "temporary_workflow_execution",
        title = "Temporary Workflow Execution",
        description = "Execute deterministic multi-step or repeated device actions in one approval.",
        instructions = """
            Use `vflow_agent_run_temporary_workflow` only for one-off multi-step or repeated device actions.
            Generate a real workflow object with canonical `moduleId`, `parameters`, and stable snake_case step IDs.
            Temporary workflows must never include trigger modules or nested workflow tools.
            Prefer loop modules for repeated sequences instead of duplicating many steps.
            If a single direct tool can finish the request safely, prefer that direct tool instead.
        """.trimIndent(),
        toolNames = setOf(CHAT_TEMPORARY_WORKFLOW_TOOL_NAME),
        moduleIds = setOf(CHAT_TEMPORARY_WORKFLOW_MODULE_ID),
    )

    private val savedWorkflowSkill = ChatAgentSkillDefinition(
        id = "saved_workflow_creation",
        title = "Saved Workflow Creation",
        description = "Create reusable automations that appear in the user's workflow list.",
        instructions = """
            Use `vflow_agent_save_workflow` when the user asks to create, save, or generate an automation for later reuse.
            Put trigger modules only in `workflow.triggers` and action/data/logic modules only in `workflow.steps`.
            If the user did not request a trigger, omit `workflow.triggers` and let the app add a manual trigger.
            Never persist artifact:// handles inside saved workflows because chat artifacts are temporary.
        """.trimIndent(),
        toolNames = setOf(CHAT_SAVE_WORKFLOW_TOOL_NAME),
        moduleIds = setOf(CHAT_SAVE_WORKFLOW_MODULE_ID),
    )

    private val flashlightSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "flashlight_control",
            title = "Flashlight Control",
            description = "Operate the flashlight directly without UI automation.",
            instructions = """
                Use the direct flashlight tool for on/off/toggle requests.
                Do not open system UI, take screenshots, or search the screen for flashlight requests.
            """.trimIndent(),
            moduleIds = setOf("vflow.device.flashlight"),
        ),
        keywords = listOf("手电", "flashlight", "torch"),
    )

    private val clipboardSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "clipboard_and_share",
            title = "Clipboard And Share",
            description = "Read, write, and share clipboard-oriented content directly.",
            instructions = """
                Use clipboard or share tools for copy, paste, share, and quick-view tasks.
                Prefer direct clipboard tools instead of UI automation unless the user explicitly asks to interact inside an app screen.
            """.trimIndent(),
            moduleIds = setOf(
                "vflow.system.get_clipboard",
                "vflow.system.set_clipboard",
                "vflow.core.get_clipboard",
                "vflow.core.set_clipboard",
                "vflow.system.share",
                "vflow.data.quick_view",
            ),
        ),
        keywords = listOf("剪贴板", "clipboard", "复制到剪贴板", "读取剪贴板", "设置剪贴板", "粘贴", "分享", "share", "预览", "quick view", "quickview"),
    )

    private val connectivitySkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "device_settings_control",
            title = "Device Settings Control",
            description = "Toggle or adjust direct device settings without navigating system UI.",
            instructions = """
                Use direct system tools for wifi, bluetooth, brightness, mobile data, dark mode, and volume changes.
                For dark/light theme requests, call the direct dark mode tool with the requested mode instead of opening Settings.
                Avoid opening Settings or Quick Settings when a direct tool can perform the change safely.
            """.trimIndent(),
            moduleIds = setOf(
                "vflow.system.wifi",
                "vflow.core.wifi",
                "vflow.core.wifi_state",
                "vflow.system.bluetooth",
                "vflow.core.bluetooth",
                "vflow.core.bluetooth_state",
                "vflow.system.brightness",
                "vflow.system.mobile_data",
                "vflow.system.darkmode",
                "vflow.core.volume",
                "vflow.core.volume_state",
            ),
        ),
        keywords = listOf(
            "wifi",
            "无线网络",
            "无线局域网",
            "蓝牙",
            "bluetooth",
            "亮度",
            "brightness",
            "移动数据",
            "蜂窝数据",
            "mobile data",
            "cellular",
            "深色模式",
            "夜间模式",
            "暗色模式",
            "dark mode",
            "音量",
            "volume",
        ),
    )

    private val screenStateSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "screen_state_control",
            title = "Screen State Control",
            description = "Wake, sleep, lock, or unlock the screen directly.",
            instructions = """
                Use the direct screen state tools for wake, sleep, lock, and unlock requests.
                Do not build a workflow unless the user asks for repetition or a sequence involving multiple actions.
            """.trimIndent(),
            moduleIds = setOf(
                "vflow.system.wake_screen",
                "vflow.system.wake_and_unlock_screen",
                "vflow.system.sleep_screen",
                "vflow.core.wake_screen",
                "vflow.core.sleep_screen",
                "vflow.core.screen_status",
            ),
        ),
        keywords = listOf("亮屏", "熄屏", "锁屏", "解锁", "唤醒屏幕", "sleep screen", "wake screen", "unlock"),
    )

    private val observationSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "screen_observation",
            title = "Screen Observation",
            description = "Observe the current screen, activity, or visible text when state is unknown.",
            instructions = """
                Use read-only tools to build a fresh picture of the current UI before complex screen interactions and again before final completion.
                Prefer `vflow_agent_observe_ui` for a full control snapshot and `vflow_agent_verify_ui` for final confirmation.
                Prefer `vflow_agent_read_page_content` when the task depends on reading article text, visible copy, or current page content from the node tree.
                Treat `vflow_agent_read_page_content` as a pure read-only snapshot; if more content is needed, choose an explicit swipe yourself and then read again.
                Treat activity changes and detail-like screen roles as strong evidence that navigation already succeeded.
                On feed/list screens, treat the primary content targets as already ordered by the current viewport from top to bottom.
                On detail/article screens, use the visible node-tree text before asking for more downward scrolling.
                Do not recommend scrolling while the requested top content target is already visible on screen.
                Prefer `find_element` over OCR when the UI is in the accessibility tree; use it only as a module-level fallback.
                Use current-activity tools only when the foreground app or activity must be confirmed before acting.
                Prefer direct action tools when they can complete the request without observation.
            """.trimIndent(),
            toolNames = setOf(
                CHAT_AGENT_OBSERVE_UI_TOOL_NAME,
                CHAT_AGENT_READ_PAGE_CONTENT_TOOL_NAME,
                CHAT_AGENT_VERIFY_UI_TOOL_NAME,
            ),
            moduleIds = setOf(
                "vflow.interaction.get_current_activity",
                "vflow.interaction.find_element",
            ),
        ),
        keywords = listOf(
            "截图",
            "屏幕截图",
            "当前页面",
            "当前界面",
            "有什么控件",
            "哪些控件",
            "当前activity",
            "current activity",
            "screenshot",
            "ocr",
            "识别文字",
            "屏幕文字",
            "找文字",
            "查找文字",
            "screen controls",
        ),
    )

    private val visualFallbackSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "visual_screen_fallback",
            title = "Visual Screen Fallback",
            description = "Capture screenshots or use OCR only when the user explicitly asks for visual inspection or when non-visual node-tree tools are insufficient.",
            instructions = """
                This is an explicit visual fallback layer.
                Prefer the accessibility/node-tree helper tools first.
                Use screenshot capture or OCR only when the user explicitly requests screenshot/OCR behavior, or when a future multimodal model needs visual evidence for a UI surface the node tree cannot expose.
            """.trimIndent(),
            relatedSkillIds = setOf("screen_observation"),
            moduleIds = setOf(
                "vflow.system.capture_screen",
                "vflow.core.capture_screen",
                "vflow.interaction.ocr",
            ),
        ),
        keywords = listOf(
            "ocr",
            "截图",
            "屏幕截图",
            "截屏",
            "识别图片",
            "识图",
            "screen shot",
            "screenshot",
            "visual inspect",
            "read image",
        ),
    )

    private val uiInteractionSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "ui_interaction",
            title = "UI Interaction",
            description = "Tap, swipe, type, or press keys inside app UI when direct tools are not enough.",
            instructions = """
                Use the agent-native tap, long-press, swipe, input, key, and wait helpers as the primary screen-operation layer.
                Before the first interaction in a multi-step UI flow, observe the screen and work from returned ScreenElement handles or verified id data instead of guessing labels or coordinates.
                Never issue repeated swipes in the same direction without an intervening observation.
                If a visible top-ranked content target already satisfies a request like "open the first article", tap it before any scroll.
                Treat feed/list target ordering as the current viewport order unless a tool result proves otherwise.
                On detail/article screens, scrolling is an explicit agent decision; after each swipe, re-observe or re-read before deciding whether another swipe is justified.
                After a tap that should navigate, re-observe first; only scroll if the fresh observation shows that navigation did not happen and the desired target is no longer visible.
                Re-observe after meaningful screen changes and perform a final verification check before declaring the task complete.
                Input-text tools type into the focused field, so establish focus before typing when necessary.
            """.trimIndent(),
            relatedSkillIds = setOf("screen_observation"),
            toolNames = setOf(
                CHAT_AGENT_TAP_TOOL_NAME,
                CHAT_AGENT_LONG_PRESS_TOOL_NAME,
                CHAT_AGENT_INPUT_TEXT_TOOL_NAME,
                CHAT_AGENT_SWIPE_TOOL_NAME,
                CHAT_AGENT_PRESS_KEY_TOOL_NAME,
                CHAT_AGENT_WAIT_TOOL_NAME,
            ),
            moduleIds = setOf(
                "vflow.device.click",
                "vflow.interaction.screen_operation",
                "vflow.core.screen_operation",
                "vflow.interaction.input_text",
                "vflow.core.input_text",
                "vflow.device.send_key_event",
                "vflow.core.press_key",
            ),
        ),
        keywords = listOf("点击", "长按", "滑动", "坐标", "按钮", "tap", "click", "swipe", "输入", "打字", "文本框", "type ", "input text", "enter text", "按键", "返回键", "home键", "音量键", "key event", "press key", "back button"),
    )

    private val appLifecycleSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "app_lifecycle",
            title = "App Lifecycle",
            description = "Launch, stop, or inspect app state directly.",
            instructions = """
                Use the agent-native app lookup and launch helpers before falling back to app modules.
                If the user names an app by display name or brand instead of an Android package, resolve it with the installed-app lookup helper before launching or closing it.
                After launching an app for inspection, use read-only observation tools to confirm the foreground app or visible content when needed.
                Use current activity only when the active app or screen must be confirmed before acting.
            """.trimIndent(),
            relatedSkillIds = setOf("screen_observation"),
            toolNames = setOf(
                CHAT_AGENT_LOOKUP_APP_TOOL_NAME,
                CHAT_AGENT_LAUNCH_APP_TOOL_NAME,
            ),
            moduleIds = setOf(
                "vflow.system.find_installed_app",
                "vflow.system.launch_app",
                "vflow.system.close_app",
                "vflow.core.force_stop_app",
                "vflow.interaction.get_current_activity",
            ),
        ),
        keywords = listOf("打开应用", "启动应用", "关闭应用", "停止应用", "launch app", "open app", "close app", "force stop"),
        regexes = listOf(
            Regex("""(打开|启动|关闭|停止).*(应用|app|软件)"""),
            Regex("""\b(open|launch|close|stop)\b.*\b(app|application)\b"""),
        ),
    )

    private val notificationSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "notifications",
            title = "Notifications",
            description = "Send or manage local notifications.",
            instructions = """
                Use notification tools for creating, finding, or removing Android notifications.
                Do not route notification requests through UI automation unless the user explicitly asks to interact with another app.
            """.trimIndent(),
            moduleIds = setOf(
                "vflow.notification.send_notification",
                "vflow.notification.find",
                "vflow.notification.remove",
            ),
        ),
        keywords = listOf("通知", "notification"),
    )

    private val feedbackSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "device_feedback",
            title = "Device Feedback",
            description = "Produce device feedback such as toast, vibration, speech, audio, or calls.",
            instructions = """
                Use direct feedback tools for toast, vibration, TTS, speech-to-text, audio playback, and phone calls.
                Prefer the direct tool that matches the user's requested output modality.
            """.trimIndent(),
            moduleIds = setOf(
                "vflow.device.toast",
                "vflow.device.vibration",
                "vflow.device.text_to_speech",
                "vflow.device.speech_to_text",
                "vflow.device.play_audio",
                "vflow.device.call_phone",
            ),
        ),
        keywords = listOf(
            "toast",
            "轻提示",
            "弹个提示",
            "振动",
            "震动",
            "vibrate",
            "vibration",
            "朗读",
            "播报",
            "语音合成",
            "文字转语音",
            "text to speech",
            "tts",
            "语音转文字",
            "语音识别",
            "speech to text",
            "stt",
            "transcribe",
            "播放音频",
            "播放音乐",
            "play audio",
            "打电话",
            "拨号",
            "call phone",
            "phone call",
        ),
    )

    private val shellSkill = SkillRule(
        definition = ChatAgentSkillDefinition(
            id = "shell_execution",
            title = "Shell Execution",
            description = "Run shell-like commands only when no safer vFlow tool can complete the task.",
            instructions = """
                Shell tools are high risk and should be the last resort.
                Use them only when no safer direct vFlow module can observe or complete the task.
                Keep shell commands narrowly scoped and never assume they succeeded before reading the result.
            """.trimIndent(),
            moduleIds = setOf("vflow.shizuku.shell_command", "vflow.core.shell_command"),
        ),
        keywords = listOf("shell", "终端命令", "命令行", "adb", "shizuku命令"),
    )

    private val fallbackInteractionSkill = ChatAgentSkillDefinition(
        id = "generic_device_interaction",
        title = "Generic Device Interaction",
        description = "Handle broad device-action requests with a minimal safe fallback toolset.",
        instructions = """
            Use this fallback only when no more specific skill matches the request.
            Prefer direct tools first; for screen work, use the agent-native helper tools before human-oriented vFlow action modules.
            Observe the screen before tapping or typing when the target is uncertain.
            Treat the node tree as primary and keep OCR/screenshot paths for explicit visual fallback only.
            Do not perform blind repeated swipes; after each navigation tap, re-observe once before deciding to scroll.
            On feed/list screens, treat the visible primary content ranking as viewport order and exhaust those visible targets before scrolling.
            On detail/article screens, keep downward scrolling conservative and prefer summarizing from the currently visible node-tree text unless more content is clearly needed.
            If the requested main content is already visible, act on that visible target instead of scrolling past it.
            If the task requires multiple screen actions, start with a fresh control snapshot and end with a verification step instead of guessing that the task is done.
            Keep the plan short and avoid escalating to shell or workflows unless the user explicitly needs them.
        """.trimIndent(),
        relatedSkillIds = setOf("screen_observation"),
        toolNames = setOf(
            CHAT_AGENT_OBSERVE_UI_TOOL_NAME,
            CHAT_AGENT_READ_PAGE_CONTENT_TOOL_NAME,
            CHAT_AGENT_VERIFY_UI_TOOL_NAME,
            CHAT_AGENT_TAP_TOOL_NAME,
            CHAT_AGENT_LONG_PRESS_TOOL_NAME,
            CHAT_AGENT_INPUT_TEXT_TOOL_NAME,
            CHAT_AGENT_SWIPE_TOOL_NAME,
            CHAT_AGENT_PRESS_KEY_TOOL_NAME,
            CHAT_AGENT_WAIT_TOOL_NAME,
            CHAT_AGENT_LOOKUP_APP_TOOL_NAME,
            CHAT_AGENT_LAUNCH_APP_TOOL_NAME,
        ),
        moduleIds = setOf(
            "vflow.interaction.get_current_activity",
            "vflow.interaction.find_element",
            "vflow.device.click",
            "vflow.interaction.input_text",
            "vflow.system.launch_app",
            "vflow.system.set_clipboard",
            "vflow.device.toast",
        ),
    )

    private val SKILL_CATALOG = listOf(
        SkillRule(definition = temporaryWorkflowSkill, requiresOperationalIntent = false),
        SkillRule(definition = savedWorkflowSkill, requiresOperationalIntent = false),
        flashlightSkill,
        clipboardSkill,
        connectivitySkill,
        screenStateSkill,
        observationSkill,
        visualFallbackSkill,
        uiInteractionSkill,
        appLifecycleSkill,
        notificationSkill,
        feedbackSkill,
        shellSkill,
        SkillRule(definition = fallbackInteractionSkill),
    )

    private val WORKFLOW_NOUNS = listOf("工作流", "自动化", "automation", "workflow")
    private val WORKFLOW_CREATE_VERBS = listOf("保存", "创建", "生成", "新建", "save", "create", "build", "generate")
    private val WORKFLOW_TRIGGER_SIGNALS = listOf("定时", "触发", "每天", "每周", "每月", "闹钟", "schedule", "scheduled", "trigger")
    private val TEMPORARY_WORKFLOW_KEYWORDS = listOf("临时工作流", "执行工作流", "temporary workflow", "run workflow")
    private val TEMPORARY_SEQUENCE_SIGNALS = listOf("多步", "一系列", "依次", "重复", "循环", "多次", "每隔", "repeat", "loop")
    private val CONTINUATION_SIGNALS = listOf("继续", "再来", "刚才", "上一步", "那个", "这次", "改成", "换成", "同样", "继续刚才", "继续上一个", "继续那个", "接着做")
    private val KNOWLEDGE_QUESTION_SIGNALS = listOf("什么是", "是什么意思", "解释", "为什么", "原理", "设计", "思路", "区别", "介绍")
    private val ALWAYS_EXPOSED_NATIVE_HELPERS = setOf(
        ChatAgentNativeHelperId.OBSERVE_UI,
        ChatAgentNativeHelperId.READ_PAGE_CONTENT,
        ChatAgentNativeHelperId.TAP,
        ChatAgentNativeHelperId.LONG_PRESS,
        ChatAgentNativeHelperId.INPUT_TEXT,
        ChatAgentNativeHelperId.SWIPE,
        ChatAgentNativeHelperId.PRESS_KEY,
        ChatAgentNativeHelperId.WAIT,
        ChatAgentNativeHelperId.VERIFY_UI,
        ChatAgentNativeHelperId.LOOKUP_APP,
        ChatAgentNativeHelperId.LAUNCH_APP,
    )
    private val OPERATIONAL_SIGNALS = listOf(
        "打开",
        "关闭",
        "设置",
        "读取",
        "获取",
        "发送",
        "查找",
        "点击",
        "输入",
        "复制",
        "粘贴",
        "启动",
        "停止",
        "执行",
        "保存",
        "创建",
        "生成",
        "截图",
        "识别",
        "朗读",
        "播报",
        "播放",
        "拨号",
        "分享",
        "切换",
        "调高",
        "调低",
        "打开应用",
        "关闭应用",
    )
}
