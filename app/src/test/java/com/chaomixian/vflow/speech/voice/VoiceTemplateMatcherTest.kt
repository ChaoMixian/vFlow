package com.chaomixian.vflow.speech.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateMatcherTest {
    private val featureDim = VoiceTemplateFeatureExtractor.Config().numMfcc * 3

    @Test
    fun identicalTemplateMatchesTrigger() {
        val template = buildFeatureVector(frameCount = 12, seed = 0.3f)
        val result = VoiceTemplateMatcher.match(
            featureVector = template,
            templates = listOf(template, template.copyOf(), template.copyOf()),
            config = VoiceTemplateMatcher.Config(similarityThreshold = 0.87f)
        )

        assertNotNull(result)
        assertTrue(VoiceTemplateMatcher.isTriggered(result, VoiceTemplateMatcher.Config(similarityThreshold = 0.87f)))
        assertTrue((result?.bestSimilarity ?: 0f) >= 0.99f)
    }

    @Test
    fun clearlyDifferentTemplateDoesNotTrigger() {
        val templateA = buildFeatureVector(frameCount = 12, seed = 0.2f)
        val templateB = buildFeatureVector(frameCount = 12, seed = 0.2f, invertOddDimensions = true)
        val result = VoiceTemplateMatcher.match(
            featureVector = templateB,
            templates = listOf(templateA, templateA.copyOf(), templateA.copyOf()),
            config = VoiceTemplateMatcher.Config(similarityThreshold = 0.87f)
        )

        assertNotNull(result)
        assertFalse(VoiceTemplateMatcher.isTriggered(result, VoiceTemplateMatcher.Config(similarityThreshold = 0.87f)))
    }

    private fun buildFeatureVector(
        frameCount: Int,
        seed: Float,
        invertOddDimensions: Boolean = false,
    ): FloatArray {
        val values = FloatArray(frameCount * featureDim)
        for (frame in 0 until frameCount) {
            for (dim in 0 until featureDim) {
                val sign = if (invertOddDimensions && dim % 2 == 1) -1f else 1f
                values[frame * featureDim + dim] =
                    sign * ((frame + 1) * (dim + 1)).toFloat() * seed / featureDim.toFloat()
            }
        }
        return values
    }
}
