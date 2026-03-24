package com.franklinharper.social.media.client.repository

import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.db.SelectAllSourceErrors
import com.franklinharper.social.media.client.db.SelectSourceErrorsForSource
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSyncState
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry

class SqlDelightConfiguredSourceRepository(
    private val database: SocialMediaDatabase,
    private val ownerUserId: String,
) : ConfiguredSourceRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun listSources(): List<ConfiguredSource> =
        queries.selectConfiguredSources(
            user_id = ownerUserId,
            mapper = ::toConfiguredSource,
        ).executeAsList()

    override suspend fun addSource(source: ConfiguredSource) {
        queries.upsertConfiguredSource(
            user_id = ownerUserId,
            platform_id = source.platformId.serializedName,
            source_kind = source.kind,
            value_ = source.serializedValue,
        )
    }

    override suspend fun removeSource(source: ConfiguredSource) {
        queries.removeConfiguredSource(
            user_id = ownerUserId,
            platform_id = source.platformId.serializedName,
            source_kind = source.kind,
            value_ = source.serializedValue,
        )
    }

    override suspend fun clearAll() {
        queries.removeAllConfiguredSources(user_id = ownerUserId)
    }

    companion object {
        fun forUser(
            database: SocialMediaDatabase,
            ownerUserId: String,
        ): SqlDelightConfiguredSourceRepository =
            SqlDelightConfiguredSourceRepository(database, requireServerOwnerUserId(ownerUserId))
    }
}

class SqlDelightSeenItemRepository(
    private val database: SocialMediaDatabase,
    private val ownerUserId: String,
    private val clock: () -> Long = { 0L },
) : SeenItemRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun markSeen(itemId: String) {
        queries.markSeen(
            user_id = ownerUserId,
            item_key = itemId,
            seen_at_epoch_millis = clock(),
        )
    }

    override suspend fun markSeen(itemIds: List<String>) {
        database.transaction {
            itemIds.forEach { itemId ->
                queries.markSeen(
                    user_id = ownerUserId,
                    item_key = itemId,
                    seen_at_epoch_millis = clock(),
                )
            }
        }
    }

    override suspend fun isSeen(itemId: String): Boolean =
        queries.selectSeenItem(
            user_id = ownerUserId,
            item_key = itemId,
        ).executeAsOneOrNull() != null

    override suspend fun clearAll() {
        queries.removeAllSeenItems(user_id = ownerUserId)
    }

    companion object {
        fun forUser(
            database: SocialMediaDatabase,
            ownerUserId: String,
            clock: () -> Long = { 0L },
        ): SqlDelightSeenItemRepository =
            SqlDelightSeenItemRepository(database, requireServerOwnerUserId(ownerUserId), clock)
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
    private val ownerUserId: String,
    private val clock: () -> Long = { 0L },
) : FeedCacheRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun readItems(source: FeedSource, includeSeen: Boolean): List<FeedItem> {
        val mapper = ::toFeedItemRow
        return if (includeSeen) {
            queries.selectFeedItemsForSource(
                user_id = ownerUserId,
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                mapper = mapper,
            ).executeAsList()
        } else {
            queries.selectUnseenFeedItemsForSource(
                user_id = ownerUserId,
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
                user_id = ownerUserId,
                platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                display_name = source.displayName,
            )
            queries.removeFeedItemsForSource(
                user_id = ownerUserId,
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
            )
            items.forEach { item ->
                queries.upsertFeedItem(
                    user_id = ownerUserId,
                    item_key = item.cacheKey,
                    item_id = item.itemId,
                    platform_id = item.platformId.serializedName,
                    source_platform_id = source.platformId.serializedName,
                    source_id = source.sourceId,
                    author_name = item.authorName,
                    title = item.title,
                    body = item.body,
                    permalink = item.permalink,
                    comments_permalink = item.commentsPermalink,
                    published_at_epoch_millis = item.publishedAtEpochMillis,
                    cached_at_epoch_millis = clock(),
                )
                if (item.seenState is SeenState.Seen) {
                    queries.markSeen(
                        user_id = ownerUserId,
                        item_key = item.cacheKey,
                        seen_at_epoch_millis = clock(),
                    )
                }
            }
            queries.upsertSyncState(
                user_id = ownerUserId,
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                next_cursor_value = nextCursor,
                last_refreshed_at_epoch_millis = refreshedAtEpochMillis,
            )
        }
    }

    override suspend fun getSyncState(source: FeedSource): FeedSyncState? =
        queries.selectSyncState(
            user_id = ownerUserId,
            source_platform_id = source.platformId.serializedName,
            source_id = source.sourceId,
            mapper = ::toFeedSyncState,
        ).executeAsOneOrNull()

    override suspend fun clearAll() {
        database.transaction {
            queries.removeAllFeedItems(user_id = ownerUserId)
            queries.removeAllSyncState(user_id = ownerUserId)
            queries.removeAllFeedSources(user_id = ownerUserId)
        }
    }

    companion object {
        fun forUser(
            database: SocialMediaDatabase,
            ownerUserId: String,
            clock: () -> Long = { 0L },
        ): SqlDelightFeedCacheRepository =
            SqlDelightFeedCacheRepository(database, requireServerOwnerUserId(ownerUserId), clock)
    }
}

class SqlDelightSourceErrorRepository(
    private val database: SocialMediaDatabase,
    private val ownerUserId: String,
) : SourceErrorRepository {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    ) {
        queries.upsertFeedSource(
            user_id = ownerUserId,
            platform_id = source.platformId.serializedName,
            source_id = source.sourceId,
            display_name = source.displayName,
        )
        queries.insertSourceError(
            user_id = ownerUserId,
            source_platform_id = source.platformId.serializedName,
            source_id = source.sourceId,
            content_origin = contentOrigin.serializedName,
            error_kind = errorKind,
            error_message = errorMessage,
            occurred_at_epoch_millis = occurredAtEpochMillis,
        )
    }

    override suspend fun listErrors(source: FeedSource?, limit: Long): List<SourceErrorLogEntry> =
        if (source == null) {
            queries.selectAllSourceErrors(ownerUserId, limit).executeAsList().map(SelectAllSourceErrors::toDomain)
        } else {
            queries.selectSourceErrorsForSource(
                user_id = ownerUserId,
                source_platform_id = source.platformId.serializedName,
                source_id = source.sourceId,
                value_ = limit,
            ).executeAsList().map(SelectSourceErrorsForSource::toDomain)
    }

    override suspend fun clearAll() {
        queries.removeAllSourceErrors(user_id = ownerUserId)
    }

    companion object {
        fun forUser(
            database: SocialMediaDatabase,
            ownerUserId: String,
        ): SqlDelightSourceErrorRepository =
            SqlDelightSourceErrorRepository(database, requireServerOwnerUserId(ownerUserId))
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
    comments_permalink: String?,
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
    commentsPermalink = comments_permalink,
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

private fun toSourceErrorLogEntry(
    id: Long,
    source_platform_id: String,
    source_id: String,
    display_name: String,
    content_origin: String,
    error_kind: String,
    error_message: String?,
    occurred_at_epoch_millis: Long,
): SourceErrorLogEntry = SourceErrorLogEntry(
    id = id,
    source = FeedSource(
        platformId = PlatformId.fromSerializedName(source_platform_id),
        sourceId = source_id,
        displayName = display_name,
    ),
    contentOrigin = sourceContentOriginFromSerializedName(content_origin),
    errorKind = error_kind,
    errorMessage = error_message,
    occurredAtEpochMillis = occurred_at_epoch_millis,
)

private fun SelectAllSourceErrors.toDomain(): SourceErrorLogEntry = toSourceErrorLogEntry(
    id = id,
    source_platform_id = source_platform_id,
    source_id = source_id,
    display_name = display_name,
    content_origin = content_origin,
    error_kind = error_kind,
    error_message = error_message,
    occurred_at_epoch_millis = occurred_at_epoch_millis,
)

private fun SelectSourceErrorsForSource.toDomain(): SourceErrorLogEntry = toSourceErrorLogEntry(
    id = id,
    source_platform_id = source_platform_id,
    source_id = source_id,
    display_name = display_name,
    content_origin = content_origin,
    error_kind = error_kind,
    error_message = error_message,
    occurred_at_epoch_millis = occurred_at_epoch_millis,
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

const val LOCAL_OWNER_USER_ID = ""

private fun requireServerOwnerUserId(ownerUserId: String): String =
    ownerUserId.takeIf(String::isNotBlank) ?: error("Server-owned repositories require a non-blank user id")

private fun PlatformId.Companion.fromSerializedName(value: String): PlatformId = when (value) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> error("Unsupported platform id: $value")
}

private val SourceContentOrigin.serializedName: String
    get() = name.lowercase()

private fun sourceContentOriginFromSerializedName(value: String): SourceContentOrigin = when (value) {
    "refresh" -> SourceContentOrigin.Refresh
    "cache" -> SourceContentOrigin.Cache
    "none" -> SourceContentOrigin.None
    else -> error("Unsupported source content origin: $value")
}
