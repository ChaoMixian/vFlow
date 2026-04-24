package com.chaomixian.vflow.ui.chat

import android.content.Context
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VCoordinateRegion
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowReentryBehavior
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.services.ServiceStateBus
import java.io.File
import java.util.Stack
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class ChatAgentArtifactStore {
    private val artifacts = linkedMapOf<String, Any?>()
    private val sessionState = linkedMapOf<String, Any?>()

    fun createReferences(
        callId: String,
        outputs: Map<String, Any?>,
    ): List<ChatArtifactReference> {
        return outputs.mapNotNull { (key, value) ->
            store(callId = callId, key = key, value = value)
        }
    }

    fun store(
        callId: String,
        key: String,
        value: Any?,
        explicitTypeLabel: String? = null,
    ): ChatArtifactReference? {
        val typeLabel = explicitTypeLabel ?: chatArtifactTypeLabel(value) ?: return null
        val handle = "artifact://$callId/$key"
        artifacts[handle] = value
        return ChatArtifactReference(
            key = key,
            handle = handle,
            typeLabel = typeLabel,
        )
    }

    fun resolve(handle: String): Any? = artifacts[handle]

    fun snapshotArtifacts(): Map<String, Any?> = LinkedHashMap(artifacts)

    fun rememberSessionValue(
        key: String,
        value: Any?,
    ) {
        sessionState[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> recallSessionValue(key: String): T? = sessionState[key] as? T

    fun snapshotSessionState(): Map<String, Any?> = LinkedHashMap(sessionState)
}

internal fun chatArtifactTypeLabel(value: Any?): String? {
    return when (value) {
        is VImage -> "image"
        is VCoordinate -> "coordinate"
        is VCoordinateRegion -> "coordinate region"
        is VScreenElement -> "screen element"
        is ChatAgentUiSnapshot -> "ui snapshot"
        else -> null
    }
}

internal sealed interface ChatPreparedToolItem {
    val toolCall: ChatToolCall

    data class Ready(
        override val toolCall: ChatToolCall,
        val definition: ChatAgentToolDefinition,
        val module: ActionModule,
        val step: ActionStep,
        val missingPermissions: List<Permission>,
    ) : ChatPreparedToolItem

    data class NativeReady(
        override val toolCall: ChatToolCall,
        val definition: ChatAgentToolDefinition,
        val request: ChatAgentNativeRequest,
        val missingPermissions: List<Permission>,
    ) : ChatPreparedToolItem

    data class ImmediateResult(
        override val toolCall: ChatToolCall,
        val result: ChatToolResult,
    ) : ChatPreparedToolItem

    data class TemporaryWorkflow(
        override val toolCall: ChatToolCall,
        val definition: ChatAgentToolDefinition,
        val workflow: Workflow,
        val preparedSteps: List<Ready>,
        val validationErrors: List<ChatToolResult>,
        val missingPermissions: List<Permission>,
        val riskLevel: ChatAgentToolRiskLevel,
    ) : ChatPreparedToolItem

    data class SaveWorkflow(
        override val toolCall: ChatToolCall,
        val definition: ChatAgentToolDefinition,
        val workflow: Workflow,
        val validationErrors: List<ChatToolResult>,
        val missingPermissions: List<Permission>,
        val riskLevel: ChatAgentToolRiskLevel,
    ) : ChatPreparedToolItem
}

internal data class ChatPreparedToolBatch(
    val items: List<ChatPreparedToolItem>,
    val missingPermissions: List<Permission>,
    val riskLevel: ChatAgentToolRiskLevel,
)

internal class ChatAgentModuleExecutor(
    context: Context,
    private val toolRegistry: ChatAgentToolRegistry,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val nativeToolExecutor = ChatAgentNativeToolExecutor(appContext)

    fun prepareBatch(
        toolCalls: List<ChatToolCall>,
        artifactStore: ChatAgentArtifactStore,
    ): ChatPreparedToolBatch {
        val items = toolCalls.map { prepareToolCall(it, artifactStore) }
        val missingPermissions = items
            .flatMap { item ->
                when (item) {
                    is ChatPreparedToolItem.Ready -> item.missingPermissions
                    is ChatPreparedToolItem.NativeReady -> item.missingPermissions
                    is ChatPreparedToolItem.TemporaryWorkflow -> item.missingPermissions
                    is ChatPreparedToolItem.SaveWorkflow -> emptyList()
                    is ChatPreparedToolItem.ImmediateResult -> emptyList()
                }
            }
            .distinctBy { it.id }
        return ChatPreparedToolBatch(
            items = items,
            missingPermissions = missingPermissions,
            riskLevel = ChatAgentToolRiskLevel.maxOf(items.map(::riskLevelOf)),
        ).also { batch ->
            DebugLogger.i(
                LOG_TAG,
                "Prepared tool batch tools=${toolCalls.joinToString { it.name }} items=${batch.items.size} risk=${batch.riskLevel} missingPermissions=${batch.missingPermissions.joinToString { it.id }}"
            )
        }
    }

    suspend fun executeBatch(
        batch: ChatPreparedToolBatch,
        artifactStore: ChatAgentArtifactStore,
    ): List<ChatToolResult> {
        DebugLogger.i(
            LOG_TAG,
            "Executing prepared batch items=${batch.items.size} risk=${batch.riskLevel}"
        )
        return batch.items.map { item ->
            DebugLogger.i(LOG_TAG, "Tool start ${item.describeForLog()}")
            when (item) {
                is ChatPreparedToolItem.ImmediateResult -> item.result
                is ChatPreparedToolItem.Ready -> executeReadyTool(item, artifactStore)
                is ChatPreparedToolItem.NativeReady -> nativeToolExecutor.execute(item, artifactStore)
                is ChatPreparedToolItem.TemporaryWorkflow -> executeTemporaryWorkflow(item, artifactStore)
                is ChatPreparedToolItem.SaveWorkflow -> executeSaveWorkflow(item)
            }.also { result ->
                DebugLogger.i(
                    LOG_TAG,
                    "Tool end ${item.describeForLog()} status=${result.status} output=${result.outputText.compactForLog()}"
                )
            }
        }
    }

    fun buildWorkflowForSave(toolCall: ChatToolCall): Workflow? {
        if (toolCall.name != CHAT_TEMPORARY_WORKFLOW_TOOL_NAME) return null
        return try {
            val spec = parseTemporaryWorkflowSpec(toolCall)
            val readySteps = mutableListOf<ActionStep>()
            spec.steps.forEach { stepSpec ->
                if (!toolRegistry.isTemporaryWorkflowModuleAllowed(stepSpec.moduleId)) return null
                val module = ModuleRegistry.getModule(stepSpec.moduleId) ?: return null
                val parameters = buildParameters(module, stepSpec.parameters.toString(), ChatAgentArtifactStore())
                readySteps += ActionStep(
                    moduleId = module.id,
                    parameters = parameters,
                    indentationLevel = stepSpec.indentationLevel,
                    id = stepSpec.id,
                )
            }
            Workflow(
                id = "chat_saved_${UUID.randomUUID()}",
                name = spec.name,
                triggers = listOf(
                    ActionStep(moduleId = "vflow.trigger.manual", parameters = emptyMap(), id = "manual_trigger")
                ),
                steps = readySteps,
                isEnabled = true,
                description = spec.description,
                maxExecutionTime = spec.maxExecutionTime,
                reentryBehavior = WorkflowReentryBehavior.STOP_CURRENT_AND_RUN_NEW,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun buildRejectedResults(toolCalls: List<ChatToolCall>): List<ChatToolResult> {
        return toolCalls.map { toolCall ->
            val definition = toolRegistry.getTool(toolCall.name)
            val title = definition?.title ?: toolCall.name
            ChatToolResult(
                callId = toolCall.id,
                name = toolCall.name,
                status = ChatToolResultStatus.REJECTED,
                summary = title,
                outputText = chatAgentAppendNextStep(
                    baseMessage = "Tool execution was rejected by the user for `$title`.",
                    nextStep = "choose a lower-risk tool, ask the user for approval, or explain what still needs confirmation.",
                ),
            )
        }
    }

    fun buildPermissionRequiredResults(batch: ChatPreparedToolBatch): List<ChatToolResult> {
        return batch.items.map { item ->
            when (item) {
                is ChatPreparedToolItem.ImmediateResult -> item.result
                is ChatPreparedToolItem.NativeReady -> {
                    val permissionNames = item.missingPermissions
                        .map { it.getLocalizedName(appContext) }
                        .distinct()
                    ChatToolResult(
                        callId = item.toolCall.id,
                        name = item.toolCall.name,
                        status = ChatToolResultStatus.PERMISSION_REQUIRED,
                        summary = item.definition.title,
                        outputText = chatAgentAppendNextStep(
                            baseMessage = "Tool `${item.definition.title}` could not run because the following permissions are not granted: ${permissionNames.joinToString()}.",
                            nextStep = "ask the user to grant those permissions, then retry the same tool. ${item.definition.nativeHelperId?.let(::chatAgentNativeRecoveryHint).orEmpty()}".trim(),
                        ),
                    )
                }
                is ChatPreparedToolItem.SaveWorkflow -> {
                    ChatToolResult(
                        callId = item.toolCall.id,
                        name = item.toolCall.name,
                        status = ChatToolResultStatus.PERMISSION_REQUIRED,
                        summary = item.definition.title,
                        outputText = chatAgentAppendNextStep(
                            baseMessage = "Saved workflow `${item.workflow.name}` could not be saved because required permissions are not granted.",
                            nextStep = "ask the user to grant the missing permissions, then save the workflow again.",
                        ),
                    )
                }
                is ChatPreparedToolItem.TemporaryWorkflow -> {
                    val permissionNames = item.missingPermissions
                        .map { it.getLocalizedName(appContext) }
                        .distinct()
                    ChatToolResult(
                        callId = item.toolCall.id,
                        name = item.toolCall.name,
                        status = ChatToolResultStatus.PERMISSION_REQUIRED,
                        summary = item.definition.title,
                        outputText = chatAgentAppendNextStep(
                            baseMessage = "Temporary workflow `${item.definition.title}` could not run because the following permissions are not granted: ${permissionNames.joinToString()}.",
                            nextStep = "ask the user to grant those permissions, then retry the workflow.",
                        ),
                    )
                }
                is ChatPreparedToolItem.Ready -> {
                    val permissionNames = item.missingPermissions
                        .map { it.getLocalizedName(appContext) }
                        .distinct()
                    ChatToolResult(
                        callId = item.toolCall.id,
                        name = item.toolCall.name,
                        status = ChatToolResultStatus.PERMISSION_REQUIRED,
                        summary = item.definition.title,
                        outputText = chatAgentAppendNextStep(
                            baseMessage = "Tool `${item.definition.title}` could not run because the following permissions are not granted: ${permissionNames.joinToString()}.",
                            nextStep = "ask the user to grant those permissions, then retry the same tool.",
                        ),
                    )
                }
            }
        }
    }

    private fun riskLevelOf(item: ChatPreparedToolItem): ChatAgentToolRiskLevel {
        return when (item) {
            is ChatPreparedToolItem.Ready -> item.definition.riskLevel
            is ChatPreparedToolItem.NativeReady -> item.definition.riskLevel
            is ChatPreparedToolItem.TemporaryWorkflow -> item.riskLevel
            is ChatPreparedToolItem.SaveWorkflow -> item.riskLevel
            is ChatPreparedToolItem.ImmediateResult -> ChatAgentToolRiskLevel.HIGH
        }
    }

    private fun prepareToolCall(
        toolCall: ChatToolCall,
        artifactStore: ChatAgentArtifactStore,
    ): ChatPreparedToolItem {
        if (toolCall.name == CHAT_TEMPORARY_WORKFLOW_TOOL_NAME) {
            return prepareTemporaryWorkflow(toolCall, artifactStore)
        }
        if (toolCall.name == CHAT_SAVE_WORKFLOW_TOOL_NAME) {
            return prepareSaveWorkflow(toolCall, artifactStore)
        }

        val definition = toolRegistry.getTool(toolCall.name)
            ?: return ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = toolCall.name,
                    outputText = "Unknown tool `${toolCall.name}`.",
                )
            )

        if (definition.backend == ChatAgentToolBackend.NATIVE_HELPER) {
            return nativeToolExecutor.prepare(definition, toolCall, artifactStore)
        }

        val module = ModuleRegistry.getModule(definition.moduleId)
            ?: return ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = "The vFlow module for `${definition.title}` is not registered.",
                )
            )

        return prepareModuleStep(
            toolCall = toolCall,
            definition = definition,
            module = module,
            rawArgumentsJson = toolCall.argumentsJson,
            artifactStore = artifactStore,
        )
    }

    private fun prepareModuleStep(
        toolCall: ChatToolCall,
        definition: ChatAgentToolDefinition,
        module: ActionModule,
        rawArgumentsJson: String,
        artifactStore: ChatAgentArtifactStore,
        stepId: String = UUID.randomUUID().toString(),
        indentationLevel: Int = 0,
    ): ChatPreparedToolItem {
        return try {
            val parameters = buildParameters(module, rawArgumentsJson, artifactStore)
            val step = ActionStep(
                moduleId = module.id,
                parameters = parameters,
                indentationLevel = indentationLevel,
                id = stepId,
            )
            val validation = module.validate(step, listOf(step))
            if (!validation.isValid) {
                ChatPreparedToolItem.ImmediateResult(
                    toolCall = toolCall,
                    result = ChatToolResult(
                        callId = toolCall.id,
                        name = toolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = definition.title,
                        outputText = validation.errorMessage
                            ?: "Validation failed for `${definition.title}`.",
                    )
                )
            } else {
                ChatPreparedToolItem.Ready(
                    toolCall = toolCall,
                    definition = definition,
                    module = module,
                    step = step,
                    missingPermissions = module.getRequiredPermissions(step)
                        .filterNot { PermissionManager.isGranted(appContext, it) },
                )
            }
        } catch (throwable: Throwable) {
            ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = throwable.message?.ifBlank { null }
                        ?: "Failed to parse arguments for `${definition.title}`.",
                )
            )
        }
    }

    private fun prepareTemporaryWorkflow(
        toolCall: ChatToolCall,
        artifactStore: ChatAgentArtifactStore,
    ): ChatPreparedToolItem {
        val definition = toolRegistry.getTool(toolCall.name)
            ?: return ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = toolCall.name,
                    outputText = "Unknown temporary workflow tool `${toolCall.name}`.",
                )
            )

        return try {
            val spec = parseTemporaryWorkflowSpec(toolCall)
            val readySteps = mutableListOf<ChatPreparedToolItem.Ready>()
            val validationErrors = mutableListOf<ChatToolResult>()
            spec.steps.forEachIndexed { index, stepSpec ->
                val preparedToolCall = ChatToolCall(
                    id = "${normalizedToolCallId(toolCall)}_${stepSpec.id}",
                    name = chatToolNameFromModuleId(stepSpec.moduleId),
                    argumentsJson = stepSpec.parameters.toString(),
                )
                if (!toolRegistry.isTemporaryWorkflowModuleAllowed(stepSpec.moduleId)) {
                    validationErrors += buildTemporaryWorkflowValidationError(
                        toolCall = preparedToolCall,
                        summary = definition.title,
                        outputText = "Temporary workflow step ${index + 1} uses module `${stepSpec.moduleId}`, which is not exposed to the chat agent.",
                    )
                    return@forEachIndexed
                }
                val module = ModuleRegistry.getModule(stepSpec.moduleId)
                if (module == null) {
                    validationErrors += buildTemporaryWorkflowValidationError(
                        toolCall = preparedToolCall,
                        summary = definition.title,
                        outputText = "Temporary workflow step ${index + 1} uses unregistered module `${stepSpec.moduleId}`.",
                    )
                    return@forEachIndexed
                }
                val stepDefinition = toolRegistry.getToolForModuleId(stepSpec.moduleId)
                    ?: ChatAgentToolDefinition(
                        name = preparedToolCall.name,
                        title = stepSpec.moduleId,
                        description = stepSpec.moduleId,
                        moduleId = stepSpec.moduleId,
                        moduleDisplayName = stepSpec.moduleId,
                        routingHints = setOf(stepSpec.moduleId),
                        inputSchema = buildJsonObject { },
                        permissionNames = emptyList(),
                        riskLevel = toolRegistry.getRiskLevelForModuleId(stepSpec.moduleId),
                        usageScopes = setOf(ChatAgentToolUsageScope.TEMPORARY_WORKFLOW),
                    )
                when (val prepared = prepareModuleStep(
                    toolCall = preparedToolCall,
                    definition = stepDefinition,
                    module = module,
                    rawArgumentsJson = stepSpec.parameters.toString(),
                    artifactStore = artifactStore,
                    stepId = stepSpec.id,
                    indentationLevel = stepSpec.indentationLevel,
                )) {
                    is ChatPreparedToolItem.Ready -> readySteps += prepared
                    is ChatPreparedToolItem.NativeReady -> validationErrors += ChatToolResult(
                        callId = preparedToolCall.id,
                        name = preparedToolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = definition.title,
                        outputText = "Native helper tools cannot be embedded inside temporary workflows.",
                    )
                    is ChatPreparedToolItem.ImmediateResult -> validationErrors += prepared.result
                    is ChatPreparedToolItem.TemporaryWorkflow -> validationErrors += ChatToolResult(
                        callId = preparedToolCall.id,
                        name = preparedToolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = definition.title,
                        outputText = "Nested temporary workflows are not supported.",
                    )
                    is ChatPreparedToolItem.SaveWorkflow -> validationErrors += ChatToolResult(
                        callId = preparedToolCall.id,
                        name = preparedToolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = definition.title,
                        outputText = "Saving a workflow from inside a temporary workflow is not supported.",
                    )
                }
            }
            val workflow = buildTemporaryWorkflow(spec, readySteps.map { it.step })
            val workflowMissingPermissions = PermissionManager.getMissingPermissions(appContext, workflow)
            ChatPreparedToolItem.TemporaryWorkflow(
                toolCall = toolCall,
                definition = definition,
                workflow = workflow,
                preparedSteps = readySteps,
                validationErrors = validationErrors,
                missingPermissions = (readySteps.flatMap { it.missingPermissions } + workflowMissingPermissions)
                    .distinctBy { it.id },
                riskLevel = ChatAgentToolRiskLevel.maxOf(readySteps.map { it.definition.riskLevel }),
            )
        } catch (throwable: Throwable) {
            ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = throwable.message?.ifBlank { null }
                        ?: "Failed to parse the temporary workflow.",
                )
            )
        }
    }

    private fun prepareSaveWorkflow(
        toolCall: ChatToolCall,
        artifactStore: ChatAgentArtifactStore,
    ): ChatPreparedToolItem {
        val definition = toolRegistry.getTool(toolCall.name)
            ?: return ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = toolCall.name,
                    outputText = "Unknown saved workflow tool `${toolCall.name}`.",
                )
            )

        return try {
            val spec = parseSaveWorkflowSpec(toolCall)
            val validationErrors = mutableListOf<ChatToolResult>()
            val triggerCandidates = prepareSavedWorkflowSteps(
                toolCall = toolCall,
                definition = definition,
                stepSpecs = spec.triggers,
                expectTrigger = true,
                artifactStore = artifactStore,
                validationErrors = validationErrors,
            ).ifEmpty {
                listOf(createManualTriggerCandidate())
            }
            val stepCandidates = prepareSavedWorkflowSteps(
                toolCall = toolCall,
                definition = definition,
                stepSpecs = spec.steps,
                expectTrigger = false,
                artifactStore = artifactStore,
                validationErrors = validationErrors,
            )
            val allCandidates = triggerCandidates + stepCandidates
            val allSteps = allCandidates.map { it.step }
            allCandidates.forEach { candidate ->
                val validation = candidate.module.validate(candidate.step, allSteps)
                if (!validation.isValid) {
                    validationErrors += buildSaveWorkflowValidationError(
                        toolCall = toolCall,
                        summary = definition.title,
                        outputText = "${candidate.sourceLabel} `${candidate.step.moduleId}` is invalid: ${
                            validation.errorMessage ?: "Validation failed."
                        }",
                    )
                }
            }

            val workflow = Workflow(
                id = "chat_saved_${UUID.randomUUID()}",
                name = spec.name,
                triggers = triggerCandidates.map { it.step },
                steps = stepCandidates.map { it.step },
                isEnabled = spec.isEnabled,
                folderId = spec.folderId,
                description = spec.description,
                tags = spec.tags,
                maxExecutionTime = spec.maxExecutionTime,
                reentryBehavior = spec.reentryBehavior,
            )
            val missingPermissions = PermissionManager.getMissingPermissions(appContext, workflow)
            ChatPreparedToolItem.SaveWorkflow(
                toolCall = toolCall,
                definition = definition,
                workflow = workflow,
                validationErrors = validationErrors,
                missingPermissions = missingPermissions,
                riskLevel = riskLevelForSavedWorkflow(workflow),
            )
        } catch (throwable: Throwable) {
            ChatPreparedToolItem.ImmediateResult(
                toolCall = toolCall,
                result = ChatToolResult(
                    callId = toolCall.id,
                    name = toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = definition.title,
                    outputText = throwable.message?.ifBlank { null }
                        ?: "Failed to parse the saved workflow.",
                )
            )
        }
    }

    private fun prepareSavedWorkflowSteps(
        toolCall: ChatToolCall,
        definition: ChatAgentToolDefinition,
        stepSpecs: List<WorkflowStepSpec>,
        expectTrigger: Boolean,
        artifactStore: ChatAgentArtifactStore,
        validationErrors: MutableList<ChatToolResult>,
    ): List<SavedWorkflowStepCandidate> {
        return stepSpecs.mapIndexedNotNull { index, stepSpec ->
            val sourceLabel = if (expectTrigger) "trigger ${index + 1}" else "step ${index + 1}"
            if (!toolRegistry.isSavedWorkflowModuleAllowed(stepSpec.moduleId)) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel uses module `${stepSpec.moduleId}`, which is not exposed to the chat agent for saved workflows.",
                )
                return@mapIndexedNotNull null
            }
            val module = ModuleRegistry.getModule(stepSpec.moduleId)
            if (module == null) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel uses unregistered module `${stepSpec.moduleId}`.",
                )
                return@mapIndexedNotNull null
            }
            val isTrigger = toolRegistry.isTriggerModule(stepSpec.moduleId)
            if (expectTrigger && !isTrigger) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel must be a trigger module, but `${stepSpec.moduleId}` is not a trigger.",
                )
                return@mapIndexedNotNull null
            }
            if (!expectTrigger && isTrigger) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel uses trigger module `${stepSpec.moduleId}` in workflow.steps. Put triggers in workflow.triggers instead.",
                )
                return@mapIndexedNotNull null
            }
            if (containsArtifactHandle(stepSpec.parameters)) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel contains an artifact:// handle. Chat artifacts are temporary and cannot be saved into reusable workflows.",
                )
                return@mapIndexedNotNull null
            }

            try {
                val parameters = buildParameters(module, stepSpec.parameters.toString(), artifactStore)
                SavedWorkflowStepCandidate(
                    module = module,
                    step = ActionStep(
                        moduleId = module.id,
                        parameters = parameters,
                        indentationLevel = stepSpec.indentationLevel,
                        id = stepSpec.id,
                    ),
                    sourceLabel = sourceLabel,
                )
            } catch (throwable: Throwable) {
                validationErrors += buildSaveWorkflowValidationError(
                    toolCall = toolCall,
                    summary = definition.title,
                    outputText = "Saved workflow $sourceLabel could not parse `${stepSpec.moduleId}` parameters: ${
                        throwable.message?.ifBlank { null } ?: "Invalid arguments."
                    }",
                )
                null
            }
        }
    }

    private fun createManualTriggerCandidate(): SavedWorkflowStepCandidate {
        val moduleId = "vflow.trigger.manual"
        val module = ModuleRegistry.getModule(moduleId)
            ?: throw IllegalStateException("Manual trigger module is not registered.")
        return SavedWorkflowStepCandidate(
            module = module,
            step = ActionStep(
                moduleId = moduleId,
                parameters = emptyMap(),
                id = "manual_trigger",
            ),
            sourceLabel = "trigger 1",
        )
    }

    private fun parseSaveWorkflowSpec(toolCall: ChatToolCall): SaveWorkflowSpec {
        val root = json.parseToJsonElement(toolCall.argumentsJson) as? JsonObject
            ?: throw IllegalArgumentException("Saved workflow arguments must be a JSON object.")
        val workflow = root["workflow"] as? JsonObject ?: root
        val steps = workflow["steps"] as? JsonArray
            ?: throw IllegalArgumentException("Saved workflow requires a `steps` array.")
        val triggers = workflow["triggers"] as? JsonArray

        val parsedSteps = parseWorkflowStepSpecs(
            source = "Saved workflow step",
            array = steps,
            maxItems = MAX_SAVED_WORKFLOW_STEPS,
        )
        if (parsedSteps.isEmpty()) {
            throw IllegalArgumentException("Saved workflow must include at least one action step.")
        }

        return SaveWorkflowSpec(
            name = workflow["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Saved workflow requires a non-empty `name`."),
            description = workflow["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            isEnabled = workflow["isEnabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            folderId = workflow["folderId"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() },
            tags = parseStringArray(workflow["tags"] as? JsonArray),
            maxExecutionTime = workflow["maxExecutionTime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.coerceIn(1, MAX_SAVED_WORKFLOW_MAX_SECONDS),
            reentryBehavior = WorkflowReentryBehavior.fromStoredValue(
                workflow["reentryBehavior"]?.jsonPrimitive?.contentOrNull
            ),
            triggers = triggers?.let {
                parseWorkflowStepSpecs(
                    source = "Saved workflow trigger",
                    array = it,
                    maxItems = MAX_SAVED_WORKFLOW_TRIGGERS,
                )
            }.orEmpty(),
            steps = parsedSteps,
        )
    }

    private fun parseWorkflowStepSpecs(
        source: String,
        array: JsonArray,
        maxItems: Int,
    ): List<WorkflowStepSpec> {
        if (array.size > maxItems) {
            throw IllegalArgumentException("$source list is too long: ${array.size}, max is $maxItems.")
        }
        return array.mapIndexed { index, stepElement ->
            val step = stepElement as? JsonObject
                ?: throw IllegalArgumentException("$source ${index + 1} must be an object.")
            val moduleId = step["moduleId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (moduleId.isBlank()) {
                throw IllegalArgumentException("$source ${index + 1} is missing `moduleId`.")
            }
            val parameters = step["parameters"] as? JsonObject
                ?: buildJsonObject { }
            val stepId = step["id"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "step_${index + 1}"
            val indentationLevel = step["indentationLevel"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0
            WorkflowStepSpec(
                id = stepId,
                moduleId = moduleId,
                parameters = parameters,
                indentationLevel = indentationLevel.coerceIn(0, MAX_WORKFLOW_INDENTATION_LEVEL),
            )
        }
    }

    private fun parseStringArray(array: JsonArray?): List<String> {
        return array?.toList().orEmpty()
            .mapNotNull { element -> element.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }
            .distinct()
            .take(MAX_SAVED_WORKFLOW_TAGS)
    }

    private fun containsArtifactHandle(element: JsonElement): Boolean {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.startsWith("artifact://") == true
            is JsonArray -> element.any(::containsArtifactHandle)
            is JsonObject -> element.values.any(::containsArtifactHandle)
            JsonNull -> false
        }
    }

    private fun riskLevelForSavedWorkflow(workflow: Workflow): ChatAgentToolRiskLevel {
        val moduleLevels = workflow.steps.map { toolRegistry.getRiskLevelForModuleId(it.moduleId) }
        val triggerLevels = workflow.triggers.map { trigger ->
            if (trigger.moduleId == "vflow.trigger.manual") {
                ChatAgentToolRiskLevel.LOW
            } else {
                ChatAgentToolRiskLevel.HIGH
            }
        }
        return ChatAgentToolRiskLevel.maxOf(
            moduleLevels + triggerLevels + ChatAgentToolRiskLevel.STANDARD
        )
    }

    private fun parseTemporaryWorkflowSpec(toolCall: ChatToolCall): TemporaryWorkflowSpec {
        val root = json.parseToJsonElement(toolCall.argumentsJson) as? JsonObject
            ?: throw IllegalArgumentException("Temporary workflow arguments must be a JSON object.")
        val workflow = root["workflow"] as? JsonObject ?: root
        val steps = workflow["steps"] as? JsonArray
            ?: throw IllegalArgumentException("Temporary workflow requires a `steps` array.")
        val parsedSteps = mutableListOf<WorkflowStepSpec>()

        steps.forEachIndexed { stepIndex, stepElement ->
            val step = stepElement as? JsonObject
                ?: throw IllegalArgumentException("Temporary workflow step ${stepIndex + 1} must be an object.")
            val moduleId = step["moduleId"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: step["module_id"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: step["tool"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.let { toolName -> toolRegistry.getTool(toolName)?.moduleId }
                ?: ""
            if (moduleId.isBlank()) {
                throw IllegalArgumentException("Temporary workflow step ${stepIndex + 1} is missing `moduleId`.")
            }
            if (moduleId == CHAT_TEMPORARY_WORKFLOW_MODULE_ID || step["tool"]?.jsonPrimitive?.contentOrNull == CHAT_TEMPORARY_WORKFLOW_TOOL_NAME) {
                throw IllegalArgumentException("Nested temporary workflows are not supported.")
            }
            val parameters = step["parameters"] as? JsonObject
                ?: step["arguments"] as? JsonObject
                ?: buildJsonObject { }
            val baseStepId = step["id"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "step_${stepIndex + 1}"
            val indentationLevel = step["indentationLevel"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: step["indentation_level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0
            val repeat = step["repeat"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.coerceIn(1, MAX_TEMPORARY_WORKFLOW_REPEAT) ?: 1
            val delayMsAfter = step["delay_ms_after"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?.coerceIn(0L, MAX_TEMPORARY_WORKFLOW_DELAY_MS) ?: 0L

            repeat(repeat) { repeatIndex ->
                parsedSteps += WorkflowStepSpec(
                    id = if (repeat == 1) baseStepId else "${baseStepId}_${repeatIndex + 1}",
                    moduleId = moduleId,
                    parameters = parameters,
                    indentationLevel = indentationLevel.coerceIn(0, 8),
                )
                if (delayMsAfter > 0 && repeatIndex < repeat - 1) {
                    parsedSteps += WorkflowStepSpec(
                        id = "${baseStepId}_${repeatIndex + 1}_delay",
                        moduleId = "vflow.device.delay",
                        parameters = buildJsonObject {
                            put("duration", delayMsAfter)
                        },
                        indentationLevel = indentationLevel.coerceIn(0, 8),
                    )
                }
            }
        }

        if (parsedSteps.isEmpty()) {
            throw IllegalArgumentException("Temporary workflow must include at least one executable step.")
        }
        if (parsedSteps.size > MAX_TEMPORARY_WORKFLOW_EXPANDED_STEPS) {
            throw IllegalArgumentException("Temporary workflow is too long: ${parsedSteps.size} expanded steps, max is $MAX_TEMPORARY_WORKFLOW_EXPANDED_STEPS.")
        }
        return TemporaryWorkflowSpec(
            name = workflow["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "聊天临时工作流",
            description = workflow["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            maxExecutionTime = workflow["maxExecutionTime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?.coerceIn(1, MAX_TEMPORARY_WORKFLOW_MAX_SECONDS)
                ?: DEFAULT_TEMPORARY_WORKFLOW_MAX_SECONDS,
            steps = parsedSteps,
        )
    }

    private fun buildTemporaryWorkflow(
        spec: TemporaryWorkflowSpec,
        steps: List<ActionStep>,
    ): Workflow {
        return Workflow(
            id = "chat_temp_${UUID.randomUUID()}",
            name = spec.name,
            triggers = listOf(
                ActionStep(
                    moduleId = "vflow.trigger.manual",
                    parameters = emptyMap(),
                    id = "chat_manual_trigger",
                )
            ),
            steps = steps,
            isEnabled = true,
            description = spec.description,
            maxExecutionTime = spec.maxExecutionTime,
            reentryBehavior = WorkflowReentryBehavior.STOP_CURRENT_AND_RUN_NEW,
        )
    }

    private fun buildTemporaryWorkflowValidationError(
        toolCall: ChatToolCall,
        summary: String,
        outputText: String,
    ): ChatToolResult {
        return ChatToolResult(
            callId = toolCall.id,
            name = toolCall.name,
            status = ChatToolResultStatus.ERROR,
            summary = summary,
            outputText = outputText,
        )
    }

    private fun buildSaveWorkflowValidationError(
        toolCall: ChatToolCall,
        summary: String,
        outputText: String,
    ): ChatToolResult {
        return ChatToolResult(
            callId = toolCall.id,
            name = toolCall.name,
            status = ChatToolResultStatus.ERROR,
            summary = summary,
            outputText = outputText,
        )
    }

    private fun executeSaveWorkflow(workflow: ChatPreparedToolItem.SaveWorkflow): ChatToolResult {
        if (workflow.validationErrors.isNotEmpty()) {
            return ChatToolResult(
                callId = workflow.toolCall.id,
                name = workflow.toolCall.name,
                status = ChatToolResultStatus.ERROR,
                summary = workflow.definition.title,
                outputText = buildString {
                    append("Saved workflow `${workflow.workflow.name}` was not saved because some parts are invalid.\n")
                    workflow.validationErrors.take(10).forEachIndexed { index, result ->
                        append("${index + 1}. ")
                        append(result.outputText)
                        append("\n")
                    }
                }.trim(),
            )
        }

        return try {
            WorkflowManager(appContext).saveWorkflow(workflow.workflow)
            ChatToolResult(
                callId = workflow.toolCall.id,
                name = workflow.toolCall.name,
                status = ChatToolResultStatus.SUCCESS,
                summary = workflow.definition.title,
                outputText = buildSavedWorkflowResultText(workflow),
            )
        } catch (throwable: Throwable) {
            ChatToolResult(
                callId = workflow.toolCall.id,
                name = workflow.toolCall.name,
                status = ChatToolResultStatus.ERROR,
                summary = workflow.definition.title,
                outputText = buildString {
                    append("Saved workflow `${workflow.workflow.name}` failed to save.")
                    throwable.message?.takeIf { it.isNotBlank() }?.let {
                        append("\n")
                        append(it)
                    }
                }.trim(),
            )
        }
    }

    private fun buildSavedWorkflowResultText(workflow: ChatPreparedToolItem.SaveWorkflow): String {
        return buildString {
            append("Workflow `${workflow.workflow.name}` was saved successfully.")
            append("\n\nID: ")
            append(workflow.workflow.id)
            append("\nTriggers: ")
            append(workflow.workflow.triggers.size)
            append(if (workflow.workflow.hasAutoTriggers()) " (includes auto triggers)" else " (manual)")
            append("\nSteps: ")
            append(workflow.workflow.steps.size)
            append("\nRisk level: ")
            append(workflow.riskLevel.name.lowercase())

            if (workflow.missingPermissions.isNotEmpty()) {
                append("\n\nMissing permissions before execution:\n")
                workflow.missingPermissions
                    .map { it.getLocalizedName(appContext) }
                    .distinct()
                    .forEach { permissionName ->
                        append("- ")
                        append(permissionName)
                        append("\n")
                    }
            }

            append("\n\nWorkflow outline:\n")
            workflow.workflow.triggers.take(8).forEach { trigger ->
                append("- Trigger: ")
                append(trigger.moduleId)
                append("\n")
            }
            workflow.workflow.steps.take(30).forEachIndexed { index, step ->
                append("- ")
                append(index + 1)
                append(". ")
                append(step.moduleId)
                append("\n")
            }
            if (workflow.workflow.steps.size > 30) {
                append("- ... ")
                append(workflow.workflow.steps.size - 30)
                append(" more steps\n")
            }
        }.trim()
    }

    private suspend fun executeTemporaryWorkflow(
        workflow: ChatPreparedToolItem.TemporaryWorkflow,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        if (workflow.validationErrors.isNotEmpty()) {
            return ChatToolResult(
                callId = workflow.toolCall.id,
                name = workflow.toolCall.name,
                status = ChatToolResultStatus.ERROR,
                summary = workflow.definition.title,
                outputText = buildString {
                    append("Temporary workflow could not start because some steps are invalid.\n")
                    workflow.validationErrors.take(8).forEachIndexed { index, result ->
                        append("${index + 1}. ")
                        append(result.summary)
                        append(": ")
                        append(result.outputText)
                        append("\n")
                    }
                }.trim(),
            )
        }

        return coroutineScope {
            val terminalState = async {
                withTimeout((workflow.workflow.maxExecutionTime ?: DEFAULT_TEMPORARY_WORKFLOW_MAX_SECONDS) * 1000L + 5_000L) {
                    ExecutionStateBus.stateFlow.first { state ->
                        state.workflowId == workflow.workflow.id && state.isTerminalExecutionState()
                    }
                }
            }
            val executionInstanceId = WorkflowExecutor.execute(
                workflow = workflow.workflow,
                context = appContext,
                triggerStepId = workflow.workflow.manualTrigger()?.id,
            )
            if (executionInstanceId.isBlank()) {
                terminalState.cancel()
                return@coroutineScope ChatToolResult(
                    callId = workflow.toolCall.id,
                    name = workflow.toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = workflow.definition.title,
                    outputText = "Temporary workflow `${workflow.workflow.name}` did not start because another execution is already running.",
                )
            }

            try {
                buildTemporaryWorkflowResult(
                    workflow = workflow,
                    terminalState = terminalState.await(),
                )
            } catch (timeout: TimeoutCancellationException) {
                WorkflowExecutor.stopExecution(workflow.workflow.id)
                ChatToolResult(
                    callId = workflow.toolCall.id,
                    name = workflow.toolCall.name,
                    status = ChatToolResultStatus.ERROR,
                    summary = workflow.definition.title,
                    outputText = "Temporary workflow `${workflow.workflow.name}` timed out.",
                )
            } catch (cancellation: CancellationException) {
                WorkflowExecutor.stopExecution(workflow.workflow.id)
                throw cancellation
            }
        }
    }

    private suspend fun executeReadyTool(
        ready: ChatPreparedToolItem.Ready,
        artifactStore: ChatAgentArtifactStore,
    ): ChatToolResult {
        val progressMessages = mutableListOf<String>()
        val workDir = File(appContext.cacheDir, "chat-agent").apply { mkdirs() }
        val services = ExecutionServices().apply {
            ServiceStateBus.getAccessibilityService()?.let(::add)
            add(ExecutionUIService(appContext))
        }
        val executionContext = ExecutionContext(
            applicationContext = appContext,
            variables = ExecutionContext.mutableMapToVObjectMap(ready.step.parameters.toMutableMap()),
            magicVariables = mutableMapOf(),
            services = services,
            allSteps = listOf(ready.step),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = workDir,
        )

        return try {
            when (val result = ready.module.execute(executionContext) { update ->
                update.message.takeIf { it.isNotBlank() }?.let(progressMessages::add)
            }) {
                is ExecutionResult.Success -> {
                    val artifacts = artifactStore.createReferences(
                        callId = normalizedToolCallId(ready.toolCall),
                        outputs = result.outputs,
                    )
                    ChatToolResult(
                        callId = ready.toolCall.id,
                        name = ready.toolCall.name,
                        status = ChatToolResultStatus.SUCCESS,
                        summary = ready.definition.title,
                        outputText = buildSuccessOutputText(
                            definition = ready.definition,
                            outputs = result.outputs,
                            artifacts = artifacts,
                            progressMessages = progressMessages,
                        ),
                        artifacts = artifacts,
                    )
                }

                is ExecutionResult.Failure -> {
                    ChatToolResult(
                        callId = ready.toolCall.id,
                        name = ready.toolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = ready.definition.title,
                        outputText = buildFailureOutputText(
                            definition = ready.definition,
                            failure = result,
                            progressMessages = progressMessages,
                        ),
                    )
                }

                is ExecutionResult.Signal -> {
                    ChatToolResult(
                        callId = ready.toolCall.id,
                        name = ready.toolCall.name,
                        status = ChatToolResultStatus.ERROR,
                        summary = ready.definition.title,
                        outputText = "Tool `${ready.definition.title}` returned an unsupported workflow control signal instead of a normal result.",
                    )
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            ChatToolResult(
                callId = ready.toolCall.id,
                name = ready.toolCall.name,
                status = ChatToolResultStatus.ERROR,
                summary = ready.definition.title,
                outputText = buildString {
                    append("Tool `${ready.definition.title}` failed with an exception.")
                    throwable.message?.takeIf { it.isNotBlank() }?.let {
                        append("\n")
                        append(it)
                    }
                    if (progressMessages.isNotEmpty()) {
                        append("\n\nRecent progress:\n")
                        progressMessages.takeLast(6).forEach { line ->
                            append("- ")
                            append(line)
                            append("\n")
                        }
                    }
                }.trim(),
            )
        }
    }

    private fun buildParameters(
        module: ActionModule,
        rawArgumentsJson: String,
        artifactStore: ChatAgentArtifactStore,
    ): Map<String, Any?> {
        val defaults = module.createSteps().firstOrNull()?.parameters?.toMutableMap() ?: mutableMapOf()
        val baseStep = ActionStep(moduleId = module.id, parameters = defaults)
        val inputs = module.getDynamicInputs(baseStep, listOf(baseStep))
        val arguments = parseArguments(rawArgumentsJson)

        arguments.forEach { (key, value) ->
            val input = inputs.firstOrNull { it.id == key } ?: return@forEach
            defaults[key] = coerceInputValue(input, value, artifactStore)
        }

        return defaults
    }

    private fun ChatPreparedToolItem.describeForLog(): String {
        return when (this) {
            is ChatPreparedToolItem.ImmediateResult -> "immediate name=${toolCall.name}"
            is ChatPreparedToolItem.NativeReady -> "native name=${toolCall.name} helper=${definition.nativeHelperId}"
            is ChatPreparedToolItem.Ready -> "module name=${toolCall.name} module=${module.id}"
            is ChatPreparedToolItem.SaveWorkflow -> "save_workflow name=${toolCall.name} workflow=${workflow.name}"
            is ChatPreparedToolItem.TemporaryWorkflow -> "temporary_workflow name=${toolCall.name} workflow=${workflow.name}"
        }
    }

    private fun String.compactForLog(maxLength: Int = 160): String {
        val compact = replace(Regex("""\s+"""), " ").trim()
        return if (compact.length > maxLength) compact.take(maxLength) + "…" else compact
    }

    private fun parseArguments(rawArgumentsJson: String): Map<String, Any?> {
        if (rawArgumentsJson.isBlank()) return emptyMap()
        val element = json.parseToJsonElement(rawArgumentsJson)
        val root = element as? JsonObject ?: return emptyMap()
        return root.mapValues { (_, value) -> normalizeJsonValue(value) }
    }

    private fun normalizeJsonValue(element: JsonElement): Any? {
        return when (element) {
            JsonNull -> null
            is JsonObject -> element.mapValues { (_, value) -> normalizeJsonValue(value) }
            is JsonArray -> element.map(::normalizeJsonValue)
            is JsonPrimitive -> {
                element.booleanOrNull
                    ?: element.doubleOrNull
                    ?: element.contentOrNull
            }
        }
    }

    private fun coerceInputValue(
        input: InputDefinition,
        rawValue: Any?,
        artifactStore: ChatAgentArtifactStore,
    ): Any? {
        val artifactValue = resolveArtifactValue(rawValue, artifactStore)
        return when (input.staticType) {
            ParameterType.STRING -> artifactValue ?: rawValue?.toString().orEmpty()
            ParameterType.NUMBER -> coerceNumber(rawValue, input.defaultValue)
            ParameterType.BOOLEAN -> coerceBoolean(rawValue)
            ParameterType.ENUM -> input.normalizeEnumValue(rawValue?.toString(), rawValue?.toString())
            ParameterType.ANY -> artifactValue ?: rawValue
        }
    }

    private fun resolveArtifactValue(
        rawValue: Any?,
        artifactStore: ChatAgentArtifactStore,
    ): Any? {
        val handle = rawValue as? String ?: return null
        if (!handle.startsWith("artifact://")) return null
        return artifactStore.resolve(handle)
            ?: throw IllegalArgumentException("Artifact handle is no longer available: $handle")
    }

    private fun coerceNumber(rawValue: Any?, defaultValue: Any?): Any? {
        val number = when (rawValue) {
            is Number -> rawValue
            is String -> rawValue.toDoubleOrNull()
            else -> null
        } ?: return rawValue

        return when (defaultValue) {
            is Int -> number.toInt()
            is Long -> number.toLong()
            is Float -> number.toFloat()
            else -> number.toDouble()
        }
    }

    private fun coerceBoolean(rawValue: Any?): Any? {
        return when (rawValue) {
            is Boolean -> rawValue
            is String -> rawValue.equals("true", ignoreCase = true)
            else -> rawValue
        }
    }

    private fun buildSuccessOutputText(
        definition: ChatAgentToolDefinition,
        outputs: Map<String, Any?>,
        artifacts: List<ChatArtifactReference>,
        progressMessages: List<String>,
    ): String {
        return buildString {
            append("Tool `${definition.title}` completed successfully.")

            if (outputs.isNotEmpty()) {
                append("\n\nOutputs:\n")
                outputs.forEach { (key, value) ->
                    append("- ")
                    append(key)
                    append(": ")
                    append(summarizeOutputValue(value))
                    append("\n")
                }
            }

            if (artifacts.isNotEmpty()) {
                append("\nArtifacts:\n")
                artifacts.forEach { artifact ->
                    append("- ")
                    append(artifact.key)
                    append(" (")
                    append(artifact.typeLabel)
                    append("): ")
                    append(artifact.handle)
                    append("\n")
                }
            }

            if (progressMessages.isNotEmpty()) {
                append("\nProgress:\n")
                progressMessages.takeLast(8).forEach { message ->
                    append("- ")
                    append(message)
                    append("\n")
                }
            }
        }.trim()
    }

    private fun buildFailureOutputText(
        definition: ChatAgentToolDefinition,
        failure: ExecutionResult.Failure,
        progressMessages: List<String>,
    ): String {
        return buildString {
            append("Tool `${definition.title}` failed.")
            if (failure.errorTitle.isNotBlank()) {
                append("\n")
                append(failure.errorTitle)
            }
            if (failure.errorMessage.isNotBlank()) {
                append("\n")
                append(failure.errorMessage)
            }
            if (progressMessages.isNotEmpty()) {
                append("\n\nRecent progress:\n")
                progressMessages.takeLast(8).forEach { message ->
                    append("- ")
                    append(message)
                    append("\n")
                }
            }
        }.trim()
    }

    private fun buildTemporaryWorkflowResult(
        workflow: ChatPreparedToolItem.TemporaryWorkflow,
        terminalState: ExecutionState,
    ): ChatToolResult {
        val status = if (terminalState is ExecutionState.Finished) {
            ChatToolResultStatus.SUCCESS
        } else {
            ChatToolResultStatus.ERROR
        }
        return ChatToolResult(
            callId = workflow.toolCall.id,
            name = workflow.toolCall.name,
            status = status,
            summary = workflow.definition.title,
            outputText = buildString {
                append(
                    when (terminalState) {
                        is ExecutionState.Finished -> "Temporary workflow `${workflow.workflow.name}` completed successfully."
                        is ExecutionState.Failure -> "Temporary workflow `${workflow.workflow.name}` failed at step ${terminalState.stepIndex + 1}."
                        is ExecutionState.Cancelled -> "Temporary workflow `${workflow.workflow.name}` was cancelled."
                        is ExecutionState.Running -> "Temporary workflow `${workflow.workflow.name}` is still running."
                    }
                )
                append("\n\nSteps:\n")
                workflow.preparedSteps.take(30).forEachIndexed { index, readyStep ->
                    append("- ")
                    append(index + 1)
                    append(". ")
                    append(readyStep.definition.title)
                    append(" (")
                    append(readyStep.step.moduleId)
                    append(")")
                    append("\n")
                }
                if (workflow.preparedSteps.size > 30) {
                    append("- ... ")
                    append(workflow.preparedSteps.size - 30)
                    append(" more steps\n")
                }

                val detailedLog = terminalState.detailedLogOrEmpty().trim()
                if (detailedLog.isNotBlank()) {
                    append("\nExecution log:\n")
                    append(truncateMultiline(detailedLog))
                    append("\n")
                }
            }.trim(),
        )
    }

    private fun ExecutionState.isTerminalExecutionState(): Boolean {
        return this is ExecutionState.Finished ||
            this is ExecutionState.Failure ||
            this is ExecutionState.Cancelled
    }

    private fun ExecutionState.detailedLogOrEmpty(): String {
        return when (this) {
            is ExecutionState.Finished -> detailedLog
            is ExecutionState.Failure -> detailedLog
            is ExecutionState.Cancelled -> detailedLog
            is ExecutionState.Running -> ""
        }
    }

    private fun summarizeOutputValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is VString -> truncate(value.raw)
            is VBoolean -> value.raw.toString()
            is VNumber -> value.raw.toString()
            is VCoordinate -> value.asString()
            is VCoordinateRegion -> value.asString()
            is VImage -> "image artifact available"
            is VScreenElement -> buildString {
                append(value.text?.takeIf { it.isNotBlank() } ?: value.viewId ?: value.className ?: "screen element")
                append(" @ ")
                append(value.centerX)
                append(",")
                append(value.centerY)
            }
            is ChatAgentUiSnapshot -> buildString {
                append(value.currentUi)
                append(" (")
                append(value.elements.size)
                append(" elements)")
            }
            is VObject -> truncate(value.asString())
            is String -> truncate(value)
            else -> truncate(value.toString())
        }
    }

    private fun truncate(text: String, maxLength: Int = 180): String {
        val normalized = text.replace('\n', ' ').trim()
        return if (normalized.length > maxLength) {
            normalized.take(maxLength) + "..."
        } else {
            normalized
        }
    }

    private fun truncateMultiline(text: String, maxLength: Int = 4_000): String {
        val normalized = text.trim()
        return if (normalized.length > maxLength) {
            normalized.take(maxLength) + "\n..."
        } else {
            normalized
        }
    }

    private fun normalizedToolCallId(toolCall: ChatToolCall): String {
        return toolCall.id ?: "call_${UUID.randomUUID()}"
    }

    private data class TemporaryWorkflowSpec(
        val name: String,
        val description: String,
        val maxExecutionTime: Int,
        val steps: List<WorkflowStepSpec>,
    )

    private data class SaveWorkflowSpec(
        val name: String,
        val description: String,
        val isEnabled: Boolean,
        val folderId: String?,
        val tags: List<String>,
        val maxExecutionTime: Int?,
        val reentryBehavior: WorkflowReentryBehavior,
        val triggers: List<WorkflowStepSpec>,
        val steps: List<WorkflowStepSpec>,
    )

    private data class WorkflowStepSpec(
        val id: String,
        val moduleId: String,
        val parameters: JsonObject,
        val indentationLevel: Int,
    )

    private data class SavedWorkflowStepCandidate(
        val module: ActionModule,
        val step: ActionStep,
        val sourceLabel: String,
    )

    private companion object {
        private const val LOG_TAG = "ChatToolExec"
        const val DEFAULT_TEMPORARY_WORKFLOW_MAX_SECONDS = 120
        const val MAX_TEMPORARY_WORKFLOW_MAX_SECONDS = 300
        const val MAX_TEMPORARY_WORKFLOW_REPEAT = 50
        const val MAX_TEMPORARY_WORKFLOW_DELAY_MS = 60_000L
        const val MAX_TEMPORARY_WORKFLOW_EXPANDED_STEPS = 80
        const val MAX_SAVED_WORKFLOW_TRIGGERS = 12
        const val MAX_SAVED_WORKFLOW_STEPS = 200
        const val MAX_SAVED_WORKFLOW_MAX_SECONDS = 3600
        const val MAX_SAVED_WORKFLOW_TAGS = 12
        const val MAX_WORKFLOW_INDENTATION_LEVEL = 12
    }
}
