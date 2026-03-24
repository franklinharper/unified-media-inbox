package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.VisibleFeedEmptyState

@Composable
fun FeedScreen(
    state: FeedShellUiState,
    isWideLayout: Boolean = false,
    onAddSourcesClick: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .safeContentPadding()
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Feed")

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
