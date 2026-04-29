package com.chaomixian.vflow.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaomixian.vflow.ui.common.BaseActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ChatBenchmarkHostSession(
    val sessionToken: Long = 0L,
    val closeToken: Long = 0L,
    val caseId: String = "",
    val variantId: String = "",
    val definition: ChatBenchmarkCaseDefinition? = null,
    val currentRoute: String = "feed",
    val selectedTabId: String? = null,
    val openedContentId: String? = null,
    val openedForbiddenContentId: String? = null,
    val savedContentIds: Set<String> = emptySet(),
    val activeOverlayId: String? = null,
    val visible: Boolean = false,
)

internal object ChatBenchmarkHostStateManager {
    private val sessionState = MutableStateFlow(ChatBenchmarkHostSession())
    private var nextToken = 1L

    fun state() = sessionState.asStateFlow()

    fun configure(caseId: String, variantId: String): ChatBenchmarkHostSession {
        val definition = ChatBenchmarkCatalog.definition(caseId, variantId)
        val session = ChatBenchmarkHostSession(
            sessionToken = nextToken++,
            caseId = caseId,
            variantId = variantId,
            definition = definition,
            currentRoute = "feed",
            selectedTabId = definition?.defaultTabId,
            openedContentId = null,
            openedForbiddenContentId = null,
            savedContentIds = emptySet(),
            activeOverlayId = definition?.launchOverlay?.id,
            visible = false,
        )
        sessionState.value = session
        return session
    }

    fun markVisible(isVisible: Boolean) {
        sessionState.update { it.copy(visible = isVisible) }
    }

    fun requestClose() {
        sessionState.update { it.copy(closeToken = nextToken++) }
    }

    fun selectTab(tabId: String) {
        sessionState.update { session ->
            val definition = session.definition ?: return@update session
            if (session.activeOverlayId != null) return@update session
            if (definition.tabById(tabId) == null) return@update session
            session.copy(
                selectedTabId = tabId,
                currentRoute = "feed",
                openedContentId = null,
            )
        }
    }

    fun dismissOverlay() {
        sessionState.update { session ->
            if (session.activeOverlayId == null) {
                session
            } else {
                session.copy(activeOverlayId = null)
            }
        }
    }

    fun openCard(cardId: String) {
        sessionState.update { session ->
            val definition = session.definition ?: return@update session
            if (session.activeOverlayId != null) return@update session
            val card = definition.tabById(session.selectedTabId)?.cards?.firstOrNull { it.id == cardId }
                ?: definition.cardById(cardId)
                ?: return@update session
            if (!card.isActionable) return@update session
            session.copy(
                currentRoute = "detail",
                openedContentId = card.id,
                openedForbiddenContentId = if (card.isSponsored) card.id else session.openedForbiddenContentId,
            )
        }
    }

    fun toggleSaved(cardId: String) {
        sessionState.update { session ->
            val definition = session.definition ?: return@update session
            if (!definition.saveEnabled || definition.cardById(cardId) == null) return@update session
            val nextSaved = session.savedContentIds.toMutableSet()
            if (!nextSaved.add(cardId)) {
                nextSaved.remove(cardId)
            }
            session.copy(savedContentIds = nextSaved)
        }
    }

    fun navigateBack() {
        sessionState.update { session ->
            if (session.currentRoute == "detail") {
                session.copy(currentRoute = "feed")
            } else {
                session
            }
        }
    }

    fun snapshot(): ChatBenchmarkHostSession = sessionState.value
}

class ChatBenchmarkHostActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val session by ChatBenchmarkHostStateManager.state().collectAsState()
            val definition = session.definition
            LaunchedEffect(session.closeToken) {
                if (session.closeToken > 0L) {
                    finish()
                }
            }
            if (definition == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Benchmark scene unavailable")
                }
            } else {
                ChatBenchmarkHostScreen(
                    session = session,
                    definition = definition,
                    onCardClick = ChatBenchmarkHostStateManager::openCard,
                    onTabSelected = ChatBenchmarkHostStateManager::selectTab,
                    onDismissOverlay = ChatBenchmarkHostStateManager::dismissOverlay,
                    onToggleSaved = ChatBenchmarkHostStateManager::toggleSaved,
                    onNavigateBack = ChatBenchmarkHostStateManager::navigateBack,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ChatBenchmarkHostStateManager.markVisible(true)
    }

    override fun onPause() {
        ChatBenchmarkHostStateManager.markVisible(false)
        super.onPause()
    }

    companion object {
        fun buildIntent(
            context: Context,
            caseId: String,
            variantId: String,
        ): Intent {
            ChatBenchmarkHostStateManager.configure(caseId, variantId)
            return Intent(context, ChatBenchmarkHostActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}

@Composable
private fun ChatBenchmarkHostScreen(
    session: ChatBenchmarkHostSession,
    definition: ChatBenchmarkCaseDefinition,
    onCardClick: (String) -> Unit,
    onTabSelected: (String) -> Unit,
    onDismissOverlay: () -> Unit,
    onToggleSaved: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val background = if (definition.useDarkTheme) Color(0xFF0E141B) else Color(0xFFF5F7FB)
    val surfaceColor = if (definition.useDarkTheme) Color(0xFF16202A) else Color.White
    val contentColor = if (definition.useDarkTheme) Color(0xFFF5F7FB) else Color(0xFF101418)
    val selectedCard = definition.cardById(session.openedContentId)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background,
            contentColor = contentColor,
        ) {
            BackHandler(enabled = session.currentRoute == "detail") {
                onNavigateBack()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (session.currentRoute == "detail" && selectedCard != null) {
                    BenchmarkDetailScreen(
                        session = session,
                        definition = definition,
                        card = selectedCard,
                        surfaceColor = surfaceColor,
                        contentColor = contentColor,
                        onNavigateBack = onNavigateBack,
                        onToggleSaved = onToggleSaved,
                    )
                } else {
                    BenchmarkFeedScreen(
                        session = session,
                        definition = definition,
                        surfaceColor = surfaceColor,
                        contentColor = contentColor,
                        onTabSelected = onTabSelected,
                        onCardClick = onCardClick,
                    )
                }

                if (session.activeOverlayId == definition.launchOverlay?.id && definition.launchOverlay != null) {
                    BenchmarkOverlay(
                        overlay = definition.launchOverlay,
                        onDismiss = onDismissOverlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkFeedScreen(
    session: ChatBenchmarkHostSession,
    definition: ChatBenchmarkCaseDefinition,
    surfaceColor: Color,
    contentColor: Color,
    onTabSelected: (String) -> Unit,
    onCardClick: (String) -> Unit,
) {
    val currentTab = definition.tabById(session.selectedTabId) ?: definition.tabs.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (definition.useDarkTheme) Color(0xFF0E141B) else Color(0xFFF5F7FB)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(definition.accentColor)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = definition.sceneTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Explore  Sections  Saved",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                definition.tabs.forEach { tab ->
                    val selected = tab.id == currentTab.id
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White
                                else Color.White.copy(alpha = 0.18f)
                            )
                            .clickable { onTabSelected(tab.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = tab.label,
                            color = if (selected) definition.accentColor else Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "scene_hint") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = currentTab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor.copy(alpha = 0.72f),
                        )
                        Text(
                            text = definition.caseTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                }
            }

            items(currentTab.cards, key = { it.id }) { card ->
                BenchmarkCard(
                    card = card,
                    surfaceColor = surfaceColor,
                    contentColor = contentColor,
                    saved = session.savedContentIds.contains(card.id),
                    onClick = if (card.isActionable) {
                        { onCardClick(card.id) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun BenchmarkCard(
    card: BenchmarkContentCard,
    surfaceColor: Color,
    contentColor: Color,
    saved: Boolean,
    onClick: (() -> Unit)?,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (card.isSponsored) Color(0xFFD97706) else contentColor.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f),
                )
                if (card.isSponsored) {
                    BenchmarkChip(
                        text = "Sponsored",
                        background = Color(0xFFFFF3E0),
                        contentColor = Color(0xFFB45309),
                    )
                } else if (saved) {
                    BenchmarkChip(
                        text = "Saved",
                        background = Color(0xFFD1FAE5),
                        contentColor = Color(0xFF047857),
                    )
                }
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                text = card.detailBody,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 4,
            )
            if (!card.isActionable) {
                Text(
                    text = "Preview only",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE11D48),
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(24.dp),
            onClick = onClick,
        ) { body() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(24.dp),
        ) { body() }
    }
}

@Composable
private fun BenchmarkDetailScreen(
    session: ChatBenchmarkHostSession,
    definition: ChatBenchmarkCaseDefinition,
    card: BenchmarkContentCard,
    surfaceColor: Color,
    contentColor: Color,
    onNavigateBack: () -> Unit,
    onToggleSaved: (String) -> Unit,
) {
    val isSaved = session.savedContentIds.contains(card.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (definition.useDarkTheme) Color(0xFF0E141B) else Color(0xFFF5F7FB)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(definition.accentColor)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Back",
                modifier = Modifier.clickable(onClick = onNavigateBack),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = definition.tabById(session.selectedTabId)?.label ?: definition.sceneTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    HorizontalDivider()
                    Text(
                        text = card.detailBody,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    Text(
                        text = "Visible summary: ${card.detailBody}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Related controls",
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                    )
                    Text(
                        text = if (definition.saveEnabled) "Like  Share  Save" else "Like  Share  More",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.74f),
                    )
                    if (definition.saveEnabled) {
                        FilledTonalButton(onClick = { onToggleSaved(card.id) }) {
                            Text(if (isSaved) "Saved to list" else "Save clip")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkOverlay(
    overlay: BenchmarkOverlayDefinition,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = overlay.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101418),
                )
                Text(
                    text = overlay.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4B5563),
                )
                FilledTonalButton(onClick = onDismiss) {
                    Text(overlay.dismissLabel)
                }
            }
        }
    }
}

@Composable
private fun BenchmarkChip(
    text: String,
    background: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
