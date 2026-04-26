package com.chaomixian.vflow.ui.chat

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class ChatBenchmarkRunStatus {
    RUNNING,
    COMPLETED,
    BLOCKED,
    CANCELLED,
}

@Serializable
enum class ChatBenchmarkCaseOutcome {
    PASS,
    PARTIAL,
    FAIL,
}

@Serializable
enum class ChatBenchmarkCheckStatus {
    PASS,
    FAIL,
}

@Serializable
data class ChatBenchmarkExecutionPolicy(
    val enabled: Boolean = true,
    val autoApproveAllTools: Boolean = true,
    val maxToolCallsPerCase: Int = 24,
    val maxCaseDurationSeconds: Int = 90,
    val maxNoProgressCycles: Int = 3,
)

@Serializable
data class ChatBenchmarkVariant(
    val id: String,
    val title: String,
    val description: String,
    val isBase: Boolean = false,
)

@Serializable
data class ChatBenchmarkScene(
    val id: String,
    val familyId: String,
    val title: String,
    val description: String,
    val variants: List<ChatBenchmarkVariant>,
)

@Serializable
data class ChatBenchmarkCase(
    val id: String,
    val sceneId: String,
    val familyId: String,
    val variantId: String,
    val title: String,
    val prompt: String,
    val expectedRoute: String,
    val expectedTabId: String? = null,
    val expectedContentId: String,
    val expectedAnswerKeywords: List<String>,
    val forbiddenContentIds: List<String> = emptyList(),
    val requiredSavedContentIds: List<String> = emptyList(),
    val maxToolCalls: Int = 24,
    val maxCaseDurationSeconds: Int = 90,
    val maxNoProgressCycles: Int = 3,
    val isBaseCase: Boolean = false,
)

@Serializable
data class ChatBenchmarkSuite(
    val id: String,
    val title: String,
    val description: String,
    val executionPolicy: ChatBenchmarkExecutionPolicy,
    val scenes: List<ChatBenchmarkScene>,
    val cases: List<ChatBenchmarkCase>,
)

@Serializable
data class ChatBenchmarkCheck(
    val id: String,
    val title: String,
    val status: ChatBenchmarkCheckStatus,
    val message: String,
)

@Serializable
data class ChatBenchmarkPreflightResult(
    val checkedAtMillis: Long,
    val isReady: Boolean,
    val checks: List<ChatBenchmarkCheck>,
)

@Serializable
data class ChatBenchmarkGroundTruth(
    val caseId: String,
    val variantId: String,
    val expectedRoute: String,
    val expectedContentId: String,
    val expectedAnswerKeywords: List<String>,
    val forbiddenContentIds: List<String>,
    val currentRoute: String,
    val selectedTabId: String?,
    val openedContentId: String?,
    val openedForbiddenContentId: String?,
    val savedContentIds: List<String>,
    val activeOverlayId: String?,
    val visibleTitle: String?,
    val visibleBody: String?,
    val fingerprint: String,
    val hostVisible: Boolean,
    val escapedScene: Boolean,
    val currentPackageName: String?,
    val currentActivityName: String?,
)

@Serializable
data class ChatBenchmarkToolExecution(
    val sequence: Int,
    val toolName: String,
    val argumentsJson: String,
    val status: ChatToolResultStatus? = null,
    val summary: String = "",
    val outputText: String = "",
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val wasActionTool: Boolean = false,
    val wasVerificationTool: Boolean = false,
    val stateFingerprintAfter: String? = null,
    val stateChanged: Boolean = false,
)

@Serializable
data class ChatBenchmarkTrace(
    val messages: List<ChatMessage>,
    val toolExecutions: List<ChatBenchmarkToolExecution>,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val toolCallCount: Int,
    val noProgressCycles: Int,
    val noProgressLoopDetected: Boolean,
    val timedOut: Boolean,
    val toolBudgetExceeded: Boolean,
    val finalVerificationObserved: Boolean,
    val finalStateFingerprint: String?,
    val finalRoute: String?,
    val escapedScene: Boolean,
)

@Serializable
data class ChatBenchmarkCaseResult(
    val caseId: String,
    val sceneId: String,
    val familyId: String,
    val variantId: String,
    val title: String,
    val prompt: String,
    val outcome: ChatBenchmarkCaseOutcome,
    val score: Int,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val failureReason: String? = null,
    val finalAssistantMessage: String = "",
    val finalGroundTruth: ChatBenchmarkGroundTruth? = null,
    val trace: ChatBenchmarkTrace,
)

@Serializable
data class ChatBenchmarkScore(
    val overall: Int,
    val endToEnd: Int,
    val robustnessSafety: Int,
    val completedCaseCount: Int,
    val totalCaseCount: Int,
)

@Serializable
data class ChatBenchmarkRun(
    val id: String = UUID.randomUUID().toString(),
    val suiteId: String,
    val suiteTitle: String,
    val presetId: String,
    val presetName: String,
    val provider: String,
    val modelName: String,
    val status: ChatBenchmarkRunStatus,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val preflight: ChatBenchmarkPreflightResult? = null,
    val score: ChatBenchmarkScore? = null,
    val caseResults: List<ChatBenchmarkCaseResult> = emptyList(),
)

data class ChatBenchmarkUiState(
    val suite: ChatBenchmarkSuite = ChatBenchmarkCatalog.defaultSuite(),
    val preflight: ChatBenchmarkPreflightResult? = null,
    val recentRuns: List<ChatBenchmarkRun> = emptyList(),
    val activeRun: ChatBenchmarkRun? = null,
)
