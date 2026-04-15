package com.chaomixian.vflow.core.workflow.module.system

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechToTextModuleTest {

    @Test
    fun `engine input normalizes legacy local label`() {
        val normalized = SpeechToTextModule.ENGINE_INPUT_DEFINITION.normalizeEnumValueOrNull("本地 Sherpa-ncnn")

        assertEquals(SpeechToTextModule.ENGINE_SHERPA_NCNN, normalized)
    }

    @Test
    fun `engine input falls back to canonical system value`() {
        val normalized = SpeechToTextModule.ENGINE_INPUT_DEFINITION.normalizeEnumValueOrNull("SYSTEM")

        assertEquals(SpeechToTextModule.ENGINE_SYSTEM, normalized)
    }

    @Test
    fun `sherpa language uses dedicated parameter only`() {
        val resolved = SpeechToTextModule.resolveRequestedLanguage(
            engine = SpeechToTextModule.ENGINE_SHERPA_NCNN,
            rawLanguage = "ja-JP",
            rawSherpaLanguage = "en-US",
        )

        assertEquals("en-US", resolved)
    }

    @Test
    fun `sherpa language falls back to auto when unset`() {
        val resolved = SpeechToTextModule.resolveRequestedLanguage(
            engine = SpeechToTextModule.ENGINE_SHERPA_NCNN,
            rawLanguage = "zh-CN",
            rawSherpaLanguage = null,
        )

        assertEquals("auto", resolved)
    }
}
