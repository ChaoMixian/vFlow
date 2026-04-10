package com.chaomixian.vflow.core.workflow.module

import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.data.FileOperationModule
import com.chaomixian.vflow.core.workflow.module.data.TextProcessingModule
import com.chaomixian.vflow.core.workflow.module.interaction.FindTextModule
import com.chaomixian.vflow.core.workflow.module.interaction.OCRModule
import com.chaomixian.vflow.core.workflow.module.system.GetAppUsageStatsModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleOutputEnumNormalizationTest {

    @Test
    fun `text processing dynamic outputs normalize legacy split value`() {
        val outputs = TextProcessingModule().getDynamicOutputs(
            step("vflow.data.text_processing", "operation", "分割"),
            null
        )

        assertEquals(1, outputs.size)
        assertEquals("result_list", outputs.single().id)
        assertEquals(VTypeRegistry.LIST.id, outputs.single().typeName)
    }

    @Test
    fun `file operation outputs normalize legacy create value`() {
        val outputs = FileOperationModule().getOutputs(
            step("vflow.data.file_operation", "operation", "创建")
        )

        assertTrue(outputs.any { it.id == "file_path" })
        assertTrue(outputs.any { it.id == "file_name" })
    }

    @Test
    fun `find text outputs normalize legacy coordinate value`() {
        val outputs = FindTextModule().getOutputs(
            step("vflow.device.find.text", "outputFormat", "坐标")
        )

        val firstResult = outputs.first { it.id == "first_result" }
        val allResults = outputs.first { it.id == "all_results" }
        assertEquals(VTypeRegistry.COORDINATE.id, firstResult.typeName)
        assertEquals(VTypeRegistry.COORDINATE.id, allResults.listElementType)
    }

    @Test
    fun `ocr outputs normalize legacy find mode`() {
        val outputs = OCRModule().getOutputs(
            step("vflow.interaction.ocr", "mode", "查找文本")
        )

        assertTrue(outputs.any { it.id == "found" })
        assertTrue(outputs.any { it.id == "all_matches" })
    }

    @Test
    fun `usage stats list output declares dictionary element type`() {
        val outputs = GetAppUsageStatsModule().getOutputs(null)

        val statsList = outputs.first { it.id == "stats_list" }
        assertEquals(VTypeRegistry.LIST.id, statsList.typeName)
        assertEquals(VTypeRegistry.DICTIONARY.id, statsList.listElementType)
    }

    private fun step(moduleId: String, key: String, value: String): ActionStep {
        return ActionStep(
            moduleId = moduleId,
            parameters = mapOf(key to value)
        )
    }
}
