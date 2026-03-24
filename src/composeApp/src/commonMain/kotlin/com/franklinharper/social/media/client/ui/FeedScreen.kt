package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.VisibleFeedEmptyState
import com.franklinharper.social.media.client.domain.FeedSource

@Composable
fun FeedScreen(
    state: FeedShellUiState,
    isWideLayout: Boolean = false,
    onAddSourcesClick: () -> Unit = {},
    onSelectSource: (FeedSource?) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    Text("Feed")

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
                            )
                        }

                        null -> {
                            FeedItemList(
                                items = state.visibleItems.sortedBy { it.publishedAtEpochMillis },
                                isWideLayout = isWideLayout,
                            )
                        }
                    }
                }
            }
        }
    }
}
