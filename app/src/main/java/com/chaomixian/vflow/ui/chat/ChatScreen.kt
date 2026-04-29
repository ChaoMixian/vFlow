package com.chaomixian.vflow.ui.chat

import android.app.Activity
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaomixian.vflow.R
import com.chaomixian.vflow.permissions.PermissionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val MAX_RECENT_PHOTOS = 18
private const val MAX_PICKED_PHOTOS = 10

private val prettyToolArgumentsJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private data class RecentPhotoItem(
    val uri: Uri,
    val aspectRatio: Float,
)

private data class ChatSuggestion(
    val titleRes: Int,
    val promptRes: Int,
)

private enum class ChatSheetSegmentPosition {
    Top,
    Bottom,
    Single,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    newConversationVersion: Int = 0,
    chatViewModel: ChatViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val chatUiState by chatViewModel.uiState.collectAsState()
    val activeConversation = remember(chatUiState.activeConversationId, chatUiState.conversations) {
        chatUiState.conversations.firstOrNull { it.id == chatUiState.activeConversationId }
    }
    val availableToolsByName = remember(chatUiState.availableTools) {
        chatUiState.availableTools.associateBy { it.name }
    }
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = imeBottom > navBottom
    val imeExtraPadding = with(density) {
        (imeBottom - navBottom).coerceAtLeast(0).toDp()
    }
    val messageListBottomPadding by animateDpAsState(
        targetValue = contentPadding.calculateBottomPadding() + 132.dp + imeExtraPadding,
        label = "chatMessageListBottomPadding"
    )
    val composerBottomPadding by animateDpAsState(
        targetValue = if (imeVisible) 14.dp else contentPadding.calculateBottomPadding() + 14.dp,
        label = "chatComposerBottomPadding"
    )
    val snackbarBottomPadding by animateDpAsState(
        targetValue = if (imeVisible) 24.dp else contentPadding.calculateBottomPadding() + 104.dp,
        label = "chatSnackbarBottomPadding"
    )
    val showJumpToBottom by remember(activeConversation?.id, activeConversation?.messages?.size) {
        derivedStateOf {
            activeConversation?.messages?.isNotEmpty() == true && listState.canScrollForward
        }
    }
    var prompt by rememberSaveable { mutableStateOf("") }
    var attachmentSheetVisible by rememberSaveable { mutableStateOf(false) }
    var benchmarkSheetVisible by rememberSaveable { mutableStateOf(false) }
    var hasPhotoPermission by remember { mutableStateOf(hasPhotoPermission(context)) }
    var recentPhotos by remember { mutableStateOf(emptyList<RecentPhotoItem>()) }
    var recentPhotosLoading by remember { mutableStateOf(false) }
    var recentPhotoReloadVersion by remember { mutableIntStateOf(0) }
    val selectedPhotoUris = remember { mutableStateListOf<Uri>() }
    var capturedPreview by remember { mutableStateOf<Bitmap?>(null) }
    var webSearchSelected by rememberSaveable { mutableStateOf(false) }
    var benchmarkDeleteTarget by remember { mutableStateOf<ChatBenchmarkRun?>(null) }
    val hasPendingToolApproval = remember(activeConversation?.messages) {
        activeConversation?.messages?.any { message ->
            message.role == ChatMessageRole.ASSISTANT &&
                message.toolApprovalState == ChatToolApprovalState.PENDING
        } == true
    }
    val isAgentActive = chatUiState.isAgentRunning ||
        chatUiState.isSending ||
        hasPendingToolApproval ||
        chatUiState.pendingPermissionRequest != null
    val shouldShowStopButton = isAgentActive && prompt.isBlank()
    val canUseComposerAction = shouldShowStopButton || prompt.isNotBlank()
    val welcomeSuggestions = remember {
        listOf(
            ChatSuggestion(
                titleRes = R.string.chat_suggestion_build_workflow,
                promptRes = R.string.chat_suggestion_build_workflow_prompt,
            ),
            ChatSuggestion(
                titleRes = R.string.chat_suggestion_pick_modules,
                promptRes = R.string.chat_suggestion_pick_modules_prompt,
            ),
            ChatSuggestion(
                titleRes = R.string.chat_suggestion_debug_workflow,
                promptRes = R.string.chat_suggestion_debug_workflow_prompt,
            ),
            ChatSuggestion(
                titleRes = R.string.chat_suggestion_refine_steps,
                promptRes = R.string.chat_suggestion_refine_steps_prompt,
            ),
        )
    }
    val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val pickPhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotoUris.clear()
            selectedPhotoUris.addAll(uris)
            capturedPreview = null
        }
    }
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        capturedPreview = bitmap
    }
    val requestPhotoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPhotoPermission = granted
        if (granted) {
            recentPhotoReloadVersion++
        }
    }
    val toolPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        chatViewModel.onToolPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(chatViewModel) {
        chatViewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(chatUiState.activeConversationId) {
        prompt = ""
        selectedPhotoUris.clear()
        capturedPreview = null
        webSearchSelected = false
    }

    LaunchedEffect(activeConversation?.messages?.size) {
        val lastIndex = activeConversation?.messages?.lastIndex ?: -1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex + 1)
        }
    }

    LaunchedEffect(attachmentSheetVisible) {
        if (attachmentSheetVisible) {
            hasPhotoPermission = hasPhotoPermission(context)
            if (hasPhotoPermission) {
                recentPhotoReloadVersion++
            }
        }
    }

    LaunchedEffect(attachmentSheetVisible, hasPhotoPermission, recentPhotoReloadVersion) {
        if (!attachmentSheetVisible || !hasPhotoPermission) return@LaunchedEffect
        recentPhotosLoading = true
        recentPhotos = withContext(Dispatchers.IO) { queryRecentPhotos(context) }
        recentPhotosLoading = false
    }

    LaunchedEffect(newConversationVersion) {
        prompt = ""
        selectedPhotoUris.clear()
        capturedPreview = null
        webSearchSelected = false
    }

    LaunchedEffect(chatUiState.pendingPermissionRequest?.requestId) {
        val request = chatUiState.pendingPermissionRequest ?: return@LaunchedEffect
        chatViewModel.markPermissionRequestLaunched()
        val intent = Intent(context, PermissionActivity::class.java).apply {
            putParcelableArrayListExtra(
                PermissionActivity.EXTRA_PERMISSIONS,
                ArrayList(request.permissions)
            )
            putExtra(
                PermissionActivity.EXTRA_WORKFLOW_NAME,
                context.getString(R.string.chat_tool_permission_workflow_name)
            )
        }
        toolPermissionLauncher.launch(intent)
    }

    val showWelcome = (activeConversation == null || activeConversation.messages.isEmpty()) && prompt.isBlank()
    val activePreset = remember(activeConversation?.presetId, chatUiState.presets, chatUiState.defaultPresetId) {
        val preferredId = activeConversation?.presetId ?: chatUiState.defaultPresetId
        chatUiState.presets.firstOrNull { it.id == preferredId } ?: chatUiState.presets.firstOrNull()
    }

    LaunchedEffect(benchmarkSheetVisible, activePreset?.id, chatUiState.isBenchmarkRunning) {
        if (benchmarkSheetVisible) {
            chatViewModel.refreshBenchmarkPreflight()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = messageListBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (activeConversation != null && activeConversation.messages.isNotEmpty()) {
                items(
                    items = activeConversation.messages,
                    key = { it.id },
                ) { message ->
                    ChatMessageBubble(
                        message = message,
                        availableToolsByName = availableToolsByName,
                        modifier = Modifier.fillMaxWidth(),
                        onApproveToolCalls = chatViewModel::approveToolCalls,
                        onRejectToolCalls = chatViewModel::rejectToolCalls,
                        onRerunToolCalls = chatViewModel::rerunToolCalls,
                        actionsEnabled = !chatUiState.isSending,
                        onCopyMessage = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("chat_message", message.content))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.copied_to_clipboard)
                                )
                            }
                        },
                        canSaveWorkflow = chatViewModel.canSaveTemporaryWorkflow(message.id),
                        onSaveWorkflow = { chatViewModel.saveTemporaryWorkflow(message.id) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showWelcome,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = contentPadding.calculateTopPadding() + 70.dp,
                )
                .padding(bottom = contentPadding.calculateBottomPadding() + 96.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ChatWelcomeState(
                suggestions = welcomeSuggestions,
                onSuggestionClick = { suggestion ->
                    prompt = context.getString(suggestion.promptRes)
                }
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = composerBottomPadding),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.chat_prompt_hint))
                },
                leadingIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 4.dp),
                        onClick = {
                            attachmentSheetVisible = true
                            if (!hasPhotoPermission(context)) {
                                requestPhotoPermissionLauncher.launch(photoPermissionName())
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.chat_more),
                        )
                    }
                },
                trailingIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = {
                            if (shouldShowStopButton) {
                                chatViewModel.stopAgent()
                                return@FilledIconButton
                            }
                            if (selectedPhotoUris.isNotEmpty() || capturedPreview != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.chat_attachment_not_supported)
                                    )
                                }
                            }
                            if (chatViewModel.sendMessage(prompt)) {
                                prompt = ""
                                selectedPhotoUris.clear()
                                capturedPreview = null
                                webSearchSelected = false
                            }
                        },
                        enabled = canUseComposerAction,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = when {
                                shouldShowStopButton -> MaterialTheme.colorScheme.error
                                prompt.isNotBlank() -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = when {
                                shouldShowStopButton -> MaterialTheme.colorScheme.onError
                                prompt.isNotBlank() -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                    ) {
                        Icon(
                            imageVector = if (shouldShowStopButton) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(
                                if (shouldShowStopButton) R.string.chat_stop else R.string.chat_send
                            ),
                        )
                    }
                },
                singleLine = false,
                maxLines = 6,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (shouldShowStopButton) {
                            chatViewModel.stopAgent()
                            return@KeyboardActions
                        }
                        if (chatViewModel.sendMessage(prompt)) {
                            prompt = ""
                            selectedPhotoUris.clear()
                            capturedPreview = null
                            webSearchSelected = false
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomPadding)
        )

        AnimatedVisibility(
            visible = showJumpToBottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(bottom = composerBottomPadding + 86.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FilledTonalIconButton(
                onClick = {
                    val lastIndex = activeConversation?.messages?.lastIndex ?: return@FilledTonalIconButton
                    scope.launch { listState.animateScrollToItem(lastIndex) }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.chat_scroll_to_bottom),
                )
            }
        }
    }

    if (attachmentSheetVisible) {
        val selectedCount = selectedPhotoUris.size + if (capturedPreview != null) 1 else 0
        ModalBottomSheet(
            onDismissRequest = { attachmentSheetVisible = false },
            sheetState = attachmentSheetState,
        ) {
            ChatAttachmentSheet(
                recentPhotos = recentPhotos,
                recentPhotosLoading = recentPhotosLoading,
                hasPhotoPermission = hasPhotoPermission,
                selectedPhotoUris = selectedPhotoUris,
                hasCapturedPreview = capturedPreview != null,
                onRequestPermission = {
                    requestPhotoPermissionLauncher.launch(photoPermissionName())
                },
                onCameraClick = {
                    cameraPreviewLauncher.launch(null)
                },
                onPhotoToggle = { uri ->
                    if (selectedPhotoUris.contains(uri)) {
                        selectedPhotoUris.remove(uri)
                    } else {
                        selectedPhotoUris.add(uri)
                    }
                },
                onAddPhotos = {
                    attachmentSheetVisible = false
                },
                selectedCount = selectedCount,
                webSearchSelected = webSearchSelected,
                onWebSearchToggle = { webSearchSelected = !webSearchSelected },
                autoApprovalScope = chatUiState.autoApprovalScope,
                onAutoApprovalScopeChange = chatViewModel::setAutoApprovalScope,
                onBenchmarkClick = {
                    attachmentSheetVisible = false
                    benchmarkSheetVisible = true
                },
                onPickAllPhotos = {
                    pickPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    if (benchmarkSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { benchmarkSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ChatBenchmarkSheet(
                benchmarkUi = chatUiState.benchmarkUi,
                preset = activePreset,
                isRunning = chatUiState.isBenchmarkRunning,
                onStartBenchmark = chatViewModel::startBenchmarkRun,
                onExportRun = { run ->
                    chatViewModel.exportBenchmarkRun(run)?.let(context::startActivity)
                },
                onDeleteRun = { run ->
                    benchmarkDeleteTarget = run
                },
            )
        }
    }

    if (benchmarkDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { benchmarkDeleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.chat_benchmark_delete_run_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = benchmarkDeleteTarget ?: return@TextButton
                        chatViewModel.deleteBenchmarkRun(target.id)
                        benchmarkDeleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { benchmarkDeleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ChatBenchmarkSheet(
    benchmarkUi: ChatBenchmarkUiState,
    preset: ChatPresetConfig?,
    isRunning: Boolean,
    onStartBenchmark: () -> Unit,
    onExportRun: (ChatBenchmarkRun) -> Unit,
    onDeleteRun: (ChatBenchmarkRun) -> Unit,
) {
    val scrollState = rememberScrollState()
    val activeRun = benchmarkUi.activeRun
    val totalCases = benchmarkUi.suite.cases.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_benchmark_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.chat_benchmark_panel_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_benchmark_selected_preset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = preset?.name?.ifBlank { preset.model } ?: stringResource(R.string.chat_model_unconfigured),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${benchmarkUi.suite.scenes.size} scenes · $totalCases cases",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onStartBenchmark,
                    enabled = benchmarkUi.preflight?.isReady == true && !isRunning,
                ) {
                    Text(
                        text = if (isRunning) {
                            stringResource(R.string.chat_benchmark_running)
                        } else {
                            stringResource(R.string.chat_benchmark_start)
                        }
                    )
                }
                if (activeRun != null) {
                    Text(
                        text = stringResource(
                            R.string.chat_benchmark_progress_value,
                            activeRun.caseResults.size,
                            totalCases,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_benchmark_preflight_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                benchmarkUi.preflight?.checks?.forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.title,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = check.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (check.status == ChatBenchmarkCheckStatus.PASS) "OK" else "Blocked",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (check.status == ChatBenchmarkCheckStatus.PASS) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                } ?: Text(
                    text = stringResource(R.string.chat_benchmark_preflight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (benchmarkUi.recentRuns.isNotEmpty()) {
            Text(
                text = stringResource(R.string.chat_benchmark_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            benchmarkUi.recentRuns.forEach { run ->
                ChatBenchmarkRunCard(
                    run = run,
                    onExportRun = onExportRun,
                    onDeleteRun = onDeleteRun,
                )
            }
        }
    }
}

@Composable
private fun ChatBenchmarkRunCard(
    run: ChatBenchmarkRun,
    onExportRun: (ChatBenchmarkRun) -> Unit,
    onDeleteRun: (ChatBenchmarkRun) -> Unit,
) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.presetName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${run.provider} · ${run.modelName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = run.score?.overall?.let { "$it" } ?: run.status.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = when (run.status) {
                        ChatBenchmarkRunStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        ChatBenchmarkRunStatus.BLOCKED -> MaterialTheme.colorScheme.error
                        ChatBenchmarkRunStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        ChatBenchmarkRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatChatTime(run.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                run.score?.let { score ->
                    Text(
                        text = "E2E ${score.endToEnd} · R&S ${score.robustnessSafety}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    run.preflight?.checks?.takeIf { run.caseResults.isEmpty() }?.forEach { check ->
                        Text(
                            text = "${check.title}: ${check.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    run.caseResults.forEach { result ->
                        HorizontalDivider()
                        Text(
                            text = "${result.title} · ${result.outcome.name} · ${result.score}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    Text(
                        text = result.failureReason ?: result.finalAssistantMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                        if (result.trace.toolExecutions.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_benchmark_trace_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            result.trace.toolExecutions.forEach { execution ->
                                Text(
                                    text = "${execution.sequence}. ${execution.toolName} · ${execution.status ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { onExportRun(run) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.chat_benchmark_export_logs))
                    }
                    TextButton(
                        onClick = { onDeleteRun(run) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.chat_benchmark_delete_run))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatWelcomeState(
    suggestions: List<ChatSuggestion>,
    onSuggestionClick: (ChatSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_welcome_eyebrow),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.chat_welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
                lineHeight = MaterialTheme.typography.headlineLarge.lineHeight,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                    onClick = { onSuggestionClick(suggestion) },
                ) {
                    Text(
                        text = stringResource(suggestion.titleRes),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    availableToolsByName: Map<String, ChatAgentToolDefinition>,
    modifier: Modifier = Modifier,
    onApproveToolCalls: (String) -> Unit,
    onRejectToolCalls: (String) -> Unit,
    onRerunToolCalls: (String) -> Unit,
    actionsEnabled: Boolean,
    onCopyMessage: () -> Unit,
    canSaveWorkflow: Boolean = false,
    onSaveWorkflow: () -> Unit = {},
) {
    when (message.role) {
        ChatMessageRole.USER -> UserMessageBubble(
            message = message,
            modifier = modifier,
            onCopyMessage = onCopyMessage,
        )

        ChatMessageRole.ASSISTANT -> AssistantMessageCard(
            message = message,
            availableToolsByName = availableToolsByName,
            modifier = modifier,
            onApproveToolCalls = onApproveToolCalls,
            onRejectToolCalls = onRejectToolCalls,
            onRerunToolCalls = onRerunToolCalls,
            actionsEnabled = actionsEnabled,
            onCopyMessage = onCopyMessage,
            canSaveWorkflow = canSaveWorkflow,
            onSaveWorkflow = onSaveWorkflow,
        )

        ChatMessageRole.TOOL -> ToolMessageCard(
            message = message,
            availableToolsByName = availableToolsByName,
            modifier = modifier,
            onCopyMessage = onCopyMessage,
        )

        ChatMessageRole.ERROR -> ErrorMessageCard(
            message = message,
            modifier = modifier,
            onCopyMessage = onCopyMessage,
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onCopyMessage: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(R.string.chat_role_you),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 10.dp,
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    MessageFooterRow(
                        timestampMillis = message.timestampMillis,
                        tokenCount = message.tokenCount,
                        contentColor = contentColor,
                        onCopyMessage = onCopyMessage,
                        showCopy = message.content.isNotBlank(),
                        containerTint = contentColor.copy(alpha = 0.14f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageCard(
    message: ChatMessage,
    availableToolsByName: Map<String, ChatAgentToolDefinition>,
    modifier: Modifier = Modifier,
    onApproveToolCalls: (String) -> Unit,
    onRejectToolCalls: (String) -> Unit,
    onRerunToolCalls: (String) -> Unit,
    actionsEnabled: Boolean,
    onCopyMessage: () -> Unit,
    canSaveWorkflow: Boolean = false,
    onSaveWorkflow: () -> Unit = {},
) {
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val hasReasoning = !message.reasoningContent.isNullOrBlank()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 0.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_role_assistant),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (!message.isPending && message.content.isNotBlank()) {
                        FilledTonalIconButton(
                            onClick = onCopyMessage,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.common_copy),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                if (message.isPending) {
                    Text(
                        text = stringResource(R.string.chat_generating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (hasReasoning) {
                        Surface(
                            onClick = { reasoningExpanded = !reasoningExpanded },
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (reasoningExpanded) {
                                        stringResource(R.string.chat_reasoning_hide)
                                    } else {
                                        stringResource(R.string.chat_reasoning_show)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = if (reasoningExpanded) {
                                        Icons.Rounded.ExpandLess
                                    } else {
                                        Icons.Rounded.ExpandMore
                                    },
                                    contentDescription = null,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = reasoningExpanded,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(24.dp),
                                tonalElevation = 0.dp,
                            ) {
                                ChatMarkdownContent(
                                    markdown = message.reasoningContent.orEmpty().trim(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        ChatMarkdownContent(
                            markdown = message.content,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (message.toolCalls.isNotEmpty()) {
                        AssistantToolProposalSection(
                            message = message,
                            availableToolsByName = availableToolsByName,
                            actionsEnabled = actionsEnabled,
                            onApprove = { onApproveToolCalls(message.id) },
                            onReject = { onRejectToolCalls(message.id) },
                            onRerun = { onRerunToolCalls(message.id) },
                            canSaveWorkflow = canSaveWorkflow,
                            onSaveWorkflow = onSaveWorkflow,
                        )
                    }
                }

                MessageFooterRow(
                    timestampMillis = message.timestampMillis,
                    tokenCount = message.tokenCount,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onCopyMessage = onCopyMessage,
                    showCopy = false,
                    containerTint = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun AssistantToolProposalSection(
    message: ChatMessage,
    availableToolsByName: Map<String, ChatAgentToolDefinition>,
    actionsEnabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRerun: () -> Unit,
    canSaveWorkflow: Boolean = false,
    onSaveWorkflow: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_tool_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ToolApprovalBadge(state = message.toolApprovalState)
            }

            message.toolCalls.forEach { toolCall ->
                val tool = availableToolsByName[toolCall.name]
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = tool?.title ?: toolCall.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = tool?.description ?: toolCall.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = prettyFormatToolArguments(toolCall.argumentsJson),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val permissionNames = tool?.permissionNames.orEmpty()
                        if (permissionNames.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.chat_tool_permissions_value,
                                    permissionNames.joinToString()
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (message.toolApprovalState == ChatToolApprovalState.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onReject,
                        enabled = actionsEnabled,
                    ) {
                        Text(text = stringResource(R.string.chat_tool_reject))
                    }
                    FilledTonalButton(
                        onClick = onApprove,
                        enabled = actionsEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.widthIn(min = 8.dp))
                        Text(text = stringResource(R.string.chat_tool_approve))
                    }
                }
            } else if (message.toolApprovalState in setOf(
                    ChatToolApprovalState.APPROVED,
                    ChatToolApprovalState.REJECTED,
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canSaveWorkflow) {
                        TextButton(
                            onClick = onSaveWorkflow,
                            enabled = actionsEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.widthIn(min = 4.dp))
                            Text(text = stringResource(R.string.chat_tool_save_workflow))
                        }
                    }
                    FilledTonalButton(
                        onClick = onRerun,
                        enabled = actionsEnabled,
                    ) {
                        Text(text = stringResource(R.string.chat_tool_rerun))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolApprovalBadge(state: ChatToolApprovalState?) {
    val label = when (state) {
        ChatToolApprovalState.PENDING -> stringResource(R.string.chat_tool_pending)
        ChatToolApprovalState.RUNNING -> stringResource(R.string.chat_tool_running)
        ChatToolApprovalState.APPROVED -> stringResource(R.string.chat_tool_approved)
        ChatToolApprovalState.REJECTED -> stringResource(R.string.chat_tool_rejected)
        null -> return
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ToolMessageCard(
    message: ChatMessage,
    availableToolsByName: Map<String, ChatAgentToolDefinition>,
    modifier: Modifier = Modifier,
    onCopyMessage: () -> Unit,
) {
    val toolResult = message.toolResult
    val containerColor = when (toolResult?.status) {
        ChatToolResultStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatToolResultStatus.ERROR -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatToolResultStatus.REJECTED,
        ChatToolResultStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.surfaceContainerHigh
        null -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (toolResult?.status) {
        ChatToolResultStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        ChatToolResultStatus.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        ChatToolResultStatus.REJECTED,
        ChatToolResultStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.onSecondaryContainer
        null -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (toolResult?.status) {
        ChatToolResultStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        ChatToolResultStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
        ChatToolResultStatus.REJECTED,
        ChatToolResultStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.26f)
        null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val toolTitle = toolResult?.let { result ->
        availableToolsByName[result.name]?.title ?: result.summary
    }.orEmpty()

    val lineCount = remember(message.content) {
        message.content.count { it == '\n' }.let { if (it == 0) 1 else it + 1 }
    }
    val isLongContent = lineCount > 6
    var contentCollapsed by rememberSaveable(message.id) { mutableStateOf(isLongContent) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 540.dp),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_role_tool),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        if (toolTitle.isNotBlank()) {
                            Text(
                                text = toolTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.85f),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onCopyMessage,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = contentColor.copy(alpha = 0.12f),
                            contentColor = contentColor,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    ChatMarkdownContent(
                        markdown = message.content,
                        contentColor = contentColor,
                        modifier = if (contentCollapsed) {
                            Modifier
                                .heightIn(max = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        },
                    )
                    if (contentCollapsed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            containerColor,
                                        )
                                    )
                                )
                        )
                    }
                }
                if (isLongContent) {
                    Row(
                        modifier = Modifier
                            .offset(y = (-2).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { contentCollapsed = !contentCollapsed }
                            .padding(horizontal = 2.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (contentCollapsed) R.string.chat_tool_result_expand
                                else R.string.chat_tool_result_collapse
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = if (contentCollapsed) Icons.Rounded.ExpandMore
                            else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                MessageFooterRow(
                    timestampMillis = message.timestampMillis,
                    tokenCount = null,
                    contentColor = contentColor.copy(alpha = 0.9f),
                    onCopyMessage = onCopyMessage,
                    showCopy = false,
                    containerTint = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun ErrorMessageCard(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onCopyMessage: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_role_error),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                ChatMarkdownContent(
                    markdown = message.content,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                MessageFooterRow(
                    timestampMillis = message.timestampMillis,
                    tokenCount = null,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    onCopyMessage = onCopyMessage,
                    showCopy = message.content.isNotBlank(),
                    containerTint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun MessageFooterRow(
    timestampMillis: Long,
    tokenCount: Int?,
    contentColor: Color,
    onCopyMessage: () -> Unit,
    showCopy: Boolean,
    containerTint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatChatTime(timestampMillis),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.9f),
        )
        if (tokenCount != null) {
            Text(
                text = stringResource(R.string.chat_tokens_value, tokenCount),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showCopy) {
            FilledTonalIconButton(
                onClick = onCopyMessage,
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = containerTint,
                    contentColor = contentColor.copy(alpha = 0.9f),
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.common_copy),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatConversationItem(
    conversation: ChatConversation,
    selected: Boolean,
    presetName: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conversation.messages.lastOrNull()?.content
                    ?.replace('\n', ' ')
                    ?.trim()
                    .orEmpty()
                    .ifBlank { stringResource(R.string.chat_side_sheet_empty_preview) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presetName ?: stringResource(R.string.chat_model_unconfigured),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatChatTime(conversation.updatedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatChatTime(timestampMillis: Long): String {
    return DateFormat.format("HH:mm", timestampMillis).toString()
}

private fun prettyFormatToolArguments(argumentsJson: String): String {
    if (argumentsJson.isBlank()) return "{}"
    return runCatching {
        prettyToolArgumentsJson.parseToJsonElement(argumentsJson).toString()
    }.getOrDefault(argumentsJson)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatAttachmentSheet(
    recentPhotos: List<RecentPhotoItem>,
    recentPhotosLoading: Boolean,
    hasPhotoPermission: Boolean,
    selectedPhotoUris: List<Uri>,
    hasCapturedPreview: Boolean,
    onRequestPermission: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotoToggle: (Uri) -> Unit,
    onAddPhotos: () -> Unit,
    selectedCount: Int,
    webSearchSelected: Boolean,
    onWebSearchToggle: () -> Unit,
    autoApprovalScope: ChatToolAutoApprovalScope,
    onAutoApprovalScopeChange: (ChatToolAutoApprovalScope) -> Unit,
    onBenchmarkClick: () -> Unit,
    onPickAllPhotos: () -> Unit,
) {
    val carouselItemCount = if (hasPhotoPermission) recentPhotos.size + 1 else 1
    val carouselState = rememberCarouselState { carouselItemCount }
    val scrollState = rememberScrollState()
    val animationScope = rememberCoroutineScope()
    val autoApprovalScopeLabel = when (autoApprovalScope) {
        ChatToolAutoApprovalScope.OFF -> stringResource(R.string.chat_auto_approve_scope_off)
        ChatToolAutoApprovalScope.READ_ONLY -> stringResource(R.string.chat_auto_approve_scope_read_only)
        ChatToolAutoApprovalScope.LOW_RISK -> stringResource(R.string.chat_auto_approve_scope_low_risk)
        ChatToolAutoApprovalScope.STANDARD -> stringResource(R.string.chat_auto_approve_scope_standard)
        ChatToolAutoApprovalScope.ALL -> stringResource(R.string.chat_auto_approve_scope_all)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = if (selectedCount > 0) 112.dp else 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickAllPhotos) {
                    Text(stringResource(R.string.chat_all_photos))
                }
            }

            HorizontalMultiBrowseCarousel(
                state = carouselState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
                preferredItemWidth = 96.dp,
                itemSpacing = 8.dp,
                minSmallItemWidth = 38.dp,
                maxSmallItemWidth = 56.dp,
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) { index ->
                val isFocused = carouselState.currentItem == index
                if (index == 0) {
                    CameraCarouselItem(
                        selected = hasCapturedPreview,
                        focused = isFocused,
                        onClick = {
                            animationScope.launch { carouselState.animateScrollToItem(index) }
                            onCameraClick()
                        },
                    )
                } else {
                    val photo = recentPhotos[index - 1]
                    PhotoCarouselItem(
                        photo = photo,
                        selected = selectedPhotoUris.contains(photo.uri),
                        focused = isFocused,
                        onClick = {
                            animationScope.launch { carouselState.animateScrollToItem(index) }
                            onPhotoToggle(photo.uri)
                        },
                    )
                }
            }

            when {
                !hasPhotoPermission -> {
                    TextButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(stringResource(R.string.chat_allow_photo_access))
                    }
                }

                recentPhotosLoading -> {
                    Text(
                        text = stringResource(R.string.chat_loading_photos),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ChatAttachmentSheetSegmentedRow(
                title = stringResource(R.string.chat_web_search_title),
                subtitle = stringResource(R.string.chat_web_search_description),
                icon = Icons.Rounded.Language,
                selected = webSearchSelected,
                position = ChatSheetSegmentPosition.Top,
                onClick = onWebSearchToggle,
                trailingContent = {
                    AnimatedVisibility(visible = webSearchSelected, enter = fadeIn(), exit = fadeOut()) {
                        FilledTonalIconButton(
                            onClick = onWebSearchToggle,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            ChatAttachmentSheetSegmentedRow(
                title = stringResource(R.string.chat_benchmark_title),
                subtitle = stringResource(R.string.chat_benchmark_description),
                icon = Icons.Rounded.AutoAwesome,
                selected = false,
                position = ChatSheetSegmentPosition.Single,
                onClick = onBenchmarkClick,
                trailingContent = {
                    Text(
                        text = stringResource(R.string.chat_benchmark_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            ChatAttachmentSheetSegmentedRow(
                title = stringResource(R.string.chat_auto_approve_title),
                subtitle = stringResource(R.string.chat_auto_approve_description),
                icon = Icons.Rounded.AutoAwesome,
                selected = autoApprovalScope != ChatToolAutoApprovalScope.OFF,
                position = ChatSheetSegmentPosition.Bottom,
                onClick = { onAutoApprovalScopeChange(autoApprovalScope.next()) },
                trailingContent = {
                    Text(
                        text = autoApprovalScopeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (autoApprovalScope != ChatToolAutoApprovalScope.OFF) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            )
        }

        if (selectedCount > 0) {
            FilledTonalButton(
                onClick = onAddPhotos,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_add_photos, selectedCount),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatAttachmentSheetSegmentedRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    position: ChatSheetSegmentPosition,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val outerShape = when (position) {
        ChatSheetSegmentPosition.Top -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        ChatSheetSegmentPosition.Bottom -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        ChatSheetSegmentPosition.Single -> RoundedCornerShape(18.dp)
    }
    val innerShape = when (position) {
        ChatSheetSegmentPosition.Top -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
        ChatSheetSegmentPosition.Bottom -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        ChatSheetSegmentPosition.Single -> RoundedCornerShape(14.dp)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = outerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(innerShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
            }

            ListItem(
                modifier = Modifier.fillMaxWidth(),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                },
                supportingContent = subtitle?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            LocalContentColor.current
                        }
                    )
                },
                trailingContent = trailingContent
            )
        }
    }
}

@Composable
private fun CarouselItemScope.CameraCarouselItem(
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val baseModifier = Modifier
        .fillMaxSize()
        .maskClip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable(onClick = onClick)
    Box(
        modifier = if (selected || focused) {
            baseModifier.maskBorder(
                border = BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )
        } else {
            baseModifier
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CarouselItemScope.PhotoCarouselItem(
    photo: RecentPhotoItem,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val baseModifier = Modifier
        .fillMaxSize()
        .maskClip(RoundedCornerShape(24.dp))
        .clickable(onClick = onClick)
    Box(
        modifier = if (selected || focused) {
            baseModifier.maskBorder(
                border = BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )
        } else {
            baseModifier
        }
    ) {
        PhotoThumbnail(
            uri = photo.uri,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        focused -> MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                )
        )
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(720, 720), null)
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            ThumbnailPlaceholder()
        }
    }
}

@Composable
private fun ThumbnailPlaceholder() {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = 2.5.dp.toPx()
        drawLine(
            color = outlineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width * 0.36f, size.height * 0.52f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = outlineColor,
            start = Offset(size.width * 0.36f, size.height * 0.52f),
            end = Offset(size.width * 0.62f, size.height * 0.74f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = outlineColor,
            start = Offset(size.width * 0.62f, size.height * 0.74f),
            end = Offset(size.width, size.height * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = outlineColor,
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.76f, size.height * 0.28f),
            style = Stroke(stroke)
        )
    }
}

private fun hasPhotoPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        photoPermissionName()
    ) == PackageManager.PERMISSION_GRANTED
}

private fun photoPermissionName(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun queryRecentPhotos(context: Context): List<RecentPhotoItem> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    return buildList {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext() && size < MAX_RECENT_PHOTOS) {
                val id = cursor.getLong(idIndex)
                val width = cursor.getInt(widthIndex)
                val height = cursor.getInt(heightIndex)
                val rawAspect = if (width > 0 && height > 0) {
                    width.toFloat() / height.toFloat()
                } else {
                    1f
                }
                add(
                    RecentPhotoItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        aspectRatio = rawAspect.coerceIn(0.78f, 1.18f)
                    )
                )
            }
        }
    }
}
