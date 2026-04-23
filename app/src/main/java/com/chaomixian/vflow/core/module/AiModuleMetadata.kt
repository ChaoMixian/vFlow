package com.chaomixian.vflow.core.module

enum class AiModuleUsageScope {
    DIRECT_TOOL,
    TEMPORARY_WORKFLOW,
}

enum class AiModuleRiskLevel {
    READ_ONLY,
    LOW,
    STANDARD,
    HIGH,
}

data class AiModuleMetadata(
    val usageScopes: Set<AiModuleUsageScope> = emptySet(),
    val riskLevel: AiModuleRiskLevel = AiModuleRiskLevel.STANDARD,
    val directToolDescription: String? = null,
    val workflowStepDescription: String? = null,
    val inputHints: Map<String, String> = emptyMap(),
    val requiredInputIds: Set<String> = emptySet(),
    val allowSavedWorkflow: Boolean? = null,
)

fun directToolMetadata(
    riskLevel: AiModuleRiskLevel = AiModuleRiskLevel.STANDARD,
    directToolDescription: String? = null,
    workflowStepDescription: String? = null,
    inputHints: Map<String, String> = emptyMap(),
    requiredInputIds: Set<String> = emptySet(),
    allowSavedWorkflow: Boolean? = null,
): AiModuleMetadata {
    return AiModuleMetadata(
        usageScopes = setOf(
            AiModuleUsageScope.DIRECT_TOOL,
            AiModuleUsageScope.TEMPORARY_WORKFLOW,
        ),
        riskLevel = riskLevel,
        directToolDescription = directToolDescription,
        workflowStepDescription = workflowStepDescription,
        inputHints = inputHints,
        requiredInputIds = requiredInputIds,
        allowSavedWorkflow = allowSavedWorkflow,
    )
}

fun temporaryWorkflowOnlyMetadata(
    riskLevel: AiModuleRiskLevel = AiModuleRiskLevel.STANDARD,
    workflowStepDescription: String? = null,
    inputHints: Map<String, String> = emptyMap(),
    requiredInputIds: Set<String> = emptySet(),
    allowSavedWorkflow: Boolean? = null,
): AiModuleMetadata {
    return AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = riskLevel,
        workflowStepDescription = workflowStepDescription,
        inputHints = inputHints,
        requiredInputIds = requiredInputIds,
        allowSavedWorkflow = allowSavedWorkflow,
    )
}
