package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeRssClient
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultFeedRepositoryTest {

    @Test
    fun `loadFeedItems merges and sorts items across clients`() {
        runBlocking {
            val rssSource = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "RSS Feed")
            val blueskySource = FeedSource(PlatformId.Bluesky, "frank", "frank")
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(
                    listOf(
                        FakeRssClient(
                            itemsByUrl = mapOf(
                                rssSource.sourceId to listOf(
                                    feedItem("rss-1", PlatformId.Rss, rssSource, 100L),
                                ),
                            ),
                        ),
                        FakeBlueskyClient(
                            itemsByUser = mapOf(
                                blueskySource.sourceId to listOf(
                                    feedItem("bsky-1", PlatformId.Bluesky, blueskySource, 200L),
                                ),
                            ),
                        ),
                    ),
                ),
                seenItemRepository = InMemorySeenItemRepository(),
                feedCacheRepository = InMemoryFeedCacheRepository(),
            )

            val result = repository.loadFeedItems(
                FeedRequest(
                    sources = listOf(
                        ConfiguredSource.RssFeed(url = rssSource.sourceId),
                        ConfiguredSource.SocialUser(platformId = PlatformId.Bluesky, user = blueskySource.sourceId),
                    ),
                ),
            )

            assertEquals(listOf("bsky-1", "rss-1"), result.items.map(FeedItem::itemId))
            assertEquals(2, result.sourceStatuses.size)
            assertEquals(
                listOf(SourceContentOrigin.Refresh, SourceContentOrigin.Refresh),
                result.sourceStatuses.map { it.contentOrigin },
            )
        }
    }

    @Test
    fun `loadFeedItems filters seen items unless includeSeen is true`() {
        runBlocking {
            val source = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "RSS Feed")
            val seenRepository = InMemorySeenItemRepository(seenKeys = mutableSetOf("rss:rss-1"))
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(
                    listOf(
                        FakeRssClient(
                            itemsByUrl = mapOf(
                                source.sourceId to listOf(
                                    feedItem("rss-1", PlatformId.Rss, source, 100L),
                                    feedItem("rss-2", PlatformId.Rss, source, 200L),
                                ),
                            ),
                        ),
                    ),
                ),
                seenItemRepository = seenRepository,
                feedCacheRepository = InMemoryFeedCacheRepository(),
            )

            val unseenOnly = repository.loadFeedItems(
                FeedRequest(
                    sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId)),
                    includeSeen = false,
                ),
            )
            val allItems = repository.loadFeedItems(
                FeedRequest(
                    sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId)),
                    includeSeen = true,
                ),
            )

            assertEquals(listOf("rss-2"), unseenOnly.items.map(FeedItem::itemId))
            assertEquals(listOf("rss-2", "rss-1"), allItems.items.map(FeedItem::itemId))
            assertEquals(SeenState.Seen, allItems.items.last().seenState)
        }
    }

    @Test
    fun `loadFeedItems reports partial failures without dropping successful items`() {
        runBlocking {
            val goodSource = FeedSource(PlatformId.Rss, "https://example.com/good.xml", "good")
            val badSourceUrl = "https://example.com/bad.xml"
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(
                    listOf(
                        FakeRssClient(
                            itemsByUrl = mapOf(
                                goodSource.sourceId to listOf(feedItem("rss-1", PlatformId.Rss, goodSource, 100L)),
                            ),
                            errorsByUrl = mapOf(
                                badSourceUrl to ClientError.NetworkError("offline"),
                            ),
                        ),
                    ),
                ),
                seenItemRepository = InMemorySeenItemRepository(),
                feedCacheRepository = InMemoryFeedCacheRepository(),
            )

            val result = repository.loadFeedItems(
                FeedRequest(
                    sources = listOf(
                        ConfiguredSource.RssFeed(url = goodSource.sourceId),
                        ConfiguredSource.RssFeed(url = badSourceUrl),
                    ),
                ),
            )

            assertEquals(listOf("rss-1"), result.items.map(FeedItem::itemId))
            val errorStatus = result.sourceStatuses.single { it.source.sourceId == badSourceUrl }
            assertIs<com.franklinharper.social.media.client.domain.SourceLoadState.Error>(errorStatus.state)
            assertEquals(SourceContentOrigin.None, errorStatus.contentOrigin)
        }
    }

    @Test
    fun `loadFeedItems falls back to cached items when refresh fails`() {
        runBlocking {
            val source = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "RSS Feed")
            val cacheRepository = InMemoryFeedCacheRepository().apply {
                replaceItems(
                    source = source,
                    items = listOf(feedItem("cached-1", PlatformId.Rss, source, 150L)),
                    nextCursor = "cursor-1",
                    refreshedAtEpochMillis = 500L,
                )
            }
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(
                    listOf(
                        FakeRssClient(
                            itemsByUrl = emptyMap(),
                            errorsByUrl = mapOf(source.sourceId to ClientError.NetworkError("offline")),
                        ),
                    ),
                ),
                seenItemRepository = InMemorySeenItemRepository(),
                feedCacheRepository = cacheRepository,
            )

            val result = repository.loadFeedItems(
                FeedRequest(sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId))),
            )

            assertEquals(listOf("cached-1"), result.items.map(FeedItem::itemId))
            assertIs<com.franklinharper.social.media.client.domain.SourceLoadState.Error>(result.sourceStatuses.single().state)
            assertEquals(SourceContentOrigin.Cache, result.sourceStatuses.single().contentOrigin)
        }
    }

    @Test
    fun `loadFeedItems stores refreshed items in cache`() {
        runBlocking {
            val source = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "RSS Feed")
            val cacheRepository = InMemoryFeedCacheRepository()
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(
                    listOf(
                        FakeRssClient(
                            itemsByUrl = mapOf(
                                source.sourceId to listOf(feedItem("rss-1", PlatformId.Rss, source, 100L)),
                            ),
                        ),
                    ),
                ),
                seenItemRepository = InMemorySeenItemRepository(),
                feedCacheRepository = cacheRepository,
                clock = { 999L },
            )

            repository.loadFeedItems(
                FeedRequest(sources = listOf(ConfiguredSource.RssFeed(url = source.sourceId))),
            )

            assertEquals(listOf("rss-1"), cacheRepository.readItems(source, includeSeen = true).map(FeedItem::itemId))
            assertEquals(999L, cacheRepository.getSyncState(source)?.lastRefreshedAtEpochMillis)
        }
    }

    private fun feedItem(
        id: String,
        platformId: PlatformId,
        source: FeedSource,
        publishedAt: Long,
    ): FeedItem = FeedItem(
        itemId = id,
        platformId = platformId,
        source = source,
        authorName = source.displayName,
        title = id,
        body = id,
        permalink = null,
        publishedAtEpochMillis = publishedAt,
        seenState = SeenState.Unseen,
    )
}

private class InMemorySeenItemRepository(
    private val seenKeys: MutableSet<String> = mutableSetOf(),
) : SeenItemRepository {
    override suspend fun markSeen(itemId: String) {
        seenKeys += itemId
    }

    override suspend fun markSeen(itemIds: List<String>) {
        seenKeys += itemIds
    }

    override suspend fun isSeen(itemId: String): Boolean = itemId in seenKeys

    override suspend fun clearAll() {
        seenKeys.clear()
    }
}

private class InMemoryFeedCacheRepository : FeedCacheRepository {
    private val itemsBySource = mutableMapOf<String, List<FeedItem>>()
    private val syncStateBySource = mutableMapOf<String, com.franklinharper.social.media.client.domain.FeedSyncState>()

    override suspend fun readItems(source: FeedSource, includeSeen: Boolean): List<FeedItem> =
        itemsBySource[source.cacheKey].orEmpty()

    override suspend fun replaceItems(
        source: FeedSource,
        items: List<FeedItem>,
        nextCursor: String?,
        refreshedAtEpochMillis: Long?,
    ) {
        itemsBySource[source.cacheKey] = items
        syncStateBySource[source.cacheKey] = com.franklinharper.social.media.client.domain.FeedSyncState(
            source = source,
            nextCursor = nextCursor?.let { value ->
                com.franklinharper.social.media.client.domain.FeedCursor(value)
            },
            lastRefreshedAtEpochMillis = refreshedAtEpochMillis,
        )
    }

    override suspend fun getSyncState(source: FeedSource): com.franklinharper.social.media.client.domain.FeedSyncState? =
        syncStateBySource[source.cacheKey]

    override suspend fun clearAll() {
        itemsBySource.clear()
        syncStateBySource.clear()
    }
}

private val FeedSource.cacheKey: String
    get() = "${platformId.name.lowercase()}:$sourceId"
