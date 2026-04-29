package com.chaomixian.vflow.speech.voice

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder

data class VoiceTriggerTemplates(
    val template1: FloatArray,
    val template2: FloatArray,
    val template3: FloatArray,
) {
    fun asList(): List<FloatArray> = listOf(template1, template2, template3)
}

object VoiceTriggerConfig {
    const val DOWNLOAD_SOURCE_PREF_KEY = "voice_trigger_download_source"
    const val KEY_AUDIO_SOURCE = "voice_trigger_audio_source"
    const val KEY_RETRIGGER_COOLDOWN_MS = "voice_trigger_retrigger_cooldown_ms"
    const val KEY_SIMILARITY_THRESHOLD_PERCENT = "voice_trigger_similarity_threshold_percent"
    const val KEY_TEMPLATE_1 = "voice_trigger_template_1"
    const val KEY_TEMPLATE_2 = "voice_trigger_template_2"
    const val KEY_TEMPLATE_3 = "voice_trigger_template_3"

    const val DEFAULT_SIMILARITY_THRESHOLD_PERCENT = 87f
    const val MIN_SIMILARITY_THRESHOLD_PERCENT = 80f
    const val MAX_SIMILARITY_THRESHOLD_PERCENT = 95f
    const val SIMILARITY_THRESHOLD_STEP_PERCENT = 1f
    const val DEFAULT_RETRIGGER_COOLDOWN_MS = 2500L
    const val MIN_RETRIGGER_COOLDOWN_MS = 500L
    const val MAX_RETRIGGER_COOLDOWN_MS = 10000L
    const val RETRIGGER_COOLDOWN_STEP_MS = 100L

    enum class AudioSource(
        val preferenceValue: String,
        val mediaRecorderSource: Int,
    ) {
        VOICE_RECOGNITION("voice_recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION),
        VOICE_COMMUNICATION("voice_communication", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
        MIC("mic", MediaRecorder.AudioSource.MIC),
        ;

        companion object {
            fun fromPreferenceValue(value: String?): AudioSource {
                return entries.firstOrNull { it.preferenceValue == value } ?: VOICE_RECOGNITION
            }
        }
    }

    fun prefs(context: Context) = context.getSharedPreferences("module_config_prefs", Context.MODE_PRIVATE)

    fun readAudioSource(context: Context): AudioSource = readAudioSource(prefs(context))

    fun readAudioSource(prefs: android.content.SharedPreferences): AudioSource {
        return AudioSource.fromPreferenceValue(prefs.getString(KEY_AUDIO_SOURCE, null))
    }

    fun readRetriggerCooldownMs(context: Context): Long = readRetriggerCooldownMs(prefs(context))

    fun readRetriggerCooldownMs(prefs: android.content.SharedPreferences): Long {
        return prefs.getLong(KEY_RETRIGGER_COOLDOWN_MS, DEFAULT_RETRIGGER_COOLDOWN_MS)
            .coerceIn(MIN_RETRIGGER_COOLDOWN_MS, MAX_RETRIGGER_COOLDOWN_MS)
    }

    fun readSimilarityThresholdPercent(context: Context): Float = readSimilarityThresholdPercent(prefs(context))

    fun readSimilarityThresholdPercent(prefs: SharedPreferences): Float {
        return prefs.getFloat(KEY_SIMILARITY_THRESHOLD_PERCENT, DEFAULT_SIMILARITY_THRESHOLD_PERCENT)
            .coerceIn(MIN_SIMILARITY_THRESHOLD_PERCENT, MAX_SIMILARITY_THRESHOLD_PERCENT)
    }

    fun readTemplates(context: Context): VoiceTriggerTemplates? = readTemplates(prefs(context))

    fun readTemplates(prefs: SharedPreferences): VoiceTriggerTemplates? {
        val template1 = readTemplate(prefs, KEY_TEMPLATE_1) ?: return null
        val template2 = readTemplate(prefs, KEY_TEMPLATE_2) ?: return null
        val template3 = readTemplate(prefs, KEY_TEMPLATE_3) ?: return null
        return VoiceTriggerTemplates(
            template1 = template1,
            template2 = template2,
            template3 = template3,
        )
    }

    fun hasTemplates(context: Context): Boolean = hasTemplates(prefs(context))

    fun hasTemplates(prefs: SharedPreferences): Boolean = readTemplates(prefs) != null

    fun recordedTemplateCount(context: Context): Int = recordedTemplateCount(prefs(context))

    fun recordedTemplateCount(prefs: SharedPreferences): Int {
        return listOf(KEY_TEMPLATE_1, KEY_TEMPLATE_2, KEY_TEMPLATE_3).count { key ->
            readTemplate(prefs, key) != null
        }
    }

    fun saveTemplates(
        prefs: SharedPreferences,
        template1: FloatArray,
        template2: FloatArray,
        template3: FloatArray,
    ) {
        prefs.edit()
            .putString(KEY_TEMPLATE_1, encodeTemplate(template1))
            .putString(KEY_TEMPLATE_2, encodeTemplate(template2))
            .putString(KEY_TEMPLATE_3, encodeTemplate(template3))
            .apply()
    }

    fun clearTemplates(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_TEMPLATE_1)
            .remove(KEY_TEMPLATE_2)
            .remove(KEY_TEMPLATE_3)
            .apply()
    }

    private fun readTemplate(prefs: SharedPreferences, key: String): FloatArray? {
        val encoded = prefs.getString(key, null).orEmpty()
        if (encoded.isBlank()) return null
        val parts = encoded.split(',')
        if (parts.isEmpty()) return null
        val values = FloatArray(parts.size)
        for (index in parts.indices) {
            values[index] = parts[index].toFloatOrNull() ?: return null
        }
        return values.takeIf { it.isNotEmpty() }
    }

    private fun encodeTemplate(template: FloatArray): String {
        return template.joinToString(separator = ",")
    }
}
