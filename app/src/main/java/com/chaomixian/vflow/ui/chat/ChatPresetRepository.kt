package com.chaomixian.vflow.ui.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatPresetRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "module_config_prefs"
        private const val KEY_CHAT_PRESETS_JSON = "chat_presets_json"
        private const val KEY_CHAT_PROVIDER_CONFIGS_JSON = "chat_provider_configs_json"
        private const val KEY_CHAT_DEFAULT_PRESET_ID = "chat_default_preset_id"
        private const val KEY_CHAT_SESSION_STATE_JSON = "chat_session_state_json"

        const val BUILTIN_OPENAI_ID = "builtin-openai"
        const val BUILTIN_DEEPSEEK_ID = "builtin-deepseek"
        const val BUILTIN_ANTHROPIC_ID = "builtin-anthropic"
        const val BUILTIN_OPENROUTER_ID = "builtin-openrouter"
        const val BUILTIN_OLLAMA_ID = "builtin-ollama"
    }

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getProviderConfig(providerConfigId: String): ChatProviderConfig? {
        return loadState().providers.firstOrNull { it.id == providerConfigId }
    }

    fun getPresets(): List<ChatPresetConfig> = loadState().presets

    fun getProviderConfigs(): List<ChatProviderConfig> = loadState().providers

    fun getDefaultPresetId(): String? {
        return prefs.getString(KEY_CHAT_DEFAULT_PRESET_ID, null)
    }

    fun getSessionState(): ChatSessionState {
        val raw = prefs.getString(KEY_CHAT_SESSION_STATE_JSON, null).orEmpty()
        if (raw.isBlank()) return ChatSessionState()
        return runCatching {
            json.decodeFromString<ChatSessionState>(raw)
        }.getOrDefault(ChatSessionState())
    }

    fun saveSessionState(state: ChatSessionState) {
        prefs.edit {
            putString(KEY_CHAT_SESSION_STATE_JSON, json.encodeToString(state))
        }
    }

    fun saveProviderConfig(config: ChatProviderConfig): ChatProviderConfig {
        val state = loadState()
        val normalized = config.normalize()
        val updatedProviders = state.providers.replaceById(normalized)
        val updatedPresets = state.presets.map { preset ->
            if (preset.providerConfigId == normalized.id) {
                preset.syncWithProvider(normalized)
            } else {
                preset
            }
        }
        persistState(updatedProviders, updatedPresets)
        return normalized
    }

    fun deleteProviderConfig(providerConfigId: String) {
        val state = loadState()
        val updatedProviders = state.providers.filterNot { it.id == providerConfigId }
        val removedPresetIds = state.presets
            .filter { it.providerConfigId == providerConfigId }
            .map { it.id }
            .toSet()
        val updatedPresets = state.presets.filterNot { it.providerConfigId == providerConfigId }
        persistState(updatedProviders, updatedPresets)
        if (removedPresetIds.contains(getDefaultPresetId())) {
            setDefaultPresetId(updatedPresets.firstOrNull()?.id)
        }
    }

    fun savePreset(preset: ChatPresetConfig): ChatPresetConfig {
        val state = loadState()
        val provider = state.providers.firstOrNull { it.id == preset.providerConfigId }
            ?: ChatProviderConfig(
                id = preset.providerConfigId.ifBlank { UUID.randomUUID().toString() },
                name = preset.providerEnum.displayName,
                provider = preset.provider,
                apiKey = preset.apiKey,
                baseUrl = preset.baseUrl,
                systemPrompt = preset.systemPrompt,
                temperature = preset.temperature,
            ).normalize()

        val normalizedPreset = preset.normalize(provider)
        val updatedProviders = state.providers.replaceById(provider)
        val updatedPresets = state.presets.replaceById(normalizedPreset)
        persistState(updatedProviders, updatedPresets)
        if (getDefaultPresetId().isNullOrBlank()) {
            setDefaultPresetId(normalizedPreset.id)
        }
        return normalizedPreset
    }

    fun deletePreset(presetId: String) {
        val state = loadState()
        val updated = state.presets.filterNot { it.id == presetId }
        persistState(state.providers, updated)
        if (getDefaultPresetId() == presetId) {
            setDefaultPresetId(updated.firstOrNull()?.id)
        }
    }

    fun setDefaultPresetId(presetId: String?) {
        prefs.edit {
            if (presetId.isNullOrBlank()) {
                remove(KEY_CHAT_DEFAULT_PRESET_ID)
            } else {
                putString(KEY_CHAT_DEFAULT_PRESET_ID, presetId)
            }
        }
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun loadState(): RepositoryState {
        val rawPresets = decodeList(KEY_CHAT_PRESETS_JSON)
        val rawProviders = decodeProviderList(KEY_CHAT_PROVIDER_CONFIGS_JSON)
        val builtInProviders = builtInProviderConfigs()
        val builtInIds = builtInProviders.map { it.id }.toSet()
        val rawProvidersById = rawProviders.associateBy { it.id }

        val normalizedProviders = buildMap {
            builtInProviders.forEach { builtIn ->
                val stored = rawProvidersById[builtIn.id]
                put(
                    builtIn.id,
                    (stored ?: builtIn).copy(isBuiltIn = true).normalize()
                )
            }
            rawProviders
                .filterNot { it.id in builtInIds }
                .forEach { provider ->
                    put(provider.id, provider.normalize())
                }
        }.toMutableMap()

        val normalizedPresets = rawPresets.map { rawPreset ->
            val providerId = rawPreset.providerConfigId.ifBlank { rawPreset.id }
            val linkedProvider = normalizedProviders[providerId] ?: ChatProviderConfig(
                id = providerId,
                name = rawPreset.providerEnum.displayName,
                provider = rawPreset.provider,
                apiKey = rawPreset.apiKey,
                baseUrl = rawPreset.baseUrl,
                systemPrompt = rawPreset.systemPrompt,
                temperature = rawPreset.temperature,
                isBuiltIn = false,
            ).normalize().also { normalizedProviders[it.id] = it }
            rawPreset.normalize(linkedProvider)
        }

        val providers = normalizedProviders.values.sortedWith(
            compareBy<ChatProviderConfig> { provider ->
                if (provider.isBuiltIn) {
                    builtInSortOrder(provider.id)
                } else {
                    Int.MAX_VALUE
                }
            }.thenBy { it.name.lowercase() }
        )
        val presets = normalizedPresets.sortedBy { it.name.lowercase() }

        val shouldPersist = rawProviders.size != providers.size ||
            rawPresets != presets ||
            rawProviders.sortedBy { it.id } != providers.sortedBy { it.id }
        if (shouldPersist) {
            persistState(providers, presets)
        }

        return RepositoryState(
            providers = providers,
            presets = presets,
        )
    }

    private fun decodeList(key: String): List<ChatPresetConfig> {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ChatPresetConfig>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun decodeProviderList(key: String): List<ChatProviderConfig> {
        val raw = prefs.getString(key, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ChatProviderConfig>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun persistState(
        providers: List<ChatProviderConfig>,
        presets: List<ChatPresetConfig>,
    ) {
        prefs.edit {
            putString(KEY_CHAT_PROVIDER_CONFIGS_JSON, json.encodeToString(providers))
            putString(KEY_CHAT_PRESETS_JSON, json.encodeToString(presets))
        }
    }

    private fun ChatProviderConfig.normalize(): ChatProviderConfig {
        val resolvedProvider = providerEnum
        return copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            name = name.trim().ifBlank { resolvedProvider.displayName },
            provider = resolvedProvider.storageValue,
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim().ifBlank { resolvedProvider.defaultBaseUrl },
            systemPrompt = systemPrompt.trim().ifBlank { ChatPresetConfig.DEFAULT_SYSTEM_PROMPT },
            temperature = temperature.coerceIn(0.0, 2.0),
            isBuiltIn = isBuiltIn,
        )
    }

    private fun ChatPresetConfig.normalize(providerConfig: ChatProviderConfig): ChatPresetConfig {
        val resolvedProvider = providerConfig.providerEnum
        return copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            providerConfigId = providerConfig.id,
            name = name.trim().ifBlank { model.trim().ifBlank { resolvedProvider.defaultModel } },
            provider = resolvedProvider.storageValue,
            model = model.trim().ifBlank { resolvedProvider.defaultModel },
            apiKey = providerConfig.apiKey,
            baseUrl = providerConfig.baseUrl,
            systemPrompt = providerConfig.systemPrompt,
            temperature = providerConfig.temperature,
        )
    }

    private fun ChatPresetConfig.syncWithProvider(providerConfig: ChatProviderConfig): ChatPresetConfig {
        return normalize(providerConfig).copy(name = name)
    }

    private fun List<ChatPresetConfig>.replaceById(item: ChatPresetConfig): List<ChatPresetConfig> {
        val index = indexOfFirst { it.id == item.id }
        return if (index >= 0) {
            toMutableList().apply { set(index, item) }
        } else {
            this + item
        }
    }

    private fun List<ChatProviderConfig>.replaceById(item: ChatProviderConfig): List<ChatProviderConfig> {
        val index = indexOfFirst { it.id == item.id }
        return if (index >= 0) {
            toMutableList().apply { set(index, item) }
        } else {
            this + item
        }
    }

    private data class RepositoryState(
        val providers: List<ChatProviderConfig>,
        val presets: List<ChatPresetConfig>,
    )

    private fun builtInProviderConfigs(): List<ChatProviderConfig> {
        return listOf(
            builtInProvider(id = BUILTIN_OPENAI_ID, provider = ChatProvider.OPENAI),
            builtInProvider(id = BUILTIN_DEEPSEEK_ID, provider = ChatProvider.DEEPSEEK),
            builtInProvider(id = BUILTIN_ANTHROPIC_ID, provider = ChatProvider.ANTHROPIC),
            builtInProvider(id = BUILTIN_OPENROUTER_ID, provider = ChatProvider.OPENROUTER),
            builtInProvider(id = BUILTIN_OLLAMA_ID, provider = ChatProvider.OLLAMA),
        )
    }

    private fun builtInProvider(id: String, provider: ChatProvider): ChatProviderConfig {
        return ChatProviderConfig(
            id = id,
            name = provider.displayName,
            provider = provider.storageValue,
            baseUrl = provider.defaultBaseUrl,
            isBuiltIn = true,
        )
    }

    private fun builtInSortOrder(providerId: String): Int {
        return when (providerId) {
            BUILTIN_OPENAI_ID -> 0
            BUILTIN_DEEPSEEK_ID -> 1
            BUILTIN_ANTHROPIC_ID -> 2
            BUILTIN_OPENROUTER_ID -> 3
            BUILTIN_OLLAMA_ID -> 4
            else -> Int.MAX_VALUE
        }
    }
}
