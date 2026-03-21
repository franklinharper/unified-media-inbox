package com.franklinharper.social.media.client.domain

enum class PlatformId {
    Rss,
    Bluesky,
    Twitter,
    ;

    companion object
}

data class AccountSession(
    val accountId: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochMillis: Long? = null,
)

data class FeedSource(
    val platformId: PlatformId,
    val sourceId: String,
    val displayName: String,
)

sealed interface ConfiguredSource {
    val platformId: PlatformId

    data class SocialUser(
        override val platformId: PlatformId,
        val user: String,
    ) : ConfiguredSource

    data class RssFeed(
        override val platformId: PlatformId = PlatformId.Rss,
        val url: String,
    ) : ConfiguredSource
}

data class FeedRequest(
    val sources: List<ConfiguredSource>,
    val includeSeen: Boolean = false,
)

sealed interface FeedQuery {
    val platformId: PlatformId

    data class SocialUsers(
        override val platformId: PlatformId,
        val users: List<String>,
        val cursor: FeedCursor? = null,
    ) : FeedQuery

    data class RssFeeds(
        override val platformId: PlatformId = PlatformId.Rss,
        val urls: List<String>,
        val cursor: FeedCursor? = null,
    ) : FeedQuery
}

data class FeedCursor(
    val value: String,
)

sealed interface SeenState {
    data object Seen : SeenState
    data object Unseen : SeenState
}

data class FeedItem(
    val itemId: String,
    val platformId: PlatformId,
    val source: FeedSource,
    val authorName: String?,
    val title: String?,
    val body: String?,
    val permalink: String?,
    val publishedAtEpochMillis: Long,
    val seenState: SeenState,
)

data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: FeedCursor? = null,
)

sealed interface SessionState {
    data object NotRequired : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: AccountSession) : SessionState
    data class Expired(val reason: String? = null) : SessionState
}

sealed interface ClientError {
    data class NetworkError(val message: String? = null) : ClientError
    data class AuthenticationError(val message: String? = null) : ClientError
    data class RateLimitError(val retryAfterMillis: Long? = null) : ClientError
    data class ParsingError(val message: String? = null) : ClientError
    data class TemporaryFailure(val message: String? = null) : ClientError
    data class PermanentFailure(val message: String? = null) : ClientError
}

data class FeedLoadResult(
    val items: List<FeedItem>,
    val sourceStatuses: List<FeedSourceStatus>,
)

data class FeedSourceStatus(
    val source: FeedSource,
    val state: SourceLoadState,
    val contentOrigin: SourceContentOrigin,
)

data class FeedSyncState(
    val source: FeedSource,
    val nextCursor: FeedCursor? = null,
    val lastRefreshedAtEpochMillis: Long? = null,
)

data class SourceErrorLogEntry(
    val id: Long,
    val source: FeedSource,
    val contentOrigin: SourceContentOrigin,
    val errorKind: String,
    val errorMessage: String?,
    val occurredAtEpochMillis: Long,
)

sealed interface SourceLoadState {
    data object Loading : SourceLoadState
    data object Success : SourceLoadState
    data class Error(val error: ClientError) : SourceLoadState
}

enum class SourceContentOrigin {
    Refresh,
    Cache,
    None,
}
