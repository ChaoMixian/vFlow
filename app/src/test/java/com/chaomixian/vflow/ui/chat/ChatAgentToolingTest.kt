package com.chaomixian.vflow.ui.chat

import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAgentToolingTest {

    @Test
    fun toolName_isStableAndSafe() {
        assertEquals(
            "vflow_system_capture_screen",
            chatToolNameFromModuleId("vflow.system.capture_screen")
        )
        assertEquals(
            "vflow_core_force_stop_app",
            chatToolNameFromModuleId("vflow.core.force_stop_app")
        )
        assertEquals(
            "vflow_device_flashlight",
            chatToolNameFromModuleId("vflow.device.flashlight")
        )
    }

    @Test
    fun artifactStore_createsAndResolvesComplexHandles() {
        val store = ChatAgentArtifactStore()
        val image = VImage("file:///tmp/screen.png")
        val coordinate = VCoordinate(12, 34)

        val references = store.createReferences(
            callId = "call_123",
            outputs = mapOf(
                "image" to image,
                "point" to coordinate,
                "text" to VString("hello"),
            )
        )

        assertEquals(2, references.size)
        assertTrue(references.any { it.key == "image" && it.handle == "artifact://call_123/image" })
        assertTrue(references.any { it.key == "point" && it.handle == "artifact://call_123/point" })
        assertEquals(image, store.resolve("artifact://call_123/image"))
        assertEquals(coordinate, store.resolve("artifact://call_123/point"))
        assertNull(store.resolve("artifact://call_123/text"))
    }

    @Test
    fun autoApprovalScope_allowsExpectedRiskLevels() {
        assertTrue(ChatToolAutoApprovalScope.READ_ONLY.allows(ChatAgentToolRiskLevel.READ_ONLY))
        assertTrue(ChatToolAutoApprovalScope.STANDARD.allows(ChatAgentToolRiskLevel.LOW))
        assertTrue(ChatToolAutoApprovalScope.STANDARD.allows(ChatAgentToolRiskLevel.STANDARD))
        assertTrue(ChatToolAutoApprovalScope.ALL.allows(ChatAgentToolRiskLevel.HIGH))
        assertTrue(!ChatToolAutoApprovalScope.OFF.allows(ChatAgentToolRiskLevel.READ_ONLY))
        assertTrue(!ChatToolAutoApprovalScope.STANDARD.allows(ChatAgentToolRiskLevel.HIGH))
    }
}
