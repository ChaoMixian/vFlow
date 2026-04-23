package com.chaomixian.vflow.ui.chat

import android.content.Context
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.AiModuleUsageScope
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ChatAgentToolDefinition(
    val name: String,
    val title: String,
    val description: String,
    val moduleId: String,
    val moduleDisplayName: String,
    val inputSchema: JsonObject,
    val permissionNames: List<String>,
    val riskLevel: ChatAgentToolRiskLevel,
    val usageScopes: Set<ChatAgentToolUsageScope>,
)

internal const val CHAT_TEMPORARY_WORKFLOW_TOOL_NAME = "vflow_agent_run_temporary_workflow"
internal const val CHAT_TEMPORARY_WORKFLOW_MODULE_ID = "vflow.agent.temporary_workflow"
internal const val CHAT_SAVE_WORKFLOW_TOOL_NAME = "vflow_agent_save_workflow"
internal const val CHAT_SAVE_WORKFLOW_MODULE_ID = "vflow.agent.save_workflow"

internal fun chatToolNameFromModuleId(moduleId: String): String {
    val normalized = moduleId
        .lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
    return if (normalized.startsWith("vflow_")) normalized else "vflow_$normalized"
}

internal class ChatAgentToolRegistry(context: Context) {
    private val appContext = context.applicationContext

    private val toolsByName: Map<String, ChatAgentToolDefinition>
    private val temporaryWorkflowModuleIds: List<String>
    private val savedWorkflowModuleIds: List<String>

    init {
        ModuleRegistry.initialize(appContext)
        temporaryWorkflowModuleIds = buildTemporaryWorkflowModuleIds()
        savedWorkflowModuleIds = buildSavedWorkflowModuleIds()
        toolsByName = (
            listOf(buildTemporaryWorkflowToolDefinition(), buildSaveWorkflowToolDefinition()) +
                buildDirectToolDefinitions()
            ).associateBy { it.name }
    }

    fun getTools(): List<ChatAgentToolDefinition> = toolsByName.values.toList()

    fun getTool(name: String): ChatAgentToolDefinition? = toolsByName[name]

    fun getToolForModuleId(moduleId: String): ChatAgentToolDefinition? {
        return toolsByName[chatToolNameFromModuleId(moduleId)] ?: buildToolDefinition(moduleId)
    }

    fun getRiskLevelForModuleId(moduleId: String): ChatAgentToolRiskLevel = riskLevelForModuleId(moduleId)

    fun isTemporaryWorkflowModuleAllowed(moduleId: String): Boolean {
        return moduleId in temporaryWorkflowModuleIds
    }

    fun isSavedWorkflowModuleAllowed(moduleId: String): Boolean {
        return moduleId in savedWorkflowModuleIds
    }

    fun isTriggerModule(moduleId: String): Boolean {
        return ModuleRegistry.getModule(moduleId)?.let(::isTriggerModule) == true ||
            moduleId.startsWith(TRIGGER_MODULE_PREFIX)
    }

    private fun buildToolDefinition(moduleId: String): ChatAgentToolDefinition? {
        val module = ModuleRegistry.getModule(moduleId) ?: return null
        val baseStep = module.createSteps().firstOrNull() ?: ActionStep(module.id, emptyMap())
        val inputs = module.getDynamicInputs(baseStep, listOf(baseStep))
            .filterNot { it.isHidden }
            .filter(::isInputSupported)
        val localizedName = module.metadata.getLocalizedName(appContext)
        val permissions = module.getRequiredPermissions(baseStep)
            .map { it.getLocalizedName(appContext) }
            .distinct()
        val riskLevel = riskLevelForModuleId(module.id)
        val usageScopes = buildModuleUsageScopes(module.id)

        return ChatAgentToolDefinition(
            name = chatToolNameFromModuleId(module.id),
            title = localizedName,
            description = buildToolDescription(module, localizedName, permissions, riskLevel, usageScopes),
            moduleId = module.id,
            moduleDisplayName = localizedName,
            inputSchema = buildToolSchema(module.id, inputs),
            permissionNames = permissions,
            riskLevel = riskLevel,
            usageScopes = usageScopes,
        )
    }

    private fun buildTemporaryWorkflowToolDefinition(): ChatAgentToolDefinition {
        val stepCatalog = buildCompactModuleCatalog(
            temporaryWorkflowModuleIds.filterNot(::isTriggerModule),
            maxModules = 40,
            preferWorkflowDescriptions = true,
        )
        return ChatAgentToolDefinition(
            name = CHAT_TEMPORARY_WORKFLOW_TOOL_NAME,
            title = "临时工作流",
            description = buildString {
                append("Run a short temporary vFlow workflow in one approval. ")
                append("Use this for deterministic multi-step or repeated actions, for example toggling the flashlight 10 times with a 2000 ms delay. ")
                append("Do not use this for a single clear action; call the matching single-purpose tool directly. ")
                append("Generate real vFlow ActionStep objects with moduleId and parameters. ")
                append("Use stable descriptive step ids and only parameters defined by each module schema. ")
                append("Use vflow.logic.loop.start and vflow.logic.loop.end for compact repeated sequences. ")
                append("Allowed steps are curated action modules only, not triggers and not this temporary workflow tool. ")
                append("Risk level is computed from the workflow steps.")
                append(stepCatalog)
                append(buildVariablePassingGuide(temporaryWorkflowModuleIds))
            },
            moduleId = CHAT_TEMPORARY_WORKFLOW_MODULE_ID,
            moduleDisplayName = "临时工作流",
            inputSchema = buildTemporaryWorkflowSchema(temporaryWorkflowModuleIds),
            permissionNames = emptyList(),
            riskLevel = ChatAgentToolRiskLevel.STANDARD,
            usageScopes = setOf(ChatAgentToolUsageScope.TEMPORARY_WORKFLOW),
        )
    }

    private fun buildSaveWorkflowToolDefinition(): ChatAgentToolDefinition {
        val triggerCatalog = buildCompactModuleCatalog(
            savedWorkflowModuleIds.filter(::isTriggerModule),
            maxModules = 24,
            preferWorkflowDescriptions = false,
        )
        val stepCatalog = buildCompactModuleCatalog(
            savedWorkflowModuleIds.filterNot(::isTriggerModule),
            maxModules = 48,
            preferWorkflowDescriptions = true,
        )
        return ChatAgentToolDefinition(
            name = CHAT_SAVE_WORKFLOW_TOOL_NAME,
            title = "保存工作流",
            description = buildString {
                append("Save a reusable vFlow workflow into the user's workflow list. ")
                append("Use this only when the user asks to create, generate, or save an automation for later reuse. ")
                append("For immediate one-off execution, use a direct tool or vflow_agent_run_temporary_workflow instead. ")
                append("The workflow must contain real vFlow ActionStep objects with canonical moduleId and parameters. ")
                append("Put trigger modules only in workflow.triggers; put action/data/logic modules only in workflow.steps. ")
                append("If no trigger is requested, omit workflow.triggers and the app will add a manual trigger. ")
                append("Use stable descriptive step ids and only parameters defined by each module schema. ")
                append("Do not persist artifact:// handles in saved workflows because chat artifacts are temporary. ")
                append("Risk level is computed from saved modules; workflows with auto triggers or shell-like modules are high risk. ")
                append("Usage scope: saved workflow step. ")
                append(triggerCatalog)
                append(stepCatalog)
                append(buildVariablePassingGuide(savedWorkflowModuleIds))
            },
            moduleId = CHAT_SAVE_WORKFLOW_MODULE_ID,
            moduleDisplayName = "保存工作流",
            inputSchema = buildSaveWorkflowSchema(savedWorkflowModuleIds),
            permissionNames = emptyList(),
            riskLevel = ChatAgentToolRiskLevel.HIGH,
            usageScopes = setOf(ChatAgentToolUsageScope.SAVED_WORKFLOW),
        )
    }

    private fun buildTemporaryWorkflowSchema(moduleIds: List<String>): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", JsonPrimitive(false))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "workflow",
                        buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", JsonPrimitive(false))
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "Short user-visible workflow name.")
                                        }
                                    )
                                    put(
                                        "description",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "Brief summary of what the workflow will do.")
                                        }
                                    )
                                    put(
                                        "maxExecutionTime",
                                        buildJsonObject {
                                            put("type", "integer")
                                            put("minimum", 1)
                                            put("maximum", 300)
                                            put("description", "Maximum execution time in seconds. Omit for 120 seconds.")
                                        }
                                    )
                                    put(
                                        "steps",
                                        buildJsonObject {
                                            put("type", "array")
                                            put("minItems", 1)
                                            put("maxItems", 80)
                                            put("description", "Ordered vFlow ActionStep objects. Do not include trigger modules.")
                                            put(
                                                "items",
                                                buildJsonObject {
                                                    put("type", "object")
                                                    put("additionalProperties", JsonPrimitive(false))
                                                    put(
                                                        "properties",
                                                        buildJsonObject {
                                                            put(
                                                                "id",
                                                                buildJsonObject {
                                                                    put("type", "string")
                                                                    put("description", "Unique step ID. Critical: other steps reference this step's outputs via {{this_id.output_name}}.")
                                                                }
                                                            )
                                                            put(
                                                                "moduleId",
                                                                buildJsonObject {
                                                                    put("type", "string")
                                                                    put("enum", JsonArray(moduleIds.map(::JsonPrimitive)))
                                                                }
                                                            )
                                                            put(
                                                                "parameters",
                                                                buildJsonObject {
                                                                    put("type", "object")
                                                                    put("additionalProperties", JsonPrimitive(true))
                                                                    put("description", "Step parameters. Values can be literal values or magic variable references like {{previousStepId.outputId}} to pass data from earlier steps.")
                                                                }
                                                            )
                                                            put(
                                                                "indentationLevel",
                                                                buildJsonObject {
                                                                    put("type", "integer")
                                                                    put("minimum", 0)
                                                                    put("maximum", 8)
                                                                    put("description", "Visual indentation level for block contents. Use 1 inside a loop or if block.")
                                                                }
                                                            )
                                                        }
                                                    )
                                                    put(
                                                        "required",
                                                        buildJsonArray {
                                                            add(JsonPrimitive("moduleId"))
                                                            add(JsonPrimitive("parameters"))
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                            put(
                                "required",
                                buildJsonArray {
                                    add(JsonPrimitive("name"))
                                    add(JsonPrimitive("steps"))
                                }
                            )
                        }
                    )
                }
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("workflow"))
                }
            )
        }
    }

    private fun buildSaveWorkflowSchema(moduleIds: List<String>): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", JsonPrimitive(false))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "workflow",
                        buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", JsonPrimitive(false))
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "name",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "Short user-visible workflow name.")
                                        }
                                    )
                                    put(
                                        "description",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "Brief summary shown in the workflow list.")
                                        }
                                    )
                                    put(
                                        "isEnabled",
                                        buildJsonObject {
                                            put("type", "boolean")
                                            put("description", "Whether the saved workflow is enabled. Omit for true.")
                                        }
                                    )
                                    put(
                                        "folderId",
                                        buildJsonObject {
                                            put("type", "string")
                                            put("description", "Optional existing workflow folder id.")
                                        }
                                    )
                                    put(
                                        "tags",
                                        buildJsonObject {
                                            put("type", "array")
                                            put("items", buildJsonObject { put("type", "string") })
                                            put("description", "Optional workflow tags.")
                                        }
                                    )
                                    put(
                                        "maxExecutionTime",
                                        buildJsonObject {
                                            put("type", "integer")
                                            put("minimum", 1)
                                            put("maximum", 3600)
                                            put("description", "Maximum execution time in seconds. Omit to use the app default.")
                                        }
                                    )
                                    put(
                                        "reentryBehavior",
                                        buildJsonObject {
                                            put("type", "string")
                                            put(
                                                "enum",
                                                JsonArray(
                                                    listOf(
                                                        "block_new",
                                                        "stop_current_and_run_new",
                                                        "allow_parallel",
                                                    ).map(::JsonPrimitive)
                                                )
                                            )
                                            put("description", "How to handle a new trigger while the workflow is already running.")
                                        }
                                    )
                                    put(
                                        "triggers",
                                        buildJsonObject {
                                            put("type", "array")
                                            put("maxItems", 12)
                                            put("description", "Optional trigger ActionStep objects. Use only vflow.trigger.* modules here. Omit for a manual trigger.")
                                            put("items", buildWorkflowStepItemSchema(moduleIds, "Trigger or manual step ID. Other steps may reference this step's outputs via {{this_id.output_name}}."))
                                        }
                                    )
                                    put(
                                        "steps",
                                        buildJsonObject {
                                            put("type", "array")
                                            put("minItems", 1)
                                            put("maxItems", 200)
                                            put("description", "Ordered non-trigger vFlow ActionStep objects.")
                                            put("items", buildWorkflowStepItemSchema(moduleIds, "Unique step ID. Critical: other steps reference this step's outputs via {{this_id.output_name}}."))
                                        }
                                    )
                                }
                            )
                            put(
                                "required",
                                buildJsonArray {
                                    add(JsonPrimitive("name"))
                                    add(JsonPrimitive("steps"))
                                }
                            )
                        }
                    )
                }
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("workflow"))
                }
            )
        }
    }

    private fun buildWorkflowStepItemSchema(
        moduleIds: List<String>,
        idDescription: String,
    ): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", JsonPrimitive(false))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "id",
                        buildJsonObject {
                            put("type", "string")
                            put("description", idDescription)
                        }
                    )
                    put(
                        "moduleId",
                        buildJsonObject {
                            put("type", "string")
                            put("enum", JsonArray(moduleIds.map(::JsonPrimitive)))
                        }
                    )
                    put(
                        "parameters",
                        buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", JsonPrimitive(true))
                            put("description", "Step parameters. Values can be literal values or magic variable references like {{previousStepId.outputId}} to pass data from earlier steps.")
                        }
                    )
                    put(
                        "indentationLevel",
                        buildJsonObject {
                            put("type", "integer")
                            put("minimum", 0)
                            put("maximum", 12)
                            put("description", "Visual indentation level for block contents.")
                        }
                    )
                }
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("moduleId"))
                    add(JsonPrimitive("parameters"))
                }
            )
        }
    }

    private fun buildToolDescription(
        module: ActionModule,
        localizedName: String,
        permissions: List<String>,
        riskLevel: ChatAgentToolRiskLevel,
        usageScopes: Set<ChatAgentToolUsageScope>,
    ): String {
        val parts = mutableListOf<String>()
        parts += "vFlow module: $localizedName."
        parts += module.aiMetadata?.directToolDescription
            ?: module.metadata.getLocalizedDescription(appContext)
        parts += "Risk level: ${riskLevel.name.lowercase()}."
        if (usageScopes.isNotEmpty()) {
            parts += "Usage scope: ${usageScopes.joinToString { it.label }}."
        }
        if (permissions.isNotEmpty()) {
            parts += "May require Android permissions: ${permissions.joinToString()}."
        }
        return parts.joinToString(separator = " ")
    }

    private fun buildToolSchema(
        moduleId: String,
        inputs: List<InputDefinition>,
    ): JsonObject {
        val required = ModuleRegistry.getModule(moduleId)?.aiMetadata?.requiredInputIds.orEmpty()
            .filter { inputId -> inputs.any { it.id == inputId } }

        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", JsonPrimitive(false))
            put(
                "properties",
                buildJsonObject {
                    inputs.forEach { input ->
                        put(input.id, buildInputSchema(moduleId, input))
                    }
                }
            )
            if (required.isNotEmpty()) {
                put(
                    "required",
                    buildJsonArray {
                        required.forEach { inputId ->
                            add(JsonPrimitive(inputId))
                        }
                    }
                )
            }
        }
    }

    private fun buildInputSchema(moduleId: String, input: InputDefinition): JsonObject {
        return buildJsonObject {
            when (input.staticType) {
                ParameterType.STRING -> put("type", "string")
                ParameterType.NUMBER -> put("type", "number")
                ParameterType.BOOLEAN -> put("type", "boolean")
                ParameterType.ENUM -> {
                    put("type", "string")
                    put(
                        "enum",
                        JsonArray(input.options.map(::JsonPrimitive))
                    )
                }
                ParameterType.ANY -> put("type", "string")
            }

            val description = buildInputDescription(moduleId, input)
            if (description.isNotBlank()) {
                put("description", description)
            }
        }
    }

    private fun buildInputDescription(moduleId: String, input: InputDefinition): String {
        val parts = mutableListOf<String>()
        parts += input.getLocalizedName(appContext)
        input.getLocalizedHint(appContext)
            ?.takeIf { it.isNotBlank() }
            ?.let(parts::add)

        val artifactTypes = input.acceptedMagicVariableTypes
            .mapNotNull(::artifactTypeLabelFromTypeId)
            .distinct()
        if (artifactTypes.isNotEmpty()) {
            parts += "Can accept prior artifact handles of type ${artifactTypes.joinToString()}."
        }

        ModuleRegistry.getModule(moduleId)?.aiMetadata?.inputHints?.get(input.id)
            ?.let(parts::add)

        if (input.staticType == ParameterType.ENUM && input.options.isNotEmpty()) {
            parts += "Allowed values: ${input.options.joinToString()}."
        }

        return parts.joinToString(separator = " ")
    }

    private fun isInputSupported(input: InputDefinition): Boolean {
        return when (input.staticType) {
            ParameterType.STRING,
            ParameterType.NUMBER,
            ParameterType.BOOLEAN,
            ParameterType.ENUM -> true

            ParameterType.ANY -> input.acceptedMagicVariableTypes.isNotEmpty()
        }
    }

    private fun artifactTypeLabelFromTypeId(typeId: String): String? {
        return when (typeId) {
            VTypeRegistry.IMAGE.id -> "image"
            VTypeRegistry.COORDINATE.id -> "coordinate"
            VTypeRegistry.COORDINATE_REGION.id -> "coordinate region"
            VTypeRegistry.SCREEN_ELEMENT.id -> "screen element"
            VTypeRegistry.STRING.id -> "text"
            VTypeRegistry.NUMBER.id -> "number"
            else -> null
        }
    }

    private fun buildModuleUsageScopes(moduleId: String): Set<ChatAgentToolUsageScope> {
        val metadata = ModuleRegistry.getModule(moduleId)?.aiMetadata
        return buildSet {
            if (metadata?.usageScopes?.contains(AiModuleUsageScope.DIRECT_TOOL) == true) {
                add(ChatAgentToolUsageScope.DIRECT_TOOL)
            }
            if (metadata?.usageScopes?.contains(AiModuleUsageScope.TEMPORARY_WORKFLOW) == true) {
                add(ChatAgentToolUsageScope.TEMPORARY_WORKFLOW)
            }
            if (moduleId in savedWorkflowModuleIds) {
                add(ChatAgentToolUsageScope.SAVED_WORKFLOW)
            }
        }
    }

    private fun buildDirectToolDefinitions(): List<ChatAgentToolDefinition> {
        val moduleIds = ModuleRegistry.getAllModules()
            .filter { module ->
                module.aiMetadata?.usageScopes?.contains(AiModuleUsageScope.DIRECT_TOOL) == true ||
                    module.id in LEGACY_DIRECT_TOOL_MODULE_IDS
            }
            .map { it.id }
            .distinct()
            .sorted()
        return moduleIds.mapNotNull(::buildToolDefinition)
    }

    private fun buildTemporaryWorkflowModuleIds(): List<String> {
        return ModuleRegistry.getAllModules()
            .filter { module ->
                module.aiMetadata?.usageScopes?.contains(AiModuleUsageScope.TEMPORARY_WORKFLOW) == true ||
                    module.id in LEGACY_TEMPORARY_WORKFLOW_MODULE_IDS
            }
            .sortedWith(compareBy<ActionModule> { ModuleCategories.getSortOrder(it.metadata.getResolvedCategoryId()) }.thenBy { it.id })
            .map { it.id }
    }

    private fun buildSavedWorkflowModuleIds(): List<String> {
        return ModuleRegistry.getAllModules()
            .filter(::isSavedWorkflowModuleAllowed)
            .sortedWith(compareBy<ActionModule> { ModuleCategories.getSortOrder(it.metadata.getResolvedCategoryId()) }.thenBy { it.id })
            .map { it.id }
    }

    private fun isSavedWorkflowModuleAllowed(module: ActionModule): Boolean {
        val category = module.metadata.getResolvedCategoryId()
        if (category == ModuleCategories.TEMPLATE) return false
        if (module.id.startsWith("vflow.snippet.")) return false
        if (module.id in LEGACY_SAVED_WORKFLOW_EXCLUDED_MODULE_IDS) return false
        if (module.aiMetadata?.allowSavedWorkflow == false) return false
        return true
    }

    private fun isTriggerModule(module: ActionModule): Boolean {
        return module.metadata.getResolvedCategoryId() == ModuleCategories.TRIGGER ||
            module.id.startsWith(TRIGGER_MODULE_PREFIX)
    }

    private fun buildCompactModuleCatalog(
        moduleIds: List<String>,
        maxModules: Int,
        preferWorkflowDescriptions: Boolean,
    ): String {
        val entries = moduleIds
            .take(maxModules)
            .mapNotNull { moduleId ->
                val module = ModuleRegistry.getModule(moduleId) ?: return@mapNotNull null
                val defaultStep = module.createSteps().firstOrNull() ?: ActionStep(module.id, emptyMap())
                val inputs = module.getDynamicInputs(defaultStep, listOf(defaultStep))
                    .filterNot { it.isHidden }
                    .filter(::isInputSupported)
                    .take(6)
                    .joinToString(", ") { input ->
                        val options = if (input.staticType == ParameterType.ENUM && input.options.isNotEmpty()) {
                            "=${input.options.joinToString("/")}"
                        } else {
                            ""
                        }
                        "${input.id}$options"
                    }
                val name = module.metadata.getLocalizedName(appContext)
                val description = if (preferWorkflowDescriptions) {
                    module.aiMetadata?.workflowStepDescription
                } else {
                    module.aiMetadata?.directToolDescription
                } ?: module.metadata.getLocalizedDescription(appContext)
                if (inputs.isBlank()) {
                    "${module.id}($name: $description)"
                } else {
                    "${module.id}($name: $description; inputs: $inputs)"
                }
            }
        if (entries.isEmpty()) return ""
        return " Module catalog: ${entries.joinToString("; ")}."
    }

    private fun buildModuleOutputCatalog(moduleIds: List<String>): String {
        val entries = moduleIds.mapNotNull { moduleId ->
            val module = ModuleRegistry.getModule(moduleId) ?: return@mapNotNull null
            val outputs = module.getOutputs(null).take(8).joinToString(",") { it.id }
            if (outputs.isBlank()) null else "${module.id}($outputs)"
        }
        if (entries.isEmpty()) return ""
        return "\n\nAvailable step outputs: ${entries.joinToString("; ")}."
    }

    private fun buildVariablePassingGuide(moduleIds: List<String>): String {
        val outputCatalog = buildModuleOutputCatalog(moduleIds)
        return """
To pass data from one step to another, give each step a meaningful `id` and use magic variable syntax in parameters: {{STEP_ID.OUTPUT_ID}}.
- References must point to earlier steps only. Do not reference future steps or output ids that are not listed for that module.
- Property access: {{STEP_ID.OUTPUT_ID.PROPERTY}}.
- Available properties by output type: Image(.width,.height,.path,.size,.name,.uri), ScreenElement(.text,.x,.y,.width,.height,.center,.region,.id,.class), Coordinate(.x,.y), List(.count,.first,.last,.random,.isempty), String(.length,.uppercase,.lowercase,.trim,.removeSpaces), Number(.int,.round,.abs,.length), Dictionary(.count,.keys,.values).
- Coordinate passing: When a step outputs a Coordinate or a property like .center, pass the whole object directly (e.g. "target": "{{find_btn.elements.0.center}}"). Do NOT manually splice x,y components unless the target format requires separate values.
- List indexing: {{step.list.0}} for first item; String slicing: {{step.str.0}} for first character, {{step.str.0:3}} for substring.
- Element finding preference: Prefer vflow.interaction.find_element (accessibility service) over OCR whenever possible; use OCR only as a fallback when accessibility cannot find the target.

Block structure rules:
- Loop.start/Loop.end and If.start/If.middle/If.end must be paired. Set indentationLevel=1 for steps inside a loop or if block.
- Loop.start outputs "loop_index" (1-based) and "loop_total". Use {{loop_start_id.loop_index}} inside the loop body.
- If.start evaluates its "condition" parameter; If.end has no extra parameters.""".trimIndent() + outputCatalog
    }

    private companion object {
        private const val TRIGGER_MODULE_PREFIX = "vflow.trigger."

        private val LEGACY_DIRECT_TOOL_MODULE_IDS = setOf(
            "vflow.interaction.get_current_activity",
            "vflow.system.capture_screen",
            "vflow.core.capture_screen",
            "vflow.interaction.ocr",
            "vflow.interaction.find_element",
            "vflow.device.click",
            "vflow.interaction.screen_operation",
            "vflow.interaction.input_text",
            "vflow.device.send_key_event",
            "vflow.core.screen_operation",
            "vflow.core.input_text",
            "vflow.core.press_key",
            "vflow.system.launch_app",
            "vflow.system.close_app",
            "vflow.core.force_stop_app",
            "vflow.system.wifi",
            "vflow.system.bluetooth",
            "vflow.system.brightness",
            "vflow.system.mobile_data",
            "vflow.system.get_clipboard",
            "vflow.system.set_clipboard",
            "vflow.core.get_clipboard",
            "vflow.core.set_clipboard",
            "vflow.system.wake_screen",
            "vflow.system.wake_and_unlock_screen",
            "vflow.system.sleep_screen",
            "vflow.system.darkmode",
            "vflow.device.vibration",
            "vflow.device.flashlight",
            "vflow.device.delay",
            "vflow.shizuku.shell_command",
            "vflow.core.shell_command",
        )

        private val LEGACY_TEMPORARY_WORKFLOW_MODULE_IDS = LEGACY_DIRECT_TOOL_MODULE_IDS + setOf(
            "vflow.logic.loop.start",
            "vflow.logic.loop.end",
            "vflow.logic.if.start",
            "vflow.logic.if.middle",
            "vflow.logic.if.end",
            "vflow.logic.break_loop",
            "vflow.logic.continue_loop",
        )

        private val LEGACY_SAVED_WORKFLOW_EXCLUDED_MODULE_IDS = setOf(
            "vflow.ai.agent",
            "vflow.ai.autoglm",
            "vflow.interaction.operit",
        )

        private fun riskLevelForModuleId(moduleId: String): ChatAgentToolRiskLevel {
            return when (ModuleRegistry.getModule(moduleId)?.aiMetadata?.riskLevel) {
                AiModuleRiskLevel.READ_ONLY -> ChatAgentToolRiskLevel.READ_ONLY
                AiModuleRiskLevel.LOW -> ChatAgentToolRiskLevel.LOW
                AiModuleRiskLevel.HIGH -> ChatAgentToolRiskLevel.HIGH
                AiModuleRiskLevel.STANDARD, null -> ChatAgentToolRiskLevel.STANDARD
            }
        }
    }
}
