package com.chaomixian.vflow.core.workflow.module.system

import com.chaomixian.vflow.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordAudioModuleTest {

    private val module = RecordAudioModule()

    @Test
    fun `module id is documented device record audio id`() {
        assertEquals("vflow.device.record_audio", module.id)
    }

    @Test
    fun `module category is device`() {
        assertEquals("应用与系统", module.metadata.category)
        assertEquals("device", module.metadata.categoryId)
    }

    @Test
    fun `module requires microphone permission`() {
        val permissions = module.getRequiredPermissions(null)
        assertEquals(listOf(PermissionManager.MICROPHONE), permissions)
    }

    @Test
    fun `module exposes expected inputs in order`() {
        val inputIds = module.getInputs().map { it.id }
        assertEquals(listOf("duration", "save_path", "audio_source", "audio_format"), inputIds)
    }

    @Test
    fun `module exposes expected outputs in order`() {
        val outputIds = module.getOutputs(null).map { it.id }
        assertEquals(listOf("success", "file", "path", "duration"), outputIds)
    }

    @Test
    fun `audio source options include mic camcorder voice_recognition`() {
        val sourceInput = module.getInputs().first { it.id == "audio_source" }
        assertEquals(listOf("mic", "camcorder", "voice_recognition"), sourceInput.options)
        assertEquals("mic", sourceInput.defaultValue)
    }

    @Test
    fun `audio format options include m4a and 3gp`() {
        val formatInput = module.getInputs().first { it.id == "audio_format" }
        assertEquals(listOf("m4a", "3gp"), formatInput.options)
        assertEquals("m4a", formatInput.defaultValue)
    }

    @Test
    fun `fallback metadata name is localized label`() {
        assertEquals("录音", module.metadata.name)
        assertTrue(module.metadata.description.isNotEmpty())
    }
}
