package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.franklinharper.social.media.client.AppRoot
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.AddSourceUiState
import com.franklinharper.social.media.client.app.WebAuthStatus
import com.franklinharper.social.media.client.app.WebAuthUiState
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

    @Test
    fun `web root shows login before authenticated feed`() = runComposeUiTest {
        setContent {
            AppRoot(
                feedState = FeedShellUiState(),
                addSourceState = AddSourceUiState(),
                authState = WebAuthUiState(status = WebAuthStatus.Unauthenticated),
            )
        }

        onNodeWithTag("login-email-field").assertExists()
        onNodeWithText("Feed").assertDoesNotExist()
    }

    @Test
    fun `feed screen shows add sources empty state when no sources exist`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState(noSources = true)) }

        onNodeWithText("Add sources").assertExists().assertHasClickAction()
    }

    @Test
    fun `feed screen shows oldest items first`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val oldest = feedItem(
            itemId = "oldest",
            source = source,
            publishedAtEpochMillis = 1L,
            title = "Oldest item",
        )
        val newest = feedItem(
            itemId = "newest",
            source = source,
            publishedAtEpochMillis = 2L,
            title = "Newest item",
        )

        setContent { FeedScreen(state = fakeState(items = listOf(oldest, newest))) }

        val oldestY = onNodeWithText(oldest.title!!).fetchSemanticsNode().positionInRoot.y
        val newestY = onNodeWithText(newest.title!!).fetchSemanticsNode().positionInRoot.y

        check(oldestY < newestY) {
            "Expected oldest item to render above newest item, but positions were $oldestY and $newestY."
        }
    }

    @Test
    fun `feed screen shows no items for selected source state distinctly from no sources`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState(noSources = false, noItemsForSelectedSource = true)) }

        onNodeWithText("No items for this source").assertExists()
    }

    @Test
    fun `narrow layout uses dropdown source selector`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState()) }

        onNodeWithTag("source-filter-dropdown").assertExists()
        onNodeWithTag("source-filter-trigger").assertTextEquals("All items")
    }

    @Test
    fun `wide layout uses persistent source panel`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState(), isWideLayout = true) }

        onNodeWithTag("source-panel").assertExists()
        onNodeWithText("All items").assertExists()
    }

    @Test
    fun `narrow layout dropdown emits source selection`() = runComposeUiTest {
        val selectedSources = mutableListOf<String?>()
        val source = FeedSource(PlatformId.Bluesky, "user-1", "Alice")
        setContent {
            FeedScreen(
                state = fakeState(sources = listOf(source)),
                onSelectSource = { selectedSources += it?.sourceId },
            )
        }

        onNodeWithTag("source-filter-trigger").performClick()
        onNodeWithText("Alice").performClick()

        assertContentEquals(listOf("user-1"), selectedSources)
    }

    @Test
    fun `refresh button emits refresh action`() = runComposeUiTest {
        var refreshCount = 0
        setContent {
            FeedScreen(
                state = fakeState(),
                onRefresh = { refreshCount += 1 },
                nowEpochMillis = 10_000L,
            )
        }

        onNodeWithTag("feed-refresh-button").performClick()

        kotlin.test.assertEquals(1, refreshCount)
    }

    @Test
    fun `sign out button emits sign out action`() = runComposeUiTest {
        var signOutCount = 0
        setContent {
            FeedScreen(
                state = fakeState(),
                onSignOut = { signOutCount += 1 },
            )
        }

        onNodeWithTag("feed-sign-out-button").performClick()

        kotlin.test.assertEquals(1, signOutCount)
    }

    @Test
    fun `show seen items button emits action at bottom of feed`() = runComposeUiTest {
        var showSeenCount = 0
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val item = feedItem(
            itemId = "item-1",
            source = source,
            publishedAtEpochMillis = 1L,
            title = "Visible item",
        )

        setContent {
            FeedScreen(
                state = fakeState(items = listOf(item)),
                onShowSeenItems = { showSeenCount += 1 },
            )
        }

        onNodeWithTag("feed-show-seen-button").performClick()

        kotlin.test.assertEquals(1, showSeenCount)
    }

    @Test
    fun `empty filtered source state can offer show seen items`() = runComposeUiTest {
        setContent {
            FeedScreen(
                state = FeedShellUiState(
                    sources = listOf(FeedSource(PlatformId.Rss, "rss-1", "rss-1")),
                    selectedSourceId = "rss-1",
                    selectedSourceKey = FeedSource(PlatformId.Rss, "rss-1", "rss-1"),
                    items = emptyList(),
                    includeSeen = false,
                ),
            )
        }

        onNodeWithText("Show seen items").assertExists()
    }

    @Test
    fun `feed screen shows relative timestamps`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val item = feedItem(
            itemId = "item-1",
            source = source,
            publishedAtEpochMillis = 7_000L,
            title = "Relative item",
        )

        setContent {
            FeedScreen(
                state = fakeState(items = listOf(item)),
                nowEpochMillis = 10_000L,
            )
        }

        onNodeWithText("00:00:03 ago").assertExists()
    }

    @Test
    fun `clicking non-url item opens detail screen`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Bluesky, "user-1", "user-1")
        val item = feedItem(
            itemId = "item-1",
            source = source,
            publishedAtEpochMillis = 7_000L,
            title = "Detail item",
            body = "Full item body",
        )

        setContent {
            AppRoot(
                feedState = fakeState(items = listOf(item)),
                addSourceState = com.franklinharper.social.media.client.app.AddSourceUiState(),
            )
        }

        onNodeWithText("Detail item").performClick()

        onNodeWithText("Full item body").assertExists()
        onNodeWithTag("feed-item-detail-close").assertExists()
    }

    @Test
    fun `clicking rss item opens permalink in external browser`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val item = feedItem(
            itemId = "item-1",
            source = source,
            publishedAtEpochMillis = 7_000L,
            title = "Url item",
            body = "Body text",
            permalink = "https://example.com/post",
        )
        var openedUrl: String? = null

        setContent {
            AppRoot(
                feedState = fakeState(items = listOf(item)),
                addSourceState = com.franklinharper.social.media.client.app.AddSourceUiState(),
                onOpenExternalUrl = { openedUrl = it },
            )
        }

        onNodeWithText("Url item").performClick()

        kotlin.test.assertEquals("https://example.com/post", openedUrl)
        onNodeWithTag("feed-item-detail-close").assertDoesNotExist()
    }

    @Test
    fun `comments button opens comments link from feed item`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val item = feedItem(
            itemId = "item-1",
            source = source,
            publishedAtEpochMillis = 7_000L,
            title = "Commented item",
            body = "Body text",
            permalink = "https://example.com/post",
            commentsPermalink = "https://example.com/post/comments",
        )
        var openedUrl: String? = null

        setContent {
            AppRoot(
                feedState = fakeState(items = listOf(item)),
                addSourceState = com.franklinharper.social.media.client.app.AddSourceUiState(),
                onOpenExternalUrl = { openedUrl = it },
            )
        }

        onNodeWithTag("feed-item-comments-button-item-1").performClick()

        kotlin.test.assertEquals("https://example.com/post/comments", openedUrl)
    }

    @Test
    fun `seen items sort to the bottom when included`() = runComposeUiTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val seenItem = feedItem(
            itemId = "seen",
            source = source,
            publishedAtEpochMillis = 1L,
            title = "Seen item",
            seenState = SeenState.Seen,
        )
        val unseenItem = feedItem(
            itemId = "unseen",
            source = source,
            publishedAtEpochMillis = 2L,
            title = "Unseen item",
            seenState = SeenState.Unseen,
        )

        setContent {
            FeedScreen(
                state = FeedShellUiState(
                    sources = listOf(source),
                    items = listOf(seenItem, unseenItem),
                    includeSeen = true,
                ),
            )
        }

        val unseenY = onNodeWithText("Unseen item").fetchSemanticsNode().positionInRoot.y
        val seenY = onNodeWithText("Seen item").fetchSemanticsNode().positionInRoot.y

        check(unseenY < seenY) {
            "Expected unseen items above seen items, but positions were $unseenY and $seenY."
        }
    }

    @Test
    fun `unauthorized result returns user to login screen`() = runComposeUiTest {
        setContent {
            var authState by remember { mutableStateOf(WebAuthUiState(status = WebAuthStatus.Authenticated)) }
            AppRoot(
                feedState = FeedShellUiState(
                    loadError = ClientError.AuthenticationError("Your session expired. Sign in again."),
                ),
                addSourceState = AddSourceUiState(),
                authState = authState,
                onAuthenticationFailure = {
                    authState = WebAuthUiState(
                        status = WebAuthStatus.SessionExpired,
                        message = "Your session expired. Sign in again.",
                    )
                },
            )
        }

        onNodeWithTag("login-email-field").assertExists()
        onNodeWithText("Your session expired. Sign in again.").assertExists()
    }

    @Test
    fun `signing out from app root returns user to login screen`() = runComposeUiTest {
        setContent {
            var authState by remember { mutableStateOf(WebAuthUiState(status = WebAuthStatus.Authenticated)) }
            AppRoot(
                feedState = fakeState(),
                addSourceState = AddSourceUiState(),
                authState = authState,
                onSignOut = {
                    authState = WebAuthUiState(status = WebAuthStatus.Unauthenticated)
                },
            )
        }

        onNodeWithTag("feed-sign-out-button").performClick()

        onNodeWithTag("login-email-field").assertExists()
        onNodeWithText("Feed").assertDoesNotExist()
    }
}

private fun fakeState(
    noSources: Boolean = false,
    noItemsForSelectedSource: Boolean = false,
    items: List<FeedItem> = emptyList(),
    sources: List<FeedSource> = listOf(FeedSource(PlatformId.Rss, "rss-1", "rss-1")),
): FeedShellUiState {
    val source = sources.first()
    return FeedShellUiState(
        sources = if (noSources) emptyList() else sources,
        selectedSourceId = if (noItemsForSelectedSource) source.sourceId else null,
        selectedSourceKey = if (noItemsForSelectedSource) source else null,
        items = if (noItemsForSelectedSource) emptyList() else items,
    )
}

private fun feedItem(
    itemId: String,
    source: FeedSource,
    publishedAtEpochMillis: Long,
    title: String,
    body: String? = null,
    permalink: String? = null,
    commentsPermalink: String? = null,
    seenState: SeenState = SeenState.Unseen,
): FeedItem = FeedItem(
    itemId = itemId,
    platformId = source.platformId,
    source = source,
    authorName = null,
    title = title,
    body = body,
    permalink = permalink,
    commentsPermalink = commentsPermalink,
    publishedAtEpochMillis = publishedAtEpochMillis,
    seenState = seenState,
)
