package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.SocialPlatformClient
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeRssClient
import com.franklinharper.social.media.client.domain.FeedPage
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry
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
            val sourceErrorRepository = InMemorySourceErrorRepository()
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
                sourceErrorRepository = sourceErrorRepository,
                clock = { 123L },
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
            assertEquals(1, sourceErrorRepository.entries.size)
            assertEquals("network", sourceErrorRepository.entries.single().errorKind)
            assertEquals("offline", sourceErrorRepository.entries.single().errorMessage)
            assertEquals(123L, sourceErrorRepository.entries.single().occurredAtEpochMillis)
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

    @Test
    fun `loadFeedItems batches twitter follows into one client request and splits cache per source`() {
        runBlocking {
            val frankSource = FeedSource(PlatformId.Twitter, "frank", "frank")
            val samSource = FeedSource(PlatformId.Twitter, "@sam", "@sam")
            val cacheRepository = InMemoryFeedCacheRepository()
            val seenRepository = InMemorySeenItemRepository()
            val queries = mutableListOf<FeedQuery.SocialUsers>()
            val twitterClient = object : SocialPlatformClient {
                override val id: PlatformId = PlatformId.Twitter
                override val displayName: String = "Twitter"

                override suspend fun sessionState(): SessionState = SessionState.SignedOut

                override suspend fun loadProfile(accountId: String) =
                    error("Not used in this test")

                override suspend fun loadFeed(query: FeedQuery, cursor: com.franklinharper.social.media.client.domain.FeedCursor?): FeedPage {
                    val socialQuery = query as FeedQuery.SocialUsers
                    queries += socialQuery
                    return FeedPage(
                        items = listOf(
                            feedItem("tw-1", PlatformId.Twitter, frankSource, 100L),
                            feedItem("tw-2", PlatformId.Twitter, samSource, 200L),
                        ),
                    )
                }
            }
            val repository = DefaultFeedRepository(
                clientRegistry = ClientRegistry(listOf(twitterClient)),
                seenItemRepository = seenRepository,
                feedCacheRepository = cacheRepository,
                clock = { 999L },
            )

            val result = repository.loadFeedItems(
                FeedRequest(
                    sources = listOf(
                        ConfiguredSource.SocialUser(platformId = PlatformId.Twitter, user = frankSource.sourceId),
                        ConfiguredSource.SocialUser(platformId = PlatformId.Twitter, user = samSource.sourceId),
                    ),
                ),
            )

            assertEquals(1, queries.size)
            assertEquals(listOf("frank", "@sam"), queries.single().users)
            assertEquals(listOf("tw-2", "tw-1"), result.items.map(FeedItem::itemId))
            assertEquals(listOf("tw-1"), cacheRepository.readItems(frankSource, includeSeen = true).map(FeedItem::itemId))
            assertEquals(listOf("tw-2"), cacheRepository.readItems(samSource, includeSeen = true).map(FeedItem::itemId))
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

private class InMemorySourceErrorRepository : SourceErrorRepository {
    val entries = mutableListOf<SourceErrorLogEntry>()

    override suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    ) {
        entries += SourceErrorLogEntry(
            id = entries.size.toLong() + 1L,
            source = source,
            contentOrigin = contentOrigin,
            errorKind = errorKind,
            errorMessage = errorMessage,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
    }

    override suspend fun listErrors(source: FeedSource?, limit: Long): List<SourceErrorLogEntry> =
        entries
            .asReversed()
            .filter { entry -> source == null || entry.source == source }
            .take(limit.toInt())

    override suspend fun clearAll() {
        entries.clear()
    }
}

private val FeedSource.cacheKey: String
    get() = "${platformId.name.lowercase()}:$sourceId"
