package com.chaomixian.vflow.core.module

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.execution.VariableType
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.core.workflow.module.logic.IF_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.IfModule
import com.chaomixian.vflow.core.workflow.module.logic.WHILE_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.WhileModule
import com.chaomixian.vflow.core.workflow.module.network.AIModule
import com.chaomixian.vflow.core.workflow.module.system.DarkModeModule
import com.chaomixian.vflow.core.workflow.module.system.FlashlightModule
import com.chaomixian.vflow.core.workflow.module.system.InputModule
import com.chaomixian.vflow.core.workflow.module.system.VibrationModule
import com.chaomixian.vflow.core.workflow.module.triggers.BatteryTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.PowerTriggerModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class LegacyEnumNormalizationTest {

    @Test
    fun `input definition normalizes legacy enum value`() {
        val input = InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = "new",
            options = listOf("new"),
            legacyValueMap = mapOf("旧值" to "new")
        )

        assertEquals("new", input.normalizeEnumValue("旧值"))
        assertEquals("new", input.normalizeEnumValue(null))
        assertEquals("custom", input.normalizeEnumValue("custom"))
    }

    @Test
    fun `input list supports explicit fallback override`() {
        val mode = DarkModeModule().getInputs().normalizeEnumValue("mode", null, DarkModeModule.MODE_AUTO)

        assertEquals(DarkModeModule.MODE_AUTO, mode)
    }

    @Test
    fun `flashlight dynamic inputs normalize legacy mode`() {
        val inputs = FlashlightModule().getDynamicInputs(
            ActionStep(
                moduleId = "vflow.device.flashlight",
                parameters = mapOf("mode" to "开启")
            ),
            null
        )

        val strengthInput = inputs.first { it.id == "strengthPercent" }
        assertFalse(strengthInput.isHidden)
    }

    @Test
    fun `vibration dynamic inputs normalize legacy pattern mode`() {
        val inputs = VibrationModule().getDynamicInputs(
            ActionStep(
                moduleId = "vflow.device.vibration",
                parameters = mapOf("mode" to "自定义模式")
            ),
            null
        )

        assertTrue(inputs.first { it.id == "duration" }.isHidden)
        assertFalse(inputs.first { it.id == "patternVibrate" }.isHidden)
        assertFalse(inputs.first { it.id == "patternPause" }.isHidden)
        assertFalse(inputs.first { it.id == "patternRepeat" }.isHidden)
    }

    @Test
    fun `battery trigger input normalizes legacy condition`() {
        val condition = BatteryTriggerModule()
            .getInputs()
            .normalizeEnumValue("above_or_below", "高于")

        assertEquals(BatteryTriggerModule.VALUE_ABOVE, condition)
    }

    @Test
    fun `power trigger input normalizes legacy state`() {
        val state = PowerTriggerModule()
            .getInputs()
            .normalizeEnumValue("power_state", "已断开")

        assertEquals(PowerTriggerModule.VALUE_DISCONNECTED, state)
    }

    @Test
    fun `create variable type definition normalizes historical image alias`() {
        assertEquals(
            CreateVariableModule.TYPE_IMAGE,
            CreateVariableModule.TYPE_INPUT_DEFINITION.normalizeEnumValueOrNull("图片")
        )
        assertEquals(VariableType.IMAGE, VariableType.fromStoredValue("图片"))
    }

    @Test
    fun `variable type recognizes complex vtype ids`() {
        assertEquals(VariableType.NOTIFICATION, VariableType.fromStoredValue(VTypeRegistry.NOTIFICATION.id))
        assertEquals(VariableType.UI_COMPONENT, VariableType.fromStoredValue(VTypeRegistry.UI_COMPONENT.id))
        assertEquals(VariableType.EVENT, VariableType.fromStoredValue(VTypeRegistry.EVENT.id))
        assertEquals(VariableType.COORDINATE_REGION, VariableType.fromStoredValue(VTypeRegistry.COORDINATE_REGION.id))
    }

    @Test
    fun `input module input type definition normalizes legacy time value`() {
        assertEquals(InputModule.TYPE_TIME, InputModule.INPUT_TYPE_DEFINITION.normalizeEnumValueOrNull("Time"))
    }

    @Test
    fun `ai provider input definition normalizes legacy provider`() {
        assertEquals(AIModule.PROVIDER_OPENAI, AIModule.PROVIDER_INPUT_DEFINITION.normalizeEnumValueOrNull("OpenAI"))
    }

    @Test
    fun `if module execute normalizes legacy operator`() = runBlocking {
        val step = ActionStep(moduleId = IF_START_ID, parameters = emptyMap())
        val context = ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = ExecutionContext.mutableMapToVObjectMap(
                mutableMapOf(
                    "input1" to 42,
                    "operator" to "等于",
                    "value1" to "42"
                )
            ),
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = listOf(step),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            namedVariables = mutableMapOf(),
            workDir = File("build/test-workdir")
        )

        val result = IfModule().execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        assertEquals(true, context.stepOutputs[step.id]?.get("result")?.asBoolean())
    }

    @Test
    fun `while module dynamic inputs normalize legacy between operator`() {
        val inputs = WhileModule().getDynamicInputs(
            ActionStep(
                moduleId = WHILE_START_ID,
                parameters = mapOf("operator" to "介于")
            ),
            emptyList()
        )

        assertTrue(inputs.any { it.id == "value2" })
    }
}
