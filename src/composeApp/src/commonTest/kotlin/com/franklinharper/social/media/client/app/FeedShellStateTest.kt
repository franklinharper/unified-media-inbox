package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class FeedShellStateTest {

    @Test
    fun `initial load fetches items automatically and updates ui state`() = runTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId)),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(
                items = listOf(feedItem("item-1", source)),
                sourceStatuses = emptyList(),
            ),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()

        assertEquals(1, fakeFeedRepository.requests.size)
        assertEquals(listOf(source), state.uiState.value.sources)
        assertEquals(listOf("item-1"), state.uiState.value.visibleItems.map { it.itemId })
        assertFalse(state.uiState.value.isLoading)
        assertNull(state.uiState.value.loadError)
    }

    @Test
    fun `selecting a source filters visible items through ui state`() = runTest {
        val rssSource = FeedSource(PlatformId.Rss, "shared", "shared")
        val blueskySource = FeedSource(PlatformId.Bluesky, "shared", "shared")
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(
                ConfiguredSource.RssFeed(url = rssSource.sourceId),
                ConfiguredSource.SocialUser(platformId = PlatformId.Bluesky, user = blueskySource.sourceId),
            ),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(
                items = listOf(
                    feedItem("rss-item", rssSource),
                    feedItem("bsky-item", blueskySource),
                ),
                sourceStatuses = emptyList(),
            ),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectResolvedSource(blueskySource)

        assertEquals(blueskySource.sourceId, state.uiState.value.selectedSourceId)
        assertEquals(blueskySource, state.uiState.value.selectedSourceKey)
        assertEquals(listOf("bsky-item"), state.uiState.value.visibleItems.map { it.itemId })
    }

    @Test
    fun `feed shell keeps configured sources and selected source empty state distinct`() = runTest {
        val selectedSource = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = selectedSource.sourceId)),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectResolvedSource(selectedSource)

        assertEquals(listOf(selectedSource), state.sources)
        assertEquals(VisibleFeedEmptyState.NoItemsForSelectedSource(selectedSource.sourceId), state.emptyState)
    }

    @Test
    fun `feed shell exposes loading and error state`() = runTest {
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = "rss-1")),
        )
        val fakeFeedRepository = FakeFeedRepository(
            failure = FakeClientException(ClientError.NetworkError("offline")),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()

        assertFalse(state.isLoading)
        assertIs<ClientError.NetworkError>(state.loadError)
        assertEquals("offline", (state.loadError as ClientError.NetworkError).message)
    }

    @Test
    fun `refresh rethrows cancellation`() = runTest {
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = "rss-1")),
        )
        val fakeFeedRepository = FakeFeedRepository(
            failure = CancellationException("cancelled"),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        assertFailsWith<CancellationException> {
            state.start()
        }

        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `selecting an unknown source id clears the selection`() = runTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId)),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(items = listOf(feedItem("item-1", source)), sourceStatuses = emptyList()),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectSource("missing")

        assertNull(state.uiState.value.selectedSourceId)
        assertNull(state.uiState.value.selectedSourceKey)
        assertEquals(listOf("item-1"), state.uiState.value.visibleItems.map { it.itemId })
    }

    @Test
    fun `refresh clears the selection when the selected source disappears`() = runTest {
        val source = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val fakeConfiguredSourceRepository = MutableConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId)),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(items = listOf(feedItem("item-1", source)), sourceStatuses = emptyList()),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectResolvedSource(source)
        fakeConfiguredSourceRepository.sources = emptyList()
        fakeFeedRepository.resultState.update { FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()) }

        state.refresh()

        assertNull(state.uiState.value.selectedSourceId)
        assertNull(state.uiState.value.selectedSourceKey)
        assertEquals(VisibleFeedEmptyState.NoConfiguredSources, state.emptyState)
    }
}

private class FakeConfiguredSourceRepository(
    private val sources: List<ConfiguredSource>,
) : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> = sources
    override suspend fun addSource(source: ConfiguredSource) = error("Not used")
    override suspend fun removeSource(source: ConfiguredSource) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private class MutableConfiguredSourceRepository(
    var sources: List<ConfiguredSource>,
) : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> = sources
    override suspend fun addSource(source: ConfiguredSource) = error("Not used")
    override suspend fun removeSource(source: ConfiguredSource) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private class FakeFeedRepository(
    result: FeedLoadResult = FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()),
    private val failure: Throwable? = null,
) : FeedRepository {
    val resultState = MutableStateFlow(result)
    val requests = mutableListOf<FeedRequest>()

    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult {
        requests += request
        failure?.let { throw it }
        return resultState.value
    }
}

private class FakeClientException(
    override val clientError: ClientError,
) : RuntimeException(), ClientFailure

private fun feedItem(itemId: String, source: FeedSource): FeedItem =
    FeedItem(
        itemId = itemId,
        platformId = source.platformId,
        source = source,
        authorName = null,
        title = itemId,
        body = null,
        permalink = null,
        publishedAtEpochMillis = 0L,
        seenState = SeenState.Unseen,
    )
