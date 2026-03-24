package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeSocialPlatformClient
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedPage
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry
import com.franklinharper.social.media.client.domain.SocialProfile
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedCacheRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import com.franklinharper.social.media.client.repository.SeenItemRepository
import com.franklinharper.social.media.client.repository.SessionRepository
import com.franklinharper.social.media.client.repository.SourceErrorRepository
import kotlin.test.Test
import kotlin.test.assertNotNull

class AppContainerContractTest {

    @Test
    fun `app container exposes repositories needed by feed and add-source flows`() {
        val container = FakeAppContainer()

        assertNotNull(container.feedRepository)
        assertNotNull(container.configuredSourceRepository)
        assertNotNull(container.sessionRepository)
        assertNotNull(container.seenItemRepository)
        assertNotNull(container.feedCacheRepository)
        assertNotNull(container.sourceErrorRepository)
        assertNotNull(container.clientRegistry)
    }
}

private class FakeAppContainer : AppContainer {
    override val feedRepository: FeedRepository = StubFeedRepository
    override val configuredSourceRepository: ConfiguredSourceRepository = StubConfiguredSourceRepository
    override val sessionRepository: SessionRepository = StubSessionRepository
    override val seenItemRepository: SeenItemRepository = StubSeenItemRepository
    override val feedCacheRepository: FeedCacheRepository = StubFeedCacheRepository
    override val sourceErrorRepository: SourceErrorRepository = StubSourceErrorRepository
    override val clientRegistry: ClientRegistry = ClientRegistry(
        listOf(
            FakeSocialPlatformClient(
                id = PlatformId.Rss,
                displayName = "Fake RSS",
                feedProvider = { _, _ -> FeedPage(items = emptyList(), nextCursor = null) },
            ),
        ),
    )
}

private object StubFeedRepository : FeedRepository {
    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult = error("Not used")
}

private object StubConfiguredSourceRepository : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> = error("Not used")
    override suspend fun addSource(source: ConfiguredSource) = error("Not used")
    override suspend fun removeSource(source: ConfiguredSource) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object StubSessionRepository : SessionRepository {
    override suspend fun getSessionState(platformId: PlatformId): SessionState = error("Not used")
    override suspend fun upsertSession(platformId: PlatformId, session: AccountSession) = error("Not used")
    override suspend fun signOut(platformId: PlatformId) = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object StubSeenItemRepository : SeenItemRepository {
    override suspend fun markSeen(itemId: String) = error("Not used")
    override suspend fun markSeen(itemIds: List<String>) = error("Not used")
    override suspend fun isSeen(itemId: String): Boolean = error("Not used")
    override suspend fun clearAll() = error("Not used")
}

private object StubFeedCacheRepository : FeedCacheRepository {
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

private object StubSourceErrorRepository : SourceErrorRepository {
    override suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    ) = error("Not used")

    override suspend fun listErrors(source: FeedSource?, limit: Long): List<SourceErrorLogEntry> = error("Not used")
    override suspend fun clearAll() = error("Not used")
}
