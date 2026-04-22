package com.chaomixian.vflow.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.chat.ChatPresetConfig
import com.chaomixian.vflow.ui.chat.ChatPresetRepository
import com.chaomixian.vflow.ui.chat.ChatProvider
import com.chaomixian.vflow.ui.chat.ChatProviderConfig
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.VFlowTheme
import java.util.Locale
import kotlinx.coroutines.launch

class ModelConfigActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PROVIDER_ID = "extra_provider_id"

        fun createIntent(context: Context): Intent {
            return Intent(context, ModelConfigActivity::class.java)
        }

        fun createProviderIntent(context: Context, providerId: String): Intent {
            return Intent(context, ModelConfigActivity::class.java).apply {
                putExtra(EXTRA_PROVIDER_ID, providerId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialProviderId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        setContent {
            VFlowTheme {
                ModelConfigRoute(
                    onBack = { finish() },
                    initialProviderId = initialProviderId,
                )
            }
        }
    }
}

private data class ProviderDraft(
    val name: String,
    val apiKey: String,
    val baseUrl: String,
    val systemPrompt: String,
    val temperature: Double,
)

private data class ProviderTone(
    val containerColor: Color,
    val contentColor: Color,
)

private data class AddProviderChoice(
    val defaultName: String,
    val label: String,
    val provider: ChatProvider,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelConfigRoute(
    onBack: () -> Unit,
    initialProviderId: String?,
) {
    val context = LocalContext.current
    val repository = remember { ChatPresetRepository(context) }
    var refreshToken by remember { mutableStateOf(0) }
    val providerConfigs = remember(refreshToken) { repository.getProviderConfigs() }
    val presets = remember(refreshToken) { repository.getPresets() }
    val defaultPresetId = remember(refreshToken) { repository.getDefaultPresetId() }
    var showAddProviderDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selectedProviderConfig = remember(initialProviderId, providerConfigs) {
        initialProviderId?.let(repository::getProviderConfig)
            ?: providerConfigs.firstOrNull { it.id == initialProviderId }
    }

    fun refresh() {
        refreshToken++
    }

    fun showSavedSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(initialProviderId, selectedProviderConfig) {
        if (initialProviderId != null && selectedProviderConfig == null) {
            onBack()
        }
    }

    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onConfirm = { choice ->
                val saved = repository.saveProviderConfig(
                    ChatProviderConfig(
                        name = choice.defaultName,
                        provider = choice.provider.storageValue,
                        baseUrl = choice.provider.defaultBaseUrl,
                    )
                )
                showAddProviderDialog = false
                refresh()
                context.startActivity(ModelConfigActivity.createProviderIntent(context, saved.id))
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedProviderConfig?.name
                            ?: stringResource(R.string.settings_model_config),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (selectedProviderConfig == null) {
                        IconButton(onClick = { showAddProviderDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.model_config_add_provider),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        if (initialProviderId == null || selectedProviderConfig == null) {
            ProviderListScreen(
                providerConfigs = providerConfigs,
                presets = presets,
                defaultPresetId = defaultPresetId,
                contentPadding = paddingValues,
                onSelectProvider = { provider ->
                    context.startActivity(ModelConfigActivity.createProviderIntent(context, provider.id))
                },
            )
        } else {
            ProviderDetailScreen(
                providerConfig = selectedProviderConfig,
                presets = presets.filter { it.providerConfigId == selectedProviderConfig.id },
                defaultPresetId = defaultPresetId,
                contentPadding = paddingValues,
                onSaveProvider = { draft ->
                    repository.saveProviderConfig(
                        selectedProviderConfig.copy(
                            name = draft.name,
                            apiKey = draft.apiKey,
                            baseUrl = draft.baseUrl,
                            systemPrompt = draft.systemPrompt,
                            temperature = draft.temperature,
                        )
                    )
                    refresh()
                    showSavedSnackbar(context.getString(R.string.model_config_saved))
                },
                onDeleteProvider = {
                    repository.deleteProviderConfig(selectedProviderConfig.id)
                    onBack()
                },
                onSavePreset = { preset, displayName, modelName ->
                    repository.savePreset(
                        (preset ?: ChatPresetConfig()).copy(
                            id = preset?.id.orEmpty(),
                            name = displayName,
                            providerConfigId = selectedProviderConfig.id,
                            model = modelName,
                        )
                    )
                    refresh()
                    showSavedSnackbar(context.getString(R.string.model_config_saved))
                },
                onDeletePreset = { presetId ->
                    repository.deletePreset(presetId)
                    refresh()
                    showSavedSnackbar(context.getString(R.string.model_config_saved))
                },
                onSetDefaultPreset = { presetId ->
                    repository.setDefaultPresetId(presetId)
                    refresh()
                    showSavedSnackbar(context.getString(R.string.model_config_saved))
                },
            )
        }
    }
}

@Composable
private fun ProviderListScreen(
    providerConfigs: List<ChatProviderConfig>,
    presets: List<ChatPresetConfig>,
    defaultPresetId: String?,
    contentPadding: PaddingValues,
    onSelectProvider: (ChatProviderConfig) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsSectionHeader(text = stringResource(R.string.model_config_provider_list_title))
        }
        items(providerConfigs.size) { index ->
            val providerConfig = providerConfigs[index]
            val linkedPresets = presets.filter { it.providerConfigId == providerConfig.id }
            val defaultPreset = linkedPresets.firstOrNull { it.id == defaultPresetId }
            ProviderListCard(
                providerConfig = providerConfig,
                modelCount = linkedPresets.size,
                highlightModel = defaultPreset?.model
                    ?: linkedPresets.firstOrNull()?.model
                    ?: providerConfig.providerEnum.defaultModel,
                onClick = { onSelectProvider(providerConfig) },
            )
        }
    }
}

@Composable
private fun ProviderDetailScreen(
    providerConfig: ChatProviderConfig,
    presets: List<ChatPresetConfig>,
    defaultPresetId: String?,
    contentPadding: PaddingValues,
    onSaveProvider: (ProviderDraft) -> Unit,
    onDeleteProvider: () -> Unit,
    onSavePreset: (ChatPresetConfig?, String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSetDefaultPreset: (String) -> Unit,
) {
    var selectedTab by rememberSaveable(providerConfig.id) { mutableStateOf(0) }
    var providerName by remember(providerConfig.id) { mutableStateOf(providerConfig.name) }
    var apiKey by remember(providerConfig.id) { mutableStateOf(providerConfig.apiKey) }
    var baseUrl by remember(providerConfig.id) { mutableStateOf(providerConfig.baseUrl) }
    var systemPrompt by remember(providerConfig.id) { mutableStateOf(providerConfig.systemPrompt) }
    var temperature by remember(providerConfig.id) { mutableDoubleStateOf(providerConfig.temperature) }
    var advancedExpanded by rememberSaveable(providerConfig.id) { mutableStateOf(false) }
    var showApiKey by rememberSaveable(providerConfig.id) { mutableStateOf(false) }
    var editingPresetId by rememberSaveable(providerConfig.id) { mutableStateOf<String?>(null) }
    var pendingDeletePresetId by rememberSaveable(providerConfig.id) { mutableStateOf<String?>(null) }
    var showDeleteProviderDialog by rememberSaveable(providerConfig.id) { mutableStateOf(false) }

    LaunchedEffect(providerConfig) {
        providerName = providerConfig.name
        apiKey = providerConfig.apiKey
        baseUrl = providerConfig.baseUrl
        systemPrompt = providerConfig.systemPrompt
        temperature = providerConfig.temperature
        advancedExpanded = false
    }

    if (editingPresetId != null) {
        val editingPreset = presets.firstOrNull { it.id == editingPresetId }
        ModelEditorDialog(
            initialDisplayName = editingPreset?.name.orEmpty(),
            initialModelName = editingPreset?.model ?: providerConfig.providerEnum.defaultModel,
            onDismiss = { editingPresetId = null },
            onConfirm = { displayName, modelName ->
                onSavePreset(editingPreset, displayName, modelName)
                editingPresetId = null
            },
        )
    }

    if (pendingDeletePresetId != null) {
        ConfirmDialog(
            title = stringResource(R.string.model_config_delete_model),
            body = stringResource(R.string.model_config_delete_model_confirm),
            onDismiss = { pendingDeletePresetId = null },
            onConfirm = {
                pendingDeletePresetId?.let(onDeletePreset)
                pendingDeletePresetId = null
            },
        )
    }

    if (showDeleteProviderDialog) {
        ConfirmDialog(
            title = stringResource(R.string.model_config_delete_provider),
            body = stringResource(R.string.model_config_delete_provider_confirm),
            onDismiss = { showDeleteProviderDialog = false },
            onConfirm = {
                showDeleteProviderDialog = false
                onDeleteProvider()
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ProviderHeroCard(
                providerConfig = providerConfig,
                modelCount = presets.size,
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf(
                    stringResource(R.string.model_config_tab_configuration),
                    stringResource(R.string.model_config_tab_models),
                )
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                        label = { Text(label) },
                    )
                }
            }
        }
        if (selectedTab == 0) {
            item {
                ProviderConfigurationCard(
                    providerConfig = providerConfig,
                    providerName = providerName,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    advancedExpanded = advancedExpanded,
                    showApiKey = showApiKey,
                    onProviderNameChange = { providerName = it },
                    onApiKeyChange = { apiKey = it },
                    onBaseUrlChange = { baseUrl = it },
                    onSystemPromptChange = { systemPrompt = it },
                    onTemperatureChange = { temperature = it },
                    onToggleAdvanced = { advancedExpanded = !advancedExpanded },
                    onToggleApiKeyVisibility = { showApiKey = !showApiKey },
                    onSave = {
                        onSaveProvider(
                            ProviderDraft(
                                name = providerName.trim(),
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim(),
                                systemPrompt = systemPrompt.trim(),
                                temperature = temperature,
                            )
                        )
                    },
                    onDeleteProvider = { showDeleteProviderDialog = true },
                )
            }
        } else {
            item {
                FilledTonalButton(
                    onClick = { editingPresetId = "" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.model_config_add_model))
                }
            }
            if (presets.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.model_config_empty_models_title),
                        body = stringResource(R.string.model_config_empty_models_body),
                    )
                }
            } else {
                items(presets.size) { index ->
                    val preset = presets[index]
                    ModelPresetCard(
                        preset = preset,
                        isDefault = preset.id == defaultPresetId,
                        onSetDefault = { onSetDefaultPreset(preset.id) },
                        onEdit = { editingPresetId = preset.id },
                        onDelete = { pendingDeletePresetId = preset.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderListCard(
    providerConfig: ChatProviderConfig,
    modelCount: Int,
    highlightModel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProviderBadge(providerConfig.providerEnum)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = providerConfig.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.model_config_provider_summary,
                        modelCount,
                        highlightModel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                text = providerConfig.providerEnum.displayName,
                emphasized = providerConfig.isBuiltIn,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderHeroCard(
    providerConfig: ChatProviderConfig,
    modelCount: Int,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProviderBadge(
                provider = providerConfig.providerEnum,
                size = 52.dp,
                textStyle = MaterialTheme.typography.titleLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = providerConfig.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.model_config_provider_detail_ready,
                        modelCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigurationCard(
    providerConfig: ChatProviderConfig,
    providerName: String,
    apiKey: String,
    baseUrl: String,
    systemPrompt: String,
    temperature: Double,
    advancedExpanded: Boolean,
    showApiKey: Boolean,
    onProviderNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onToggleAdvanced: () -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onSave: () -> Unit,
    onDeleteProvider: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = providerName,
                onValueChange = onProviderNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.model_config_provider_name)) },
            )

            ProviderTypeRow(providerConfig = providerConfig)

            if (providerConfig.providerEnum.requiresApiKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.module_config_chat_api_key)) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleApiKeyVisibility) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.module_config_chat_base_url)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null)
                },
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                onClick = onToggleAdvanced,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            if (advancedExpanded) R.string.model_config_hide_advanced else R.string.model_config_show_advanced
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = onSystemPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.module_config_chat_system_prompt)) },
                        minLines = 4,
                    )
                    Text(
                        text = stringResource(
                            R.string.model_config_temperature_value,
                            String.format(Locale.US, "%.1f", temperature),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = temperature.toFloat(),
                        onValueChange = { onTemperatureChange(it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.model_config_save))
                }
                if (!providerConfig.isBuiltIn) {
                    FilledTonalButton(
                        onClick = onDeleteProvider,
                        modifier = Modifier.width(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTypeRow(
    providerConfig: ChatProviderConfig,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader(text = stringResource(R.string.model_config_provider_type))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProviderBadge(
                    provider = providerConfig.providerEnum,
                    size = 28.dp,
                    textStyle = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = providerConfig.providerEnum.displayName,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelPresetCard(
    preset: ChatPresetConfig,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (isDefault) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 0.dp,
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name.ifBlank { preset.model },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preset.model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isDefault) {
                StatusPill(
                    text = stringResource(R.string.module_config_chat_default_badge),
                    emphasized = true,
                )
            } else {
                TextButton(onClick = onSetDefault) {
                    Text(stringResource(R.string.model_config_set_default))
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(index = 0, count = 1),
                        containerColor = MenuDefaults.groupStandardContainerColor,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model_config_edit_model)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            shape = MenuDefaults.itemShape(index = 0, count = 2).shape,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model_config_delete_model)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            shape = MenuDefaults.itemShape(index = 1, count = 2).shape,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (AddProviderChoice) -> Unit,
) {
    val choices = addableProviderChoices()
    var selectedProvider by remember { mutableStateOf(choices.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_config_add_provider)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSectionHeader(text = stringResource(R.string.model_config_provider_type))
                choices.forEach { choice ->
                    Surface(
                        onClick = { selectedProvider = choice },
                        shape = RoundedCornerShape(22.dp),
                        color = if (choice == selectedProvider) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProviderBadge(
                                provider = choice.provider,
                                size = 28.dp,
                                textStyle = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = choice.label,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (choice == selectedProvider) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedProvider) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ModelEditorDialog(
    initialDisplayName: String,
    initialModelName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var modelName by remember(initialModelName) { mutableStateOf(initialModelName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_config_edit_model)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_config_display_name)) },
                    placeholder = { Text(stringResource(R.string.model_config_display_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.module_config_chat_model_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(displayName.trim(), modelName.trim()) },
                enabled = modelName.trim().isNotEmpty(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderBadge(
    provider: ChatProvider,
    size: Dp = 42.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val tone = providerTone(provider)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = providerBadgeLabel(provider),
            style = textStyle,
            color = tone.contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    emphasized: Boolean,
) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun providerTone(provider: ChatProvider): ProviderTone {
    return when (provider) {
        ChatProvider.OPENAI -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ChatProvider.ANTHROPIC -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ChatProvider.DEEPSEEK -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ChatProvider.OPENROUTER -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChatProvider.OLLAMA -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        ChatProvider.CUSTOM_OPENAI -> ProviderTone(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun providerBadgeLabel(provider: ChatProvider): String {
    return when (provider) {
        ChatProvider.OPENAI -> "O"
        ChatProvider.ANTHROPIC -> "C"
        ChatProvider.DEEPSEEK -> "D"
        ChatProvider.OPENROUTER -> "R"
        ChatProvider.OLLAMA -> "L"
        ChatProvider.CUSTOM_OPENAI -> "A"
    }
}

private fun addableProviderChoices(): List<AddProviderChoice> {
    return listOf(
        AddProviderChoice(
            defaultName = "OpenAI Compatible",
            label = "OpenAI Compatible",
            provider = ChatProvider.CUSTOM_OPENAI,
        ),
        AddProviderChoice(
            defaultName = "Claude Compatible",
            label = "Claude Compatible",
            provider = ChatProvider.ANTHROPIC,
        ),
    )
}
