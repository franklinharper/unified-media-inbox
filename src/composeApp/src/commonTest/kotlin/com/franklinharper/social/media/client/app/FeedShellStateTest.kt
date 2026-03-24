package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class FeedShellStateTest {

    @Test
    fun `initial load fetches items automatically`() = runTest {
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = "rss-1")),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()

        assertEquals(1, fakeFeedRepository.requests.size)
    }

    @Test
    fun `selecting a source filters visible items`() = runTest {
        val sourceOne = FeedSource(PlatformId.Rss, "rss-1", "rss-1")
        val sourceTwo = FeedSource(PlatformId.Rss, "rss-2", "rss-2")
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(
                ConfiguredSource.RssFeed(url = sourceOne.sourceId),
                ConfiguredSource.RssFeed(url = sourceTwo.sourceId),
            ),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(
                items = listOf(
                    feedItem("item-1", sourceOne),
                    feedItem("item-2", sourceTwo),
                ),
                sourceStatuses = emptyList(),
            ),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectSource("rss-1")

        assertEquals(listOf("item-1"), state.visibleItems.map { it.itemId })
    }

    @Test
    fun `feed shell keeps configured sources and selected source empty state distinct`() = runTest {
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = "rss-1")),
        )
        val fakeFeedRepository = FakeFeedRepository(
            result = FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()),
        )
        val state = FeedShellState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
            feedRepository = fakeFeedRepository,
        )

        state.start()
        state.selectSource("rss-1")

        assertEquals(listOf("rss-1"), state.sources.map { it.sourceId })
        assertEquals(VisibleFeedEmptyState.NoItemsForSelectedSource("rss-1"), state.emptyState)
    }

    @Test
    fun `feed shell exposes loading and error state`() = runTest {
        val fakeConfiguredSourceRepository = FakeConfiguredSourceRepository(
            sources = listOf(ConfiguredSource.RssFeed(url = "rss-1")),
        )
        val fakeFeedRepository = FakeFeedRepository(
            error = ClientError.NetworkError("offline"),
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
}

private class FakeConfiguredSourceRepository(
    private val sources: List<ConfiguredSource>,
) : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> = sources
    override suspend fun addSource(source: ConfiguredSource) = error("Not used")
    override suspend fun removeSource(source: ConfiguredSource) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private class FakeFeedRepository(
    private val result: FeedLoadResult = FeedLoadResult(items = emptyList(), sourceStatuses = emptyList()),
    var error: ClientError? = null,
) : FeedRepository {
    val requests = mutableListOf<FeedRequest>()

    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult {
        requests += request
        error?.let { throw FakeClientException(it) }
        return result
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
