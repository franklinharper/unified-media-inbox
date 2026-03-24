package com.franklinharper.social.media.client

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.tooling.preview.Preview
import com.franklinharper.social.media.client.app.FeedShellState
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.PlaceholderAppContainer
import com.franklinharper.social.media.client.app.ResponsiveLayout
import com.franklinharper.social.media.client.app.createAppContainer
import com.franklinharper.social.media.client.app.isWide
import com.franklinharper.social.media.client.ui.FeedScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        BoxWithConstraints {
            val layout = remember(maxWidth) { ResponsiveLayout.forMaxWidth(maxWidth) }
            val container = remember { createAppContainer() }
            val feedShellState = remember(container) {
                if (container === PlaceholderAppContainer) {
                    null
                } else {
                    FeedShellState(
                        configuredSourceRepository = container.configuredSourceRepository,
                        feedRepository = container.feedRepository,
                    )
                }
            }
            val placeholderState by rememberUpdatedState(FeedShellUiState())
            val uiState by feedShellState?.uiState?.collectAsState() ?: rememberUpdatedState(placeholderState)

            LaunchedEffect(feedShellState) {
                feedShellState?.start()
            }

            FeedScreen(
                state = uiState,
                isWideLayout = layout.isWide,
                onSelectSource = { source -> feedShellState?.selectFeedSource(source) },
            )
        }
    }
}
