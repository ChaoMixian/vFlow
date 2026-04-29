package com.chaomixian.vflow.speech.voice

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object VoiceTemplateFeatureExtractor {
    private const val EPS = 1e-10f

    data class Config(
        val sampleRate: Int = 16000,
        val frameSize: Int = 400,
        val hopSize: Int = 160,
        val fftSize: Int = 512,
        val melBins: Int = 40,
        val numMfcc: Int = 13,
        val maxFrames: Int = 64,
        val fMin: Float = 20f,
        val fMax: Float = 4000f,
    )

    private data class MelFilterBank(
        val weights: Array<FloatArray>,
    )

    fun extractFeatures(
        pcm: ShortArray,
        size: Int,
        config: Config = Config(),
    ): FloatArray {
        val normalized = normalizePcm(pcm, size)
        val waveform = trimToMax(normalized, maxSamples = config.sampleRate * 2)

        val frames = frameSignal(waveform, config.frameSize, config.hopSize)
        if (frames.isEmpty()) return FloatArray(0)

        val mel = buildMelFilterBank(config)

        val logMels = ArrayList<FloatArray>(frames.size)
        for (frame in frames) {
            val pre = preEmphasis(frame, 0.97f)
            val windowed = applyHann(pre)
            val mag2 = fftMagSquared(windowed, config.fftSize)

            val feats = FloatArray(config.melBins)
            for (index in 0 until config.melBins) {
                var sum = 0f
                val weights = mel.weights[index]
                val limit = min(weights.size, mag2.size)
                for (bin in 0 until limit) {
                    sum += weights[bin] * mag2[bin]
                }
                feats[index] = ln(max(EPS, sum))
            }
            logMels.add(feats)
        }

        val limited = limitFrames(logMels, config.maxFrames)
        val mfcc = computeMfcc(limited, config)
        val delta = computeDelta(mfcc)
        val delta2 = computeDelta(delta)

        val featureDim = config.numMfcc * 3
        val featureSequence = Array(mfcc.size) { FloatArray(featureDim) }
        for (time in mfcc.indices) {
            val base2 = config.numMfcc
            val base3 = config.numMfcc * 2
            for (coeff in 0 until config.numMfcc) {
                featureSequence[time][coeff] = mfcc[time][coeff]
                featureSequence[time][base2 + coeff] = delta[time][coeff]
                featureSequence[time][base3 + coeff] = delta2[time][coeff]
            }
        }

        cmvnInPlace(featureSequence)

        val out = FloatArray(featureSequence.size * featureDim)
        var outIndex = 0
        for (time in featureSequence.indices) {
            for (coeff in 0 until featureDim) {
                out[outIndex++] = featureSequence[time][coeff]
            }
        }
        return out
    }

    private fun limitFrames(frames: List<FloatArray>, maxFrames: Int): List<FloatArray> {
        if (frames.size <= maxFrames) return frames
        val out = ArrayList<FloatArray>(maxFrames)
        val frameCount = frames.size
        val dimension = frames[0].size
        for (targetIndex in 0 until maxFrames) {
            val start = (targetIndex * frameCount) / maxFrames
            val end = ((targetIndex + 1) * frameCount) / maxFrames
            val count = max(1, end - start)
            val pooled = FloatArray(dimension)
            for (srcIndex in start until end) {
                val src = frames[srcIndex]
                for (dimIndex in 0 until dimension) {
                    pooled[dimIndex] += src[dimIndex]
                }
            }
            val inverse = 1f / count.toFloat()
            for (dimIndex in 0 until dimension) {
                pooled[dimIndex] *= inverse
            }
            out.add(pooled)
        }
        return out
    }

    private fun computeMfcc(logMels: List<FloatArray>, config: Config): Array<FloatArray> {
        val frameCount = logMels.size
        val out = Array(frameCount) { FloatArray(config.numMfcc) }
        val cosTable = dctCosTable(config.numMfcc, config.melBins)
        for (frameIndex in 0 until frameCount) {
            val frame = logMels[frameIndex]
            for (coeff in 0 until config.numMfcc) {
                var sum = 0f
                val row = cosTable[coeff]
                for (melIndex in 0 until config.melBins) {
                    sum += frame[melIndex] * row[melIndex]
                }
                out[frameIndex][coeff] = sum
            }
        }
        return out
    }

    private fun dctCosTable(numMfcc: Int, melBins: Int): Array<FloatArray> {
        val table = Array(numMfcc) { FloatArray(melBins) }
        val melBinsFloat = melBins.toFloat()
        for (coeff in 0 until numMfcc) {
            val coeffFloat = coeff.toFloat()
            for (melIndex in 0 until melBins) {
                val melFloat = melIndex.toFloat()
                val angle = (Math.PI * coeffFloat * (melFloat + 0.5f) / melBinsFloat).toDouble()
                table[coeff][melIndex] = cos(angle).toFloat()
            }
        }
        return table
    }

    private fun computeDelta(input: Array<FloatArray>): Array<FloatArray> {
        val frameCount = input.size
        if (frameCount == 0) return emptyArray()
        val dimension = input[0].size
        val out = Array(frameCount) { FloatArray(dimension) }
        for (index in 0 until frameCount) {
            val prev = input[if (index > 0) index - 1 else 0]
            val next = input[if (index + 1 < frameCount) index + 1 else frameCount - 1]
            for (dimIndex in 0 until dimension) {
                out[index][dimIndex] = (next[dimIndex] - prev[dimIndex]) * 0.5f
            }
        }
        return out
    }

    private fun cmvnInPlace(input: Array<FloatArray>) {
        if (input.isEmpty()) return
        val frameCount = input.size
        val dimension = input[0].size
        for (dimIndex in 0 until dimension) {
            var mean = 0f
            for (frameIndex in 0 until frameCount) {
                mean += input[frameIndex][dimIndex]
            }
            mean /= frameCount

            var varianceSum = 0f
            for (frameIndex in 0 until frameCount) {
                val diff = input[frameIndex][dimIndex] - mean
                varianceSum += diff * diff
            }
            val std = sqrt(max(EPS, varianceSum / frameCount))
            for (frameIndex in 0 until frameCount) {
                input[frameIndex][dimIndex] = (input[frameIndex][dimIndex] - mean) / std
            }
        }
    }

    private fun normalizePcm(pcm: ShortArray, size: Int): FloatArray {
        val sampleCount = size.coerceIn(0, pcm.size)
        if (sampleCount == 0) return FloatArray(0)

        var mean = 0f
        for (index in 0 until sampleCount) {
            mean += pcm[index] / 32768.0f
        }
        mean /= sampleCount

        val out = FloatArray(sampleCount)
        for (index in 0 until sampleCount) {
            out[index] = (pcm[index] / 32768.0f) - mean
        }
        return out
    }

    private fun trimToMax(waveform: FloatArray, maxSamples: Int): FloatArray {
        if (waveform.size <= maxSamples) return waveform
        val start = (waveform.size - maxSamples) / 2
        return waveform.copyOfRange(start, start + maxSamples)
    }

    private fun frameSignal(signal: FloatArray, frameSize: Int, hopSize: Int): List<FloatArray> {
        if (signal.size < frameSize || frameSize <= 0 || hopSize <= 0) return emptyList()
        val frames = ArrayList<FloatArray>()
        var position = 0
        while (position + frameSize <= signal.size) {
            val frame = FloatArray(frameSize)
            for (index in 0 until frameSize) {
                frame[index] = signal[position + index]
            }
            frames.add(frame)
            position += hopSize
        }
        return frames
    }

    private fun preEmphasis(frame: FloatArray, coeff: Float): FloatArray {
        val out = FloatArray(frame.size)
        var previous = 0f
        for (index in frame.indices) {
            val x = frame[index]
            out[index] = x - coeff * previous
            previous = x
        }
        return out
    }

    private fun applyHann(frame: FloatArray): FloatArray {
        val out = FloatArray(frame.size)
        val frameSize = frame.size
        val denominator = max(1, frameSize - 1)
        for (index in 0 until frameSize) {
            val window = 0.5f * (1f - cos((2.0 * Math.PI * index / denominator).toFloat()))
            out[index] = frame[index] * window
        }
        return out
    }

    private fun fftMagSquared(frame: FloatArray, fftSize: Int): FloatArray {
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val copyCount = min(frame.size, fftSize)
        for (index in 0 until copyCount) {
            real[index] = frame[index]
        }

        fft(real, imag)

        val outputSize = fftSize / 2 + 1
        val out = FloatArray(outputSize)
        for (index in 0 until outputSize) {
            val re = real[index]
            val im = imag[index]
            out[index] = re * re + im * im
        }
        return out
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (index in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (index < j) {
                val tmpReal = real[index]
                real[index] = real[j]
                real[j] = tmpReal
                val tmpImag = imag[index]
                imag[index] = imag[j]
                imag[j] = tmpImag
            }
        }

        var len = 2
        while (len <= n) {
            val angle = (-2.0 * Math.PI / len).toFloat()
            val wLenCos = cos(angle)
            val wLenSin = sin(angle)
            var offset = 0
            while (offset < n) {
                var wReal = 1f
                var wImag = 0f
                for (index in 0 until len / 2) {
                    val u = offset + index
                    val v = offset + index + len / 2
                    val vReal = real[v] * wReal - imag[v] * wImag
                    val vImag = real[v] * wImag + imag[v] * wReal
                    real[v] = real[u] - vReal
                    imag[v] = imag[u] - vImag
                    real[u] += vReal
                    imag[u] += vImag

                    val nextWReal = wReal * wLenCos - wImag * wLenSin
                    val nextWImag = wReal * wLenSin + wImag * wLenCos
                    wReal = nextWReal
                    wImag = nextWImag
                }
                offset += len
            }
            len = len shl 1
        }
    }

    private fun buildMelFilterBank(config: Config): MelFilterBank {
        val fftBins = config.fftSize / 2 + 1
        val melMin = hzToMel(config.fMin)
        val melMax = hzToMel(config.fMax)
        val melPoints = FloatArray(config.melBins + 2)
        for (index in melPoints.indices) {
            melPoints[index] = melMin + (melMax - melMin) * index / (config.melBins + 1).toFloat()
        }
        val hzPoints = FloatArray(melPoints.size) { index -> melToHz(melPoints[index]) }
        val binPoints = IntArray(hzPoints.size) { index ->
            ((config.fftSize + 1) * hzPoints[index] / config.sampleRate).toInt().coerceIn(0, fftBins - 1)
        }

        val weights = Array(config.melBins) { FloatArray(fftBins) }
        for (melIndex in 0 until config.melBins) {
            val left = binPoints[melIndex]
            val center = binPoints[melIndex + 1]
            val right = binPoints[melIndex + 2]
            for (bin in left until center) {
                val denominator = max(1, center - left)
                weights[melIndex][bin] = (bin - left).toFloat() / denominator.toFloat()
            }
            for (bin in center until right) {
                val denominator = max(1, right - center)
                weights[melIndex][bin] = (right - bin).toFloat() / denominator.toFloat()
            }
        }
        return MelFilterBank(weights)
    }

    private fun hzToMel(hz: Float): Float = (2595f * kotlin.math.log10(1f + hz / 700f))

    private fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun Float.pow(power: Float): Float = toDouble().pow(power.toDouble()).toFloat()
}
