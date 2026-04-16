package com.chaomixian.vflow.core.workflow.module.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `auto start and auto send inputs stay folded`() {
        assertTrue(SpeechToTextModule.AUTO_START_INPUT_DEFINITION.isFolded)
        assertTrue(SpeechToTextModule.AUTO_SEND_INPUT_DEFINITION.isFolded)
    }

    @Test
    fun `overlay request defaults auto options to false`() {
        val request = SpeechToTextOverlayRequest(
            title = "语音转文字",
            languageTag = "auto",
            forceOffline = false,
        )

        assertFalse(request.autoStart)
        assertFalse(request.autoSend)
    }

    @Test
    fun `overlay session auto send waits for final result`() {
        val session = SpeechToTextOverlaySession(
            SpeechToTextOverlayRequest(
                title = "语音转文字",
                languageTag = "auto",
                forceOffline = false,
                autoSend = true,
            )
        )

        session.onOverlayShown()
        session.startRecognition(currentEditorText = "", deviceLanguageTag = "zh-CN")
        session.onPartialResult("你好")
        assertFalse(session.shouldAutoSend())

        session.onEndOfSpeech()
        assertFalse(session.shouldAutoSend())

        session.onResults("你好")
        assertTrue(session.shouldAutoSend())
    }
}
