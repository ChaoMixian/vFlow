package com.chaomixian.vflow.speech.voice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class VoiceTemplateMatchResult(
    val bestSimilarity: Float,
    val secondBestSimilarity: Float,
    val hitCount: Int,
    val dynamicThreshold: Float,
)

object VoiceTemplateMatcher {
    data class Config(
        val similarityThreshold: Float = 0.865f,
        val dynamicThresholdMargin: Float = 0.02f,
        val minDynamicThresholdFloor: Float = 0.84f,
        val maxBestSecondGap: Float = 0.04f,
        val minIntraSimilarity: Float = 0.80f,
        val dtwBand: Int = 4,
        val requiredTemplateMatches: Int = 1,
        val minDurationRatio: Float = 0.75f,
        val maxDurationRatio: Float = 1.25f,
    )

    fun match(
        featureVector: FloatArray,
        templates: List<FloatArray>,
        config: Config = Config(),
    ): VoiceTemplateMatchResult? {
        val extractorConfig = VoiceTemplateFeatureExtractor.Config()
        val featureDim = extractorConfig.numMfcc * 3
        if (featureDim <= 0 || featureVector.isEmpty() || featureVector.size % featureDim != 0) {
            return null
        }

        val validTemplates = templates.filter { it.isNotEmpty() && it.size % featureDim == 0 }
        if (validTemplates.isEmpty()) return null

        val featureSeq = reshapeAndNormalizePerFrame(featureVector, featureDim)
        val templateSeqs = validTemplates.map { reshapeAndNormalizePerFrame(it, featureDim) }

        val intraSimilarities = ArrayList<Float>(3)
        if (templateSeqs.size >= 2) {
            for (first in 0 until templateSeqs.size) {
                for (second in first + 1 until templateSeqs.size) {
                    intraSimilarities.add(dtwSimilarity(templateSeqs[first], templateSeqs[second], config.dtwBand))
                }
            }
        }
        val intraMin = intraSimilarities.minOrNull() ?: 1f

        val meanLength = templateSeqs.map { it.size }.average().toFloat().coerceAtLeast(1f)
        val durationRatio = featureSeq.size.toFloat() / meanLength
        if (durationRatio < config.minDurationRatio || durationRatio > config.maxDurationRatio) {
            return null
        }

        val dynamicThreshold = max(
            config.minDynamicThresholdFloor,
            min(
                config.similarityThreshold,
                (intraMin - config.dynamicThresholdMargin).coerceIn(0f, 1f)
            )
        )

        var best = -1f
        var secondBest = -1f
        var hits = 0
        for (templateSeq in templateSeqs) {
            val similarity = dtwSimilarity(featureSeq, templateSeq, config.dtwBand)
            if (similarity > best) {
                secondBest = best
                best = similarity
            } else if (similarity > secondBest) {
                secondBest = similarity
            }
            if (similarity >= dynamicThreshold) {
                hits++
            }
        }

        val requiredHits = min(config.requiredTemplateMatches, templateSeqs.size)
        val bestSecondGap = if (secondBest >= 0f) best - secondBest else 0f
        val gapOk = templateSeqs.size < 2 || hits >= 2 || bestSecondGap <= config.maxBestSecondGap
        if (intraSimilarities.isNotEmpty() && intraMin < config.minIntraSimilarity) {
            // Enrollment quality is low, but we still allow matching if the live score is strong enough.
        }
        if (hits < requiredHits || !gapOk) {
            return VoiceTemplateMatchResult(
                bestSimilarity = best.coerceAtLeast(0f),
                secondBestSimilarity = secondBest.coerceAtLeast(0f),
                hitCount = hits,
                dynamicThreshold = dynamicThreshold,
            )
        }
        return VoiceTemplateMatchResult(
            bestSimilarity = best.coerceAtLeast(0f),
            secondBestSimilarity = secondBest.coerceAtLeast(0f),
            hitCount = hits,
            dynamicThreshold = dynamicThreshold,
        )
    }

    fun isTriggered(
        result: VoiceTemplateMatchResult?,
        config: Config = Config(),
    ): Boolean {
        if (result == null) return false
        val requiredHits = config.requiredTemplateMatches.coerceAtLeast(1)
        val bestSecondGap = result.bestSimilarity - result.secondBestSimilarity
        val gapOk = result.hitCount >= 2 || result.secondBestSimilarity <= 0f || bestSecondGap <= config.maxBestSecondGap
        return result.bestSimilarity >= result.dynamicThreshold &&
            result.hitCount >= requiredHits &&
            gapOk
    }

    private fun reshapeAndNormalizePerFrame(flat: FloatArray, featureDim: Int): Array<FloatArray> {
        val frames = flat.size / featureDim
        val out = Array(frames) { FloatArray(featureDim) }
        var index = 0
        for (frame in 0 until frames) {
            for (dim in 0 until featureDim) {
                out[frame][dim] = flat[index++]
            }
            l2NormalizeInPlace(out[frame])
        }
        return out
    }

    private fun dtwSimilarity(a: Array<FloatArray>, b: Array<FloatArray>, band: Int): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val n = a.size
        val m = b.size
        val bandWidth = max(band, abs(n - m))
        val dp = Array(n + 1) { FloatArray(m + 1) { Float.POSITIVE_INFINITY } }
        dp[0][0] = 0f

        for (i in 1..n) {
            val start = max(1, i - bandWidth)
            val end = min(m, i + bandWidth)
            for (j in start..end) {
                val cost = cosineDistance(a[i - 1], b[j - 1])
                val bestPrevious = min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
                dp[i][j] = cost + bestPrevious
            }
        }

        val norm = max(1f, (n + m).toFloat())
        val averageCost = dp[n][m] / norm
        return (1f - (averageCost / 2f)).coerceIn(0f, 1f)
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        val count = min(a.size, b.size)
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (index in 0 until count) {
            val va = a[index]
            val vb = b[index]
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        val denominator = kotlin.math.sqrt(max(1e-10f, normA)) * kotlin.math.sqrt(max(1e-10f, normB))
        val cosine = (dot / denominator).coerceIn(-1f, 1f)
        return 1f - cosine
    }

    private fun l2NormalizeInPlace(values: FloatArray) {
        var norm = 0f
        for (value in values) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(max(1e-10f, norm))
        for (index in values.indices) {
            values[index] /= norm
        }
    }
}
