// 文件: main/java/com/chaomixian/vflow/ui/settings/ModuleConfigActivity.kt
// 描述: 模块配置 Activity，用于配置各个模块的设置参数

package com.chaomixian.vflow.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.integration.feishu.FeishuModuleConfig
import com.chaomixian.vflow.integration.feishu.FeishuOAuthManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.ThemeUtils
import java.text.DateFormat
import java.util.Date

class ModuleConfigActivity : BaseActivity() {

    companion object {
        const val EXTRA_INITIAL_SECTION = "initial_section"
        const val SECTION_BACKTAP = "backtap"
        const val SECTION_FEISHU = "feishu"
        const val PREFS_NAME = "module_config_prefs"
        const val KEY_BACKTAP_SENSITIVITY = "backtap_sensitivity"
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

        fun createIntent(context: Context, initialSection: String? = null): Intent {
            return Intent(context, ModuleConfigActivity::class.java).apply {
                initialSection?.let { putExtra(EXTRA_INITIAL_SECTION, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = ThemeUtils.getAppColorScheme()
            ) {
                ModuleConfigScreen(
                    initialSection = intent?.getStringExtra(EXTRA_INITIAL_SECTION),
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleConfigScreen(initialSection: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
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
    var userAuthStatusVersion by remember { mutableIntStateOf(0) }
    val authUiState by FeishuOAuthManager.authState.collectAsState()
    val userAuthorizationStatus = remember(authUiState, userAuthStatusVersion) {
        FeishuModuleConfig.getUserAuthorizationStatus(context)
    }
    val redirectUri = remember { FeishuOAuthManager.getRedirectUri() }
    val isAuthInProgress = authUiState.phase == FeishuOAuthManager.Phase.WaitingForAuthorization ||
            authUiState.phase == FeishuOAuthManager.Phase.ExchangingToken

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
            renderFeishuSection()
        }
    }

    LaunchedEffect(initialSection, scrollState.maxValue) {
        if (initialSection == ModuleConfigActivity.SECTION_FEISHU && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
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
