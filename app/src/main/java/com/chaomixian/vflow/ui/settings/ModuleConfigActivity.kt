// 文件: main/java/com/chaomixian/vflow/ui/settings/ModuleConfigActivity.kt
// 描述: 模块配置 Activity，用于配置各个模块的设置参数

package com.chaomixian.vflow.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.integration.feishu.FeishuModuleConfig
import com.chaomixian.vflow.integration.feishu.FeishuOAuthManager
import com.chaomixian.vflow.services.TriggerServiceProxy
import com.chaomixian.vflow.speech.SherpaNcnnDownloadSource
import com.chaomixian.vflow.speech.SherpaNcnnModelManager
import com.chaomixian.vflow.speech.SherpaNcnnModelProgress
import com.chaomixian.vflow.speech.SherpaNcnnModelSpec
import com.chaomixian.vflow.speech.SherpaNcnnModelStage
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.speech.voice.VoiceTriggerModelManager
import com.chaomixian.vflow.speech.voice.VoiceTriggerModelProgress
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.VFlowTheme
import com.chaomixian.vflow.ui.workflow_editor.VoiceTriggerRecorderActivity
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

class ModuleConfigActivity : BaseActivity() {

    companion object {
        const val EXTRA_INITIAL_SECTION = "initial_section"
        const val SECTION_BACKTAP = "backtap"
        const val SECTION_APP_START = "app_start"
        const val SECTION_VOICE_TRIGGER = "voice_trigger"
        const val SECTION_SHERPA = "sherpa"
        const val SECTION_FEISHU = "feishu"
        const val PREFS_NAME = "module_config_prefs"
        const val KEY_BACKTAP_SENSITIVITY = "backtap_sensitivity"
        const val KEY_APP_START_CLOSE_CHECK_DELAY = "app_start_close_check_delay"
        const val KEY_APP_START_VERIFICATION_DELAY = "app_start_verification_delay"
        const val KEY_APP_START_MIN_CHECK_INTERVAL = "app_start_min_check_interval"
        const val KEY_NETWORK_PROXY = "network_proxy"
        const val KEY_FEISHU_APP_ID = "feishu_app_id"
        const val KEY_FEISHU_APP_SECRET = "feishu_app_secret"
        const val KEY_FEISHU_APP_ACCESS_TOKEN = "feishu_app_access_token"
        const val KEY_FEISHU_APP_ACCESS_TOKEN_EXPIRES_AT = "feishu_app_access_token_expires_at"
        const val KEY_FEISHU_TENANT_ACCESS_TOKEN = "feishu_access_token"
        const val KEY_FEISHU_TENANT_ACCESS_TOKEN_EXPIRES_AT = "feishu_access_token_expires_at"
        const val KEY_FEISHU_USER_AUTH_CODE = "feishu_user_auth_code"
        const val KEY_FEISHU_USER_REDIRECT_URI = "feishu_user_redirect_uri"
        const val KEY_FEISHU_USER_CODE_VERIFIER = "feishu_user_code_verifier"
        const val KEY_FEISHU_USER_SCOPE = "feishu_user_scope"
        const val KEY_FEISHU_USER_ACCESS_TOKEN = "feishu_user_access_token"
        const val KEY_FEISHU_USER_ACCESS_TOKEN_EXPIRES_AT = "feishu_user_access_token_expires_at"
        const val KEY_FEISHU_USER_REFRESH_TOKEN = "feishu_user_refresh_token"
        const val KEY_FEISHU_USER_REFRESH_TOKEN_EXPIRES_AT = "feishu_user_refresh_token_expires_at"

        const val MIN_SENSITIVITY_VALUE = 0.0f
        const val MAX_SENSITIVITY_VALUE = 0.75f
        const val DEFAULT_APP_START_CLOSE_CHECK_DELAY = 800L
        const val MIN_APP_START_CLOSE_CHECK_DELAY = 200L
        const val MAX_APP_START_CLOSE_CHECK_DELAY = 2000L
        const val APP_START_CLOSE_CHECK_DELAY_STEP = 100L
        const val DEFAULT_APP_START_VERIFICATION_DELAY = 200L
        const val MIN_APP_START_VERIFICATION_DELAY = 50L
        const val MAX_APP_START_VERIFICATION_DELAY = 1000L
        const val APP_START_VERIFICATION_DELAY_STEP = 50L
        const val DEFAULT_APP_START_MIN_CHECK_INTERVAL = 100L
        const val MIN_APP_START_MIN_CHECK_INTERVAL = 0L
        const val MAX_APP_START_MIN_CHECK_INTERVAL = 500L
        const val APP_START_MIN_CHECK_INTERVAL_STEP = 10L

        fun getSensitivityDisplayValue(value: Float): String {
            return when {
                value <= 0.01f -> "非常灵敏"
                value <= 0.02f -> "很灵敏"
                value <= 0.03f -> "灵敏"
                value <= 0.04f -> "一般"
                value <= 0.05f -> "较慢"
                value <= 0.1f -> "慢"
                value <= 0.25f -> "较难"
                value <= 0.4f -> "很难"
                value <= 0.53f -> "非常难"
                else -> "极难"
            }
        }

        fun readAppStartCloseCheckDelay(prefs: android.content.SharedPreferences): Long {
            return prefs.getLong(KEY_APP_START_CLOSE_CHECK_DELAY, DEFAULT_APP_START_CLOSE_CHECK_DELAY)
                .coerceIn(MIN_APP_START_CLOSE_CHECK_DELAY, MAX_APP_START_CLOSE_CHECK_DELAY)
        }

        fun readAppStartVerificationDelay(prefs: android.content.SharedPreferences): Long {
            return prefs.getLong(KEY_APP_START_VERIFICATION_DELAY, DEFAULT_APP_START_VERIFICATION_DELAY)
                .coerceIn(MIN_APP_START_VERIFICATION_DELAY, MAX_APP_START_VERIFICATION_DELAY)
        }

        fun readAppStartMinCheckInterval(prefs: android.content.SharedPreferences): Long {
            return prefs.getLong(KEY_APP_START_MIN_CHECK_INTERVAL, DEFAULT_APP_START_MIN_CHECK_INTERVAL)
                .coerceIn(MIN_APP_START_MIN_CHECK_INTERVAL, MAX_APP_START_MIN_CHECK_INTERVAL)
        }

        fun createIntent(context: Context, initialSection: String? = null): Intent {
            return Intent(context, ModuleConfigActivity::class.java).apply {
                initialSection?.let { putExtra(EXTRA_INITIAL_SECTION, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VFlowTheme {
                ModuleConfigScreen(
                    initialSection = intent?.getStringExtra(EXTRA_INITIAL_SECTION),
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleConfigScreen(initialSection: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
    val appPrefs = context.getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
    val scrollState = rememberScrollState()

    var sensitivityValue by remember {
        mutableFloatStateOf(
            prefs.getFloat(ModuleConfigActivity.KEY_BACKTAP_SENSITIVITY, 0.05f)
                .coerceIn(
                    ModuleConfigActivity.MIN_SENSITIVITY_VALUE,
                    ModuleConfigActivity.MAX_SENSITIVITY_VALUE
                )
        )
    }
    var appStartCloseCheckDelay by remember {
        mutableFloatStateOf(ModuleConfigActivity.readAppStartCloseCheckDelay(prefs).toFloat())
    }
    var appStartVerificationDelay by remember {
        mutableFloatStateOf(ModuleConfigActivity.readAppStartVerificationDelay(prefs).toFloat())
    }
    var appStartMinCheckInterval by remember {
        mutableFloatStateOf(ModuleConfigActivity.readAppStartMinCheckInterval(prefs).toFloat())
    }
    var feishuAppId by remember {
        mutableStateOf(
            prefs.getString(ModuleConfigActivity.KEY_FEISHU_APP_ID, "").orEmpty()
        )
    }
    var feishuAppSecret by remember {
        mutableStateOf(
            prefs.getString(ModuleConfigActivity.KEY_FEISHU_APP_SECRET, "").orEmpty()
        )
    }
    var networkProxy by remember {
        mutableStateOf(
            prefs.getString(ModuleConfigActivity.KEY_NETWORK_PROXY, "").orEmpty()
        )
    }
    var userAuthStatusVersion by remember { mutableIntStateOf(0) }
    val authUiState by FeishuOAuthManager.authState.collectAsState()
    val userAuthorizationStatus = remember(authUiState, userAuthStatusVersion) {
        FeishuModuleConfig.getUserAuthorizationStatus(context)
    }
    val redirectUri = remember { FeishuOAuthManager.getRedirectUri() }
    val sherpaModelManager = remember { SherpaNcnnModelManager(context) }
    val sherpaCoroutineScope = rememberCoroutineScope()
    var sherpaModelsVersion by remember { mutableIntStateOf(0) }
    var sherpaBusyAction by remember { mutableStateOf<String?>(null) }
    var sherpaProgress by remember { mutableStateOf<SherpaNcnnModelProgress?>(null) }
    var sherpaStatusMessage by remember { mutableStateOf<String?>(null) }
    val sherpaInstalledSpecs = remember(sherpaModelsVersion) {
        SherpaNcnnModelManager.SUPPORTED_MODEL_SPECS.filter(sherpaModelManager::isModelInstalled)
    }
    var sherpaDownloadSource by remember {
        mutableStateOf(
            SherpaNcnnDownloadSource.fromPreferenceValue(
                appPrefs.getString(SherpaNcnnModelManager.DOWNLOAD_SOURCE_PREF_KEY, null)
            )
        )
    }
    val voiceModelManager = remember { VoiceTriggerModelManager(context) }
    val voiceCoroutineScope = rememberCoroutineScope()
    var voiceModelsVersion by remember { mutableIntStateOf(0) }
    var voiceTemplatesVersion by remember { mutableIntStateOf(0) }
    var voiceBusy by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf<VoiceTriggerModelProgress?>(null) }
    var voiceStatusMessage by remember { mutableStateOf<String?>(null) }
    val voiceModelInstalled = remember(voiceModelsVersion) { voiceModelManager.isModelInstalled() }
    val voiceTemplateCount = remember(voiceTemplatesVersion) { VoiceTriggerConfig.recordedTemplateCount(prefs) }
    val voiceTemplatesReady = voiceTemplateCount == 3
    var voiceDownloadSource by remember {
        mutableStateOf(
            SherpaNcnnDownloadSource.fromPreferenceValue(
                prefs.getString(VoiceTriggerConfig.DOWNLOAD_SOURCE_PREF_KEY, null)
            )
        )
    }
    var voiceAudioSource by remember {
        mutableStateOf(VoiceTriggerConfig.readAudioSource(prefs))
    }
    var voiceSimilarityThresholdPercent by remember {
        mutableFloatStateOf(VoiceTriggerConfig.readSimilarityThresholdPercent(prefs))
    }
    var voiceRetriggerCooldownMs by remember {
        mutableFloatStateOf(VoiceTriggerConfig.readRetriggerCooldownMs(prefs).toFloat())
    }
    val isAuthInProgress = authUiState.phase == FeishuOAuthManager.Phase.WaitingForAuthorization ||
            authUiState.phase == FeishuOAuthManager.Phase.ExchangingToken

    val sherpaImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri == null) return@rememberLauncherForActivityResult
        sherpaBusyAction = "import"
        sherpaProgress = null
        sherpaStatusMessage = context.getString(R.string.module_config_sherpa_status_starting_import)
        sherpaCoroutineScope.launch {
            try {
                val result = sherpaModelManager.importModelArchive(uri) { progress ->
                    sherpaCoroutineScope.launch {
                        sherpaProgress = progress
                        sherpaStatusMessage = formatSherpaModuleConfigProgress(context, progress)
                    }
                }
                sherpaModelsVersion++
                sherpaStatusMessage = context.getString(
                    R.string.module_config_sherpa_status_installed,
                    sherpaDisplayName(context, result.modelSpec)
                )
            } catch (e: Exception) {
                sherpaStatusMessage = context.getString(
                    R.string.module_config_sherpa_status_failed,
                    e.message ?: context.getString(R.string.error_unknown_error)
                )
            } finally {
                sherpaBusyAction = null
                sherpaProgress = null
            }
        }
    }

    val voiceTemplateRecorderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val template1 = data.getFloatArrayExtra(VoiceTriggerRecorderActivity.EXTRA_RESULT_TEMPLATE_1) ?: return@rememberLauncherForActivityResult
        val template2 = data.getFloatArrayExtra(VoiceTriggerRecorderActivity.EXTRA_RESULT_TEMPLATE_2) ?: return@rememberLauncherForActivityResult
        val template3 = data.getFloatArrayExtra(VoiceTriggerRecorderActivity.EXTRA_RESULT_TEMPLATE_3) ?: return@rememberLauncherForActivityResult
        VoiceTriggerConfig.saveTemplates(
            prefs = prefs,
            template1 = template1,
            template2 = template2,
            template3 = template3,
        )
        voiceTemplatesVersion++
        voiceStatusMessage = context.getString(R.string.module_config_voice_trigger_templates_saved)
        TriggerServiceProxy.reloadTriggers(context)
    }

    val sliderPosition = ((sensitivityValue - ModuleConfigActivity.MIN_SENSITIVITY_VALUE) /
            (ModuleConfigActivity.MAX_SENSITIVITY_VALUE - ModuleConfigActivity.MIN_SENSITIVITY_VALUE) * 10)

    fun clearFeishuAppTokenCache() {
        prefs.edit {
            remove(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN)
            remove(ModuleConfigActivity.KEY_FEISHU_APP_ACCESS_TOKEN_EXPIRES_AT)
            remove(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN)
            remove(ModuleConfigActivity.KEY_FEISHU_TENANT_ACCESS_TOKEN_EXPIRES_AT)
        }
    }

    fun clearFeishuUserAuthorization() {
        FeishuModuleConfig.clearUserAuthorization(context)
        userAuthStatusVersion++
    }

    fun onFeishuCredentialChanged() {
        if (isAuthInProgress) {
            FeishuOAuthManager.cancelAuthorization("飞书配置已变更，请重新开始认证。")
        } else {
            FeishuOAuthManager.resetUiState()
        }
        clearFeishuAppTokenCache()
        clearFeishuUserAuthorization()
    }

    fun reloadVoiceTriggers() {
        TriggerServiceProxy.reloadTriggers(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            val renderBacktapSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_backtap)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_config_backtap_sensitivity),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = ModuleConfigActivity.getSensitivityDisplayValue(sensitivityValue) +
                                    String.format(" (%.2f)", sensitivityValue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = sliderPosition,
                            onValueChange = { newPosition ->
                                sensitivityValue = ModuleConfigActivity.MIN_SENSITIVITY_VALUE +
                                        (newPosition / 10f) *
                                        (ModuleConfigActivity.MAX_SENSITIVITY_VALUE - ModuleConfigActivity.MIN_SENSITIVITY_VALUE)
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putFloat(ModuleConfigActivity.KEY_BACKTAP_SENSITIVITY, sensitivityValue)
                                }
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "灵敏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "难触发",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val renderSherpaSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_sherpa)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_config_sherpa_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(
                                R.string.module_config_sherpa_source_current,
                                sherpaDownloadSourceLabel(context, sherpaDownloadSource)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_direct),
                                selected = sherpaDownloadSource == SherpaNcnnDownloadSource.DIRECT,
                                onSelected = {
                                    sherpaDownloadSource = SherpaNcnnDownloadSource.DIRECT
                                    appPrefs.edit {
                                        putString(
                                            SherpaNcnnModelManager.DOWNLOAD_SOURCE_PREF_KEY,
                                            sherpaDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = sherpaBusyAction == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_ghproxy_net),
                                selected = sherpaDownloadSource == SherpaNcnnDownloadSource.GHPROXY_NET,
                                onSelected = {
                                    sherpaDownloadSource = SherpaNcnnDownloadSource.GHPROXY_NET
                                    appPrefs.edit {
                                        putString(
                                            SherpaNcnnModelManager.DOWNLOAD_SOURCE_PREF_KEY,
                                            sherpaDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = sherpaBusyAction == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_gh_proxy_com),
                                selected = sherpaDownloadSource == SherpaNcnnDownloadSource.GH_PROXY_COM,
                                onSelected = {
                                    sherpaDownloadSource = SherpaNcnnDownloadSource.GH_PROXY_COM
                                    appPrefs.edit {
                                        putString(
                                            SherpaNcnnModelManager.DOWNLOAD_SOURCE_PREF_KEY,
                                            sherpaDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = sherpaBusyAction == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    sherpaImportLauncher.launch(
                                        Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "*/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    )
                                },
                                enabled = sherpaBusyAction == null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_sherpa_import))
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                SherpaNcnnModelManager.MODELS_DOWNLOAD_PAGE_URL.toUri()
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.module_config_sherpa_status_failed,
                                                e.message ?: context.getString(R.string.error_unknown_error)
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = sherpaBusyAction == null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_sherpa_open_page))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (sherpaBusyAction != null || sherpaProgress != null || !sherpaStatusMessage.isNullOrBlank()) {
                            sherpaStatusMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (sherpaBusyAction != null || sherpaProgress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val progress = sherpaProgress
                                if (progress?.totalBytes != null && progress.totalBytes > 0L &&
                                    (progress.stage == SherpaNcnnModelStage.DOWNLOADING || progress.stage == SherpaNcnnModelStage.IMPORTING)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { (progress.downloadedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        SherpaModelSpecCard(
                            context = context,
                            spec = SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL,
                            installed = sherpaInstalledSpecs.contains(SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL),
                            busy = sherpaBusyAction != null,
                            onDownload = {
                                sherpaBusyAction = SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL.key
                                sherpaProgress = null
                                sherpaStatusMessage = context.getString(R.string.module_config_sherpa_status_starting_download)
                                sherpaCoroutineScope.launch {
                                    try {
                                        sherpaModelManager.downloadModel(
                                            SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL,
                                            sherpaDownloadSource
                                        ) { progress ->
                                            sherpaCoroutineScope.launch {
                                                sherpaProgress = progress
                                                sherpaStatusMessage = formatSherpaModuleConfigProgress(context, progress)
                                            }
                                        }
                                        sherpaModelsVersion++
                                        sherpaStatusMessage = context.getString(
                                            R.string.module_config_sherpa_status_installed,
                                            sherpaDisplayName(context, SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL)
                                        )
                                    } catch (e: Exception) {
                                        sherpaStatusMessage = context.getString(
                                            R.string.module_config_sherpa_status_failed,
                                            e.message ?: context.getString(R.string.error_unknown_error)
                                        )
                                    } finally {
                                        sherpaBusyAction = null
                                        sherpaProgress = null
                                    }
                                }
                            },
                            onDelete = {
                                sherpaModelManager.uninstallModel(SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL)
                                sherpaModelsVersion++
                                sherpaStatusMessage = context.getString(
                                    R.string.module_config_sherpa_status_deleted,
                                    sherpaDisplayName(context, SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SherpaModelSpecCard(
                            context = context,
                            spec = SherpaNcnnModelManager.ENGLISH_MODEL,
                            installed = sherpaInstalledSpecs.contains(SherpaNcnnModelManager.ENGLISH_MODEL),
                            busy = sherpaBusyAction != null,
                            onDownload = {
                                sherpaBusyAction = SherpaNcnnModelManager.ENGLISH_MODEL.key
                                sherpaProgress = null
                                sherpaStatusMessage = context.getString(R.string.module_config_sherpa_status_starting_download)
                                sherpaCoroutineScope.launch {
                                    try {
                                        sherpaModelManager.downloadModel(
                                            SherpaNcnnModelManager.ENGLISH_MODEL,
                                            sherpaDownloadSource
                                        ) { progress ->
                                            sherpaCoroutineScope.launch {
                                                sherpaProgress = progress
                                                sherpaStatusMessage = formatSherpaModuleConfigProgress(context, progress)
                                            }
                                        }
                                        sherpaModelsVersion++
                                        sherpaStatusMessage = context.getString(
                                            R.string.module_config_sherpa_status_installed,
                                            sherpaDisplayName(context, SherpaNcnnModelManager.ENGLISH_MODEL)
                                        )
                                    } catch (e: Exception) {
                                        sherpaStatusMessage = context.getString(
                                            R.string.module_config_sherpa_status_failed,
                                            e.message ?: context.getString(R.string.error_unknown_error)
                                        )
                                    } finally {
                                        sherpaBusyAction = null
                                        sherpaProgress = null
                                    }
                                }
                            },
                            onDelete = {
                                sherpaModelManager.uninstallModel(SherpaNcnnModelManager.ENGLISH_MODEL)
                                sherpaModelsVersion++
                                sherpaStatusMessage = context.getString(
                                    R.string.module_config_sherpa_status_deleted,
                                    sherpaDisplayName(context, SherpaNcnnModelManager.ENGLISH_MODEL)
                                )
                            }
                        )
                    }
                }
            }

            val renderVoiceTriggerSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_voice_trigger)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(
                                R.string.module_config_sherpa_source_current,
                                sherpaDownloadSourceLabel(context, voiceDownloadSource)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_direct),
                                selected = voiceDownloadSource == SherpaNcnnDownloadSource.DIRECT,
                                onSelected = {
                                    voiceDownloadSource = SherpaNcnnDownloadSource.DIRECT
                                    prefs.edit {
                                        putString(
                                            VoiceTriggerConfig.DOWNLOAD_SOURCE_PREF_KEY,
                                            voiceDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_ghproxy_net),
                                selected = voiceDownloadSource == SherpaNcnnDownloadSource.GHPROXY_NET,
                                onSelected = {
                                    voiceDownloadSource = SherpaNcnnDownloadSource.GHPROXY_NET
                                    prefs.edit {
                                        putString(
                                            VoiceTriggerConfig.DOWNLOAD_SOURCE_PREF_KEY,
                                            voiceDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_sherpa_source_gh_proxy_com),
                                selected = voiceDownloadSource == SherpaNcnnDownloadSource.GH_PROXY_COM,
                                onSelected = {
                                    voiceDownloadSource = SherpaNcnnDownloadSource.GH_PROXY_COM
                                    prefs.edit {
                                        putString(
                                            VoiceTriggerConfig.DOWNLOAD_SOURCE_PREF_KEY,
                                            voiceDownloadSource.preferenceValue
                                        )
                                    }
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(
                                if (voiceModelInstalled) {
                                    R.string.module_config_voice_trigger_model_ready
                                } else {
                                    R.string.module_config_voice_trigger_model_missing
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    voiceBusy = true
                                    voiceProgress = null
                                    voiceStatusMessage = context.getString(R.string.module_config_voice_trigger_status_starting_download)
                                    voiceCoroutineScope.launch {
                                        try {
                                            voiceModelManager.downloadModel(voiceDownloadSource) { progress ->
                                                voiceCoroutineScope.launch {
                                                    voiceProgress = progress
                                                    voiceStatusMessage = formatVoiceTriggerModelProgress(context, progress)
                                                }
                                            }
                                            voiceModelsVersion++
                                            voiceStatusMessage = context.getString(R.string.module_config_voice_trigger_status_installed)
                                            reloadVoiceTriggers()
                                        } catch (e: Exception) {
                                            voiceStatusMessage = context.getString(
                                                R.string.module_config_voice_trigger_status_failed,
                                                e.message ?: context.getString(R.string.error_unknown_error)
                                            )
                                        } finally {
                                            voiceBusy = false
                                            voiceProgress = null
                                        }
                                    }
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_voice_trigger_download_model))
                            }
                            OutlinedButton(
                                onClick = {
                                    voiceModelManager.uninstallModel()
                                    voiceModelsVersion++
                                    voiceStatusMessage = context.getString(R.string.module_config_voice_trigger_status_deleted)
                                    reloadVoiceTriggers()
                                },
                                enabled = voiceModelInstalled && !voiceBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_voice_trigger_delete_model))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            VoiceTriggerModelManager.MODELS_DOWNLOAD_PAGE_URL.toUri()
                                        )
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.module_config_voice_trigger_status_failed,
                                            e.message ?: context.getString(R.string.error_unknown_error)
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !voiceBusy,
                        ) {
                            Text(stringResource(R.string.module_config_voice_trigger_open_page))
                        }

                        if (voiceBusy || voiceProgress != null || !voiceStatusMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            voiceStatusMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (voiceBusy || voiceProgress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val progress = voiceProgress
                                if (progress?.totalBytes != null && progress.totalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (progress.downloadedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_templates_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_templates_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                if (voiceTemplatesReady) {
                                    R.string.module_config_voice_trigger_templates_ready
                                } else {
                                    R.string.module_config_voice_trigger_templates_missing
                                },
                                voiceTemplateCount,
                                3
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (voiceTemplatesReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!voiceModelInstalled) {
                                        Toast.makeText(
                                            context,
                                            R.string.voice_trigger_model_missing_prompt,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        voiceTemplateRecorderLauncher.launch(
                                            VoiceTriggerRecorderActivity.createIntent(context)
                                        )
                                    }
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_voice_trigger_record_templates))
                            }
                            OutlinedButton(
                                onClick = {
                                    VoiceTriggerConfig.clearTemplates(prefs)
                                    voiceTemplatesVersion++
                                    voiceStatusMessage = context.getString(R.string.module_config_voice_trigger_templates_deleted)
                                    reloadVoiceTriggers()
                                },
                                enabled = voiceTemplateCount > 0 && !voiceBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_voice_trigger_delete_templates))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_similarity_threshold),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.module_config_voice_trigger_similarity_threshold_value,
                                voiceSimilarityThresholdPercent.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = voiceSimilarityThresholdPercent,
                            onValueChange = { value ->
                                voiceSimilarityThresholdPercent = value
                                    .coerceIn(
                                        VoiceTriggerConfig.MIN_SIMILARITY_THRESHOLD_PERCENT,
                                        VoiceTriggerConfig.MAX_SIMILARITY_THRESHOLD_PERCENT
                                    )
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putFloat(
                                        VoiceTriggerConfig.KEY_SIMILARITY_THRESHOLD_PERCENT,
                                        voiceSimilarityThresholdPercent
                                    )
                                }
                                reloadVoiceTriggers()
                            },
                            valueRange = VoiceTriggerConfig.MIN_SIMILARITY_THRESHOLD_PERCENT..VoiceTriggerConfig.MAX_SIMILARITY_THRESHOLD_PERCENT,
                            steps = (((VoiceTriggerConfig.MAX_SIMILARITY_THRESHOLD_PERCENT - VoiceTriggerConfig.MIN_SIMILARITY_THRESHOLD_PERCENT) / VoiceTriggerConfig.SIMILARITY_THRESHOLD_STEP_PERCENT) - 1).toInt(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_audio_source),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_voice_trigger_audio_source_voice_recognition),
                                selected = voiceAudioSource == VoiceTriggerConfig.AudioSource.VOICE_RECOGNITION,
                                onSelected = {
                                    voiceAudioSource = VoiceTriggerConfig.AudioSource.VOICE_RECOGNITION
                                    prefs.edit {
                                        putString(VoiceTriggerConfig.KEY_AUDIO_SOURCE, voiceAudioSource.preferenceValue)
                                    }
                                    reloadVoiceTriggers()
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_voice_trigger_audio_source_voice_communication),
                                selected = voiceAudioSource == VoiceTriggerConfig.AudioSource.VOICE_COMMUNICATION,
                                onSelected = {
                                    voiceAudioSource = VoiceTriggerConfig.AudioSource.VOICE_COMMUNICATION
                                    prefs.edit {
                                        putString(VoiceTriggerConfig.KEY_AUDIO_SOURCE, voiceAudioSource.preferenceValue)
                                    }
                                    reloadVoiceTriggers()
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            )
                            SherpaSourceButton(
                                text = stringResource(R.string.module_config_voice_trigger_audio_source_mic),
                                selected = voiceAudioSource == VoiceTriggerConfig.AudioSource.MIC,
                                onSelected = {
                                    voiceAudioSource = VoiceTriggerConfig.AudioSource.MIC
                                    prefs.edit {
                                        putString(VoiceTriggerConfig.KEY_AUDIO_SOURCE, voiceAudioSource.preferenceValue)
                                    }
                                    reloadVoiceTriggers()
                                },
                                enabled = !voiceBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { role = Role.RadioButton },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_audio_source_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.module_config_voice_trigger_retrigger_cooldown),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.module_config_voice_trigger_retrigger_cooldown_value,
                                voiceRetriggerCooldownMs.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = voiceRetriggerCooldownMs,
                            onValueChange = { value ->
                                voiceRetriggerCooldownMs = value
                                    .toLong()
                                    .coerceIn(
                                        VoiceTriggerConfig.MIN_RETRIGGER_COOLDOWN_MS,
                                        VoiceTriggerConfig.MAX_RETRIGGER_COOLDOWN_MS
                                    )
                                    .toFloat()
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putLong(
                                        VoiceTriggerConfig.KEY_RETRIGGER_COOLDOWN_MS,
                                        voiceRetriggerCooldownMs.toLong()
                                    )
                                }
                                reloadVoiceTriggers()
                            },
                            valueRange = VoiceTriggerConfig.MIN_RETRIGGER_COOLDOWN_MS.toFloat()..VoiceTriggerConfig.MAX_RETRIGGER_COOLDOWN_MS.toFloat(),
                            steps = (((VoiceTriggerConfig.MAX_RETRIGGER_COOLDOWN_MS - VoiceTriggerConfig.MIN_RETRIGGER_COOLDOWN_MS) / VoiceTriggerConfig.RETRIGGER_COOLDOWN_STEP_MS) - 1).toInt(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val renderAppStartSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_app_start)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        val isDefault =
                            appStartCloseCheckDelay.toLong() == ModuleConfigActivity.DEFAULT_APP_START_CLOSE_CHECK_DELAY &&
                                    appStartVerificationDelay.toLong() == ModuleConfigActivity.DEFAULT_APP_START_VERIFICATION_DELAY &&
                                    appStartMinCheckInterval.toLong() == ModuleConfigActivity.DEFAULT_APP_START_MIN_CHECK_INTERVAL

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.module_config_app_start_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    appStartCloseCheckDelay = ModuleConfigActivity.DEFAULT_APP_START_CLOSE_CHECK_DELAY.toFloat()
                                    appStartVerificationDelay = ModuleConfigActivity.DEFAULT_APP_START_VERIFICATION_DELAY.toFloat()
                                    appStartMinCheckInterval = ModuleConfigActivity.DEFAULT_APP_START_MIN_CHECK_INTERVAL.toFloat()
                                    prefs.edit {
                                        putLong(
                                            ModuleConfigActivity.KEY_APP_START_CLOSE_CHECK_DELAY,
                                            ModuleConfigActivity.DEFAULT_APP_START_CLOSE_CHECK_DELAY
                                        )
                                        putLong(
                                            ModuleConfigActivity.KEY_APP_START_VERIFICATION_DELAY,
                                            ModuleConfigActivity.DEFAULT_APP_START_VERIFICATION_DELAY
                                        )
                                        putLong(
                                            ModuleConfigActivity.KEY_APP_START_MIN_CHECK_INTERVAL,
                                            ModuleConfigActivity.DEFAULT_APP_START_MIN_CHECK_INTERVAL
                                        )
                                    }
                                },
                                enabled = !isDefault
                            ) {
                                Text(stringResource(R.string.module_config_reset_defaults))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.module_config_app_start_close_check_delay),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.module_config_app_start_millis_value,
                                appStartCloseCheckDelay.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = appStartCloseCheckDelay,
                            onValueChange = { value ->
                                appStartCloseCheckDelay = value
                                    .toLong()
                                    .coerceIn(
                                        ModuleConfigActivity.MIN_APP_START_CLOSE_CHECK_DELAY,
                                        ModuleConfigActivity.MAX_APP_START_CLOSE_CHECK_DELAY
                                    )
                                    .toFloat()
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putLong(
                                        ModuleConfigActivity.KEY_APP_START_CLOSE_CHECK_DELAY,
                                        appStartCloseCheckDelay.toLong()
                                    )
                                }
                            },
                            valueRange = ModuleConfigActivity.MIN_APP_START_CLOSE_CHECK_DELAY.toFloat()..
                                    ModuleConfigActivity.MAX_APP_START_CLOSE_CHECK_DELAY.toFloat(),
                            steps = (
                                (ModuleConfigActivity.MAX_APP_START_CLOSE_CHECK_DELAY -
                                        ModuleConfigActivity.MIN_APP_START_CLOSE_CHECK_DELAY) /
                                        ModuleConfigActivity.APP_START_CLOSE_CHECK_DELAY_STEP
                                ).toInt() - 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.module_config_app_start_verification_delay),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.module_config_app_start_millis_value,
                                appStartVerificationDelay.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = appStartVerificationDelay,
                            onValueChange = { value ->
                                appStartVerificationDelay = value
                                    .toLong()
                                    .coerceIn(
                                        ModuleConfigActivity.MIN_APP_START_VERIFICATION_DELAY,
                                        ModuleConfigActivity.MAX_APP_START_VERIFICATION_DELAY
                                    )
                                    .toFloat()
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putLong(
                                        ModuleConfigActivity.KEY_APP_START_VERIFICATION_DELAY,
                                        appStartVerificationDelay.toLong()
                                    )
                                }
                            },
                            valueRange = ModuleConfigActivity.MIN_APP_START_VERIFICATION_DELAY.toFloat()..
                                    ModuleConfigActivity.MAX_APP_START_VERIFICATION_DELAY.toFloat(),
                            steps = (
                                (ModuleConfigActivity.MAX_APP_START_VERIFICATION_DELAY -
                                        ModuleConfigActivity.MIN_APP_START_VERIFICATION_DELAY) /
                                        ModuleConfigActivity.APP_START_VERIFICATION_DELAY_STEP
                                ).toInt() - 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.module_config_app_start_min_check_interval),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.module_config_app_start_millis_value,
                                appStartMinCheckInterval.toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = appStartMinCheckInterval,
                            onValueChange = { value ->
                                appStartMinCheckInterval = value
                                    .toLong()
                                    .coerceIn(
                                        ModuleConfigActivity.MIN_APP_START_MIN_CHECK_INTERVAL,
                                        ModuleConfigActivity.MAX_APP_START_MIN_CHECK_INTERVAL
                                    )
                                    .toFloat()
                            },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putLong(
                                        ModuleConfigActivity.KEY_APP_START_MIN_CHECK_INTERVAL,
                                        appStartMinCheckInterval.toLong()
                                    )
                                }
                            },
                            valueRange = ModuleConfigActivity.MIN_APP_START_MIN_CHECK_INTERVAL.toFloat()..
                                    ModuleConfigActivity.MAX_APP_START_MIN_CHECK_INTERVAL.toFloat(),
                            steps = (
                                (ModuleConfigActivity.MAX_APP_START_MIN_CHECK_INTERVAL -
                                        ModuleConfigActivity.MIN_APP_START_MIN_CHECK_INTERVAL) /
                                        ModuleConfigActivity.APP_START_MIN_CHECK_INTERVAL_STEP
                                ).toInt() - 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val renderFeishuSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_feishu)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_config_feishu_app_id),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = feishuAppId,
                            onValueChange = {
                                feishuAppId = it
                                prefs.edit {
                                    putString(ModuleConfigActivity.KEY_FEISHU_APP_ID, it.trim())
                                }
                                onFeishuCredentialChanged()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.module_config_feishu_app_id)) },
                            placeholder = { Text(stringResource(R.string.module_config_feishu_app_id_hint)) },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_app_secret),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = feishuAppSecret,
                            onValueChange = {
                                feishuAppSecret = it
                                prefs.edit {
                                    putString(ModuleConfigActivity.KEY_FEISHU_APP_SECRET, it.trim())
                                }
                                onFeishuCredentialChanged()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.module_config_feishu_app_secret)) },
                            placeholder = { Text(stringResource(R.string.module_config_feishu_app_secret_hint)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_user_auth_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = buildFeishuUserAuthStatusLine(userAuthorizationStatus, authUiState),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = buildFeishuUserAuthDetailLine(userAuthorizationStatus, authUiState),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_user_redirect_label),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SelectionContainer {
                            Text(
                                text = redirectUri,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_user_redirect_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    when (val result = FeishuOAuthManager.startAuthorization(context)) {
                                        is FeishuOAuthManager.StartResult.Failure -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        }

                                        is FeishuOAuthManager.StartResult.OpenBrowser -> {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, result.url.toUri()))
                                            } catch (_: Exception) {
                                                val message = "无法打开浏览器，请检查系统是否安装了可用浏览器。"
                                                FeishuOAuthManager.cancelAuthorization(message)
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = feishuAppId.isNotBlank() && feishuAppSecret.isNotBlank() && !isAuthInProgress,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(
                                        when {
                                            isAuthInProgress -> R.string.module_config_feishu_user_auth_in_progress
                                            userAuthorizationStatus.isAuthorized -> R.string.module_config_feishu_user_auth_restart
                                            else -> R.string.module_config_feishu_user_auth_start
                                        }
                                    )
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Feishu Redirect URI", redirectUri))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.copied_to_clipboard),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.module_config_feishu_user_auth_copy_redirect))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                FeishuOAuthManager.resetUiState()
                                clearFeishuUserAuthorization()
                            },
                            enabled = !isAuthInProgress &&
                                    (userAuthorizationStatus.isAuthorized || authUiState.phase != FeishuOAuthManager.Phase.Idle)
                        ) {
                            Text(stringResource(R.string.module_config_feishu_user_auth_clear))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.module_config_feishu_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            renderBacktapSection()
            renderAppStartSection()
            renderVoiceTriggerSection()
            renderSherpaSection()

            val renderNetworkSection: @Composable () -> Unit = {
                ModuleConfigSection(title = stringResource(R.string.module_config_section_network)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_config_network_proxy),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = networkProxy,
                            onValueChange = {
                                networkProxy = it
                                prefs.edit {
                                    putString(ModuleConfigActivity.KEY_NETWORK_PROXY, it.trim())
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.module_config_network_proxy)) },
                            placeholder = { Text(stringResource(R.string.module_config_network_proxy_hint)) },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.module_config_network_proxy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            renderNetworkSection()
            renderFeishuSection()
        }
    }

    LaunchedEffect(initialSection, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            when (initialSection) {
                ModuleConfigActivity.SECTION_APP_START -> scrollState.animateScrollTo(scrollState.maxValue / 6)
                ModuleConfigActivity.SECTION_VOICE_TRIGGER -> scrollState.animateScrollTo((scrollState.maxValue * 2) / 5)
                ModuleConfigActivity.SECTION_SHERPA -> scrollState.animateScrollTo((scrollState.maxValue * 3) / 5)
                ModuleConfigActivity.SECTION_FEISHU -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}

@Composable
fun ModuleConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SherpaSourceButton(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit,
    enabled: Boolean,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { checked ->
            if (checked && !selected) {
                onSelected()
            }
        },
        enabled = enabled,
        shapes = shapes,
        modifier = modifier,
    ) {
        Text(text)
    }
}

@Composable
private fun SherpaModelSpecCard(
    context: Context,
    spec: SherpaNcnnModelSpec,
    installed: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = sherpaDisplayName(context, spec),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = context.getString(R.string.module_config_sherpa_archive_name, spec.archiveName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = context.getString(
                    if (installed) R.string.module_config_sherpa_model_installed else R.string.module_config_sherpa_model_missing
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDownload,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.module_config_sherpa_download_model))
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = installed && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.module_config_sherpa_delete_model))
                }
            }
        }
    }
}

private fun buildFeishuUserAuthStatusLine(
    status: FeishuModuleConfig.UserAuthorizationStatus,
    authUiState: FeishuOAuthManager.AuthUiState
): String {
    return when (authUiState.phase) {
        FeishuOAuthManager.Phase.WaitingForAuthorization -> "授权状态：等待飞书授权"
        FeishuOAuthManager.Phase.ExchangingToken -> "授权状态：正在换取用户令牌"
        FeishuOAuthManager.Phase.Success -> "授权状态：已完成用户认证"
        FeishuOAuthManager.Phase.Failure -> "授权状态：认证未完成"
        FeishuOAuthManager.Phase.Idle -> when {
            status.hasValidAccessToken && status.hasValidRefreshToken -> "授权状态：已认证，可自动续期"
            status.hasValidAccessToken -> "授权状态：已认证"
            status.hasValidRefreshToken -> "授权状态：已认证，等待自动刷新"
            else -> "授权状态：未认证"
        }
    }
}

private fun buildFeishuUserAuthDetailLine(
    status: FeishuModuleConfig.UserAuthorizationStatus,
    authUiState: FeishuOAuthManager.AuthUiState
): String {
    val persistentDetail = when {
        status.hasValidAccessToken && status.hasValidRefreshToken -> {
            "user_access_token 有效至 ${formatFeishuTime(status.accessTokenExpiresAtMillis)}；refresh_token 有效至 ${formatFeishuTime(status.refreshTokenExpiresAtMillis)}。"
        }

        status.hasValidAccessToken -> {
            "user_access_token 有效至 ${formatFeishuTime(status.accessTokenExpiresAtMillis)}。当前没有可用 refresh_token，到期后需要重新认证。"
        }

        status.hasValidRefreshToken -> {
            "当前 access_token 已过期，但 refresh_token 有效至 ${formatFeishuTime(status.refreshTokenExpiresAtMillis)}，模块执行时会自动刷新。"
        }

        else -> {
            "点击下方按钮后会跳转到飞书授权页，完成后会自动缓存 user_access_token。"
        }
    }

    return when (authUiState.phase) {
        FeishuOAuthManager.Phase.WaitingForAuthorization,
        FeishuOAuthManager.Phase.ExchangingToken,
        FeishuOAuthManager.Phase.Failure -> authUiState.message.ifBlank { persistentDetail }

        FeishuOAuthManager.Phase.Success -> {
            val authMessage = authUiState.message.ifBlank { persistentDetail }
            if (status.isAuthorized) {
                "$authMessage 当前状态：$persistentDetail"
            } else {
                authMessage
            }
        }

        FeishuOAuthManager.Phase.Idle -> persistentDetail
    }
}

private fun formatFeishuTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) {
        return "未知"
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMillis))
}

private fun sherpaDownloadSourceLabel(context: Context, source: SherpaNcnnDownloadSource): String {
    return when (source) {
        SherpaNcnnDownloadSource.DIRECT -> context.getString(R.string.module_config_sherpa_source_direct)
        SherpaNcnnDownloadSource.GHPROXY_NET -> context.getString(R.string.module_config_sherpa_source_ghproxy_net)
        SherpaNcnnDownloadSource.GH_PROXY_COM -> context.getString(R.string.module_config_sherpa_source_gh_proxy_com)
    }
}

private fun sherpaDisplayName(context: Context, spec: SherpaNcnnModelSpec): String {
    return when (spec) {
        SherpaNcnnModelManager.SMALL_BILINGUAL_MODEL -> context.getString(R.string.module_config_sherpa_model_bilingual)
        SherpaNcnnModelManager.ENGLISH_MODEL -> context.getString(R.string.module_config_sherpa_model_english)
        else -> spec.archiveName
    }
}

private fun formatSherpaModuleConfigProgress(
    context: Context,
    progress: SherpaNcnnModelProgress,
): String {
    return when (progress.stage) {
        SherpaNcnnModelStage.DOWNLOADING -> formatSherpaTransferStatus(
            context,
            progress,
            R.string.module_config_sherpa_status_downloading,
            R.string.module_config_sherpa_status_downloading_progress,
        )
        SherpaNcnnModelStage.IMPORTING -> formatSherpaTransferStatus(
            context,
            progress,
            R.string.module_config_sherpa_status_importing,
            R.string.module_config_sherpa_status_importing_progress,
        )
        SherpaNcnnModelStage.VERIFYING -> context.getString(R.string.module_config_sherpa_status_verifying)
        SherpaNcnnModelStage.EXTRACTING -> context.getString(R.string.module_config_sherpa_status_extracting)
    }
}

private fun formatSherpaTransferStatus(
    context: Context,
    progress: SherpaNcnnModelProgress,
    indeterminateRes: Int,
    determinateRes: Int,
): String {
    val totalBytes = progress.totalBytes
    if (totalBytes == null || totalBytes <= 0L) {
        return context.getString(
            indeterminateRes,
            Formatter.formatShortFileSize(context, progress.downloadedBytes)
        )
    }
    val percent = ((progress.downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    return context.getString(
        determinateRes,
        percent,
        Formatter.formatShortFileSize(context, progress.downloadedBytes),
        Formatter.formatShortFileSize(context, totalBytes),
    )
}

private fun formatVoiceTriggerModelProgress(
    context: Context,
    progress: VoiceTriggerModelProgress,
): String {
    val totalBytes = progress.totalBytes
    if (totalBytes == null || totalBytes <= 0L) {
        return context.getString(
            R.string.module_config_voice_trigger_status_downloading,
            Formatter.formatShortFileSize(context, progress.downloadedBytes)
        )
    }
    val percent = ((progress.downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    return context.getString(
        R.string.module_config_voice_trigger_status_downloading_progress,
        percent,
        Formatter.formatShortFileSize(context, progress.downloadedBytes),
        Formatter.formatShortFileSize(context, totalBytes),
    )
}
