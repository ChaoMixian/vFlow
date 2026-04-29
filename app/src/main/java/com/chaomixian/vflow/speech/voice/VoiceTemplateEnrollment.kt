package com.chaomixian.vflow.speech.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VoiceTemplateEnrollment {
    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 512

    @SuppressLint("MissingPermission")
    suspend fun recordOneTemplate(
        modelFile: File,
        audioSource: VoiceTriggerConfig.AudioSource,
        maxRecordMs: Long = 6000L,
        minSpeechMs: Long = 250L,
        endSilenceMs: Long = 350L,
    ): FloatArray? = withContext(Dispatchers.Default) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            audioSource.mediaRecorderSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize.coerceAtLeast(FRAME_SIZE) * 2
        )

        val vad = OnnxSileroVad(
            modelFile = modelFile,
            sampleRate = SAMPLE_RATE,
            frameSize = FRAME_SIZE,
            mode = OnnxSileroVad.Mode.NORMAL,
            speechDurationMs = 60,
            silenceDurationMs = 300,
        )

        try {
            val buffer = ShortArray(FRAME_SIZE)
            val speechSamples = ArrayList<Short>()

            var seenSpeech = false
            var silenceMsAfterSpeech = 0L
            var speechMs = 0L

            val startedAt = System.currentTimeMillis()
            audioRecord.startRecording()

            while (true) {
                val now = System.currentTimeMillis()
                if (now - startedAt > maxRecordMs) break

                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val isSpeech = if (read == FRAME_SIZE) vad.isSpeech(buffer) else false
                if (isSpeech) {
                    seenSpeech = true
                    silenceMsAfterSpeech = 0L
                    speechMs += (read * 1000L) / SAMPLE_RATE
                    for (index in 0 until read) {
                        speechSamples.add(buffer[index])
                    }
                } else if (seenSpeech) {
                    silenceMsAfterSpeech += (read * 1000L) / SAMPLE_RATE
                    if (silenceMsAfterSpeech >= endSilenceMs) break
                }
            }

            if (!seenSpeech || speechMs < minSpeechMs) {
                return@withContext null
            }

            val pcm = ShortArray(speechSamples.size)
            for (index in pcm.indices) {
                pcm[index] = speechSamples[index]
            }
            VoiceTemplateFeatureExtractor.extractFeatures(pcm, pcm.size)
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { vad.close() }
        }
    }
}
