package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeClientException
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceLoadState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DefaultFeedRepository(
    private val clientRegistry: ClientRegistry,
    private val seenItemRepository: SeenItemRepository,
    private val feedCacheRepository: FeedCacheRepository? = null,
    private val sourceErrorRepository: SourceErrorRepository? = null,
    private val clock: () -> Long = { 0L },
) : FeedRepository {
    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult = coroutineScope {
        val twitterSourceIndexes = request.sources.withIndex().filter { indexedSource ->
            indexedSource.value is ConfiguredSource.SocialUser && indexedSource.value.platformId == PlatformId.Twitter
        }
        val nonTwitterSourceIndexes = request.sources.withIndex().filterNot { indexedSource ->
            indexedSource.value is ConfiguredSource.SocialUser && indexedSource.value.platformId == PlatformId.Twitter
        }
        val nonTwitterResults = nonTwitterSourceIndexes.map { indexedSource ->
            async {
                indexedSource.index to loadSource(indexedSource.value, request.includeSeen)
            }
        }.awaitAll()
        val twitterResults = if (twitterSourceIndexes.isEmpty()) {
            emptyList()
        } else {
            loadTwitterSources(twitterSourceIndexes, request.includeSeen)
        }
        val loadResults = (nonTwitterResults + twitterResults)
            .sortedBy(Pair<Int, SourceLoadResult>::first)
            .map(Pair<Int, SourceLoadResult>::second)

        val mergedItems = loadResults
            .flatMap(SourceLoadResult::items)
            .filter { item -> request.includeSeen || !seenItemRepository.isSeen(item.cacheKey) }
            .map { item ->
                val isSeen = seenItemRepository.isSeen(item.cacheKey)
                item.withSeenState(isSeen)
            }
            .sortedByDescending(FeedItem::publishedAtEpochMillis)

        FeedLoadResult(
            items = mergedItems,
            sourceStatuses = loadResults.map(SourceLoadResult::status),
        )
    }

    private suspend fun loadTwitterSources(
        sources: List<IndexedValue<ConfiguredSource>>,
        includeSeen: Boolean,
    ): List<Pair<Int, SourceLoadResult>> {
        val twitterSources = sources.map { indexedSource ->
            indexedSource.index to (indexedSource.value as ConfiguredSource.SocialUser)
        }
        val client = clientRegistry.require(PlatformId.Twitter)
        val query = FeedQuery.SocialUsers(
            platformId = PlatformId.Twitter,
            users = twitterSources.map { (_, source) -> source.user },
        )
        val cachedItemsBySource = twitterSources.associate { (_, source) ->
            source.toFeedSource() to feedCacheRepository?.readItems(source.toFeedSource(), includeSeen = true).orEmpty()
        }
        return try {
            val page = client.loadFeed(query)
            twitterSources.map { (index, source) ->
                val feedSource = source.toFeedSource()
                val sourceItems = page.items.filter { item -> item.source == feedSource }
                feedCacheRepository?.replaceItems(
                    source = feedSource,
                    items = sourceItems,
                    nextCursor = null,
                    refreshedAtEpochMillis = clock(),
                )
                index to SourceLoadResult(
                    items = sourceItems.filteredBySeen(includeSeen, seenItemRepository),
                    status = FeedSourceStatus(
                        source = feedSource,
                        state = SourceLoadState.Success,
                        contentOrigin = SourceContentOrigin.Refresh,
                    ),
                )
            }
        } catch (error: Throwable) {
            val clientError = error.toClientError()
            twitterSources.map { (index, source) ->
                val feedSource = source.toFeedSource()
                val filteredCachedItems = cachedItemsBySource[feedSource].orEmpty()
                    .filteredBySeen(includeSeen, seenItemRepository)
                val contentOrigin = if (filteredCachedItems.isEmpty()) SourceContentOrigin.None else SourceContentOrigin.Cache
                sourceErrorRepository?.logError(
                    source = feedSource,
                    contentOrigin = contentOrigin,
                    errorKind = clientError.kind,
                    errorMessage = clientError.messageOrNull,
                    occurredAtEpochMillis = clock(),
                )
                index to SourceLoadResult(
                    items = filteredCachedItems,
                    status = FeedSourceStatus(
                        source = feedSource,
                        state = SourceLoadState.Error(clientError),
                        contentOrigin = contentOrigin,
                    ),
                )
            }
        }
    }

    private suspend fun loadSource(source: ConfiguredSource, includeSeen: Boolean): SourceLoadResult {
        val feedSource = source.toFeedSource()
        val query = source.toFeedQuery()
        val client = clientRegistry.require(source.platformId)
        val cachedItems = feedCacheRepository?.readItems(feedSource, includeSeen = true).orEmpty()
        return try {
            val page = client.loadFeed(query)
            feedCacheRepository?.replaceItems(
                source = feedSource,
                items = page.items,
                nextCursor = page.nextCursor?.value,
                refreshedAtEpochMillis = clock(),
            )
            SourceLoadResult(
                items = page.items.filteredBySeen(includeSeen, seenItemRepository),
                status = FeedSourceStatus(
                    source = feedSource,
                    state = SourceLoadState.Success,
                    contentOrigin = SourceContentOrigin.Refresh,
                ),
            )
        } catch (error: Throwable) {
            val filteredCachedItems = cachedItems.filteredBySeen(includeSeen, seenItemRepository)
            val contentOrigin = if (filteredCachedItems.isEmpty()) SourceContentOrigin.None else SourceContentOrigin.Cache
            val clientError = error.toClientError()
            sourceErrorRepository?.logError(
                source = feedSource,
                contentOrigin = contentOrigin,
                errorKind = clientError.kind,
                errorMessage = clientError.messageOrNull,
                occurredAtEpochMillis = clock(),
            )
            SourceLoadResult(
                items = filteredCachedItems,
                status = FeedSourceStatus(
                    source = feedSource,
                    state = SourceLoadState.Error(clientError),
                    contentOrigin = contentOrigin,
                ),
            )
        }
    }
}

private data class SourceLoadResult(
    val items: List<FeedItem>,
    val status: FeedSourceStatus,
)

private fun ConfiguredSource.toFeedQuery(): FeedQuery = when (this) {
    is ConfiguredSource.RssFeed -> FeedQuery.RssFeeds(urls = listOf(url))
    is ConfiguredSource.SocialUser -> FeedQuery.SocialUsers(platformId = platformId, users = listOf(user))
}

private fun ConfiguredSource.toFeedSource(): FeedSource = when (this) {
    is ConfiguredSource.RssFeed -> FeedSource(
        platformId = PlatformId.Rss,
        sourceId = url,
        displayName = url,
    )
    is ConfiguredSource.SocialUser -> FeedSource(
        platformId = platformId,
        sourceId = user,
        displayName = user,
    )
}

private fun Throwable.toClientError(): ClientError = when (this) {
    is ClientFailure -> clientError
    else -> ClientError.TemporaryFailure(message)
}

private val ClientError.kind: String
    get() = when (this) {
        is ClientError.NetworkError -> "network"
        is ClientError.AuthenticationError -> "authentication"
        is ClientError.RateLimitError -> "rate_limit"
        is ClientError.ParsingError -> "parsing"
        is ClientError.TemporaryFailure -> "temporary"
        is ClientError.PermanentFailure -> "permanent"
    }

private val ClientError.messageOrNull: String?
    get() = when (this) {
        is ClientError.NetworkError -> message
        is ClientError.AuthenticationError -> message
        is ClientError.RateLimitError -> retryAfterMillis?.toString()
        is ClientError.ParsingError -> message
        is ClientError.TemporaryFailure -> message
        is ClientError.PermanentFailure -> message
    }

private val FeedItem.cacheKey: String
    get() = "${platformId.name.lowercase()}:$itemId"

private fun FeedItem.withSeenState(isSeen: Boolean): FeedItem =
    copy(seenState = if (isSeen) com.franklinharper.social.media.client.domain.SeenState.Seen else com.franklinharper.social.media.client.domain.SeenState.Unseen)

private suspend fun List<FeedItem>.filteredBySeen(
    includeSeen: Boolean,
    seenItemRepository: SeenItemRepository,
): List<FeedItem> = buildList {
    for (item in this@filteredBySeen) {
        val isSeen = seenItemRepository.isSeen(item.cacheKey)
        if (includeSeen || !isSeen) {
            add(item.withSeenState(isSeen))
        }
    }
}
