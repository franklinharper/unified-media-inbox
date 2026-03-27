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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import com.franklinharper.social.media.client.app.WebAuthUiState
import com.franklinharper.social.media.client.app.WebAuthState
import com.franklinharper.social.media.client.app.WebAuthStatus
import com.franklinharper.social.media.client.app.createAppContainer
import com.franklinharper.social.media.client.app.isWide
import com.franklinharper.social.media.client.app.WebAutomationState
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.ui.AddSourceScreen
import com.franklinharper.social.media.client.ui.FeedItemDetailScreen
import com.franklinharper.social.media.client.ui.FeedScreen
import com.franklinharper.social.media.client.ui.LoginScreen
import com.franklinharper.social.media.client.sync.AuthenticatedSessionRepository
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    App(automationState = null)
}

@Composable
fun App(
    automationState: WebAutomationState? = null,
) {
    MaterialTheme {
        BoxWithConstraints {
            val uriHandler = LocalUriHandler.current
            val layout = remember(maxWidth) { ResponsiveLayout.forMaxWidth(maxWidth) }
            val container = remember { createAppContainer() }
            val authRepository = remember(container) {
                container
                    .takeUnless { it === PlaceholderAppContainer }
                    ?.sessionRepository as? AuthenticatedSessionRepository
            }
            val authState = remember(authRepository) {
                authRepository?.let(::WebAuthState)
            }
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
            val authUiState by authState?.uiState?.collectAsState()
                ?: rememberUpdatedState(
                    com.franklinharper.social.media.client.app.WebAuthUiState(
                        status = WebAuthStatus.Authenticated,
                    ),
                )
            val scope = rememberCoroutineScope()

            LaunchedEffect(authState) {
                authState?.start()
            }
            LaunchedEffect(feedShellState, authUiState.status) {
                if (authUiState.status == WebAuthStatus.Authenticated) {
                    feedShellState?.start()
                }
            }

            AppRoot(
                feedState = uiState,
                addSourceState = addSourceUiState,
                authState = authUiState,
                automationState = automationState,
                isWideLayout = layout.isWide,
                onSignIn = { email, password ->
                    if (authState != null) {
                        scope.launch {
                            authState.signIn(email, password)
                        }
                    }
                },
                onSignUp = { email, password ->
                    if (authState != null) {
                        scope.launch {
                            authState.signUp(email, password)
                        }
                    }
                },
                onSignOut = {
                    if (authState != null) {
                        scope.launch {
                            authState.signOut()
                        }
                    }
                },
                onAuthenticationFailure = {
                    if (authState != null) {
                        scope.launch {
                            authState.onUnauthorized()
                        }
                    }
                },
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
    authState: WebAuthUiState = WebAuthUiState(status = WebAuthStatus.Authenticated),
    automationState: WebAutomationState? = null,
    isWideLayout: Boolean = false,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {},
    onAuthenticationFailure: () -> Unit = {},
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

    SideEffect {
        automationState?.bindActions(
            onSignIn = onSignIn,
            onSignUp = onSignUp,
            onRefreshFeed = onRefreshFeed,
            onSignOut = onSignOut,
            onOpenAddSourceScreen = {
                onOpenAddSource()
                screen = AppScreen.AddSource
            },
            onSelectRssSourceType = {
                onSelectAddSourceType(SourceType.Rss)
            },
            onAddRssSource = onAddRssSource,
        )
        automationState?.updateEnvironment(
            authVisible = authState.status != WebAuthStatus.Authenticated,
            feedVisible = authState.status == WebAuthStatus.Authenticated,
            isSubmitting = authState.toLoginUiState().isSubmitting,
            isAddingSource = addSourceState.isAdding,
            feedItemCount = feedState.visibleItems.size,
            feedSourceNames = feedState.visibleItems.map { it.source.displayName }.distinct(),
            feedItemTitles = feedState.visibleItems.mapNotNull { it.title?.trim()?.takeIf(String::isNotEmpty) }.take(10),
        )
    }

    DisposableEffect(automationState) {
        onDispose {
            automationState?.clearActions()
        }
    }

    LaunchedEffect(authState.status, feedState.loadError) {
        if (
            authState.status == WebAuthStatus.Authenticated &&
            feedState.loadError is ClientError.AuthenticationError
        ) {
            onAuthenticationFailure()
        }
    }

    LaunchedEffect(screen, addSourceState.didAddSource) {
        if (screen == AppScreen.AddSource && addSourceState.didAddSource) {
            screen = AppScreen.Feed
            onRefreshFeed()
        }
    }

    if (authState.status != WebAuthStatus.Authenticated) {
        LoginScreen(
            state = authState.toLoginUiState(),
            onSignIn = onSignIn,
            onSignUp = onSignUp,
        )
        return
    }

    when (screen) {
        AppScreen.Feed -> FeedScreen(
            state = feedState,
            isWideLayout = isWideLayout,
            onAddSourcesClick = {
                onOpenAddSource()
                screen = AppScreen.AddSource
            },
            onSignOut = onSignOut,
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
            onSignOut = onSignOut,
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
