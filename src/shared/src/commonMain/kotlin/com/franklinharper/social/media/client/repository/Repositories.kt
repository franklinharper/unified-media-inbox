package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry
import com.franklinharper.social.media.client.domain.SourceContentOrigin

interface FeedRepository {
    suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult
}

interface SeenItemRepository {
    suspend fun markSeen(itemId: String)
    suspend fun markSeen(itemIds: List<String>)
    suspend fun isSeen(itemId: String): Boolean
    suspend fun clearAll()
}

interface ConfiguredSourceRepository {
    suspend fun listSources(): List<ConfiguredSource>
    suspend fun addSource(source: ConfiguredSource)
    suspend fun removeSource(source: ConfiguredSource)
    suspend fun clearAll()
}

interface SessionRepository {
    suspend fun getSessionState(platformId: PlatformId): SessionState
    suspend fun upsertSession(platformId: PlatformId, session: com.franklinharper.social.media.client.domain.AccountSession)
    suspend fun signOut(platformId: PlatformId)
    suspend fun clearAll()
}

interface FeedCacheRepository {
    suspend fun readItems(source: FeedSource, includeSeen: Boolean = false): List<FeedItem>
    suspend fun replaceItems(
        source: FeedSource,
        items: List<FeedItem>,
        nextCursor: String? = null,
        refreshedAtEpochMillis: Long? = null,
    )
    suspend fun getSyncState(source: FeedSource): FeedSyncState?
    suspend fun clearAll()
}

interface SourceErrorRepository {
    suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    )
    suspend fun listErrors(source: FeedSource? = null, limit: Long = 100): List<SourceErrorLogEntry>
    suspend fun clearAll()
}
