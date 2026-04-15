package com.chaomixian.vflow.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.chaomixian.vflow.core.logging.DebugLogger
import com.k2fsa.sherpa.ncnn.ModelConfig
import com.k2fsa.sherpa.ncnn.RecognizerConfig
import com.k2fsa.sherpa.ncnn.SherpaNcnn
import com.k2fsa.sherpa.ncnn.getDecoderConfig
import com.k2fsa.sherpa.ncnn.getFeatureExtractorConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SherpaNcnnPreparationResult(
    val modelSpec: SherpaNcnnModelSpec,
)

class SherpaNcnnStreamingRecognizer(context: Context) {
    companion object {
        private const val TAG = "SherpaStreamingRecognizer"
        private const val SAMPLE_RATE = 16000
    }

    private val appContext = context.applicationContext
    private val modelManager = SherpaNcnnModelManager(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()

    private var recognizer: SherpaNcnn? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var latestText: String = ""
    private var lastEmittedText: String = ""

    val isPrepared: Boolean
        get() = recognizer != null

    fun isModelInstalled(languageTag: String): Boolean {
        return modelManager.isModelInstalled(languageTag)
    }

    suspend fun prepare(
        languageTag: String,
    ): SherpaNcnnPreparationResult = stateMutex.withLock {
        if (recognizer != null) {
            val modelSpec = modelManager.resolveInstalledModelSpec(languageTag)
                ?: throw IllegalStateException("Sherpa model is not installed")
            return SherpaNcnnPreparationResult(
                modelSpec = modelSpec,
            )
        }

        val modelSpec = modelManager.resolveInstalledModelSpec(languageTag)
            ?: throw IllegalStateException("Sherpa model is not installed")
        val modelDir = modelManager.installedModelDir(languageTag)
            ?: throw IllegalStateException("Sherpa model is not installed")
        recognizer = createRecognizer(modelDir)
        SherpaNcnnPreparationResult(
            modelSpec = modelSpec,
        )
    }

    suspend fun startListening(onPartialResult: (String) -> Unit): Boolean = stateMutex.withLock {
        val activeRecognizer = recognizer ?: throw IllegalStateException("Sherpa recognizer is not prepared")
        if (recordingJob?.isActive == true) {
            return false
        }

        latestText = ""
        lastEmittedText = ""
        activeRecognizer.reset(false)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "AudioRecord buffer initialization failed: $minBufferSize" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord is not initialized")
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            throw IllegalStateException(e.message ?: "AudioRecord start failed")
        }

        audioRecord = record
        recordingJob = scope.launch {
            val buffer = ShortArray(minBufferSize)
            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        continue
                    }

                    val samples = FloatArray(read) { index -> buffer[index] / 32768.0f }
                    activeRecognizer.acceptSamples(samples)
                    while (activeRecognizer.isReady()) {
                        activeRecognizer.decode()
                    }

                    val text = activeRecognizer.text.trim()
                    if (text.isNotBlank() && text != lastEmittedText) {
                        latestText = text
                        lastEmittedText = text
                        onPartialResult(text)
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Sherpa recording loop failed", e)
            }
        }
        true
    }

    suspend fun stopListening(): String = stateMutex.withLock {
        val activeRecognizer = recognizer ?: return latestText.trim()

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to stop AudioRecord", e)
        }

        recordingJob?.cancelAndJoin()
        recordingJob = null

        try {
            activeRecognizer.inputFinished()
            while (activeRecognizer.isReady()) {
                activeRecognizer.decode()
            }
            val finalText = activeRecognizer.text.trim()
            if (finalText.isNotBlank()) {
                latestText = finalText
            }
            activeRecognizer.reset(false)
        } finally {
            releaseAudioRecord()
        }

        latestText.trim()
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            recordingJob?.cancelAndJoin()
            recordingJob = null
            releaseAudioRecord()
            recognizer?.release()
            recognizer = null
        }
    }

    private fun releaseAudioRecord() {
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            runCatching { record.release() }
        }
    }

    private fun createRecognizer(modelDir: File): SherpaNcnn {
        val recognizerConfig = RecognizerConfig(
            featConfig = getFeatureExtractorConfig(sampleRate = SAMPLE_RATE.toFloat(), featureDim = 80),
            modelConfig = ModelConfig(
                encoderParam = File(modelDir, "encoder_jit_trace-pnnx.ncnn.param").absolutePath,
                encoderBin = File(modelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                decoderParam = File(modelDir, "decoder_jit_trace-pnnx.ncnn.param").absolutePath,
                decoderBin = File(modelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                joinerParam = File(modelDir, "joiner_jit_trace-pnnx.ncnn.param").absolutePath,
                joinerBin = File(modelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 2,
                useGPU = false,
            ),
            decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4),
            enableEndpoint = true,
            rule1MinTrailingSilence = 2.4f,
            rule2MinTrailingSilence = 1.2f,
            rule3MinUtteranceLength = 20.0f,
            hotwordsFile = "",
            hotwordsScore = 1.5f,
        )

        return SherpaNcnn(
            config = recognizerConfig,
            assetManager = null,
        )
    }
}
