package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeClientException
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SourceLoadState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DefaultFeedRepository(
    private val clientRegistry: ClientRegistry,
    private val seenItemRepository: SeenItemRepository,
) : FeedRepository {
    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult = coroutineScope {
        val loadResults = request.sources.map { source ->
            async {
                loadSource(source)
            }
        }.awaitAll()

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

    private suspend fun loadSource(source: ConfiguredSource): SourceLoadResult {
        val feedSource = source.toFeedSource()
        val query = source.toFeedQuery()
        val client = clientRegistry.require(source.platformId)
        return try {
            val page = client.loadFeed(query)
            SourceLoadResult(
                items = page.items,
                status = FeedSourceStatus(
                    source = feedSource,
                    state = SourceLoadState.Success,
                ),
            )
        } catch (error: Throwable) {
            SourceLoadResult(
                items = emptyList(),
                status = FeedSourceStatus(
                    source = feedSource,
                    state = SourceLoadState.Error(error.toClientError()),
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
    is FakeClientException -> clientError
    else -> ClientError.TemporaryFailure(message)
}

private val FeedItem.cacheKey: String
    get() = "${platformId.name.lowercase()}:$itemId"

private fun FeedItem.withSeenState(isSeen: Boolean): FeedItem =
    copy(seenState = if (isSeen) com.franklinharper.social.media.client.domain.SeenState.Seen else com.franklinharper.social.media.client.domain.SeenState.Unseen)
