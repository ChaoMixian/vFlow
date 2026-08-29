package com.chaomixian.vflow.core.workflow.module.system

import com.chaomixian.vflow.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakePhotoModuleTest {

    private val module = TakePhotoModule()

    @Test
    fun `module id is documented device take photo id`() {
        assertEquals("vflow.device.take_photo", module.id)
    }

    @Test
    fun `module category is device`() {
        assertEquals("应用与系统", module.metadata.category)
        assertEquals("device", module.metadata.categoryId)
    }

    @Test
    fun `module requires camera permission`() {
        val permissions = module.getRequiredPermissions(null)
        assertEquals(listOf(PermissionManager.CAMERA), permissions)
    }

    @Test
    fun `module exposes expected inputs in order`() {
        val inputIds = module.getInputs().map { it.id }
        assertEquals(listOf("camera", "flash", "quality", "save_path"), inputIds)
    }

    @Test
    fun `module exposes expected outputs in order`() {
        val outputIds = module.getOutputs(null).map { it.id }
        assertEquals(listOf("success", "image", "file", "path"), outputIds)
    }

    @Test
    fun `camera options include back and front`() {
        val cameraInput = module.getInputs().first { it.id == "camera" }
        assertEquals(listOf("back", "front"), cameraInput.options)
        assertEquals("back", cameraInput.defaultValue)
    }

    @Test
    fun `flash options include off auto on torch`() {
        val flashInput = module.getInputs().first { it.id == "flash" }
        assertEquals(listOf("off", "auto", "on", "torch"), flashInput.options)
        assertEquals("off", flashInput.defaultValue)
    }

    @Test
    fun `fallback metadata name is localized label`() {
        assertEquals("拍照", module.metadata.name)
        assertTrue(module.metadata.description.isNotEmpty())
    }
}
