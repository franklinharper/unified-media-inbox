package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.repository.FeedCacheRepository

class WebRemoteFeedCacheRepository(
    private val http: WebApiHttp,
) : FeedCacheRepository {
    override suspend fun readItems(source: FeedSource, includeSeen: Boolean): List<FeedItem> =
        http.get("/api/feed?includeSeen=$includeSeen")
            .decodeSuccess(FeedResponseDto.serializer())
            .items
            .map(FeedItemDto::toDomain)
            .filter { item -> item.source == source }

    override suspend fun replaceItems(
        source: FeedSource,
        items: List<FeedItem>,
        nextCursor: String?,
        refreshedAtEpochMillis: Long?,
    ) {
    }

    override suspend fun getSyncState(source: FeedSource): FeedSyncState? = null

    override suspend fun clearAll() {
    }
}
