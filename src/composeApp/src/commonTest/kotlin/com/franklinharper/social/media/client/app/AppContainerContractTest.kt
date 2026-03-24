package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedCacheRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import com.franklinharper.social.media.client.repository.SeenItemRepository
import com.franklinharper.social.media.client.repository.SessionRepository
import com.franklinharper.social.media.client.repository.SourceErrorRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AppContainerContractTest {

    @Test
    fun `app container delegates its boundary through dependencies`() {
        val dependencies = AppDependencies(
            clientRegistry = ClientRegistry(emptyList()),
            configuredSourceRepository = NoOpConfiguredSourceRepository,
            sessionRepository = NoOpSessionRepository,
            seenItemRepository = NoOpSeenItemRepository,
            feedCacheRepository = NoOpFeedCacheRepository,
            sourceErrorRepository = NoOpSourceErrorRepository,
            feedRepository = NoOpFeedRepository,
        )
        val container = object : AppContainer {
            override val dependencies: AppDependencies = dependencies
        }

        assertSame(dependencies, container.dependencies)
        assertSame(dependencies.clientRegistry, container.clientRegistry)
        assertSame(dependencies.configuredSourceRepository, container.configuredSourceRepository)
        assertSame(dependencies.sessionRepository, container.sessionRepository)
        assertSame(dependencies.seenItemRepository, container.seenItemRepository)
        assertSame(dependencies.feedCacheRepository, container.feedCacheRepository)
        assertSame(dependencies.sourceErrorRepository, container.sourceErrorRepository)
        assertSame(dependencies.feedRepository, container.feedRepository)
    }

    @Test
    fun `createAppContainer returns the placeholder container without throwing`() {
        val container = createAppContainer()

        assertSame(PlaceholderAppContainer, container)
        assertFailsWith<IllegalStateException> {
            container.dependencies
        }
    }
}

private object NoOpFeedRepository : FeedRepository {
    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult =
        error("Not used")
}

private object NoOpConfiguredSourceRepository : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> = error("Not used")
    override suspend fun addSource(source: ConfiguredSource) = error("Not used")
    override suspend fun removeSource(source: ConfiguredSource) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object NoOpSessionRepository : SessionRepository {
    override suspend fun getSessionState(platformId: PlatformId): SessionState = error("Not used")
    override suspend fun upsertSession(platformId: PlatformId, session: AccountSession) = error("Not used")
    override suspend fun signOut(platformId: PlatformId) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object NoOpSeenItemRepository : SeenItemRepository {
    override suspend fun markSeen(itemId: String) = error("Not used")
    override suspend fun markSeen(itemIds: List<String>) = error("Not used")
    override suspend fun isSeen(itemId: String): Boolean = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object NoOpFeedCacheRepository : FeedCacheRepository {
    override suspend fun readItems(source: FeedSource, includeSeen: Boolean): List<FeedItem> = error("Not used")

    override suspend fun replaceItems(
        source: FeedSource,
        items: List<FeedItem>,
        nextCursor: String?,
        refreshedAtEpochMillis: Long?,
    ) = error("Not used")

    override suspend fun getSyncState(source: FeedSource): FeedSyncState? = error("Not used")

    override suspend fun clearAll() = error("Not used")
}

private object NoOpSourceErrorRepository : SourceErrorRepository {
    override suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    ) = error("Not used")

    override suspend fun listErrors(
        source: FeedSource?,
        limit: Long,
    ): List<SourceErrorLogEntry> = error("Not used")

    override suspend fun clearAll() = error("Not used")
}
