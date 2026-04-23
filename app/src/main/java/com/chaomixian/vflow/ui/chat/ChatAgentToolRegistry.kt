package com.chaomixian.vflow.ui.chat

import android.content.Context
import com.chaomixian.vflow.core.module.ActionModule
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
    private val savedWorkflowModuleIds: List<String>

    init {
        ModuleRegistry.initialize(appContext)
        savedWorkflowModuleIds = buildSavedWorkflowModuleIds()
        toolsByName = (
            listOf(buildTemporaryWorkflowToolDefinition(), buildSaveWorkflowToolDefinition()) +
                EXPOSED_MODULE_IDS.mapNotNull(::buildToolDefinition)
            ).associateBy { it.name }
    }

    fun getTools(): List<ChatAgentToolDefinition> = toolsByName.values.toList()

    fun getTool(name: String): ChatAgentToolDefinition? = toolsByName[name]

    fun getToolForModuleId(moduleId: String): ChatAgentToolDefinition? {
        return toolsByName[chatToolNameFromModuleId(moduleId)] ?: buildToolDefinition(moduleId)
    }

    fun getRiskLevelForModuleId(moduleId: String): ChatAgentToolRiskLevel = riskLevelForModuleId(moduleId)

    fun isTemporaryWorkflowModuleAllowed(moduleId: String): Boolean {
        return moduleId in TEMPORARY_WORKFLOW_MODULE_IDS
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
                append(buildVariablePassingGuide(TEMPORARY_WORKFLOW_MODULE_IDS))
            },
            moduleId = CHAT_TEMPORARY_WORKFLOW_MODULE_ID,
            moduleDisplayName = "临时工作流",
            inputSchema = buildTemporaryWorkflowSchema(TEMPORARY_WORKFLOW_MODULE_IDS),
            permissionNames = emptyList(),
            riskLevel = ChatAgentToolRiskLevel.STANDARD,
            usageScopes = setOf(ChatAgentToolUsageScope.TEMPORARY_WORKFLOW),
        )
    }

    private fun buildSaveWorkflowToolDefinition(): ChatAgentToolDefinition {
        val triggerCatalog = buildCompactModuleCatalog(
            savedWorkflowModuleIds.filter(::isTriggerModule),
            maxModules = 24,
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
        parts += TOOL_USAGE_NOTES[module.id]
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
        val required = REQUIRED_INPUT_IDS[moduleId].orEmpty()
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

        INPUT_USAGE_NOTES["${moduleId}#${input.id}"]
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
        return buildSet {
            add(ChatAgentToolUsageScope.DIRECT_TOOL)
            if (moduleId in TEMPORARY_WORKFLOW_MODULE_IDS) {
                add(ChatAgentToolUsageScope.TEMPORARY_WORKFLOW)
            }
            if (moduleId in savedWorkflowModuleIds) {
                add(ChatAgentToolUsageScope.SAVED_WORKFLOW)
            }
        }
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
        if (module.id in SAVED_WORKFLOW_EXCLUDED_MODULE_IDS) return false
        return true
    }

    private fun isTriggerModule(module: ActionModule): Boolean {
        return module.metadata.getResolvedCategoryId() == ModuleCategories.TRIGGER ||
            module.id.startsWith(TRIGGER_MODULE_PREFIX)
    }

    private fun buildCompactModuleCatalog(
        moduleIds: List<String>,
        maxModules: Int,
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
                if (inputs.isBlank()) {
                    "${module.id}($name)"
                } else {
                    "${module.id}($name: $inputs)"
                }
            }
        if (entries.isEmpty()) return ""
        return " Trigger catalog: ${entries.joinToString("; ")}."
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

        private val EXPOSED_MODULE_IDS = listOf(
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

        private val TEMPORARY_WORKFLOW_EXTRA_MODULE_IDS = listOf(
            "vflow.logic.loop.start",
            "vflow.logic.loop.end",
            "vflow.logic.if.start",
            "vflow.logic.if.middle",
            "vflow.logic.if.end",
            "vflow.logic.break_loop",
            "vflow.logic.continue_loop",
        )

        private val TEMPORARY_WORKFLOW_MODULE_IDS =
            (EXPOSED_MODULE_IDS + TEMPORARY_WORKFLOW_EXTRA_MODULE_IDS).distinct()

        private val SAVED_WORKFLOW_EXCLUDED_MODULE_IDS = setOf(
            "vflow.ai.agent",
            "vflow.ai.autoglm",
            "vflow.interaction.operit",
        )

        private val REQUIRED_INPUT_IDS = mapOf(
            "vflow.interaction.ocr" to listOf("image"),
            "vflow.device.click" to listOf("target"),
            "vflow.interaction.screen_operation" to listOf("target"),
            "vflow.interaction.input_text" to listOf("text"),
            "vflow.core.screen_operation" to listOf("target"),
            "vflow.core.input_text" to listOf("text"),
            "vflow.system.launch_app" to listOf("packageName"),
            "vflow.system.close_app" to listOf("packageName"),
            "vflow.core.force_stop_app" to listOf("package_name"),
            "vflow.system.wifi" to listOf("state"),
            "vflow.system.bluetooth" to listOf("state"),
            "vflow.system.brightness" to listOf("brightness_level"),
            "vflow.system.mobile_data" to listOf("action"),
            "vflow.system.set_clipboard" to listOf("content"),
            "vflow.core.set_clipboard" to listOf("text"),
            "vflow.system.darkmode" to listOf("mode"),
            "vflow.device.vibration" to listOf("mode"),
            "vflow.device.flashlight" to listOf("mode"),
            "vflow.device.delay" to listOf("duration"),
            "vflow.shizuku.shell_command" to listOf("command"),
            "vflow.core.shell_command" to listOf("command"),
            "vflow.logic.loop.start" to listOf("count"),
        )

        private fun riskLevelForModuleId(moduleId: String): ChatAgentToolRiskLevel {
            return when (moduleId) {
                "vflow.interaction.get_current_activity",
                "vflow.system.capture_screen",
                "vflow.core.capture_screen",
                "vflow.interaction.ocr",
                "vflow.interaction.find_element",
                "vflow.system.get_clipboard",
                "vflow.core.get_clipboard" -> ChatAgentToolRiskLevel.READ_ONLY

                "vflow.device.delay",
                "vflow.logic.loop.start",
                "vflow.logic.loop.end",
                "vflow.logic.if.start",
                "vflow.logic.if.middle",
                "vflow.logic.if.end",
                "vflow.logic.break_loop",
                "vflow.logic.continue_loop" -> ChatAgentToolRiskLevel.LOW

                "vflow.shizuku.shell_command",
                "vflow.core.shell_command",
                "vflow.core.uinput_screen_operation",
                "vflow.data.file_operation",
                "vflow.system.lua",
                "vflow.system.js",
                "vflow.system.invoke",
                "vflow.system.read_sms",
                "vflow.device.call_phone",
                "vflow.logic.call_workflow",
                "vflow.logic.stop_workflow" -> ChatAgentToolRiskLevel.HIGH

                else -> ChatAgentToolRiskLevel.STANDARD
            }
        }

        private val TOOL_USAGE_NOTES = mapOf(
            "vflow.interaction.get_current_activity" to "Read the current foreground app and activity. Use this to verify which screen the device is on before acting.",
            "vflow.system.capture_screen" to "Capture the current screen. Prefer this before OCR or visual reasoning. Returns an image artifact handle that can be reused by later tools.",
            "vflow.core.capture_screen" to "Capture the current screen through vFlow Core when Core is available. Returns an image artifact handle.",
            "vflow.interaction.ocr" to "Run OCR on an image artifact and optionally find target text coordinates. Use `mode=find` with `target_text` when you need a location. Prefer vflow.interaction.find_element over OCR; use OCR only as a fallback when accessibility cannot find the target.",
            "vflow.interaction.find_element" to "Search the accessibility tree for UI elements by text, id, class, or region. Returns screen element artifacts for later actions. Prefer this over OCR when the target element is accessible.",
            "vflow.device.click" to "Click a target UI element, coordinate, or text/id string.",
            "vflow.interaction.screen_operation" to "Perform tap, long press, or swipe on a coordinate or element. Prefer this for gestures instead of shell commands.",
            "vflow.interaction.input_text" to "Type text into the currently focused input field.",
            "vflow.device.send_key_event" to "Trigger global Android actions like back, home, recents, notifications, or quick settings. Do not use quick settings to control Wi-Fi, Bluetooth, flashlight, brightness, or dark mode when a direct vFlow tool exists.",
            "vflow.core.screen_operation" to "Perform tap, long press, or swipe through vFlow Core when Core is available.",
            "vflow.core.input_text" to "Type text through vFlow Core when Core is available.",
            "vflow.core.press_key" to "Send a raw Android key code through vFlow Core.",
            "vflow.system.launch_app" to "Launch an app or a specific activity by package name.",
            "vflow.system.close_app" to "Force-stop an app by package name.",
            "vflow.core.force_stop_app" to "Force-stop an app through vFlow Core by package name.",
            "vflow.system.wifi" to "Turn Wi-Fi on, off, or toggle it directly. Prefer this single-purpose tool over opening quick settings.",
            "vflow.system.bluetooth" to "Turn Bluetooth on, off, or toggle it directly. Prefer this single-purpose tool over opening quick settings.",
            "vflow.system.brightness" to "Set screen brightness directly with a value from 0 to 255. Prefer this over opening system settings.",
            "vflow.system.mobile_data" to "Enable or disable mobile data directly when shell privileges are available.",
            "vflow.system.get_clipboard" to "Read the current clipboard. May return text and/or an image artifact.",
            "vflow.system.set_clipboard" to "Write text or an image artifact into the clipboard.",
            "vflow.core.get_clipboard" to "Read clipboard text through vFlow Core.",
            "vflow.core.set_clipboard" to "Write clipboard text through vFlow Core.",
            "vflow.system.wake_screen" to "Wake the screen only. Prefer this over wake-and-unlock when unlocking is not requested.",
            "vflow.system.wake_and_unlock_screen" to "Wake the device and optionally enter a lock-screen PIN or password. Use only when the task really needs the device unlocked.",
            "vflow.system.sleep_screen" to "Turn the screen off directly. Prefer this single-purpose tool over simulating UI navigation.",
            "vflow.system.darkmode" to "Switch the system dark/light mode directly.",
            "vflow.device.vibration" to "Trigger device vibration directly.",
            "vflow.device.flashlight" to "Turn the flashlight on, off, or toggle it directly. Prefer this single-purpose tool over opening quick settings or using OCR.",
            "vflow.device.delay" to "Wait for the requested number of milliseconds. Use this when the UI needs time to settle after an action.",
            "vflow.shizuku.shell_command" to "Execute a shell command through Shizuku or Root. This is high risk; use only when no safer single-purpose vFlow module can satisfy the request.",
            "vflow.core.shell_command" to "Execute a shell command through vFlow Core with shell or root privileges. This is high risk; use only when no safer single-purpose vFlow module can satisfy the request.",
            "vflow.logic.loop.start" to "Start a fixed-count loop. Close it later with vflow.logic.loop.end. Loop outputs are 1-based.",
            "vflow.logic.loop.end" to "End the nearest fixed-count loop.",
            "vflow.logic.if.start" to "Start an if block. Close it later with vflow.logic.if.end; optionally use vflow.logic.if.middle for else.",
            "vflow.logic.if.middle" to "Else branch for the nearest if block.",
            "vflow.logic.if.end" to "End the nearest if block.",
            "vflow.logic.break_loop" to "Break out of the current loop.",
            "vflow.logic.continue_loop" to "Continue to the next iteration of the current loop.",
        )

        private val INPUT_USAGE_NOTES = mapOf(
            "vflow.system.capture_screen#region" to "Format: `left,top,right,bottom` in pixels.",
            "vflow.interaction.ocr#image" to "Pass an image artifact handle returned by a capture or clipboard tool.",
            "vflow.interaction.find_element#search_region" to "Pass a coordinate-region artifact handle to limit the search area.",
            "vflow.device.click#target" to "Use a coordinate string like `540,1200`, a plain text/view-id string, or an artifact handle for a screen element or coordinate.",
            "vflow.interaction.screen_operation#target" to "Use a coordinate string or an artifact handle for a screen element or coordinate.",
            "vflow.interaction.screen_operation#target_end" to "For swipe, use a coordinate string or an artifact handle for a screen element or coordinate.",
            "vflow.core.screen_operation#target" to "Use a coordinate string or an artifact handle for a coordinate.",
            "vflow.core.screen_operation#target_end" to "For swipe, use a coordinate string or an artifact handle for a coordinate.",
            "vflow.interaction.input_text#text" to "Use plain text only; do not include JSON.",
            "vflow.system.launch_app#packageName" to "Use the Android package name, for example `com.tencent.mm`.",
            "vflow.system.launch_app#activityName" to "Use `LAUNCH` for the app default entry point, or a fully qualified activity class name.",
            "vflow.system.close_app#packageName" to "Use the Android package name.",
            "vflow.core.force_stop_app#package_name" to "Use the Android package name.",
            "vflow.system.wifi#state" to "Use `on` to enable Wi-Fi, `off` to disable it, or `toggle` only when the user explicitly asks to toggle.",
            "vflow.system.bluetooth#state" to "Use `on` to enable Bluetooth, `off` to disable it, or `toggle` only when the user explicitly asks to toggle.",
            "vflow.system.brightness#brightness_level" to "Use a number from 0 to 255.",
            "vflow.system.mobile_data#action" to "Use `enable` to turn mobile data on or `disable` to turn it off.",
            "vflow.system.set_clipboard#content" to "Use plain text or an image artifact handle.",
            "vflow.core.set_clipboard#text" to "Use plain text only.",
            "vflow.system.darkmode#mode" to "Use `dark`, `light`, `auto`, or `toggle` only when the user explicitly asks to toggle.",
            "vflow.device.vibration#mode" to "Use `once` for a simple vibration unless the user asks for a notification, ringtone, or custom pattern.",
            "vflow.device.vibration#duration" to "Duration in milliseconds for `once` mode.",
            "vflow.device.flashlight#mode" to "Use `on` to turn the flashlight on, `off` to turn it off, or `toggle` only when the user explicitly asks to toggle.",
            "vflow.device.flashlight#strengthPercent" to "Optional Android 13+ flashlight strength from 1 to 100. Omit it for ordinary on/off requests.",
            "vflow.system.wake_and_unlock_screen#unlock_password" to "Leave empty to only wake and swipe. Provide an ASCII PIN or password only when necessary.",
            "vflow.device.delay#duration" to "Duration in milliseconds.",
            "vflow.shizuku.shell_command#mode" to "Use `auto` unless the user explicitly requests Root or Shizuku.",
            "vflow.shizuku.shell_command#command" to "Shell command to execute. Avoid destructive or broad commands unless the user explicitly asked for them.",
            "vflow.core.shell_command#mode" to "Use `auto` unless the user explicitly requests shell or root mode.",
            "vflow.core.shell_command#command" to "Shell command to execute through vFlow Core. Avoid destructive or broad commands unless the user explicitly asked for them.",
            "vflow.logic.loop.start#count" to "Positive repeat count. For 'ten times', use 10.",
        )
    }
}
