package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.VisibleFeedEmptyState
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import kotlin.time.Clock

@Composable
fun FeedScreen(
    state: FeedShellUiState,
    isWideLayout: Boolean = false,
    onAddSourcesClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSelectSource: (FeedSource?) -> Unit = {},
    onRefresh: () -> Unit = {},
    onShowSeenItems: () -> Unit = {},
    onOpenItem: (FeedItem) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSourcesClick,
                modifier = Modifier.testTag("feed-add-source-fab"),
            ) {
                Text("+")
            }
        },
    ) { innerPadding ->
        BoxWithConstraints {
            val selectedSource = state.sources.firstOrNull { it.sourceId == state.selectedSourceId }
            val sourcePanelWidth = 240.dp
            val contentWidth = if (isWideLayout) {
                (maxWidth - sourcePanelWidth - 16.dp).coerceAtLeast(0.dp)
            } else {
                maxWidth
            }

            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .safeContentPadding()
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isWideLayout && state.sources.isNotEmpty()) {
                    SourcePanel(
                        sources = state.sources,
                        selectedSource = selectedSource,
                        onSelectSource = onSelectSource,
                    )
                }

                Column(
                    modifier = Modifier.width(contentWidth),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Feed")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("feed-refresh-button"),
                            ) {
                                Text("Refresh")
                            }
                            Button(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("feed-sign-out-button"),
                            ) {
                                Text("Sign out")
                            }
                        }
                    }

                    if (!isWideLayout && state.sources.isNotEmpty()) {
                        SourceFilterDropdown(
                            sources = state.sources,
                            selectedSource = selectedSource,
                            onSelectSource = onSelectSource,
                        )
                    }

                    when (val emptyState = state.emptyState) {
                        VisibleFeedEmptyState.NoConfiguredSources -> {
                            EmptyFeedState(
                                title = "No sources yet",
                                message = "Add an RSS feed or social account to start building your feed.",
                                actionLabel = "Add sources",
                                onActionClick = onAddSourcesClick,
                            )
                        }

                        is VisibleFeedEmptyState.NoItemsForSelectedSource -> {
                            EmptyFeedState(
                                title = "No items for this source",
                                message = "Try another source or refresh again later.",
                                actionLabel = if (state.canShowSeenItems) "Show seen items" else null,
                                onActionClick = if (state.canShowSeenItems) onShowSeenItems else null,
                            )
                        }

                        null -> {
                            FeedItemList(
                                items = sortFeedItemsForDisplay(state.visibleItems),
                                isWideLayout = isWideLayout,
                                nowEpochMillis = nowEpochMillis,
                                onOpenItem = onOpenItem,
                                onOpenComments = onOpenComments,
                            )
                            if (state.canShowSeenItems) {
                                Button(
                                    onClick = onShowSeenItems,
                                    modifier = Modifier.testTag("feed-show-seen-button"),
                                ) {
                                    Text("Show seen items")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
