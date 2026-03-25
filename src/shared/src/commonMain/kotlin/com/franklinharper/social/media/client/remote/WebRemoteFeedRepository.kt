package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedLoadResult
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceLoadState
import com.franklinharper.social.media.client.repository.FeedRepository

class WebRemoteFeedRepository(
    private val http: WebApiHttp,
) : FeedRepository {
    override suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult =
        http.post("/api/feed/refresh?includeSeen=${request.includeSeen}")
            .decodeSuccess(FeedResponseDto.serializer())
            .toDomain()
}

internal fun FeedResponseDto.toDomain(): FeedLoadResult =
    FeedLoadResult(
        items = items.map(FeedItemDto::toDomain),
        sourceStatuses = sourceStatuses.map(FeedSourceStatusDto::toDomain),
    )

internal fun FeedItemDto.toDomain(): FeedItem =
    FeedItem(
        itemId = itemId,
        platformId = platformId.toPlatformId(),
        source = source.toDomain(),
        authorName = authorName,
        title = title,
        body = body,
        permalink = permalink,
        commentsPermalink = commentsPermalink,
        publishedAtEpochMillis = publishedAtEpochMillis,
        seenState = if (seen) SeenState.Seen else SeenState.Unseen,
    )

internal fun FeedSourceDto.toDomain(): FeedSource =
    FeedSource(
        platformId = platformId.toPlatformId(),
        sourceId = sourceId,
        displayName = displayName,
    )

private fun FeedSourceStatusDto.toDomain(): FeedSourceStatus =
    FeedSourceStatus(
        source = source.toDomain(),
        state = state.toSourceLoadState(errorKind = errorKind, errorMessage = errorMessage),
        contentOrigin = contentOrigin.toContentOrigin(),
    )

private fun String.toPlatformId(): PlatformId = when (lowercase()) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> error("Unsupported platform id: $this")
}

private fun String.toSourceLoadState(
    errorKind: String?,
    errorMessage: String?,
): SourceLoadState = when (lowercase()) {
    "loading" -> SourceLoadState.Loading
    "success" -> SourceLoadState.Success
    "error" -> SourceLoadState.Error(errorKind.toClientError(errorMessage))
    else -> error("Unsupported source state: $this")
}

private fun String?.toClientError(message: String?): ClientError = when (this) {
    "network" -> ClientError.NetworkError(message)
    "authentication" -> ClientError.AuthenticationError(message)
    "rate_limit" -> ClientError.RateLimitError(message?.toLongOrNull())
    "parsing" -> ClientError.ParsingError(message)
    "temporary" -> ClientError.TemporaryFailure(message)
    "permanent" -> ClientError.PermanentFailure(message)
    else -> ClientError.TemporaryFailure(message)
}

private fun String.toContentOrigin(): SourceContentOrigin = when (lowercase()) {
    "refresh" -> SourceContentOrigin.Refresh
    "cache" -> SourceContentOrigin.Cache
    "none" -> SourceContentOrigin.None
    else -> error("Unsupported content origin: $this")
}
