package com.chaomixian.vflow.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBenchmarkTest {

    @Test
    fun suite_containsBuiltInScenesAndCases() {
        val suite = ChatBenchmarkCatalog.defaultSuite()

        assertEquals(3, suite.scenes.size)
        assertEquals(18, suite.cases.size)
        assertTrue(suite.cases.count { it.isBaseCase } == 3)
        assertTrue(suite.cases.all { it.expectedContentId !in it.forbiddenContentIds })
        assertTrue(suite.cases.any { it.expectedTabId != null })
        assertTrue(suite.cases.any { it.requiredSavedContentIds.isNotEmpty() })
    }

    @Test
    fun noProgressDetection_matchesSameFingerprint() {
        assertTrue(shouldCountNoProgress("feed|tab_a|story_1|A||", "feed|tab_a|story_1|A||"))
        assertFalse(shouldCountNoProgress("feed|tab_a|story_1|A||", "detail|tab_a|story_1|A||"))
    }

    @Test
    fun finalVerificationDetection_requiresReadOnlyStepAfterAction() {
        val trace = listOf(
            ChatBenchmarkToolExecution(
                sequence = 1,
                toolName = CHAT_AGENT_TAP_TOOL_NAME,
                argumentsJson = "{}",
                status = ChatToolResultStatus.SUCCESS,
                startedAtMillis = 1L,
                finishedAtMillis = 2L,
                wasActionTool = true,
            ),
            ChatBenchmarkToolExecution(
                sequence = 2,
                toolName = CHAT_AGENT_OBSERVE_UI_TOOL_NAME,
                argumentsJson = "{}",
                status = ChatToolResultStatus.SUCCESS,
                startedAtMillis = 3L,
                finishedAtMillis = 4L,
                wasVerificationTool = true,
            ),
        )

        assertTrue(chatBenchmarkHasFinalVerification(trace))
    }

    @Test
    fun evaluator_returnsPassForVerifiedTargetAndMatchingAnswer() {
        val spec = ChatBenchmarkCatalog.defaultSuite().cases.first { it.isBaseCase }
        val evaluator = DefaultBenchmarkCaseEvaluator()
        val trace = ChatBenchmarkTrace(
            messages = emptyList(),
            toolExecutions = listOf(
                ChatBenchmarkToolExecution(
                    sequence = 1,
                    toolName = CHAT_AGENT_TAP_TOOL_NAME,
                    argumentsJson = "{}",
                    status = ChatToolResultStatus.SUCCESS,
                    startedAtMillis = 1L,
                    finishedAtMillis = 2L,
                    wasActionTool = true,
                ),
                ChatBenchmarkToolExecution(
                    sequence = 2,
                    toolName = CHAT_AGENT_VERIFY_UI_TOOL_NAME,
                    argumentsJson = "{}",
                    status = ChatToolResultStatus.SUCCESS,
                    startedAtMillis = 3L,
                    finishedAtMillis = 4L,
                    wasVerificationTool = true,
                ),
            ),
            startedAtMillis = 1L,
            finishedAtMillis = 4L,
            toolCallCount = 2,
            noProgressCycles = 0,
            noProgressLoopDetected = false,
            timedOut = false,
            toolBudgetExceeded = false,
            finalVerificationObserved = true,
            finalStateFingerprint = "detail",
            finalRoute = "detail",
            escapedScene = false,
        )
        val finalState = ChatBenchmarkGroundTruth(
            caseId = spec.id,
            variantId = spec.variantId,
            expectedRoute = spec.expectedRoute,
            expectedContentId = spec.expectedContentId,
            expectedAnswerKeywords = spec.expectedAnswerKeywords,
            forbiddenContentIds = spec.forbiddenContentIds,
            currentRoute = spec.expectedRoute,
            selectedTabId = spec.expectedTabId,
            openedContentId = spec.expectedContentId,
            openedForbiddenContentId = null,
            savedContentIds = spec.requiredSavedContentIds,
            activeOverlayId = null,
            visibleTitle = "Visible",
            visibleBody = "Body",
            fingerprint = "detail",
            hostVisible = true,
            escapedScene = false,
            currentPackageName = "com.chaomixian.vflow",
            currentActivityName = "ChatBenchmarkHostActivity",
        )

        val (outcome, reason) = evaluator.evaluate(
            spec = spec,
            trace = trace,
            finalState = finalState,
            finalAssistantMessage = spec.expectedAnswerKeywords.joinToString(separator = " "),
        )

        assertEquals(ChatBenchmarkCaseOutcome.PASS, outcome)
        assertTrue(reason == null)
    }

    @Test
    fun evaluator_failsWhenForbiddenTargetIsOpened() {
        val spec = ChatBenchmarkCatalog.defaultSuite().cases.first { it.forbiddenContentIds.isNotEmpty() }
        val evaluator = DefaultBenchmarkCaseEvaluator()
        val trace = ChatBenchmarkTrace(
            messages = emptyList(),
            toolExecutions = emptyList(),
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            toolCallCount = 0,
            noProgressCycles = 0,
            noProgressLoopDetected = false,
            timedOut = false,
            toolBudgetExceeded = false,
            finalVerificationObserved = false,
            finalStateFingerprint = "detail",
            finalRoute = "detail",
            escapedScene = false,
        )
        val finalState = ChatBenchmarkGroundTruth(
            caseId = spec.id,
            variantId = spec.variantId,
            expectedRoute = spec.expectedRoute,
            expectedContentId = spec.expectedContentId,
            expectedAnswerKeywords = spec.expectedAnswerKeywords,
            forbiddenContentIds = spec.forbiddenContentIds,
            currentRoute = "detail",
            selectedTabId = spec.expectedTabId,
            openedContentId = spec.forbiddenContentIds.first(),
            openedForbiddenContentId = spec.forbiddenContentIds.first(),
            savedContentIds = emptyList(),
            activeOverlayId = null,
            visibleTitle = "Sponsored",
            visibleBody = "Ad body",
            fingerprint = "detail",
            hostVisible = true,
            escapedScene = false,
            currentPackageName = "com.chaomixian.vflow",
            currentActivityName = "ChatBenchmarkHostActivity",
        )

        val (outcome, reason) = evaluator.evaluate(
            spec = spec,
            trace = trace,
            finalState = finalState,
            finalAssistantMessage = "",
        )

        assertEquals(ChatBenchmarkCaseOutcome.FAIL, outcome)
        assertTrue(reason?.contains("forbidden", ignoreCase = true) == true)
    }

    @Test
    fun evaluator_returnsPartialWhenRequiredSaveIsMissing() {
        val spec = ChatBenchmarkCatalog.defaultSuite().cases.first { it.requiredSavedContentIds.isNotEmpty() }
        val evaluator = DefaultBenchmarkCaseEvaluator()
        val trace = ChatBenchmarkTrace(
            messages = emptyList(),
            toolExecutions = listOf(
                ChatBenchmarkToolExecution(
                    sequence = 1,
                    toolName = CHAT_AGENT_TAP_TOOL_NAME,
                    argumentsJson = "{}",
                    status = ChatToolResultStatus.SUCCESS,
                    startedAtMillis = 1L,
                    finishedAtMillis = 2L,
                    wasActionTool = true,
                ),
                ChatBenchmarkToolExecution(
                    sequence = 2,
                    toolName = CHAT_AGENT_VERIFY_UI_TOOL_NAME,
                    argumentsJson = "{}",
                    status = ChatToolResultStatus.SUCCESS,
                    startedAtMillis = 3L,
                    finishedAtMillis = 4L,
                    wasVerificationTool = true,
                ),
            ),
            startedAtMillis = 1L,
            finishedAtMillis = 4L,
            toolCallCount = 2,
            noProgressCycles = 0,
            noProgressLoopDetected = false,
            timedOut = false,
            toolBudgetExceeded = false,
            finalVerificationObserved = true,
            finalStateFingerprint = "detail",
            finalRoute = "detail",
            escapedScene = false,
        )
        val finalState = ChatBenchmarkGroundTruth(
            caseId = spec.id,
            variantId = spec.variantId,
            expectedRoute = spec.expectedRoute,
            expectedContentId = spec.expectedContentId,
            expectedAnswerKeywords = spec.expectedAnswerKeywords,
            forbiddenContentIds = spec.forbiddenContentIds,
            currentRoute = spec.expectedRoute,
            selectedTabId = spec.expectedTabId,
            openedContentId = spec.expectedContentId,
            openedForbiddenContentId = null,
            savedContentIds = emptyList(),
            activeOverlayId = null,
            visibleTitle = "Visible",
            visibleBody = "Body",
            fingerprint = "detail",
            hostVisible = true,
            escapedScene = false,
            currentPackageName = "com.chaomixian.vflow",
            currentActivityName = "ChatBenchmarkHostActivity",
        )

        val (outcome, reason) = evaluator.evaluate(
            spec = spec,
            trace = trace,
            finalState = finalState,
            finalAssistantMessage = spec.expectedAnswerKeywords.joinToString(separator = " "),
        )

        assertEquals(ChatBenchmarkCaseOutcome.PARTIAL, outcome)
        assertTrue(reason?.contains("sav", ignoreCase = true) == true)
    }

    @Test
    fun exportPayload_keepsRunAndTraceData() {
        val run = ChatBenchmarkRun(
            suiteId = "chat_benchmark_v1",
            suiteTitle = "Chat Benchmark v1",
            presetId = "preset_1",
            presetName = "Test Preset",
            provider = "OpenAI",
            modelName = "gpt-test",
            status = ChatBenchmarkRunStatus.COMPLETED,
            startedAtMillis = 10L,
            finishedAtMillis = 20L,
            caseResults = listOf(
                ChatBenchmarkCaseResult(
                    caseId = "case_1",
                    sceneId = "scene_1",
                    familyId = "family_1",
                    variantId = "base",
                    title = "Case 1",
                    prompt = "Open the target",
                    outcome = ChatBenchmarkCaseOutcome.PASS,
                    score = 100,
                    startedAtMillis = 10L,
                    finishedAtMillis = 20L,
                    finalAssistantMessage = "completed",
                    trace = ChatBenchmarkTrace(
                        messages = listOf(
                            ChatMessage(
                                role = ChatMessageRole.USER,
                                content = "Open the target",
                                timestampMillis = 10L,
                            )
                        ),
                        toolExecutions = listOf(
                            ChatBenchmarkToolExecution(
                                sequence = 1,
                                toolName = CHAT_AGENT_TAP_TOOL_NAME,
                                argumentsJson = """{"id":"target"}""",
                                status = ChatToolResultStatus.SUCCESS,
                                startedAtMillis = 11L,
                                finishedAtMillis = 12L,
                                wasActionTool = true,
                            )
                        ),
                        startedAtMillis = 10L,
                        finishedAtMillis = 20L,
                        toolCallCount = 1,
                        noProgressCycles = 0,
                        noProgressLoopDetected = false,
                        timedOut = false,
                        toolBudgetExceeded = false,
                        finalVerificationObserved = true,
                        finalStateFingerprint = "detail|target",
                        finalRoute = "detail",
                        escapedScene = false,
                    ),
                )
            ),
        )

        val payload = ChatBenchmarkExportManager.buildExportPayload(
            run = run,
            packageName = "com.chaomixian.vflow",
            versionName = "1.0",
            versionCode = 1L,
        )
        val json = ChatBenchmarkExportManager.encodeExportPayload(payload)

        assertTrue(json.contains("\"suiteId\": \"chat_benchmark_v1\""))
        assertTrue(json.contains("\"toolName\": \"$CHAT_AGENT_TAP_TOOL_NAME\""))
        assertTrue(json.contains("\"content\": \"Open the target\""))
    }
}
