package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

    @Test
    fun `feed screen shows add sources empty state when no sources exist`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState(noSources = true)) }

        onNodeWithText("Add sources").assertExists()
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

        onNodeWithText(oldest.title!!).assertExists()
    }

    @Test
    fun `feed screen shows no items for selected source state distinctly from no sources`() = runComposeUiTest {
        setContent { FeedScreen(state = fakeState(noSources = false, noItemsForSelectedSource = true)) }

        onNodeWithText("No items for this source").assertExists()
    }
}

private fun fakeState(
    noSources: Boolean = false,
    noItemsForSelectedSource: Boolean = false,
    items: List<FeedItem> = emptyList(),
): FeedShellUiState {
    val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
    return FeedShellUiState(
        sources = if (noSources) emptyList() else listOf(source),
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
): FeedItem = FeedItem(
    itemId = itemId,
    platformId = source.platformId,
    source = source,
    authorName = null,
    title = title,
    body = null,
    permalink = null,
    publishedAtEpochMillis = publishedAtEpochMillis,
    seenState = SeenState.Unseen,
)
