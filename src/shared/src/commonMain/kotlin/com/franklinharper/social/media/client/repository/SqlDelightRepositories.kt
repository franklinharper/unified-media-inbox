package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SessionState

class SqlDelightConfiguredSourceRepository(
    private val database: SocialMediaDatabase,
) : ConfiguredSourceRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun listSources(): List<ConfiguredSource> =
        queries.selectConfiguredSources(::toConfiguredSource).executeAsList()

    override suspend fun addSource(source: ConfiguredSource) {
        queries.upsertConfiguredSource(
            platform_id = source.platformId.serializedName,
            source_kind = source.kind,
            value_ = source.serializedValue,
        )
    }

    override suspend fun removeSource(source: ConfiguredSource) {
        queries.removeConfiguredSource(
            platform_id = source.platformId.serializedName,
            source_kind = source.kind,
            value_ = source.serializedValue,
        )
    }

    override suspend fun clearAll() {
        queries.removeAllConfiguredSources()
    }
}

class SqlDelightSeenItemRepository(
    private val database: SocialMediaDatabase,
    private val clock: () -> Long = { 0L },
) : SeenItemRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun markSeen(itemId: String) {
        queries.markSeen(item_key = itemId, seen_at_epoch_millis = clock())
    }

    override suspend fun markSeen(itemIds: List<String>) {
        database.transaction {
            itemIds.forEach { itemId ->
                queries.markSeen(item_key = itemId, seen_at_epoch_millis = clock())
            }
        }
    }

    override suspend fun isSeen(itemId: String): Boolean =
        queries.selectSeenItem(item_key = itemId).executeAsOneOrNull() != null

    override suspend fun clearAll() {
        queries.removeAllSeenItems()
    }
}

class SqlDelightSessionRepository(
    private val database: SocialMediaDatabase,
) : SessionRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun getSessionState(platformId: PlatformId): SessionState {
        val session = queries.selectAccountSession(
            platform_id = platformId.serializedName,
            mapper = ::toAccountSessionRow,
        ).executeAsOneOrNull()
        return session?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut
    }

    override suspend fun signOut(platformId: PlatformId) {
        queries.removeAccountSession(platform_id = platformId.serializedName)
    }

    override suspend fun upsertSession(platformId: PlatformId, session: AccountSession) {
        queries.upsertAccountSession(
            platform_id = platformId.serializedName,
            account_id = session.accountId,
            access_token = session.accessToken,
            refresh_token = session.refreshToken,
            expires_at_epoch_millis = session.expiresAtEpochMillis,
        )
    }

    override suspend fun clearAll() {
        queries.removeAllAccountSessions()
    }
}

class SqlDelightFeedCacheRepository(
    private val database: SocialMediaDatabase,
    private val clock: () -> Long = { 0L },
) : FeedCacheRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun readItems(source: FeedSource, includeSeen: Boolean): List<FeedItem> {
        val mapper = ::toFeedItemRow
        return if (includeSeen) {
            queries.selectFeedItemsForSource(
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                mapper = mapper,
            ).executeAsList()
        } else {
            queries.selectUnseenFeedItemsForSource(
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                mapper = mapper,
            ).executeAsList()
        }
    }

    override suspend fun replaceItems(
        source: FeedSource,
        items: List<FeedItem>,
        nextCursor: String?,
        refreshedAtEpochMillis: Long?,
    ) {
        database.transaction {
            queries.upsertFeedSource(
                platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                display_name = source.displayName,
            )
            queries.removeFeedItemsForSource(
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
            )
            items.forEach { item ->
                queries.upsertFeedItem(
                    item_key = item.cacheKey,
                    item_id = item.itemId,
                    platform_id = item.platformId.serializedName,
                    source_platform_id = source.platformId.serializedName,
                    source_id = source.sourceId,
                    author_name = item.authorName,
                    title = item.title,
                    body = item.body,
                    permalink = item.permalink,
                    published_at_epoch_millis = item.publishedAtEpochMillis,
                    cached_at_epoch_millis = clock(),
                )
                if (item.seenState is SeenState.Seen) {
                    queries.markSeen(item_key = item.cacheKey, seen_at_epoch_millis = clock())
                }
            }
            queries.upsertSyncState(
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                next_cursor_value = nextCursor,
                last_refreshed_at_epoch_millis = refreshedAtEpochMillis,
            )
        }
    }

    override suspend fun getSyncState(source: FeedSource): FeedSyncState? =
        queries.selectSyncState(
            source_platform_id = source.platformId.serializedName,
            source_id = source.sourceId,
            mapper = ::toFeedSyncState,
        ).executeAsOneOrNull()

    override suspend fun clearAll() {
        database.transaction {
            queries.removeAllFeedItems()
            queries.removeAllSyncState()
            queries.removeAllFeedSources()
        }
    }
}

private fun toConfiguredSource(
    platform_id: String,
    source_kind: String,
    value: String,
): ConfiguredSource = when (source_kind) {
    "social_user" -> ConfiguredSource.SocialUser(
        platformId = PlatformId.fromSerializedName(platform_id),
        user = value,
    )
    "rss_feed" -> ConfiguredSource.RssFeed(url = value)
    else -> error("Unsupported source_kind: $source_kind")
}

private fun toAccountSessionRow(
    platform_id: String,
    account_id: String,
    access_token: String?,
    refresh_token: String?,
    expires_at_epoch_millis: Long?,
): AccountSession = AccountSession(
    accountId = account_id,
    accessToken = access_token,
    refreshToken = refresh_token,
    expiresAtEpochMillis = expires_at_epoch_millis,
)

private fun toFeedItemRow(
    item_key: String,
    item_id: String,
    platform_id: String,
    source_platform_id: String,
    source_id: String,
    display_name: String,
    author_name: String?,
    title: String?,
    body: String?,
    permalink: String?,
    published_at_epoch_millis: Long,
    seen_item_key: String?,
): FeedItem = FeedItem(
    itemId = item_id,
    platformId = PlatformId.fromSerializedName(platform_id),
    source = FeedSource(
        platformId = PlatformId.fromSerializedName(source_platform_id),
        sourceId = source_id,
        displayName = display_name,
    ),
    authorName = author_name,
    title = title,
    body = body,
    permalink = permalink,
    publishedAtEpochMillis = published_at_epoch_millis,
    seenState = if (seen_item_key == null) SeenState.Unseen else SeenState.Seen,
)

private fun toFeedSyncState(
    source_platform_id: String,
    source_id: String,
    display_name: String,
    next_cursor_value: String?,
    last_refreshed_at_epoch_millis: Long?,
): FeedSyncState = FeedSyncState(
    source = FeedSource(
        platformId = PlatformId.fromSerializedName(source_platform_id),
        sourceId = source_id,
        displayName = display_name,
    ),
    nextCursor = next_cursor_value?.let(::FeedCursor),
    lastRefreshedAtEpochMillis = last_refreshed_at_epoch_millis,
)

private val ConfiguredSource.kind: String
    get() = when (this) {
        is ConfiguredSource.RssFeed -> "rss_feed"
        is ConfiguredSource.SocialUser -> "social_user"
    }

private val ConfiguredSource.serializedValue: String
    get() = when (this) {
        is ConfiguredSource.RssFeed -> url
        is ConfiguredSource.SocialUser -> user
    }

private val FeedItem.cacheKey: String
    get() = "${platformId.serializedName}:$itemId"

private val PlatformId.serializedName: String
    get() = name.lowercase()

private fun PlatformId.Companion.fromSerializedName(value: String): PlatformId = when (value) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> error("Unsupported platform id: $value")
}
