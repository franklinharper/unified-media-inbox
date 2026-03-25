package com.franklinharper.social.media.client.api

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SourceLoadState

fun ConfiguredSource.toSourceDto(): SourceDto = when (this) {
    is ConfiguredSource.RssFeed -> SourceDto(platformId = "rss", kind = "rss_feed", value = url)
    is ConfiguredSource.SocialUser -> SourceDto(
        platformId = platformId.serializedName,
        kind = "social_user",
        value = user,
    )
}

fun SourceDto.toDomainSourceOrNull(): ConfiguredSource? = when (kind) {
    "rss_feed" -> ConfiguredSource.RssFeed(url = value)
    "social_user" -> platformId.toPlatformIdOrNull()?.let { platformId ->
        ConfiguredSource.SocialUser(platformId = platformId, user = value)
    }
    else -> null
}

fun AddSourceRequest.toDomainSourceOrNull(): ConfiguredSource? =
    SourceDto(platformId = platformId, kind = kind, value = value).toDomainSourceOrNull()

fun FeedLoadResult.toFeedResponse(): FeedResponse =
    FeedResponse(
        items = items.map(FeedItem::toDto),
        sourceStatuses = sourceStatuses.map(FeedSourceStatus::toDto),
    )

private fun FeedItem.toDto(): FeedItemDto =
    FeedItemDto(
        itemId = cacheKey,
        platformId = platformId.serializedName,
        source = source.toDto(),
        authorName = authorName,
        title = title,
        body = body,
        permalink = permalink,
        commentsPermalink = commentsPermalink,
        publishedAtEpochMillis = publishedAtEpochMillis,
        seen = seenState is SeenState.Seen,
    )

private fun FeedSource.toDto(): FeedSourceDto =
    FeedSourceDto(
        platformId = platformId.serializedName,
        sourceId = sourceId,
        displayName = displayName,
    )

private fun FeedSourceStatus.toDto(): FeedSourceStatusDto {
    val errorState = state as? SourceLoadState.Error
    return FeedSourceStatusDto(
        source = source.toDto(),
        state = when (state) {
            SourceLoadState.Loading -> "loading"
            SourceLoadState.Success -> "success"
            is SourceLoadState.Error -> "error"
        },
        contentOrigin = contentOrigin.name.lowercase(),
        errorKind = errorState?.error?.kind,
        errorMessage = errorState?.error?.message,
    )
}

private val PlatformId.serializedName: String
    get() = name.lowercase()

private fun String.toPlatformIdOrNull(): PlatformId? = when (lowercase()) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> null
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

private val ClientError.message: String?
    get() = when (this) {
        is ClientError.NetworkError -> message
        is ClientError.AuthenticationError -> message
        is ClientError.RateLimitError -> retryAfterMillis?.toString()
        is ClientError.ParsingError -> message
        is ClientError.TemporaryFailure -> message
        is ClientError.PermanentFailure -> message
    }

private val FeedItem.cacheKey: String
    get() = "${platformId.serializedName}:$itemId"
