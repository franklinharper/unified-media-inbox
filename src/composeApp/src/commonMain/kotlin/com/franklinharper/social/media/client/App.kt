package com.franklinharper.social.media.client

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import com.franklinharper.social.media.client.app.AddSourceState
import com.franklinharper.social.media.client.app.AddSourceUiState
import com.franklinharper.social.media.client.app.FeedShellState
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.PlaceholderAppContainer
import com.franklinharper.social.media.client.app.ResponsiveLayout
import com.franklinharper.social.media.client.app.SourceType
import com.franklinharper.social.media.client.app.createAppContainer
import com.franklinharper.social.media.client.app.isWide
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.ui.AddSourceScreen
import com.franklinharper.social.media.client.ui.FeedItemDetailScreen
import com.franklinharper.social.media.client.ui.FeedScreen
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    MaterialTheme {
        BoxWithConstraints {
            val uriHandler = LocalUriHandler.current
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
            val addSourceState = remember(container) {
                if (container === PlaceholderAppContainer) {
                    null
                } else {
                    AddSourceState(
                        configuredSourceRepository = container.configuredSourceRepository,
                    )
                }
            }
            val placeholderAddSourceState by rememberUpdatedState(AddSourceUiState())
            val addSourceUiState by addSourceState?.uiState?.collectAsState() ?: rememberUpdatedState(placeholderAddSourceState)
            val scope = rememberCoroutineScope()

            LaunchedEffect(feedShellState) {
                feedShellState?.start()
            }

            AppRoot(
                feedState = uiState,
                addSourceState = addSourceUiState,
                isWideLayout = layout.isWide,
                onSelectFeedSource = { source -> feedShellState?.selectFeedSource(source) },
                onSelectAddSourceType = { type -> addSourceState?.selectType(type) },
                onOpenAddSource = { addSourceState?.resetFlow() },
                onBackFromAddSource = { addSourceState?.backToTypePicker() },
                onOpenExternalUrl = { url -> uriHandler.openUri(url) },
                onShowSeenItems = {
                    scope.launch {
                        feedShellState?.showSeenItems()
                    }
                },
                onAddRssSource = { url ->
                    scope.launch {
                        addSourceState?.addRssSource(url)
                    }
                },
                onAddBlueskySource = { handle ->
                    scope.launch {
                        addSourceState?.addBlueskySource(handle)
                    }
                },
                onRefreshFeed = {
                    scope.launch {
                        feedShellState?.refresh()
                    }
                },
            )
        }
    }
}

@Composable
fun AppPreviewContent() {
    MaterialTheme {
        AppRoot(
            feedState = FeedShellUiState(),
            addSourceState = AddSourceUiState(),
        )
    }
}

@Composable
internal fun AppRoot(
    feedState: FeedShellUiState,
    addSourceState: AddSourceUiState,
    isWideLayout: Boolean = false,
    onSelectFeedSource: (com.franklinharper.social.media.client.domain.FeedSource?) -> Unit = {},
    onSelectAddSourceType: (SourceType) -> Unit = {},
    onOpenAddSource: () -> Unit = {},
    onBackFromAddSource: () -> Unit = {},
    onOpenExternalUrl: (String) -> Unit = {},
    onShowSeenItems: () -> Unit = {},
    onAddRssSource: (String) -> Unit = {},
    onAddBlueskySource: (String) -> Unit = {},
    onRefreshFeed: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Feed) }
    var selectedItem by remember { mutableStateOf<FeedItem?>(null) }

    LaunchedEffect(screen, addSourceState.didAddSource) {
        if (screen == AppScreen.AddSource && addSourceState.didAddSource) {
            screen = AppScreen.Feed
            onRefreshFeed()
        }
    }

    when (screen) {
        AppScreen.Feed -> FeedScreen(
            state = feedState,
            isWideLayout = isWideLayout,
            onAddSourcesClick = {
                onOpenAddSource()
                screen = AppScreen.AddSource
            },
            onSelectSource = onSelectFeedSource,
            onRefresh = onRefreshFeed,
            onShowSeenItems = onShowSeenItems,
            onOpenComments = onOpenExternalUrl,
            onOpenItem = { item ->
                val externalUrl = item.externalOpenUrl()
                if (externalUrl != null) {
                    onOpenExternalUrl(externalUrl)
                } else {
                    selectedItem = item
                    screen = AppScreen.ItemDetail
                }
            },
        )

        AppScreen.AddSource -> AddSourceScreen(
            state = addSourceState,
            onSelectType = onSelectAddSourceType,
            onBack = {
                if (addSourceState.selectedType == null) {
                    screen = AppScreen.Feed
                } else {
                    onBackFromAddSource()
                }
            },
            onAddRssSource = onAddRssSource,
            onAddBlueskySource = onAddBlueskySource,
        )

        AppScreen.ItemDetail -> selectedItem?.let { item ->
            FeedItemDetailScreen(
                item = item,
                onClose = {
                    selectedItem = null
                    screen = AppScreen.Feed
                },
            )
        } ?: FeedScreen(
            state = feedState,
            isWideLayout = isWideLayout,
            onAddSourcesClick = {
                onOpenAddSource()
                screen = AppScreen.AddSource
            },
            onSelectSource = onSelectFeedSource,
            onRefresh = onRefreshFeed,
            onShowSeenItems = onShowSeenItems,
            onOpenComments = onOpenExternalUrl,
            onOpenItem = { item ->
                val externalUrl = item.externalOpenUrl()
                if (externalUrl != null) {
                    onOpenExternalUrl(externalUrl)
                } else {
                    selectedItem = item
                    screen = AppScreen.ItemDetail
                }
            },
        )
    }
}

private enum class AppScreen {
    Feed,
    AddSource,
    ItemDetail,
}

private fun String.isWebUrl(): Boolean = startsWith("http://") || startsWith("https://")

private fun FeedItem.externalOpenUrl(): String? = when {
    platformId == com.franklinharper.social.media.client.domain.PlatformId.Rss ->
        permalink?.trim()?.takeIf(String::isWebUrl)
    else ->
        body?.trim()?.takeUnless { it.isNullOrEmpty() }?.takeIf(String::isWebUrl)
            ?: permalink?.trim()?.takeIf(String::isWebUrl)
}
