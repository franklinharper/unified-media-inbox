package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

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
