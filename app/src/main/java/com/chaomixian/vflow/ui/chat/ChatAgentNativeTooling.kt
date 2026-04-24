package com.chaomixian.vflow.ui.chat

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.module.interaction.AgentTools
import com.chaomixian.vflow.core.workflow.module.interaction.AgentUtils
import com.chaomixian.vflow.core.workflow.module.system.InstalledAppSearchMatch
import com.chaomixian.vflow.core.workflow.module.system.InstalledAppSearchSupport
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.Stack
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ChatAgentToolBackend {
    MODULE,
    NATIVE_HELPER,
    TEMPORARY_WORKFLOW,
    SAVED_WORKFLOW,
}

enum class ChatAgentNativeHelperId {
    OBSERVE_UI,
    READ_PAGE_CONTENT,
    TAP,
    LONG_PRESS,
    INPUT_TEXT,
    SWIPE,
    PRESS_KEY,
    WAIT,
    VERIFY_UI,
    LOOKUP_APP,
    LAUNCH_APP,
}

internal const val CHAT_AGENT_OBSERVE_UI_TOOL_NAME = "vflow_agent_observe_ui"
internal const val CHAT_AGENT_READ_PAGE_CONTENT_TOOL_NAME = "vflow_agent_read_page_content"
internal const val CHAT_AGENT_TAP_TOOL_NAME = "vflow_agent_tap_screen"
internal const val CHAT_AGENT_LONG_PRESS_TOOL_NAME = "vflow_agent_long_press_screen"
internal const val CHAT_AGENT_INPUT_TEXT_TOOL_NAME = "vflow_agent_input_text"
internal const val CHAT_AGENT_SWIPE_TOOL_NAME = "vflow_agent_swipe_screen"
internal const val CHAT_AGENT_PRESS_KEY_TOOL_NAME = "vflow_agent_press_key"
internal const val CHAT_AGENT_WAIT_TOOL_NAME = "vflow_agent_wait"
internal const val CHAT_AGENT_VERIFY_UI_TOOL_NAME = "vflow_agent_verify_ui"
internal const val CHAT_AGENT_LOOKUP_APP_TOOL_NAME = "vflow_agent_lookup_installed_app"
internal const val CHAT_AGENT_LAUNCH_APP_TOOL_NAME = "vflow_agent_launch_app"

private const val SESSION_KEY_OBSERVATION_EPOCH = "chat_agent_observation_epoch"
private const val SESSION_KEY_LAST_OBSERVATION_TOOL_NAME = "chat_agent_last_observation_tool_name"
private const val SESSION_KEY_LAST_OBSERVATION_FINGERPRINT = "chat_agent_last_observation_fingerprint"
private const val SESSION_KEY_LAST_SCREEN_ROLE = "chat_agent_last_screen_role"
private const val SESSION_KEY_LAST_PRIMARY_CONTENT_HANDLE = "chat_agent_last_primary_content_handle"
private const val SESSION_KEY_LAST_PRIMARY_SCROLLABLE_HANDLE = "chat_agent_last_primary_scrollable_handle"
private const val SESSION_KEY_LAST_SNAPSHOT_HANDLE = "chat_agent_last_snapshot_handle"
private const val SESSION_KEY_LAST_ACTION_TOOL_NAME = "chat_agent_last_action_tool_name"
private const val SESSION_KEY_LAST_ACTION_OBSERVATION_EPOCH = "chat_agent_last_action_observation_epoch"
private const val SESSION_KEY_LAST_SWIPE_DIRECTION = "chat_agent_last_swipe_direction"

internal fun chatAgentAppendNextStep(
    baseMessage: String,
    nextStep: String?,
): String {
    val trimmedBase = baseMessage.trim()
    val trimmedNext = nextStep?.trim().orEmpty()
    if (trimmedNext.isBlank()) return trimmedBase
    return buildString {
        append(trimmedBase)
        append("\n\nNext step: ")
        append(trimmedNext.removePrefix("Next step: ").trim())
    }.trim()
}

internal fun chatAgentNativeRecoveryHint(helperId: ChatAgentNativeHelperId): String {
    return when (helperId) {
        ChatAgentNativeHelperId.OBSERVE_UI ->
            "bring the target app or screen to the foreground, make sure Accessibility is running, and retry `vflow_agent_observe_ui`."
        ChatAgentNativeHelperId.READ_PAGE_CONTENT ->
            "if this still looks like a feed/list screen, use `vflow_agent_observe_ui` to choose a visible content target and open it before reading again."
        ChatAgentNativeHelperId.TAP ->
            "call `vflow_agent_observe_ui` first, then retry with a fresh `artifact://...` ScreenElement handle instead of guessing coordinates or labels."
        ChatAgentNativeHelperId.LONG_PRESS ->
            "call `vflow_agent_observe_ui` first, then retry with a fresh `artifact://...` ScreenElement handle or verified coordinates."
        ChatAgentNativeHelperId.INPUT_TEXT ->
            "observe the current UI first, focus the intended field with a fresh handle, then retry the text input."
        ChatAgentNativeHelperId.SWIPE ->
            "use `vflow_agent_observe_ui` or `vflow_agent_read_page_content` before deciding whether another swipe is still necessary."
        ChatAgentNativeHelperId.PRESS_KEY ->
            "re-observe the current UI and choose the smallest safe navigation action, or use an on-screen control when available."
        ChatAgentNativeHelperId.WAIT ->
            "after waiting, run a read-only observation tool to verify what actually changed on screen."
        ChatAgentNativeHelperId.VERIFY_UI ->
            "use `vflow_agent_observe_ui` to inspect the actual visible state, then update the expectation or fix the navigation before verifying again."
        ChatAgentNativeHelperId.LOOKUP_APP ->
            "broaden the app query or ask the user which app they mean, then retry lookup."
        ChatAgentNativeHelperId.LAUNCH_APP ->
            "call `vflow_agent_lookup_installed_app` first to resolve the exact package, then retry the launch with that match."
    }
}

internal fun chatAgentShouldBlockRepeatedSwipe(
    lastActionToolName: String?,
    lastSwipeDirection: String?,
    lastActionObservationEpoch: Int?,
    currentObservationEpoch: Int?,
    requestedDirection: String?,
): Boolean {
    val normalizedDirection = requestedDirection
        ?.lowercase(Locale.ROOT)
        ?.replace('-', '_')
        ?.replace(' ', '_')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return false
    if (lastActionToolName != CHAT_AGENT_SWIPE_TOOL_NAME) return false
    if (lastSwipeDirection != normalizedDirection) return false
    if (lastActionObservationEpoch == null || currentObservationEpoch == null) return false
    return lastActionObservationEpoch == currentObservationEpoch
}

internal fun chatAgentLooksLikePagerOrCarouselTokens(tokens: String): Boolean {
    return listOf(
        "viewpager",
        "view_pager",
        "pager",
        "carousel",
        "banner",
        "slider",
        "slideshow",
    ).any { token -> tokens.contains(token) }
}

internal fun chatAgentShouldDemoteProminentPagerCandidate(
    tokens: String,
    top: Int,
    width: Int,
    height: Int,
    screenWidth: Int,
    screenHeight: Int,
): Boolean {
    if (!chatAgentLooksLikePagerOrCarouselTokens(tokens)) return false
    val nearTop = top < (screenHeight * 0.42f).toInt()
    val largeCard = width >= (screenWidth * 0.72f).toInt() && height >= (screenHeight * 0.12f).toInt()
    return nearTop && largeCard
}

internal data class ChatAgentUiSnapshot(
    val displayId: Int,
    val currentUi: String,
    val packageName: String?,
    val hierarchy: String,
    val elements: List<VScreenElement>,
    val screenshot: VImage?,
)

internal sealed interface ChatAgentNativeRequest {
    data class ObserveUi(
        val displayId: Int,
        val query: String?,
        val clickable: Boolean?,
        val editable: Boolean?,
        val scrollable: Boolean?,
        val enabled: Boolean?,
        val limit: Int,
        val includeScreenshot: Boolean,
        val includeHierarchy: Boolean,
    ) : ChatAgentNativeRequest

    data class ReadPageContent(
        val displayId: Int,
        val mode: String,
    ) : ChatAgentNativeRequest

    data class Tap(
        val displayId: Int,
        val element: VScreenElement?,
        val x: Int?,
        val y: Int?,
        val textQuery: String?,
    ) : ChatAgentNativeRequest

    data class LongPress(
        val displayId: Int,
        val element: VScreenElement?,
        val x: Int?,
        val y: Int?,
        val textQuery: String?,
        val durationMs: Long,
    ) : ChatAgentNativeRequest

    data class InputText(
        val displayId: Int,
        val text: String,
        val element: VScreenElement?,
        val x: Int?,
        val y: Int?,
        val textQuery: String?,
    ) : ChatAgentNativeRequest

    data class Swipe(
        val displayId: Int,
        val direction: String?,
        val startX: Int?,
        val startY: Int?,
        val endX: Int?,
        val endY: Int?,
        val durationMs: Long,
    ) : ChatAgentNativeRequest

    data class PressKey(
        val displayId: Int,
        val key: String,
    ) : ChatAgentNativeRequest

    data class Wait(
        val seconds: Int,
    ) : ChatAgentNativeRequest

    data class VerifyUi(
        val displayId: Int,
        val textPresent: String?,
        val textAbsent: String?,
        val viewIdContains: String?,
        val packageNameContains: String?,
        val activityContains: String?,
        val limit: Int,
        val includeHierarchy: Boolean,
    ) : ChatAgentNativeRequest

    data class LookupApp(
        val query: String,
        val maxResults: Int,
        val launchableOnly: Boolean,
    ) : ChatAgentNativeRequest

    data class LaunchApp(
        val displayId: Int,
        val appQuery: String?,
        val packageName: String?,
    ) : ChatAgentNativeRequest
}

internal class ChatAgentNativeToolExecutor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    fun prepare(
        definition: ChatAgentToolDefinition,
        toolCall: ChatToolCall,
        artifactStore: ChatAgentArtifactStore,
    ): ChatPreparedToolItem {
        val helperId = definition.nativeHelperId
            ?: return ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = "Native helper metadata is missing for `${definition.title}`.",
                )
            )

        return try {
            val arguments = parseArguments(toolCall.argumentsJson)
            val request = when (helperId) {
                ChatAgentNativeHelperId.OBSERVE_UI -> prepareObserveUi(arguments)
                ChatAgentNativeHelperId.READ_PAGE_CONTENT -> prepareReadPageContent(arguments)
                ChatAgentNativeHelperId.TAP -> prepareTap(arguments, artifactStore)
                ChatAgentNativeHelperId.LONG_PRESS -> prepareLongPress(arguments, artifactStore)
                ChatAgentNativeHelperId.INPUT_TEXT -> prepareInputText(arguments, artifactStore)
                ChatAgentNativeHelperId.SWIPE -> prepareSwipe(arguments)
                ChatAgentNativeHelperId.PRESS_KEY -> preparePressKey(arguments)
                ChatAgentNativeHelperId.WAIT -> prepareWait(arguments)
                ChatAgentNativeHelperId.VERIFY_UI -> prepareVerifyUi(arguments)
                ChatAgentNativeHelperId.LOOKUP_APP -> prepareLookupApp(arguments)
                ChatAgentNativeHelperId.LAUNCH_APP -> prepareLaunchApp(arguments)
            }
            ChatPreparedToolItem.NativeReady(
                toolCall = toolCall,
                definition = definition,
                request = request,
                missingPermissions = requiredPermissionsFor(helperId)
                    .filterNot { PermissionManager.isGranted(appContext, it) },
            )
        } catch (throwable: Throwable) {
            ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = chatAgentAppendNextStep(
                        baseMessage = throwable.message?.ifBlank { null }
                            ?: "Failed to parse arguments for `${definition.title}`.",
                        nextStep = chatAgentNativeRecoveryHint(helperId),
                    ),
                )
            )
        }
    }

    suspend fun execute(
        ready: ChatPreparedToolItem.NativeReady,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        return when (val request = ready.request) {
            is ChatAgentNativeRequest.ObserveUi -> executeObserveUi(ready, request, artifactStore)
            is ChatAgentNativeRequest.ReadPageContent -> executeReadPageContent(ready, request, artifactStore)
            is ChatAgentNativeRequest.Tap -> executeTap(ready, request)
            is ChatAgentNativeRequest.LongPress -> executeLongPress(ready, request)
            is ChatAgentNativeRequest.InputText -> executeInputText(ready, request)
            is ChatAgentNativeRequest.Swipe -> executeSwipe(ready, request, artifactStore)
            is ChatAgentNativeRequest.PressKey -> executePressKey(ready, request)
            is ChatAgentNativeRequest.Wait -> executeWait(ready, request)
            is ChatAgentNativeRequest.VerifyUi -> executeVerifyUi(ready, request, artifactStore)
            is ChatAgentNativeRequest.LookupApp -> executeLookupApp(ready, request)
            is ChatAgentNativeRequest.LaunchApp -> executeLaunchApp(ready, request)
        }
    }

    private fun parseArguments(rawArgumentsJson: String): JsonObject {
        if (rawArgumentsJson.isBlank()) return buildJsonObject { }
        return json.parseToJsonElement(rawArgumentsJson) as? JsonObject
            ?: throw IllegalArgumentException("Tool arguments must be a JSON object.")
    }

    private fun prepareObserveUi(arguments: JsonObject): ChatAgentNativeRequest.ObserveUi {
        return ChatAgentNativeRequest.ObserveUi(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            query = stringArg(arguments, "query"),
            clickable = booleanArg(arguments, "clickable"),
            editable = booleanArg(arguments, "editable"),
            scrollable = booleanArg(arguments, "scrollable"),
            enabled = booleanArg(arguments, "enabled"),
            limit = (intArg(arguments, "limit") ?: DEFAULT_ELEMENT_HANDLE_LIMIT)
                .coerceIn(1, MAX_ELEMENT_HANDLE_LIMIT),
            includeScreenshot = booleanArg(arguments, "include_screenshot") ?: false,
            includeHierarchy = booleanArg(arguments, "include_hierarchy") ?: true,
        )
    }

    private fun prepareReadPageContent(arguments: JsonObject): ChatAgentNativeRequest.ReadPageContent {
        return ChatAgentNativeRequest.ReadPageContent(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            mode = normalizeReadPageContentMode(stringArg(arguments, "mode")),
        )
    }

    private fun prepareTap(
        arguments: JsonObject,
        artifactStore: ChatAgentArtifactStore,
    ): ChatAgentNativeRequest.Tap {
        val element = resolveElement(arguments, artifactStore)
        val x = intArg(arguments, "x")
        val y = intArg(arguments, "y")
        val textQuery = stringArg(arguments, "text_query")
        if (element == null && (x == null || y == null) && textQuery.isNullOrBlank()) {
            throw IllegalArgumentException("Tap requires `element`, `x`+`y`, or `text_query`.")
        }
        return ChatAgentNativeRequest.Tap(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            element = element,
            x = x,
            y = y,
            textQuery = textQuery,
        )
    }

    private fun prepareLongPress(
        arguments: JsonObject,
        artifactStore: ChatAgentArtifactStore,
    ): ChatAgentNativeRequest.LongPress {
        val base = prepareTap(arguments, artifactStore)
        return ChatAgentNativeRequest.LongPress(
            displayId = base.displayId,
            element = base.element,
            x = base.x,
            y = base.y,
            textQuery = base.textQuery,
            durationMs = (intArg(arguments, "duration_ms") ?: 900).toLong().coerceIn(300L, 5_000L),
        )
    }

    private fun prepareInputText(
        arguments: JsonObject,
        artifactStore: ChatAgentArtifactStore,
    ): ChatAgentNativeRequest.InputText {
        val text = stringArg(arguments, "text")
            ?: throw IllegalArgumentException("Input text requires a non-empty `text`.")
        val element = resolveElement(arguments, artifactStore)
        return ChatAgentNativeRequest.InputText(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            text = text,
            element = element,
            x = intArg(arguments, "x"),
            y = intArg(arguments, "y"),
            textQuery = stringArg(arguments, "text_query"),
        )
    }

    private fun prepareSwipe(arguments: JsonObject): ChatAgentNativeRequest.Swipe {
        val direction = stringArg(arguments, "direction")?.normalizeToken()
        val startX = intArg(arguments, "start_x")
        val startY = intArg(arguments, "start_y")
        val endX = intArg(arguments, "end_x")
        val endY = intArg(arguments, "end_y")
        if (direction == null && listOf(startX, startY, endX, endY).any { it == null }) {
            throw IllegalArgumentException("Swipe requires `direction` or all of `start_x`, `start_y`, `end_x`, and `end_y`.")
        }
        return ChatAgentNativeRequest.Swipe(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            direction = direction,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = (intArg(arguments, "duration_ms") ?: 300).toLong().coerceIn(100L, 4_000L),
        )
    }

    private fun preparePressKey(arguments: JsonObject): ChatAgentNativeRequest.PressKey {
        val key = stringArg(arguments, "key")?.normalizeToken()
            ?: throw IllegalArgumentException("Press key requires a non-empty `key`.")
        if (key !in SUPPORTED_KEY_ACTIONS) {
            throw IllegalArgumentException("Unsupported key `$key`. Allowed keys: ${SUPPORTED_KEY_ACTIONS.joinToString()}.")
        }
        return ChatAgentNativeRequest.PressKey(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            key = key,
        )
    }

    private fun prepareWait(arguments: JsonObject): ChatAgentNativeRequest.Wait {
        val seconds = (intArg(arguments, "seconds") ?: 1).coerceIn(1, 60)
        return ChatAgentNativeRequest.Wait(seconds = seconds)
    }

    private fun prepareVerifyUi(arguments: JsonObject): ChatAgentNativeRequest.VerifyUi {
        val request = ChatAgentNativeRequest.VerifyUi(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            textPresent = stringArg(arguments, "text_present"),
            textAbsent = stringArg(arguments, "text_absent"),
            viewIdContains = stringArg(arguments, "view_id_contains"),
            packageNameContains = stringArg(arguments, "package_name_contains"),
            activityContains = stringArg(arguments, "activity_contains"),
            limit = (intArg(arguments, "limit") ?: 4).coerceIn(1, 6),
            includeHierarchy = booleanArg(arguments, "include_hierarchy") ?: false,
        )
        if (
            request.textPresent == null &&
            request.textAbsent == null &&
            request.viewIdContains == null &&
            request.packageNameContains == null &&
            request.activityContains == null
        ) {
            throw IllegalArgumentException("Verify UI requires at least one expectation such as `text_present`, `text_absent`, `view_id_contains`, `package_name_contains`, or `activity_contains`.")
        }
        return request
    }

    private fun prepareLookupApp(arguments: JsonObject): ChatAgentNativeRequest.LookupApp {
        val query = stringArg(arguments, "query")
            ?: throw IllegalArgumentException("Lookup app requires a non-empty `query`.")
        return ChatAgentNativeRequest.LookupApp(
            query = query,
            maxResults = (intArg(arguments, "max_results") ?: 5).coerceIn(1, 8),
            launchableOnly = booleanArg(arguments, "launchable_only") ?: true,
        )
    }

    private fun prepareLaunchApp(arguments: JsonObject): ChatAgentNativeRequest.LaunchApp {
        val packageName = stringArg(arguments, "package_name")
        val appQuery = stringArg(arguments, "app_query")
        if (packageName == null && appQuery == null) {
            throw IllegalArgumentException("Launch app requires `package_name` or `app_query`.")
        }
        return ChatAgentNativeRequest.LaunchApp(
            displayId = intArg(arguments, "display_id")?.coerceAtLeast(0) ?: 0,
            appQuery = appQuery,
            packageName = packageName,
        )
    }

    private suspend fun executeObserveUi(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.ObserveUi,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        val observation = captureObservation(
            displayId = request.displayId,
            includeHierarchy = request.includeHierarchy,
            includeScreenshot = request.includeScreenshot,
        )
        val filtered = filterElements(
            elements = observation.snapshot.elements,
            query = request.query,
            clickable = request.clickable,
            editable = request.editable,
            scrollable = request.scrollable,
            enabled = request.enabled,
        )
        val insights = deriveObservationInsights(observation.snapshot)
        val prioritized = selectElementsForExposure(
            snapshot = observation.snapshot,
            request = request,
            filtered = filtered,
            insights = insights,
        )
            .take(request.limit)
        val callId = ready.toolCall.id ?: "call_native_observe"
        val elementReferences = prioritized.mapIndexedNotNull { index, element ->
            artifactStore.store(callId, "element_%02d".format(index + 1), element)
        }
        val snapshotReference = artifactStore.store(callId, "snapshot", observation.snapshot)
        val screenshotReference = observation.snapshot.screenshot?.let {
            artifactStore.store(callId, "screenshot", it)
        }
        val artifacts = elementReferences + listOfNotNull(snapshotReference, screenshotReference)
        val referencesByElement = prioritized.zip(elementReferences).toMap()
        rememberObservationState(
            artifactStore = artifactStore,
            toolName = ready.toolCall.name,
            snapshot = observation.snapshot,
            screenRole = insights.screenRole,
            readableBlocks = insights.readablePreview,
            snapshotHandle = snapshotReference?.handle,
            primaryContentHandle = insights.contentCandidates.firstOrNull()
                ?.let(referencesByElement::get)
                ?.handle,
            primaryScrollableHandle = insights.primaryScrollable
                ?.let(referencesByElement::get)
                ?.handle,
        )

        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = ChatToolResultStatus.SUCCESS,
            summary = ready.definition.title,
            outputText = buildObserveUiOutputText(
                observation = observation,
                request = request,
                insights = insights,
                exposedElements = prioritized,
                elementReferences = elementReferences,
                snapshotReference = snapshotReference,
                screenshotReference = screenshotReference,
            ),
            artifacts = artifacts,
        )
    }

    private suspend fun executeReadPageContent(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.ReadPageContent,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        val observation = captureObservation(
            displayId = request.displayId,
            includeHierarchy = false,
            includeScreenshot = false,
        )
        val insights = deriveObservationInsights(observation.snapshot)
        val collectedBlocks = collectReadableTextBlocks(
            snapshot = observation.snapshot,
            mode = request.mode,
            insights = insights,
        )
        rememberObservationState(
            artifactStore = artifactStore,
            toolName = ready.toolCall.name,
            snapshot = observation.snapshot,
            screenRole = insights.screenRole,
            readableBlocks = collectedBlocks,
        )
        val stopReason = "Read-only snapshot captured from the current visible node tree. This helper never scrolls; if more content is needed, the agent must explicitly swipe and then read again."

        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = if (collectedBlocks.isEmpty()) ChatToolResultStatus.ERROR else ChatToolResultStatus.SUCCESS,
            summary = ready.definition.title,
            outputText = buildReadPageContentOutputText(
                observation = observation,
                insights = insights,
                request = request,
                pagesScanned = 1,
                stopReason = stopReason,
                collectedBlocks = collectedBlocks,
            ),
        )
    }


    private suspend fun executeTap(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.Tap,
    ): ChatToolResult {
        val tools = AgentTools(buildExecutionContext(request.displayId))
        val rawResult = when {
            request.element != null -> clickLiveElement(
                tools = tools,
                displayId = request.displayId,
                element = request.element,
            )
            request.x != null && request.y != null -> tools.clickPoint(request.x, request.y)
            request.textQuery != null -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    "Failed: Text-based tap fallback requires Android 11 or above."
                } else {
                    tools.clickElement(request.textQuery)
                }
            }
            else -> "Failed: No tap target was provided."
        }
        return actionResult(
            ready = ready,
            rawResult = rawResult,
            successHint = "Tap dispatched. Re-observe the UI before the next screen action. If this tap was meant to open content, do not scroll until that fresh observation tells you whether navigation succeeded.",
        )
    }

    private suspend fun executeLongPress(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.LongPress,
    ): ChatToolResult {
        val tools = AgentTools(buildExecutionContext(request.displayId))
        val rawResult = when {
            request.element != null -> tools.longPress(
                request.element.centerX,
                request.element.centerY,
                request.durationMs,
            )
            request.x != null && request.y != null -> tools.longPress(
                request.x,
                request.y,
                request.durationMs,
            )
            request.textQuery != null -> "Failed: Long press currently requires `element` or coordinates."
            else -> "Failed: No long-press target was provided."
        }
        return actionResult(
            ready = ready,
            rawResult = rawResult,
            successHint = "Long press dispatched. Re-observe the UI before continuing.",
        )
    }

    private suspend fun executeInputText(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.InputText,
    ): ChatToolResult {
        val tools = AgentTools(buildExecutionContext(request.displayId))
        val focusResult = when {
            request.element != null -> tools.clickPoint(request.element.centerX, request.element.centerY)
            request.x != null && request.y != null -> tools.clickPoint(request.x, request.y)
            request.textQuery != null -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    "Failed: Text-based focus fallback requires Android 11 or above."
                } else {
                    tools.clickElement(request.textQuery)
                }
            }
            else -> null
        }
        if (focusResult != null && !focusResult.startsWith("Success", ignoreCase = true)) {
            return actionResult(
                ready = ready,
                rawResult = "Failed: Could not focus the target field. $focusResult",
            )
        }
        if (focusResult != null) {
            delay(250)
        }
        return actionResult(
            ready = ready,
            rawResult = tools.inputText(request.text),
            successHint = "Text input dispatched. Verify the field contents with a fresh observation before finishing.",
        )
    }

    private suspend fun executeSwipe(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.Swipe,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        val currentObservationEpoch = artifactStore.recallSessionValue<Int>(SESSION_KEY_OBSERVATION_EPOCH)
        val lastActionObservationEpoch = artifactStore.recallSessionValue<Int>(SESSION_KEY_LAST_ACTION_OBSERVATION_EPOCH)
        val lastSwipeDirection = artifactStore.recallSessionValue<String>(SESSION_KEY_LAST_SWIPE_DIRECTION)
        val lastActionToolName = artifactStore.recallSessionValue<String>(SESSION_KEY_LAST_ACTION_TOOL_NAME)
        if (chatAgentShouldBlockRepeatedSwipe(
                lastActionToolName = lastActionToolName,
                lastSwipeDirection = lastSwipeDirection,
                lastActionObservationEpoch = lastActionObservationEpoch,
                currentObservationEpoch = currentObservationEpoch,
                requestedDirection = request.direction,
            )
        ) {
            val directionLabel = request.direction ?: "same-direction"
            return ChatToolResult(
                callId = ready.toolCall.id,
                name = ready.toolCall.name,
                status = ChatToolResultStatus.ERROR,
                summary = ready.definition.title,
                outputText = chatAgentAppendNextStep(
                    baseMessage = "Blocked repeated `$directionLabel` swipe because there has been no fresh read-only observation since the previous same-direction swipe.",
                    nextStep = "call `vflow_agent_observe_ui` or `vflow_agent_read_page_content` first, inspect the updated screen, then decide whether another swipe is still needed.",
                ),
            )
        }
        val tools = AgentTools(buildExecutionContext(request.displayId))
        val rawResult = if (request.direction != null) {
            tools.scroll(request.direction)
        } else {
            tools.swipe(
                startX = request.startX ?: 0,
                startY = request.startY ?: 0,
                endX = request.endX ?: 0,
                endY = request.endY ?: 0,
                duration = request.durationMs,
            )
        }
        return actionResult(
            ready = ready,
            rawResult = rawResult,
            successHint = "Swipe dispatched. Observe the refreshed screen before deciding the next action, and do not repeat the same-direction swipe without that fresh observation.",
        ).also { result ->
            if (result.status == ChatToolResultStatus.SUCCESS) {
                artifactStore.rememberSessionValue(SESSION_KEY_LAST_ACTION_TOOL_NAME, ready.toolCall.name)
                artifactStore.rememberSessionValue(
                    SESSION_KEY_LAST_ACTION_OBSERVATION_EPOCH,
                    currentObservationEpoch,
                )
                request.direction?.let {
                    artifactStore.rememberSessionValue(SESSION_KEY_LAST_SWIPE_DIRECTION, it.normalizeToken())
                }
            }
        }
    }

    private suspend fun executePressKey(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.PressKey,
    ): ChatToolResult {
        val rawResult = performKeyAction(
            displayId = request.displayId,
            key = request.key,
        )
        return actionResult(
            ready = ready,
            rawResult = rawResult,
            successHint = "Key action dispatched. Observe the screen again before continuing.",
        )
    }

    private suspend fun executeWait(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.Wait,
    ): ChatToolResult {
        val tools = AgentTools(buildExecutionContext(displayId = 0))
        return actionResult(
            ready = ready,
            rawResult = tools.wait(request.seconds),
            successHint = "Time elapsed. Re-observe the UI before the next action instead of guessing that loading or navigation has finished.",
        )
    }

    private suspend fun executeVerifyUi(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.VerifyUi,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        val observation = captureObservation(
            displayId = request.displayId,
            includeHierarchy = request.includeHierarchy,
            includeScreenshot = false,
        )
        val insights = deriveObservationInsights(observation.snapshot)
        val failures = mutableListOf<String>()
        val matchedElements = linkedSetOf<VScreenElement>()

        request.packageNameContains?.let { expected ->
            val packageName = observation.snapshot.packageName.orEmpty()
            if (!packageName.contains(expected, ignoreCase = true)) {
                failures += "Package `$packageName` does not contain `$expected`."
            }
        }
        request.activityContains?.let { expected ->
            if (!observation.snapshot.currentUi.contains(expected, ignoreCase = true)) {
                failures += "Current UI `${observation.snapshot.currentUi}` does not contain `$expected`."
            }
        }
        request.textPresent?.let { expected ->
            val matches = filterElements(
                elements = observation.snapshot.elements,
                query = expected,
                clickable = null,
                editable = null,
                scrollable = null,
                enabled = null,
            )
            if (matches.isEmpty()) {
                failures += "No visible element matches `text_present=$expected`."
            } else {
                matchedElements += matches
            }
        }
        request.textAbsent?.let { unexpected ->
            val matches = filterElements(
                elements = observation.snapshot.elements,
                query = unexpected,
                clickable = null,
                editable = null,
                scrollable = null,
                enabled = null,
            )
            if (matches.isNotEmpty()) {
                failures += "Unexpected visible element still matches `text_absent=$unexpected`."
                matchedElements += matches
            }
        }
        request.viewIdContains?.let { expected ->
            val matches = observation.snapshot.elements.filter { element ->
                element.viewId?.contains(expected, ignoreCase = true) == true
            }
            if (matches.isEmpty()) {
                failures += "No visible element view id contains `$expected`."
            } else {
                matchedElements += matches
            }
        }

        val prioritizedMatches = prioritizeElements(matchedElements.toList()).take(request.limit)
        val callId = ready.toolCall.id ?: "call_native_verify"
        val elementReferences = prioritizedMatches.mapIndexedNotNull { index, element ->
            artifactStore.store(callId, "matched_element_%02d".format(index + 1), element)
        }
        val artifacts = elementReferences
        rememberObservationState(
            artifactStore = artifactStore,
            toolName = ready.toolCall.name,
            snapshot = observation.snapshot,
            screenRole = insights.screenRole,
            readableBlocks = insights.readablePreview,
        )

        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = if (failures.isEmpty()) ChatToolResultStatus.SUCCESS else ChatToolResultStatus.ERROR,
            summary = ready.definition.title,
            outputText = buildVerifyUiOutputText(
                observation = observation,
                insights = insights,
                request = request,
                failures = failures,
                elementReferences = elementReferences,
            ),
            artifacts = artifacts,
        )
    }

    private suspend fun executeLookupApp(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.LookupApp,
    ): ChatToolResult {
        val matches = InstalledAppSearchSupport.searchApps(
            context = appContext,
            query = request.query,
            userId = null,
            launchableOnly = request.launchableOnly,
            maxResults = request.maxResults,
        )
        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = if (matches.isEmpty()) ChatToolResultStatus.ERROR else ChatToolResultStatus.SUCCESS,
            summary = ready.definition.title,
            outputText = buildLookupAppOutputText(
                query = request.query,
                matches = matches,
                launchableOnly = request.launchableOnly,
            ),
        )
    }

    private suspend fun executeLaunchApp(
        ready: ChatPreparedToolItem.NativeReady,
        request: ChatAgentNativeRequest.LaunchApp,
    ): ChatToolResult {
        val resolvedMatch = when {
            !request.packageName.isNullOrBlank() -> null
            request.appQuery.isNullOrBlank() -> null
            else -> InstalledAppSearchSupport.searchApps(
                context = appContext,
                query = request.appQuery,
                userId = null,
                launchableOnly = true,
                maxResults = 1,
            ).firstOrNull()
        }
        val launchTarget = request.packageName
            ?: resolvedMatch?.candidate?.packageName
            ?: request.appQuery
            ?: ""
        val tools = AgentTools(buildExecutionContext(request.displayId))
        val rawResult = tools.launchApp(launchTarget)

        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = if (rawResult.startsWith("Success", ignoreCase = true)) {
                ChatToolResultStatus.SUCCESS
            } else {
                ChatToolResultStatus.ERROR
            },
            summary = ready.definition.title,
            outputText = buildLaunchAppOutputText(
                rawResult = rawResult,
                resolvedMatch = resolvedMatch,
                requestedQuery = request.appQuery,
                explicitPackage = request.packageName,
            ),
        )
    }

    private fun actionResult(
        ready: ChatPreparedToolItem.NativeReady,
        rawResult: String,
        successHint: String? = null,
    ): ChatToolResult {
        val success = rawResult.startsWith("Success", ignoreCase = true)
        return ChatToolResult(
            callId = ready.toolCall.id,
            name = ready.toolCall.name,
            status = if (success) ChatToolResultStatus.SUCCESS else ChatToolResultStatus.ERROR,
            summary = ready.definition.title,
            outputText = if (success) {
                buildString {
                    append(rawResult)
                    if (!successHint.isNullOrBlank()) {
                        append("\n\n")
                        append(successHint)
                    }
                }.trim()
            } else {
                chatAgentAppendNextStep(
                    baseMessage = rawResult,
                    nextStep = ready.definition.nativeHelperId?.let(::chatAgentNativeRecoveryHint),
                )
            },
        )
    }

    private suspend fun captureObservation(
        displayId: Int,
        includeHierarchy: Boolean,
        includeScreenshot: Boolean,
    ): UiObservation {
        val root = requireAccessibilityRoot(displayId)
        val elements = collectVisibleElements(root)
        val screenshot = if (includeScreenshot) {
            val screenResult = AgentUtils.captureScreen(appContext, buildExecutionContext(displayId))
            screenResult.path?.takeIf { it.isNotBlank() }?.let(::VImage)
        } else {
            null
        }
        val packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() }
        val currentUi = resolveCurrentUi(displayId, packageName)
        val hierarchy = if (includeHierarchy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AgentUtils.dumpHierarchy(appContext, displayId)
        } else {
            ""
        }
        return UiObservation(
            snapshot = ChatAgentUiSnapshot(
                displayId = displayId,
                currentUi = currentUi,
                packageName = packageName,
                hierarchy = hierarchy,
                elements = elements,
                screenshot = screenshot,
            ),
        )
    }

    private fun requireAccessibilityRoot(displayId: Int): AccessibilityNodeInfo {
        val service = ServiceStateBus.getAccessibilityService()
            ?: throw IllegalStateException("Accessibility service is not running.")
        return if (displayId > 0) {
            service.windows.firstOrNull { it.displayId == displayId }?.root
                ?: throw IllegalStateException("No accessibility window is available on display $displayId.")
        } else {
            service.rootInActiveWindow
                ?: throw IllegalStateException("No active accessibility window is available.")
        }
    }

    private fun collectVisibleElements(root: AccessibilityNodeInfo): List<VScreenElement> {
        val elements = mutableListOf<VScreenElement>()
        val screenBounds = Rect()
        root.getBoundsInScreen(screenBounds)

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (!node.isVisibleToUser) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() < 1 || bounds.height() < 1) return
            if (!screenBounds.isEmpty && !Rect.intersects(bounds, screenBounds)) return

            val hasContent = !node.text.isNullOrBlank() ||
                !node.contentDescription.isNullOrBlank() ||
                !node.viewIdResourceName.isNullOrBlank()
            val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isFocusable
            if (hasContent || isInteractive) {
                elements += VScreenElement.fromAccessibilityNode(node, depth)
            }

            for (childIndex in 0 until node.childCount) {
                val child = node.getChild(childIndex) ?: continue
                try {
                    walk(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        walk(root, 0)
        return elements
    }

    private fun filterElements(
        elements: List<VScreenElement>,
        query: String?,
        clickable: Boolean?,
        editable: Boolean?,
        scrollable: Boolean?,
        enabled: Boolean?,
    ): List<VScreenElement> {
        return elements.filter { element ->
            (query == null || elementMatchesQuery(element, query)) &&
                (clickable == null || element.isClickable == clickable) &&
                (editable == null || element.isEditable == editable) &&
                (scrollable == null || element.isScrollable == scrollable) &&
                (enabled == null || element.isEnabled == enabled)
        }
    }

    private fun prioritizeElements(elements: List<VScreenElement>): List<VScreenElement> {
        return elements.sortedWith(
            compareBy<VScreenElement>(
                { !(it.isClickable || it.isEditable || it.isScrollable) },
                { it.bounds.top },
                { it.bounds.left },
                { it.depth },
            )
        )
    }

    private fun selectElementsForExposure(
        snapshot: ChatAgentUiSnapshot,
        request: ChatAgentNativeRequest.ObserveUi,
        filtered: List<VScreenElement>,
        insights: UiObservationInsights,
    ): List<VScreenElement> {
        val selected = linkedSetOf<VScreenElement>()
        val filteredPrioritized = prioritizeElements(filtered)
        val allPrioritized = prioritizeElements(snapshot.elements)
        val filterActive = request.query != null ||
            request.clickable != null ||
            request.editable != null ||
            request.scrollable != null ||
            request.enabled != null

        fun appendCandidates(candidates: List<VScreenElement>) {
            candidates.forEach { candidate ->
                if (selected.size < request.limit) {
                    selected += candidate
                }
            }
        }

        if (filterActive) {
            appendCandidates(filteredPrioritized)
        }
        appendCandidates(listOfNotNull(insights.primaryScrollable))
        appendCandidates(insights.contentCandidates)
        if (!filterActive) {
            appendCandidates(insights.chromeCandidates)
        }
        appendCandidates(allPrioritized)
        return selected.toList()
    }

    private fun elementMatchesQuery(
        element: VScreenElement,
        query: String,
    ): Boolean {
        val normalizedQuery = normalizeMatchValue(query)
        if (normalizedQuery.isBlank()) return true
        val candidates = listOf(
            element.text,
            element.contentDescription,
            element.viewId?.substringAfter(":id/"),
            element.className?.substringAfterLast('.'),
        )
        return candidates.any { candidate ->
            normalizeMatchValue(candidate).contains(normalizedQuery)
        }
    }

    private fun normalizeMatchValue(value: String?): String {
        return Regex("""[\p{L}\p{N}]+""")
            .findAll(value.orEmpty().lowercase(Locale.ROOT))
            .joinToString(separator = "") { it.value }
    }

    private fun normalizeWhitespace(value: String?): String {
        return value.orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeReadPageContentMode(rawMode: String?): String {
        return when (rawMode?.normalizeToken()) {
            "all",
            "all_text",
            "full" -> "all_text"
            "primary",
            "primary_content",
            "content",
            "article" -> "primary_content"
            else -> "primary_content"
        }
    }

    private fun deriveObservationInsights(snapshot: ChatAgentUiSnapshot): UiObservationInsights {
        val elements = snapshot.elements
        if (elements.isEmpty()) {
            return UiObservationInsights(
                screenRole = "unknown",
                primaryScrollable = null,
                contentCandidates = emptyList(),
                chromeCandidates = emptyList(),
                readablePreview = emptyList(),
            )
        }
        val screenWidth = elements.maxOfOrNull { it.bounds.right }?.coerceAtLeast(1) ?: 1
        val screenHeight = elements.maxOfOrNull { it.bounds.bottom }?.coerceAtLeast(1) ?: 1
        val primaryScrollable = elements
            .filter { it.isScrollable }
            .maxByOrNull { element ->
                scoreScrollableCandidate(
                    element = element,
                    allElements = elements,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                )
            }

        val scoredContentCandidates = elements
            .asSequence()
            .filter { element ->
                (element.isClickable || element.isFocusable) &&
                    !element.isEditable &&
                    !isLikelyChromeElement(element, screenWidth, screenHeight) &&
                    !isLikelyContainerOnly(element, elements)
            }
            .map { element ->
                element to scoreContentCandidate(
                    element = element,
                    allElements = elements,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    primaryScrollable = primaryScrollable,
                )
            }
            .filter { (_, score) -> score > 0.0 }
            .toList()
        val contentCandidates = orderContentCandidates(
            snapshot = snapshot,
            scoredCandidates = scoredContentCandidates,
            screenHeight = screenHeight,
        )
            .distinct()
            .take(5)

        val chromeCandidates = elements
            .filter { element ->
                (element.isClickable || element.isScrollable) &&
                    isLikelyChromeElement(element, screenWidth, screenHeight)
            }
            .sortedWith(compareBy<VScreenElement> { it.bounds.top }.thenBy { it.bounds.left })
            .distinct()
            .take(4)

        val readablePreview = collectReadableTextBlocks(
            snapshot = snapshot,
            mode = "primary_content",
            insights = null,
        ).take(6)

        return UiObservationInsights(
            screenRole = classifyScreenRole(
                snapshot = snapshot,
                elements = elements,
                screenHeight = screenHeight,
                contentCandidates = contentCandidates,
                readablePreview = readablePreview,
            ),
            primaryScrollable = primaryScrollable,
            contentCandidates = contentCandidates,
            chromeCandidates = chromeCandidates,
            readablePreview = readablePreview,
        )
    }

    private fun collectReadableTextBlocks(
        snapshot: ChatAgentUiSnapshot,
        mode: String,
        insights: UiObservationInsights?,
    ): List<String> {
        val elements = snapshot.elements
        if (elements.isEmpty()) return emptyList()
        val screenWidth = elements.maxOfOrNull { it.bounds.right }?.coerceAtLeast(1) ?: 1
        val screenHeight = elements.maxOfOrNull { it.bounds.bottom }?.coerceAtLeast(1) ?: 1
        val primaryScrollable = insights?.primaryScrollable ?: elements
            .filter { it.isScrollable }
            .maxByOrNull { it.width * it.height }

        data class TextBlock(
            val text: String,
            val score: Double,
            val top: Int,
            val left: Int,
        )

        val blocks = elements.mapNotNull { element ->
            val text = normalizeWhitespace(element.text ?: element.contentDescription)
            if (text.length < 2) return@mapNotNull null

            var score = text.length.toDouble()
            if (!element.isClickable) score += 8
            if (element.bounds.top > (screenHeight * 0.15f).toInt()) score += 6
            if (element.width > (screenWidth * 0.35f).toInt()) score += 4
            if (primaryScrollable != null && isInsideBounds(element, primaryScrollable)) score += 5
            if (looksLikeChromeText(text) || isLikelyChromeElement(element, screenWidth, screenHeight)) {
                score -= 18
            }
            if (mode == "primary_content" && text.length < 4) {
                score -= 6
            }
            if (mode == "primary_content" && element.isClickable && text.length < 10) {
                score -= 8
            }
            if (mode == "primary_content" && isLikelyBottomChrome(element, screenHeight)) {
                score -= 12
            }
            if (score <= 0) return@mapNotNull null

            TextBlock(
                text = text,
                score = score,
                top = element.bounds.top,
                left = element.bounds.left,
            )
        }

        return blocks
            .sortedWith(compareByDescending<TextBlock> { it.score }.thenBy { it.top }.thenBy { it.left })
            .map { it.text }
            .distinctBy(::normalizeMatchValue)
            .take(if (mode == "all_text") 80 else 40)
            .sortedWith(compareBy<String> { text ->
                blocks.firstOrNull { it.text == text }?.top ?: Int.MAX_VALUE
            }.thenBy { text ->
                blocks.firstOrNull { it.text == text }?.left ?: Int.MAX_VALUE
            })
    }

    private fun orderContentCandidates(
        snapshot: ChatAgentUiSnapshot,
        scoredCandidates: List<Pair<VScreenElement, Double>>,
        screenHeight: Int,
    ): List<VScreenElement> {
        if (scoredCandidates.isEmpty()) return emptyList()
        val activityName = snapshot.currentUi
            .substringAfterLast('/')
            .substringAfterLast('.')
            .lowercase(Locale.ROOT)
        val topSafeInset = (screenHeight * 0.14f).toInt()
        val feedLike = isLikelyFeedActivityName(activityName) ||
            scoredCandidates.count { (element, _) -> element.bounds.top >= topSafeInset } >= 2

        val ordered = if (feedLike) {
            scoredCandidates.sortedWith(
                compareBy<Pair<VScreenElement, Double>> { (element, _) ->
                    if (element.bounds.top < topSafeInset) 1 else 0
                }.thenBy { (element, _) ->
                    element.bounds.top
                }.thenBy { (element, _) ->
                    element.bounds.left
                }.thenByDescending { (_, score) ->
                    score
                }
            )
        } else {
            scoredCandidates.sortedWith(
                compareByDescending<Pair<VScreenElement, Double>> { (_, score) ->
                    score
                }.thenBy { (element, _) ->
                    element.bounds.top
                }.thenBy { (element, _) ->
                    element.bounds.left
                }
            )
        }
        return ordered.map { it.first }
    }

    private fun classifyScreenRole(
        snapshot: ChatAgentUiSnapshot,
        elements: List<VScreenElement>,
        screenHeight: Int,
        contentCandidates: List<VScreenElement>,
        readablePreview: List<String>,
    ): String {
        val activityName = snapshot.currentUi
            .substringAfterLast('/')
            .substringAfterLast('.')
            .lowercase(Locale.ROOT)
        when {
            isLikelyDetailActivityName(activityName) -> return "content/detail"
            isLikelyFeedActivityName(activityName) && contentCandidates.isNotEmpty() -> return "feed/list"
        }
        val longTexts = elements.count { element ->
            normalizeWhitespace(element.text).length >= 20 &&
                !isLikelyChromeElement(
                    element = element,
                    screenWidth = elements.maxOfOrNull { it.bounds.right }?.coerceAtLeast(1) ?: 1,
                    screenHeight = screenHeight,
                )
        }
        val topChromeCount = elements.count { element ->
            isLikelyChromeElement(element, elements.maxOfOrNull { it.bounds.right }?.coerceAtLeast(1) ?: 1, screenHeight)
        }
        return when {
            longTexts >= 6 && readablePreview.size >= 6 -> "content/detail"
            contentCandidates.size >= 2 && topChromeCount >= 3 -> "feed/list"
            contentCandidates.isNotEmpty() -> "interactive content"
            else -> "mixed"
        }
    }

    private fun isLikelyDetailActivityName(activityName: String): Boolean {
        return listOf(
            "detail",
            "article",
            "reader",
            "story",
            "newsinfo",
            "webview",
            "browser",
        ).any { token -> activityName.contains(token) }
    }

    private fun isLikelyFeedActivityName(activityName: String): Boolean {
        return listOf(
            "main",
            "home",
            "feed",
            "list",
            "timeline",
            "launcher",
            "tab",
        ).any { token -> activityName.contains(token) }
    }

    private fun scoreContentCandidate(
        element: VScreenElement,
        allElements: List<VScreenElement>,
        screenWidth: Int,
        screenHeight: Int,
        primaryScrollable: VScreenElement?,
    ): Double {
        val areaRatio = (element.width.toDouble() * element.height.toDouble()) /
            (screenWidth.toDouble() * screenHeight.toDouble())
        if (areaRatio <= 0.002 || areaRatio >= 0.75) return -1.0

        var score = 0.0
        val label = semanticLabelFor(element, allElements).orEmpty()
        val elementTokens = elementTokens(element)
        val containingPagerLikeContainer = allElements.any { candidate ->
            candidate !== element &&
                candidate.bounds.left <= element.bounds.left &&
                candidate.bounds.top <= element.bounds.top &&
                candidate.bounds.right >= element.bounds.right &&
                candidate.bounds.bottom >= element.bounds.bottom &&
                chatAgentLooksLikePagerOrCarouselTokens(elementTokens(candidate))
        }
        if (label.length >= 4) score += 5
        if (label.length >= 12) score += 8
        if (element.bounds.top > (screenHeight * 0.15f).toInt()) score += 6
        if (primaryScrollable != null && isInsideBounds(element, primaryScrollable)) score += 5
        if (areaRatio in 0.01..0.25) score += 6
        if (element.childCount > 0) score += 2
        if (looksLikeChromeText(label)) score -= 12
        if (element.bounds.top < (screenHeight * 0.12f).toInt()) score -= 10
        if (isLikelyBottomChrome(element, screenHeight)) score -= 10
        if (element.isScrollable) score -= 12
        if (chatAgentShouldDemoteProminentPagerCandidate(
                tokens = elementTokens,
                top = element.bounds.top,
                width = element.width,
                height = element.height,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        ) {
            score -= 24
        }
        if (containingPagerLikeContainer) score -= 14
        return score
    }

    private fun scoreScrollableCandidate(
        element: VScreenElement,
        allElements: List<VScreenElement>,
        screenWidth: Int,
        screenHeight: Int,
    ): Double {
        var score = (element.width.toDouble() * element.height.toDouble()) / 1000.0
        val tokens = elementTokens(element)
        if (isLikelyChromeElement(element, screenWidth, screenHeight)) score -= 250.0
        if (isLikelyBottomChrome(element, screenHeight)) score -= 120.0
        val label = semanticLabelFor(element, allElements).orEmpty()
        if (looksLikeChromeText(label)) score -= 80.0
        if (element.bounds.top > (screenHeight * 0.24f).toInt()) score -= 40.0
        if (element.childCount >= 6) score += 40.0
        if (chatAgentLooksLikePagerOrCarouselTokens(tokens)) score -= 220.0
        return score
    }

    private fun isLikelyChromeElement(
        element: VScreenElement,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        val tokens = elementTokens(element)

        val idOrClassChrome = listOf(
            "toolbar",
            "appbar",
            "tab",
            "search",
            "menu",
            "message",
            "logo",
            "action",
            "titlebar",
            "navigation",
            "bottomnav",
            "status",
            "shortcut",
        ).any { token -> tokens.contains(token) }

        val topChrome = element.bounds.top < (screenHeight * 0.18f).toInt() &&
            element.height < (screenHeight * 0.14f).toInt()
        val narrowTopPill = topChrome && element.width < (screenWidth * 0.45f).toInt()

        return idOrClassChrome || narrowTopPill
    }

    private fun isLikelyBottomChrome(
        element: VScreenElement,
        screenHeight: Int,
    ): Boolean {
        return element.bounds.top > (screenHeight * 0.82f).toInt() &&
            element.height < (screenHeight * 0.14f).toInt()
    }

    private fun isLikelyContainerOnly(
        element: VScreenElement,
        allElements: List<VScreenElement>,
    ): Boolean {
        val tokens = listOfNotNull(
            element.viewId?.substringAfter(":id/"),
            element.className?.substringAfterLast('.'),
        ).joinToString(separator = " ").lowercase(Locale.ROOT)
        val genericContainer = isGenericContainerShell(tokens)
        return genericContainer && semanticLabelFor(element, allElements).isNullOrBlank()
    }

    private fun isGenericContainerShell(tokens: String): Boolean {
        return listOf(
            "layout",
            "container",
            "recyclerview",
            "viewpager",
            "frame",
            "coordinator",
            "content",
            "root",
            "list",
        ).any { token -> tokens.contains(token) }
    }

    private fun looksLikeChromeText(text: String): Boolean {
        val normalized = normalizeWhitespace(text).lowercase(Locale.ROOT)
        return normalized in setOf(
            "搜索",
            "搜索框",
            "搜索栏",
            "最新",
            "关注",
            "热榜",
            "热评",
            "事件",
            "it号",
            "linux",
            "首页",
            "返回",
            "更多",
            "菜单",
            "消息",
            "我的消息",
            "更多菜单",
        ) || normalized.length <= 2
    }

    private fun isInsideBounds(
        element: VScreenElement,
        container: VScreenElement,
    ): Boolean {
        return element.bounds.left >= container.bounds.left &&
            element.bounds.top >= container.bounds.top &&
            element.bounds.right <= container.bounds.right &&
            element.bounds.bottom <= container.bounds.bottom
    }

    private suspend fun clickLiveElement(
        tools: AgentTools,
        displayId: Int,
        element: VScreenElement,
    ): String {
        val service = ServiceStateBus.getAccessibilityService()
        val root = when {
            service == null -> null
            displayId > 0 -> service.windows.firstOrNull { it.displayId == displayId }?.root
            else -> service.rootInActiveWindow
        }
        val liveNode = root?.let { findBestLiveNodeForElement(it, element) }
        if (liveNode != null) {
            performAccessibilityClick(liveNode)?.let { method ->
                return "Success: Clicked matched live node via $method."
            }
        }
        return tools.clickPoint(element.centerX, element.centerY)
    }

    private fun findBestLiveNodeForElement(
        root: AccessibilityNodeInfo,
        target: VScreenElement,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        val targetText = normalizeMatchValue(target.text)
        val targetDesc = normalizeMatchValue(target.contentDescription)
        val targetId = normalizeMatchValue(target.viewId?.substringAfter(":id/"))
        val targetClass = normalizeMatchValue(target.className?.substringAfterLast('.'))
        val targetCenterX = target.centerX
        val targetCenterY = target.centerY

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    var score = 0
                    val nodeText = normalizeMatchValue(node.text?.toString())
                    val nodeDesc = normalizeMatchValue(node.contentDescription?.toString())
                    val nodeId = normalizeMatchValue(node.viewIdResourceName?.substringAfter(":id/"))
                    val nodeClass = normalizeMatchValue(node.className?.toString()?.substringAfterLast('.'))
                    if (targetId.isNotBlank() && nodeId == targetId) score += 80
                    if (targetText.isNotBlank() && nodeText == targetText) score += 70
                    if (targetDesc.isNotBlank() && nodeDesc == targetDesc) score += 70
                    if (targetClass.isNotBlank() && nodeClass == targetClass) score += 16
                    if (bounds == target.bounds) score += 60
                    val centerDistance = kotlin.math.abs(bounds.centerX() - targetCenterX) +
                        kotlin.math.abs(bounds.centerY() - targetCenterY)
                    score -= centerDistance / 8
                    if (node.isClickable == target.isClickable) score += 8
                    if (node.isFocusable == target.isFocusable) score += 4
                    if (score > bestScore) {
                        bestScore = score
                        bestNode = node
                    }
                }
            }
            for (childIndex in 0 until node.childCount) {
                node.getChild(childIndex)?.let(queue::add)
            }
        }
        return bestNode?.takeIf { bestScore >= 40 }
    }

    private fun performAccessibilityClick(node: AccessibilityNodeInfo): String? {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "accessibility action click"
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return "parent accessibility action click"
            }
            parent = parent.parent
        }
        return null
    }

    private fun elementTokens(element: VScreenElement): String {
        return listOfNotNull(
            element.viewId?.substringAfter(":id/"),
            element.className?.substringAfterLast('.'),
            normalizeWhitespace(element.text),
            normalizeWhitespace(element.contentDescription),
        ).joinToString(separator = " ").lowercase(Locale.ROOT)
    }

    private fun buildObservationFingerprint(
        snapshot: ChatAgentUiSnapshot,
        readableBlocks: List<String>,
    ): String {
        return buildString {
            append(snapshot.currentUi)
            append('|')
            readableBlocks.take(6).forEach { block ->
                append(normalizeMatchValue(block))
                append('|')
            }
        }
    }

    private suspend fun performKeyAction(
        displayId: Int,
        key: String,
    ): String {
        val normalizedKey = key.normalizeToken()
        val service = ServiceStateBus.getAccessibilityService()
        val globalAction = when (normalizedKey) {
            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> null
        }
        if (globalAction != null && displayId == 0 && service != null) {
            return if (service.performGlobalAction(globalAction)) {
                "Success: Performed global action `$normalizedKey`."
            } else {
                "Failed: Global action `$normalizedKey` was rejected."
            }
        }

        val shellKeyCode = when (normalizedKey) {
            "back" -> 4
            "home" -> 3
            "recents" -> 187
            "enter" -> 66
            "search" -> 84
            else -> null
        }
        if (shellKeyCode != null && (ShellManager.isShizukuActive(appContext) || ShellManager.isRootAvailable())) {
            val result = ShellManager.execShellCommand(
                appContext,
                "input ${displayOption(displayId)}keyevent $shellKeyCode",
                ShellManager.ShellMode.AUTO,
            )
            return if (!result.startsWith("Error")) {
                "Success: Sent keyevent `$normalizedKey`."
            } else {
                "Failed: Shell keyevent `$normalizedKey` failed. $result"
            }
        }

        return "Failed: Key `$normalizedKey` is not available on the current device/runtime."
    }

    private suspend fun resolveCurrentUi(
        displayId: Int,
        fallbackPackage: String?,
    ): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> try {
                AgentUtils.getCurrentUIInfo(appContext, displayId)
            } catch (_: Throwable) {
                fallbackPackage ?: "Unknown UI"
            }
            !fallbackPackage.isNullOrBlank() -> fallbackPackage
            else -> ServiceStateBus.lastActivityClassName?.let { activity ->
                listOfNotNull(ServiceStateBus.lastActivityPackageName, activity).joinToString("/")
            } ?: "Unknown UI"
        }
    }

    private fun buildObserveUiOutputText(
        observation: UiObservation,
        request: ChatAgentNativeRequest.ObserveUi,
        insights: UiObservationInsights,
        exposedElements: List<VScreenElement>,
        elementReferences: List<ChatArtifactReference>,
        snapshotReference: ChatArtifactReference?,
        screenshotReference: ChatArtifactReference?,
    ): String {
        val filteredCount = filterElements(
            elements = observation.snapshot.elements,
            query = request.query,
            clickable = request.clickable,
            editable = request.editable,
            scrollable = request.scrollable,
            enabled = request.enabled,
        ).size
        return buildString {
            append("Observed the current UI successfully.")
            append("\nCurrent UI: ")
            append(observation.snapshot.currentUi)
            observation.snapshot.packageName?.let {
                append("\nPackage: ")
                append(it)
            }
            append("\nVisible elements: ")
            append(observation.snapshot.elements.size)
            if (
                request.query != null ||
                request.clickable != null ||
                request.editable != null ||
                request.scrollable != null ||
                request.enabled != null
            ) {
                append("\nFilter matches: ")
                append(filteredCount)
            }
            append("\nLikely screen role: ")
            append(insights.screenRole)
            append(" (node-tree heuristic)")
            val referencesByElement = exposedElements.zip(elementReferences).toMap()
            val firstContentReference = insights.contentCandidates
                .firstOrNull()
                ?.let { element -> referencesByElement[element]?.let { it to element } }
            insights.primaryScrollable?.let { primaryScrollable ->
                referencesByElement[primaryScrollable]?.let { reference ->
                    append("\nPrimary scroll container: ")
                    append(reference.handle)
                    append(" — ")
                    append(describeElement(primaryScrollable, observation.snapshot.elements))
                }
            }
            if (insights.contentCandidates.isNotEmpty()) {
                val contentReferences = insights.contentCandidates
                    .mapNotNull { element -> referencesByElement[element]?.let { it to element } }
                if (contentReferences.isNotEmpty()) {
                    append("\n\nLikely primary content targets:\n")
                    contentReferences.forEachIndexed { index, (reference, element) ->
                        append(index + 1)
                        append(". ")
                        append(reference.handle)
                        append(" — ")
                        append(describeElement(element, observation.snapshot.elements))
                        append("\n")
                    }
                }
            }
            firstContentReference?.let { (reference, element) ->
                append("\nRecommended first content target: ")
                append(reference.handle)
                append(" — ")
                append(describeElement(element, observation.snapshot.elements))
            }
            when {
                insights.screenRole == "feed/list" && firstContentReference != null -> {
                    append("\nScroll recommendation: A ranked primary content target is already visible in the current viewport, so do not scroll before acting on or verifying it.")
                    append("\nRanking note: On feed/list screens, primary content targets are ordered by current viewport position from top to bottom.")
                }
                insights.screenRole == "feed/list" -> {
                    append("\nScroll recommendation: No ranked primary content target is currently visible, so only a bounded scroll followed by a fresh observation is reasonable.")
                }
            }
            if (insights.readablePreview.isNotEmpty()) {
                append("\nReadable text preview:\n")
                insights.readablePreview.forEach { line ->
                    append("- ")
                    append(line)
                    append("\n")
                }
            }
            if (elementReferences.isNotEmpty()) {
                append("\n\nElement handles:\n")
                elementReferences.zip(exposedElements).forEachIndexed { index, (reference, element) ->
                    append(index + 1)
                    append(". ")
                    append(reference.handle)
                    append(" — ")
                    append(describeElement(element, observation.snapshot.elements))
                    append("\n")
                }
            }
            snapshotReference?.let {
                append("\nSnapshot handle: ")
                append(it.handle)
            }
            screenshotReference?.let {
                append("\nScreenshot handle: ")
                append(it.handle)
            }
            if (request.includeHierarchy && observation.snapshot.hierarchy.isNotBlank()) {
                append("\n\nHierarchy:\n")
                append(truncateForDisplay(observation.snapshot.hierarchy, 6_000))
            }
            append("\n\nUse the returned element handles for tap/input/verify instead of guessing labels or coordinates.")
            append(" Prefer the primary content targets above before tabs, search, or toolbar controls when the user asks to open the first item or read the main content.")
            append(" If a matching top-ranked content target is already visible, do not scroll yet.")
            append(" After any tap that should open content, re-observe once before deciding to swipe.")
        }.trim()
    }

    private fun buildReadPageContentOutputText(
        observation: UiObservation,
        insights: UiObservationInsights,
        request: ChatAgentNativeRequest.ReadPageContent,
        pagesScanned: Int,
        stopReason: String,
        collectedBlocks: List<String>,
    ): String {
        return buildString {
            if (collectedBlocks.isEmpty()) {
                append("No readable node-tree text was collected from the current page.")
            } else {
                append("Collected readable node-tree content from the current page.")
            }
            append("\nCurrent UI: ")
            append(observation.snapshot.currentUi)
            observation.snapshot.packageName?.let {
                append("\nPackage: ")
                append(it)
            }
            append("\nLikely screen role: ")
            append(insights.screenRole)
            append(" (node-tree heuristic)")
            append("\nMode: ")
            append(request.mode)
            append("\nRead passes: ")
            append(pagesScanned)
            append("\nStop reason: ")
            append(stopReason)
            append("\nCollected text blocks: ")
            append(collectedBlocks.size)
            when (insights.screenRole) {
                "feed/list" -> append("\nScreen guidance: This still looks like a feed/list screen, not a confirmed article detail page.")
                "content/detail" -> append("\nScreen guidance: This looks like a detail/article-like screen; prefer summarizing or verifying the visible content before deciding whether to scroll.")
            }
            if (collectedBlocks.isNotEmpty()) {
                append("\n\nReadable content:\n")
                collectedBlocks.forEach { block ->
                    append("- ")
                    append(block)
                    append("\n")
                }
            }
            append("\nUse this visible node-tree content for summarization or for deciding whether an explicit swipe is still needed. Prefer this before OCR.")
            append(" If this is still a feed/list screen and the requested top item is visible, tap that visible item before any scroll.")
        }.trim()
    }


    private fun buildVerifyUiOutputText(
        observation: UiObservation,
        insights: UiObservationInsights,
        request: ChatAgentNativeRequest.VerifyUi,
        failures: List<String>,
        elementReferences: List<ChatArtifactReference>,
    ): String {
        return buildString {
            if (failures.isEmpty()) {
                append("UI verification succeeded.")
            } else {
                append("UI verification failed.")
            }
            append("\nCurrent UI: ")
            append(observation.snapshot.currentUi)
            observation.snapshot.packageName?.let {
                append("\nPackage: ")
                append(it)
            }
            append("\nVisible elements: ")
            append(observation.snapshot.elements.size)
            append("\nLikely screen role: ")
            append(insights.screenRole)
            append(" (node-tree heuristic)")

            append("\n\nExpectations:")
            request.textPresent?.let { append("\n- text_present: ").append(it) }
            request.textAbsent?.let { append("\n- text_absent: ").append(it) }
            request.viewIdContains?.let { append("\n- view_id_contains: ").append(it) }
            request.packageNameContains?.let { append("\n- package_name_contains: ").append(it) }
            request.activityContains?.let { append("\n- activity_contains: ").append(it) }

            if (failures.isNotEmpty()) {
                append("\n\nMismatches:\n")
                failures.forEach { failure ->
                    append("- ")
                    append(failure)
                    append("\n")
                }
            }

            if (elementReferences.isNotEmpty()) {
                append("\nMatched element handles:\n")
                elementReferences.forEach { reference ->
                    append("- ")
                    append(reference.handle)
                    append("\n")
                }
            }

            if (request.includeHierarchy && observation.snapshot.hierarchy.isNotBlank()) {
                append("\nHierarchy:\n")
                append(truncateForDisplay(observation.snapshot.hierarchy, 4_000))
            }

            if (failures.isNotEmpty()) {
                append("\n\nUse the mismatches above to choose the smallest corrective action, then verify again instead of repeating the same failing step blindly.")
            }
        }.trim()
    }

    private fun rememberObservationState(
        artifactStore: ChatAgentArtifactStore,
        toolName: String,
        snapshot: ChatAgentUiSnapshot,
        screenRole: String,
        readableBlocks: List<String>,
        snapshotHandle: String? = null,
        primaryContentHandle: String? = null,
        primaryScrollableHandle: String? = null,
    ) {
        val nextEpoch = (artifactStore.recallSessionValue<Int>(SESSION_KEY_OBSERVATION_EPOCH) ?: 0) + 1
        artifactStore.rememberSessionValue(SESSION_KEY_OBSERVATION_EPOCH, nextEpoch)
        artifactStore.rememberSessionValue(SESSION_KEY_LAST_OBSERVATION_TOOL_NAME, toolName)
        artifactStore.rememberSessionValue(
            SESSION_KEY_LAST_OBSERVATION_FINGERPRINT,
            buildObservationFingerprint(snapshot, readableBlocks),
        )
        artifactStore.rememberSessionValue(SESSION_KEY_LAST_SCREEN_ROLE, screenRole)
        snapshotHandle?.let {
            artifactStore.rememberSessionValue(SESSION_KEY_LAST_SNAPSHOT_HANDLE, it)
        }
        primaryContentHandle?.let {
            artifactStore.rememberSessionValue(SESSION_KEY_LAST_PRIMARY_CONTENT_HANDLE, it)
        }
        primaryScrollableHandle?.let {
            artifactStore.rememberSessionValue(SESSION_KEY_LAST_PRIMARY_SCROLLABLE_HANDLE, it)
        }
    }

    private fun buildLookupAppOutputText(
        query: String,
        matches: List<InstalledAppSearchMatch>,
        launchableOnly: Boolean,
    ): String {
        if (matches.isEmpty()) {
            return "No installed app matched `$query` (launchable_only=$launchableOnly)."
        }
        return buildString {
            append("Found ")
            append(matches.size)
            append(" installed app match(es) for `")
            append(query)
            append("`.")
            append("\n\nBest match:\n")
            append("- ")
            append(matches.first().candidate.appName)
            append(" (")
            append(matches.first().candidate.packageName)
            append(")")
            append("\n\nCandidates:\n")
            matches.forEach { match ->
                append("- ")
                append(match.candidate.appName)
                append(" (")
                append(match.candidate.packageName)
                append(")")
                append(" score=")
                append(match.score)
                if (match.isExactMatch) append(" exact")
                if (!match.candidate.isLaunchable) append(" not-launchable")
                append("\n")
            }
        }.trim()
    }

    private fun buildLaunchAppOutputText(
        rawResult: String,
        resolvedMatch: InstalledAppSearchMatch?,
        requestedQuery: String?,
        explicitPackage: String?,
    ): String {
        return buildString {
            append(rawResult)
            requestedQuery?.let {
                append("\nRequested app query: ")
                append(it)
            }
            explicitPackage?.let {
                append("\nExplicit package: ")
                append(it)
            }
            resolvedMatch?.let { match ->
                append("\nResolved package: ")
                append(match.candidate.packageName)
                append(" (")
                append(match.candidate.appName)
                append(")")
            }
            if (rawResult.startsWith("Success", ignoreCase = true)) {
                append("\n\nObserve the UI again to confirm the foreground app and the loaded screen.")
            }
        }.trim()
    }

    private fun describeElement(
        element: VScreenElement,
        allElements: List<VScreenElement>,
    ): String {
        val label = semanticLabelFor(element, allElements)
            ?: element.viewId?.substringAfter(":id/")
            ?: element.className?.substringAfterLast('.')
            ?: "element"
        return buildString {
            append(label)
            element.viewId?.substringAfter(":id/")?.takeIf { it.isNotBlank() }?.let {
                append(" id=")
                append(it)
            }
            if (element.isClickable) append(" clickable")
            if (element.isEditable) append(" editable")
            if (element.isScrollable) append(" scrollable")
            append(" bounds=[")
            append(element.bounds.left)
            append(",")
            append(element.bounds.top)
            append("][")
            append(element.bounds.right)
            append(",")
            append(element.bounds.bottom)
            append("]")
        }
    }

    private fun semanticLabelFor(
        element: VScreenElement,
        allElements: List<VScreenElement>,
    ): String? {
        val directLabel = normalizeWhitespace(
            element.text?.takeIf { it.isNotBlank() }
                ?: element.contentDescription?.takeIf { it.isNotBlank() }
        )
        if (directLabel.isNotBlank()) return directLabel

        val genericShellTokens = listOfNotNull(
            element.viewId?.substringAfter(":id/"),
            element.className?.substringAfterLast('.'),
        ).joinToString(separator = " ").lowercase(Locale.ROOT)
        val suppressTopChromeDescendants = element.isScrollable ||
            (isGenericContainerShell(genericShellTokens) && element.childCount >= 4 && element.height >= 240)
        val descendantTopCutoff = element.bounds.top + minOf(element.height / 5, 220)

        val nestedLabels = allElements
            .asSequence()
            .filter { candidate ->
                candidate !== element &&
                    candidate.bounds.left >= element.bounds.left &&
                    candidate.bounds.top >= element.bounds.top &&
                    candidate.bounds.right <= element.bounds.right &&
                    candidate.bounds.bottom <= element.bounds.bottom
            }
            .mapNotNull { candidate ->
                val label = normalizeWhitespace(candidate.text ?: candidate.contentDescription)
                if (label.isBlank()) return@mapNotNull null
                if (candidate.isEditable) return@mapNotNull null
                if (suppressTopChromeDescendants && candidate.bounds.top <= descendantTopCutoff) {
                    return@mapNotNull null
                }
                label
            }
            .distinct()
            .filterNot(::looksLikeChromeText)
            .take(2)
            .toList()
        if (nestedLabels.isNotEmpty()) {
            return nestedLabels.joinToString(separator = " / ")
        }
        return null
    }

    private fun truncateForDisplay(
        value: String,
        maxLength: Int,
    ): String {
        val trimmed = value.trim()
        return if (trimmed.length > maxLength) {
            trimmed.take(maxLength) + "\n..."
        } else {
            trimmed
        }
    }

    private fun stringArg(
        arguments: JsonObject,
        key: String,
    ): String? {
        return arguments[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun intArg(
        arguments: JsonObject,
        key: String,
    ): Int? {
        return arguments[key]?.jsonPrimitive?.intOrNull
            ?: arguments[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    }

    private fun booleanArg(
        arguments: JsonObject,
        key: String,
    ): Boolean? {
        return arguments[key]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
    }

    private fun resolveElement(
        arguments: JsonObject,
        artifactStore: ChatAgentArtifactStore,
    ): VScreenElement? {
        val handle = stringArg(arguments, "element") ?: return null
        val resolved = artifactStore.resolve(handle)
            ?: throw IllegalArgumentException("Artifact handle is no longer available: $handle")
        return resolved as? VScreenElement
            ?: throw IllegalArgumentException("Artifact `$handle` is not a ScreenElement handle.")
    }


    private fun buildExecutionContext(displayId: Int): ExecutionContext {
        val workDir = File(appContext.cacheDir, "chat-agent-native").apply { mkdirs() }
        val services = ExecutionServices().apply {
            ServiceStateBus.getAccessibilityService()?.let(::add)
        }
        return ExecutionContext(
            applicationContext = appContext,
            variables = ExecutionContext.mutableMapToVObjectMap(
                mutableMapOf("_target_display_id" to displayId)
            ),
            magicVariables = mutableMapOf(),
            services = services,
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = workDir,
        )
    }

    private fun displayOption(displayId: Int): String {
        return if (displayId > 0) "-d $displayId " else ""
    }

    private fun requiredPermissionsFor(helperId: ChatAgentNativeHelperId): List<Permission> {
        return when (helperId) {
            ChatAgentNativeHelperId.OBSERVE_UI,
            ChatAgentNativeHelperId.READ_PAGE_CONTENT,
            ChatAgentNativeHelperId.TAP,
            ChatAgentNativeHelperId.LONG_PRESS,
            ChatAgentNativeHelperId.INPUT_TEXT,
            ChatAgentNativeHelperId.SWIPE,
            ChatAgentNativeHelperId.PRESS_KEY,
            ChatAgentNativeHelperId.VERIFY_UI -> listOf(PermissionManager.ACCESSIBILITY)
            ChatAgentNativeHelperId.WAIT,
            ChatAgentNativeHelperId.LOOKUP_APP,
            ChatAgentNativeHelperId.LAUNCH_APP -> emptyList()
        }
    }

    private fun String.normalizeToken(): String {
        return lowercase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_')
            .trim()
    }

    private data class UiObservation(
        val snapshot: ChatAgentUiSnapshot,
    )

    private data class UiObservationInsights(
        val screenRole: String,
        val primaryScrollable: VScreenElement?,
        val contentCandidates: List<VScreenElement>,
        val chromeCandidates: List<VScreenElement>,
        val readablePreview: List<String>,
    )

    companion object {
        private const val DEFAULT_ELEMENT_HANDLE_LIMIT = 8
        private const val MAX_ELEMENT_HANDLE_LIMIT = 12

        private val SUPPORTED_KEY_ACTIONS = listOf(
            "back",
            "home",
            "recents",
            "notifications",
            "quick_settings",
            "power_dialog",
            "enter",
            "search",
        )

        fun buildDefinitions(context: Context): List<ChatAgentToolDefinition> {
            val appContext = context.applicationContext
            fun permissionNames(vararg permissions: Permission): List<String> {
                return permissions.map { it.getLocalizedName(appContext) }.distinct()
            }

            return listOf(
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_OBSERVE_UI_TOOL_NAME,
                    title = "观察当前界面",
                    description = "Agent-native helper: dump the current accessibility UI tree, current activity, and actionable controls. It also highlights likely primary content targets from the node tree so the agent can act on content instead of toolbar chrome.",
                    moduleId = "vflow.agent.observe_ui",
                    moduleDisplayName = "观察当前界面",
                    routingHints = setOf("当前页面有什么控件", "当前界面", "ui tree", "控件", "screen controls", "screen overview"),
                    inputSchema = buildObserveUiSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.READ_ONLY,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.OBSERVE_UI,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_READ_PAGE_CONTENT_TOOL_NAME,
                    title = "读取页面内容",
                    description = "Agent-native helper: read accessibility-node text from the current visible page state without scrolling. Use this before OCR when the agent needs article text or page content for summarization.",
                    moduleId = "vflow.agent.read_page_content",
                    moduleDisplayName = "读取页面内容",
                    routingHints = setOf("全文", "正文", "总结全文", "页面内容", "article text", "read page", "summarize article"),
                    inputSchema = buildReadPageContentSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.READ_PAGE_CONTENT,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_VERIFY_UI_TOOL_NAME,
                    title = "验证界面状态",
                    description = "Agent-native helper: re-check the live UI against concrete expectations such as visible text, missing text, package, or activity. Use this right before declaring a screen task complete.",
                    moduleId = "vflow.agent.verify_ui",
                    moduleDisplayName = "验证界面状态",
                    routingHints = setOf("verify ui", "确认完成", "确认页面", "验证界面", "check final state"),
                    inputSchema = buildVerifyUiSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.READ_ONLY,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.VERIFY_UI,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_TAP_TOOL_NAME,
                    title = "点击屏幕",
                    description = "Agent-native helper: tap a specific ScreenElement handle or coordinates. Prefer ScreenElement handles returned by observe_ui over guessed labels.",
                    moduleId = "vflow.agent.tap_screen",
                    moduleDisplayName = "点击屏幕",
                    routingHints = setOf("点击", "tap", "click", "press button"),
                    inputSchema = buildTapSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.TAP,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_LONG_PRESS_TOOL_NAME,
                    title = "长按屏幕",
                    description = "Agent-native helper: long press a specific ScreenElement handle or coordinates.",
                    moduleId = "vflow.agent.long_press_screen",
                    moduleDisplayName = "长按屏幕",
                    routingHints = setOf("长按", "long press", "press and hold"),
                    inputSchema = buildLongPressSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.LONG_PRESS,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_INPUT_TEXT_TOOL_NAME,
                    title = "输入文本",
                    description = "Agent-native helper: focus a field and input text. Prefer a ScreenElement handle from observe_ui when possible.",
                    moduleId = "vflow.agent.input_text",
                    moduleDisplayName = "输入文本",
                    routingHints = setOf("输入", "打字", "input text", "type text"),
                    inputSchema = buildInputTextSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.INPUT_TEXT,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_SWIPE_TOOL_NAME,
                    title = "滑动屏幕",
                    description = "Agent-native helper: scroll by direction or perform a precise swipe gesture by coordinates.",
                    moduleId = "vflow.agent.swipe_screen",
                    moduleDisplayName = "滑动屏幕",
                    routingHints = setOf("滑动", "scroll", "swipe"),
                    inputSchema = buildSwipeSchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.SWIPE,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_PRESS_KEY_TOOL_NAME,
                    title = "执行按键操作",
                    description = "Agent-native helper: perform a global key action such as back, home, recents, notifications, quick settings, enter, or search.",
                    moduleId = "vflow.agent.press_key",
                    moduleDisplayName = "执行按键操作",
                    routingHints = setOf("返回键", "home键", "press key", "back", "home", "enter", "search"),
                    inputSchema = buildPressKeySchema(),
                    permissionNames = permissionNames(PermissionManager.ACCESSIBILITY),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.PRESS_KEY,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_WAIT_TOOL_NAME,
                    title = "等待界面变化",
                    description = "Agent-native helper: deliberately wait for loading, animations, or navigation instead of guessing the screen state.",
                    moduleId = "vflow.agent.wait",
                    moduleDisplayName = "等待界面变化",
                    routingHints = setOf("等待", "wait", "loading"),
                    inputSchema = buildWaitSchema(),
                    permissionNames = emptyList(),
                    riskLevel = ChatAgentToolRiskLevel.READ_ONLY,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.WAIT,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_LOOKUP_APP_TOOL_NAME,
                    title = "查询本机应用",
                    description = "Agent-native helper: search installed local apps by label or package so the agent can resolve names like IT之家, 微信, or Chrome before launching them.",
                    moduleId = "vflow.agent.lookup_installed_app",
                    moduleDisplayName = "查询本机应用",
                    routingHints = setOf("查找应用", "查询应用", "installed app", "app package", "包名"),
                    inputSchema = buildLookupAppSchema(),
                    permissionNames = emptyList(),
                    riskLevel = ChatAgentToolRiskLevel.READ_ONLY,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.LOOKUP_APP,
                ),
                ChatAgentToolDefinition(
                    name = CHAT_AGENT_LAUNCH_APP_TOOL_NAME,
                    title = "打开应用",
                    description = "Agent-native helper: resolve and launch an app by display name or package. Use this before app-based screen tasks instead of guessing the package name.",
                    moduleId = "vflow.agent.launch_app",
                    moduleDisplayName = "打开应用",
                    routingHints = setOf("打开应用", "启动应用", "open app", "launch app"),
                    inputSchema = buildLaunchAppSchema(),
                    permissionNames = emptyList(),
                    riskLevel = ChatAgentToolRiskLevel.LOW,
                    usageScopes = setOf(ChatAgentToolUsageScope.DIRECT_TOOL),
                    backend = ChatAgentToolBackend.NATIVE_HELPER,
                    nativeHelperId = ChatAgentNativeHelperId.LAUNCH_APP,
                ),
            )
        }

        private fun buildObserveUiSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("query", stringProperty("Optional broad text/id/class query used to focus the returned controls. Leave blank for a full-page dump."))
                        put("clickable", booleanProperty("Optional clickable filter."))
                        put("editable", booleanProperty("Optional editable filter."))
                        put("scrollable", booleanProperty("Optional scrollable filter."))
                        put("enabled", booleanProperty("Optional enabled filter."))
                        put("limit", integerProperty("Maximum number of ScreenElement handles to expose individually. Default 8, max 12.", 1, 12))
                        put("include_screenshot", booleanProperty("Optional screenshot artifact for future multimodal/visual fallback flows. Leave false for node-tree-first operation."))
                        put("include_hierarchy", booleanProperty("When true, include a trimmed accessibility hierarchy dump in the result text."))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun buildReadPageContentSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("mode", stringProperty("`primary_content` (default) focuses on main content and excludes most chrome; `all_text` keeps more UI text."))
                        put(
                            "display_id",
                            integerProperty("Optional Android display id. Omit for the main display.", 0, 8)
                        )
                    }
                )
            }
        }


        private fun buildTapSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("element", stringProperty("Preferred: artifact handle for a ScreenElement returned by observe_ui."))
                        put("x", numberProperty("Tap x coordinate in absolute screen pixels."))
                        put("y", numberProperty("Tap y coordinate in absolute screen pixels."))
                        put("text_query", stringProperty("Fallback label/id query when you must locate the target on the fly."))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun buildLongPressSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("element", stringProperty("Preferred: artifact handle for a ScreenElement returned by observe_ui."))
                        put("x", numberProperty("Long-press x coordinate in absolute screen pixels."))
                        put("y", numberProperty("Long-press y coordinate in absolute screen pixels."))
                        put("text_query", stringProperty("Fallback label/id query when you must locate the target on the fly."))
                        put("duration_ms", integerProperty("Long-press duration in milliseconds.", 300, 5_000))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun buildInputTextSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("text", stringProperty("Text to input into the focused field."))
                        put("element", stringProperty("Preferred: ScreenElement handle for the target input field."))
                        put("x", numberProperty("Optional x coordinate to focus before typing."))
                        put("y", numberProperty("Optional y coordinate to focus before typing."))
                        put("text_query", stringProperty("Fallback label/id query to focus before typing."))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("text"))
                    }
                )
            }
        }

        private fun buildSwipeSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("direction", enumProperty("Use `up`, `down`, `left`, or `right` for a generic scroll.", listOf("up", "down", "left", "right")))
                        put("start_x", numberProperty("Optional swipe start x coordinate in absolute screen pixels."))
                        put("start_y", numberProperty("Optional swipe start y coordinate in absolute screen pixels."))
                        put("end_x", numberProperty("Optional swipe end x coordinate in absolute screen pixels."))
                        put("end_y", numberProperty("Optional swipe end y coordinate in absolute screen pixels."))
                        put("duration_ms", integerProperty("Swipe duration in milliseconds.", 100, 4_000))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun buildPressKeySchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("key", enumProperty("Key action to dispatch.", SUPPORTED_KEY_ACTIONS))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("key"))
                    }
                )
            }
        }

        private fun buildWaitSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("seconds", integerProperty("Number of seconds to wait before observing again.", 1, 60))
                    }
                )
            }
        }

        private fun buildVerifyUiSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("text_present", stringProperty("Visible text/id/class query that must be present on the current screen."))
                        put("text_absent", stringProperty("Visible text/id/class query that must not be present on the current screen."))
                        put("view_id_contains", stringProperty("Accessibility view id fragment that must exist."))
                        put("package_name_contains", stringProperty("Foreground package substring that must match."))
                        put("activity_contains", stringProperty("Current activity or UI description substring that must match."))
                        put("limit", integerProperty("Maximum number of matched ScreenElement handles to expose.", 1, 6))
                        put("include_hierarchy", booleanProperty("When true, include a trimmed hierarchy dump in the verification result."))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun buildLookupAppSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("query", stringProperty("App label, brand name, or package fragment to search locally."))
                        put("max_results", integerProperty("Maximum number of candidate matches to return.", 1, 8))
                        put("launchable_only", booleanProperty("When true, prefer apps that expose a launcher activity."))
                    }
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("query"))
                    }
                )
            }
        }

        private fun buildLaunchAppSchema(): JsonObject {
            return buildJsonObject {
                put("type", "object")
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "properties",
                    buildJsonObject {
                        put("app_query", stringProperty("App label or brand name to resolve and launch."))
                        put("package_name", stringProperty("Exact Android package name when already known."))
                        put("display_id", integerProperty("Optional Android display id. Omit for the main display.", 0, 8))
                    }
                )
            }
        }

        private fun stringProperty(description: String): JsonObject {
            return buildJsonObject {
                put("type", "string")
                put("description", description)
            }
        }

        private fun booleanProperty(description: String): JsonObject {
            return buildJsonObject {
                put("type", "boolean")
                put("description", description)
            }
        }

        private fun integerProperty(
            description: String,
            minimum: Int,
            maximum: Int,
        ): JsonObject {
            return buildJsonObject {
                put("type", "integer")
                put("minimum", minimum)
                put("maximum", maximum)
                put("description", description)
            }
        }

        private fun numberProperty(description: String): JsonObject {
            return buildJsonObject {
                put("type", "number")
                put("description", description)
            }
        }

        private fun enumProperty(
            description: String,
            values: List<String>,
        ): JsonObject {
            return buildJsonObject {
                put("type", "string")
                put("description", description)
                put(
                    "enum",
                    buildJsonArray {
                        values.forEach { value ->
                            add(JsonPrimitive(value))
                        }
                    }
                )
            }
        }
    }
}
