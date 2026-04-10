package com.chaomixian.vflow.core.workflow.module.scripted

import com.chaomixian.vflow.core.types.VTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleManifestTest {

    @Test
    fun `json output maps to registry type ids`() {
        assertEquals(
            VTypeRegistry.IMAGE.id,
            JsonOutput("result", "结果", "image").toOutputDefinition().typeName
        )
        assertEquals(
            VTypeRegistry.STRING.id,
            JsonOutput("result", "结果", "unknown").toOutputDefinition().typeName
        )
    }
}
