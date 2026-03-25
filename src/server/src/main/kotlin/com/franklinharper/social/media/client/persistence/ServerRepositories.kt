package com.franklinharper.social.media.client.persistence

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceLoadState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSourceErrorRepository

data class ServerApiDependencies(
    val database: SocialMediaDatabase,
    val clientRegistry: ClientRegistry,
    val clock: () -> Long = System::currentTimeMillis,
)

class ServerRepositories(
    private val dependencies: ServerApiDependencies,
) {
    fun forUser(userId: String): UserServerRepositories =
        UserServerRepositories(
            configuredSourceRepository = SqlDelightConfiguredSourceRepository.forUser(dependencies.database, userId),
            seenItemRepository = SqlDelightSeenItemRepository.forUser(dependencies.database, userId, dependencies.clock),
            feedCacheRepository = SqlDelightFeedCacheRepository.forUser(dependencies.database, userId, dependencies.clock),
            sourceErrorRepository = SqlDelightSourceErrorRepository.forUser(dependencies.database, userId),
            clientRegistry = dependencies.clientRegistry,
            clock = dependencies.clock,
        )
}

class UserServerRepositories(
    private val configuredSourceRepository: SqlDelightConfiguredSourceRepository,
    private val seenItemRepository: SqlDelightSeenItemRepository,
    private val feedCacheRepository: SqlDelightFeedCacheRepository,
    private val sourceErrorRepository: SqlDelightSourceErrorRepository,
    private val clientRegistry: ClientRegistry,
    private val clock: () -> Long,
) {
    private val refreshRepository = DefaultFeedRepository(
        clientRegistry = clientRegistry,
        seenItemRepository = seenItemRepository,
        feedCacheRepository = feedCacheRepository,
        sourceErrorRepository = sourceErrorRepository,
        clock = clock,
    )

    suspend fun listSources(): List<ConfiguredSource> = configuredSourceRepository.listSources()

    suspend fun addSource(source: ConfiguredSource) {
        configuredSourceRepository.addSource(source)
    }

    suspend fun removeSource(source: ConfiguredSource) {
        configuredSourceRepository.removeSource(source)
    }

    suspend fun listFeed(includeSeen: Boolean): FeedLoadResult {
        val sources = configuredSourceRepository.listSources()
        val items = sources
            .flatMap { source -> feedCacheRepository.readItems(source.toFeedSource(), includeSeen) }
            .sortedByDescending { item -> item.publishedAtEpochMillis }
        val statuses = sources.map { source ->
            val feedSource = source.toFeedSource()
            val contentOrigin = if (
                feedCacheRepository.getSyncState(feedSource) != null ||
                feedCacheRepository.readItems(feedSource, includeSeen = true).isNotEmpty()
            ) {
                SourceContentOrigin.Cache
            } else {
                SourceContentOrigin.None
            }
            FeedSourceStatus(
                source = feedSource,
                state = SourceLoadState.Success,
                contentOrigin = contentOrigin,
            )
        }
        return FeedLoadResult(items = items, sourceStatuses = statuses)
    }

    suspend fun refreshFeed(includeSeen: Boolean): FeedLoadResult =
        refreshRepository.loadFeedItems(
            FeedRequest(
                sources = configuredSourceRepository.listSources(),
                includeSeen = includeSeen,
            ),
        )

    suspend fun markSeen(itemIds: List<String>) {
        seenItemRepository.markSeen(itemIds)
    }
}

private fun ConfiguredSource.toFeedSource(): FeedSource = when (this) {
    is ConfiguredSource.RssFeed -> FeedSource(
        platformId = com.franklinharper.social.media.client.domain.PlatformId.Rss,
        sourceId = url,
        displayName = url,
    )
    is ConfiguredSource.SocialUser -> FeedSource(
        platformId = platformId,
        sourceId = user,
        displayName = user,
    )
}
