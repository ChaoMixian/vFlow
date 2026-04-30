package com.chaomixian.vflow.ui.workflow_editor

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.BlockBehavior
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionEditorUiModelBuilderTest {

    @Test
    fun `build separates normal folded and handled inputs`() {
        val sessionState = ActionEditorSessionState().apply {
            this["mode"] = "basic"
        }
        val module = FakeActionModule(
            inputs = listOf(
                InputDefinition("title", "Title", ParameterType.STRING, ""),
                InputDefinition("advanced", "Advanced", ParameterType.STRING, "", isFolded = true),
                InputDefinition("handled", "Handled", ParameterType.STRING, "")
            ),
            uiProvider = FakeModuleUiProvider(setOf("handled"))
        )

        val uiModel = ActionEditorUiModelBuilder.build(module, sessionState, allSteps = null)

        assertTrue(uiModel.showCustomUi)
        assertEquals(listOf("title"), uiModel.genericInputsSection.fields.map { it.inputDefinition.id })
        assertEquals(listOf("advanced"), uiModel.advancedInputsSection.fields.map { it.inputDefinition.id })
        assertTrue(uiModel.showsAnyGenericInputs)
        assertTrue(uiModel.shouldShowAdvancedDivider)
    }

    @Test
    fun `build respects visibility using corrected enum values`() {
        val sessionState = ActionEditorSessionState().apply {
            this["mode"] = "legacy_mode"
        }
        val modeInput = InputDefinition(
            id = "mode",
            name = "Mode",
            staticType = ParameterType.ENUM,
            defaultValue = "modern",
            options = listOf("modern", "classic"),
            legacyValueMap = mapOf("legacy_mode" to "modern")
        )
        val dependentInput = InputDefinition(
            id = "details",
            name = "Details",
            staticType = ParameterType.STRING,
            defaultValue = "",
            visibility = InputVisibility.whenEquals("mode", "modern")
        )
        val module = FakeActionModule(inputs = listOf(modeInput, dependentInput))

        val uiModel = ActionEditorUiModelBuilder.build(module, sessionState, allSteps = null)

        assertEquals("modern", uiModel.effectiveParameters["mode"])
        assertEquals(
            listOf("mode", "details"),
            uiModel.genericInputsSection.fields.map { it.inputDefinition.id }
        )
    }

    @Test
    fun `build recomputes dynamic inputs from corrected enum values`() {
        val sessionState = ActionEditorSessionState().apply {
            this["audioType"] = "legacy_local"
        }
        val audioTypeInput = InputDefinition(
            id = "audioType",
            name = "Audio Type",
            staticType = ParameterType.ENUM,
            defaultValue = "system",
            options = listOf("system", "local"),
            legacyValueMap = mapOf("legacy_local" to "local")
        )
        val systemSoundInput = InputDefinition(
            id = "systemSound",
            name = "System Sound",
            staticType = ParameterType.STRING,
            defaultValue = "",
            isHidden = true
        )
        val localFileInput = InputDefinition(
            id = "localFile",
            name = "Local File",
            staticType = ParameterType.STRING,
            defaultValue = "",
            isHidden = true
        )
        val module = object : FakeActionModule(
            inputs = listOf(audioTypeInput, systemSoundInput, localFileInput)
        ) {
            override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
                val audioType = step?.parameters?.get("audioType") as? String ?: "system"
                val isSystemAudio = audioType == "system"

                return getInputs().map { input ->
                    when (input.id) {
                        "systemSound" -> input.copy(isHidden = !isSystemAudio)
                        "localFile" -> input.copy(isHidden = isSystemAudio)
                        else -> input
                    }
                }
            }
        }

        val uiModel = ActionEditorUiModelBuilder.build(module, sessionState, allSteps = null)

        assertEquals("local", uiModel.effectiveParameters["audioType"])
        assertEquals(
            listOf("audioType", "localFile"),
            uiModel.genericInputsSection.fields.map { it.inputDefinition.id }
        )
    }

    @Test
    fun `build hides custom ui when provider is absent`() {
        val module = FakeActionModule(
            inputs = listOf(InputDefinition("title", "Title", ParameterType.STRING, ""))
        )

        val uiModel = ActionEditorUiModelBuilder.build(module, ActionEditorSessionState(), allSteps = null)

        assertFalse(uiModel.showCustomUi)
        assertTrue(uiModel.genericInputsSection.isVisible)
        assertFalse(uiModel.advancedInputsSection.isVisible)
        assertFalse(uiModel.editorActionsSection.isVisible)
    }

    private open class FakeActionModule(
        private val inputs: List<InputDefinition>,
        override val uiProvider: ModuleUIProvider? = null,
        private val editorActions: List<EditorAction> = emptyList()
    ) : ActionModule {
        override val id: String = "test.module"
        override val metadata: ActionMetadata =
            ActionMetadata("Test", "Test module", 0, "test")
        override val blockBehavior: BlockBehavior = BlockBehavior(BlockType.NONE)
        override val requiredPermissions: List<Permission> = emptyList()

        override fun getOutputs(step: ActionStep?) = emptyList<com.chaomixian.vflow.core.module.OutputDefinition>()

        override fun getDynamicOutputs(step: ActionStep?, allSteps: List<ActionStep>?) =
            emptyList<com.chaomixian.vflow.core.module.OutputDefinition>()

        override fun getInputs(): List<InputDefinition> = inputs

        override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> = inputs

        override fun getEditorActions(step: ActionStep?, allSteps: List<ActionStep>?): List<EditorAction> = editorActions

        override fun getRequiredPermissions(step: ActionStep?): List<Permission> = emptyList()

        override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult = ValidationResult(true)

        override fun createSteps(): List<ActionStep> = listOf(ActionStep(id, emptyMap()))

        override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean = false

        override fun onParameterUpdated(
            step: ActionStep,
            updatedParameterId: String,
            updatedValue: Any?
        ): Map<String, Any?> = step.parameters

        override suspend fun execute(
            context: ExecutionContext,
            onProgress: suspend (ProgressUpdate) -> Unit
        ): ExecutionResult = ExecutionResult.Success()
    }

    private class FakeModuleUiProvider(
        private val handledInputIds: Set<String>
    ) : ModuleUIProvider {
        override fun createPreview(
            context: android.content.Context,
            parent: android.view.ViewGroup,
            step: ActionStep,
            allSteps: List<ActionStep>,
            onStartActivityForResult: ((android.content.Intent, (resultCode: Int, data: android.content.Intent?) -> Unit) -> Unit)?
        ): android.view.View? = null

        override fun createEditor(
            context: android.content.Context,
            parent: android.view.ViewGroup,
            currentParameters: Map<String, Any?>,
            onParametersChanged: () -> Unit,
            onMagicVariableRequested: ((inputId: String) -> Unit)?,
            allSteps: List<ActionStep>?,
            onStartActivityForResult: ((android.content.Intent, (resultCode: Int, data: android.content.Intent?) -> Unit) -> Unit)?
        ) = object : com.chaomixian.vflow.core.module.CustomEditorViewHolder(android.view.View(context)) {}

        override fun readFromEditor(holder: com.chaomixian.vflow.core.module.CustomEditorViewHolder): Map<String, Any?> =
            emptyMap()

        override fun getHandledInputIds(): Set<String> = handledInputIds
    }
}
