package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.module.triggers.VoiceTriggerData
import com.chaomixian.vflow.speech.voice.OnnxSileroVad
import com.chaomixian.vflow.speech.voice.VoiceTemplateFeatureExtractor
import com.chaomixian.vflow.speech.voice.VoiceTemplateMatcher
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.speech.voice.VoiceTriggerModelManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class VoiceTriggerHandler : BaseTriggerHandler() {
    companion object {
        private const val TAG = "VoiceTriggerHandler"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 512
        private const val MAX_SEGMENT_MS = 1600L
        private const val MIN_SEGMENT_MS = 250L
        private const val END_SILENCE_MS = 350L
        private const val MIN_RMS = 0.003f
        private const val RMS_NOISE_MARGIN = 0.001f
        private const val NOISE_RMS_EMA_ALPHA = 0.05f
    }

    private data class ResolvedVoiceTrigger(
        val trigger: TriggerSpec,
        val templates: List<FloatArray>,
        val matcherConfig: VoiceTemplateMatcher.Config,
        var lastTriggeredAtMs: Long = 0L,
    )

    private val activeTriggers = CopyOnWriteArrayList<ResolvedVoiceTrigger>()
    private var appContext: Context? = null
    private var listeningJob: Job? = null

    override fun start(context: Context) {
        super.start(context)
        appContext = context.applicationContext
        reloadListening()
    }

    override fun stop(context: Context) {
        stopListening()
        activeTriggers.clear()
        appContext = null
        super.stop(context)
    }

    override fun addTrigger(context: Context, trigger: TriggerSpec) {
        activeTriggers.removeAll { it.trigger.triggerId == trigger.triggerId }
        resolveTrigger(trigger)?.let(activeTriggers::add)
        reloadListening()
    }

    override fun removeTrigger(context: Context, triggerId: String) {
        activeTriggers.removeAll { it.trigger.triggerId == triggerId }
        reloadListening()
    }

    private fun reloadListening() {
        if (activeTriggers.isEmpty()) {
            stopListening()
            return
        }
        startListening()
    }

    private fun startListening() {
        if (listeningJob?.isActive == true) return
        val context = appContext ?: return
        val modelManager = VoiceTriggerModelManager(context)
        if (!modelManager.isModelInstalled()) {
            DebugLogger.w(TAG, "Voice trigger model is not installed, skip listening")
            return
        }

        listeningJob = triggerScope.launch {
            while (isActive) {
                try {
                    runListeningLoop(context, modelManager)
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Voice trigger loop failed, retrying soon", e)
                    delay(1000)
                }
            }
        }
    }

    private suspend fun runListeningLoop(
        context: Context,
        modelManager: VoiceTriggerModelManager,
    ) {
        val audioSource = VoiceTriggerConfig.readAudioSource(context)
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "AudioRecord buffer initialization failed: $minBufferSize" }

        val audioRecord = AudioRecord(
            audioSource.mediaRecorderSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize.coerceAtLeast(FRAME_SIZE) * 2,
        )
        val vad = OnnxSileroVad(
            modelFile = modelManager.modelFile(),
            sampleRate = SAMPLE_RATE,
            frameSize = FRAME_SIZE,
            mode = OnnxSileroVad.Mode.NORMAL,
            speechDurationMs = 30,
            silenceDurationMs = 300,
        )

        try {
            audioRecord.startRecording()
            val buffer = ShortArray(FRAME_SIZE)
            val segment = ArrayList<Short>()

            var seenSpeech = false
            var speechMs = 0L
            var silenceMs = 0L
            var noiseRmsEma = 0f

            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val isSpeechFrame = read == FRAME_SIZE && vad.isSpeech(buffer)
                val chunkMs = (read * 1000L) / SAMPLE_RATE

                if (!isSpeechFrame && !seenSpeech) {
                    val frameRms = computeRms(buffer, read)
                    noiseRmsEma = if (noiseRmsEma <= 0f) {
                        frameRms
                    } else {
                        val alpha = NOISE_RMS_EMA_ALPHA.coerceIn(0.001f, 0.5f)
                        (1f - alpha) * noiseRmsEma + alpha * frameRms
                    }
                }

                if (isSpeechFrame) {
                    seenSpeech = true
                    silenceMs = 0L
                    speechMs += chunkMs
                    for (index in 0 until read) {
                        segment.add(buffer[index])
                    }
                    if (speechMs >= MAX_SEGMENT_MS) {
                        flushSegmentIfNeeded(context, segment, speechMs, noiseRmsEma)
                        segment.clear()
                        seenSpeech = false
                        speechMs = 0L
                        silenceMs = 0L
                    }
                } else if (seenSpeech) {
                    silenceMs += chunkMs
                    if (silenceMs >= END_SILENCE_MS) {
                        flushSegmentIfNeeded(context, segment, speechMs, noiseRmsEma)
                        segment.clear()
                        seenSpeech = false
                        speechMs = 0L
                        silenceMs = 0L
                    }
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { vad.close() }
        }
    }

    private fun flushSegmentIfNeeded(
        context: Context,
        segment: ArrayList<Short>,
        speechMs: Long,
        noiseRms: Float,
    ) {
        if (segment.isEmpty() || speechMs < MIN_SEGMENT_MS || activeTriggers.isEmpty()) {
            return
        }
        val pcm = ShortArray(segment.size)
        for (index in pcm.indices) {
            pcm[index] = segment[index]
        }

        val rms = computeRms(pcm, pcm.size)
        val rmsGate = max(MIN_RMS, noiseRms + RMS_NOISE_MARGIN)
        if (rms < rmsGate) {
            return
        }

        val featureVector = VoiceTemplateFeatureExtractor.extractFeatures(pcm, pcm.size)
        if (featureVector.isEmpty()) return

        val cooldownMs = VoiceTriggerConfig.readRetriggerCooldownMs(context)
        val now = System.currentTimeMillis()

        activeTriggers.forEach { resolved ->
            val result = VoiceTemplateMatcher.match(featureVector, resolved.templates, resolved.matcherConfig)
            if (!VoiceTemplateMatcher.isTriggered(result, resolved.matcherConfig)) {
                return@forEach
            }
            if (now - resolved.lastTriggeredAtMs < cooldownMs) {
                return@forEach
            }
            resolved.lastTriggeredAtMs = now
            executeTrigger(
                context = context,
                trigger = resolved.trigger,
                triggerData = VoiceTriggerData(
                    similarity = result?.bestSimilarity ?: 0f,
                    segmentDurationMs = speechMs,
                    hitCount = result?.hitCount ?: 0,
                )
            )
        }
    }

    private fun resolveTrigger(trigger: TriggerSpec): ResolvedVoiceTrigger? {
        val context = appContext ?: return null
        val templates = VoiceTriggerConfig.readTemplates(context)?.asList() ?: return null

        val thresholdPercent = VoiceTriggerConfig.readSimilarityThresholdPercent(context)

        return ResolvedVoiceTrigger(
            trigger = trigger,
            templates = templates,
            matcherConfig = VoiceTemplateMatcher.Config(
                similarityThreshold = thresholdPercent / 100f
            )
        )
    }

    private fun computeRms(pcm: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        val sampleCount = min(length, pcm.size)
        for (index in 0 until sampleCount) {
            val value = pcm[index].toDouble() / 32768.0
            sum += value * value
        }
        return sqrt(sum / sampleCount.toDouble()).toFloat()
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
    }
}
