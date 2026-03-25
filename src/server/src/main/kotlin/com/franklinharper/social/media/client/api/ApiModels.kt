package com.franklinharper.social.media.client.api

import kotlinx.serialization.Serializable

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthUserResponse(
    val userId: String,
    val email: String,
)

@Serializable
data class AuthSessionResponse(
    val token: String,
    val user: AuthUserResponse,
)

@Serializable
data class SourceDto(
    val platformId: String,
    val kind: String,
    val value: String,
)

@Serializable
data class AddSourceRequest(
    val platformId: String,
    val kind: String,
    val value: String,
) {
    companion object {
        fun rss(url: String): AddSourceRequest =
            AddSourceRequest(platformId = "rss", kind = "rss_feed", value = url)

        fun socialUser(platformId: String, user: String): AddSourceRequest =
            AddSourceRequest(platformId = platformId, kind = "social_user", value = user)
    }
}

@Serializable
data class ListSourcesResponse(
    val sources: List<SourceDto>,
)

@Serializable
data class FeedSourceDto(
    val platformId: String,
    val sourceId: String,
    val displayName: String,
)

@Serializable
data class FeedItemDto(
    val itemId: String,
    val platformId: String,
    val source: FeedSourceDto,
    val authorName: String?,
    val title: String?,
    val body: String?,
    val permalink: String?,
    val commentsPermalink: String?,
    val publishedAtEpochMillis: Long,
    val seen: Boolean,
)

@Serializable
data class FeedSourceStatusDto(
    val source: FeedSourceDto,
    val state: String,
    val contentOrigin: String,
    val errorKind: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class FeedResponse(
    val items: List<FeedItemDto>,
    val sourceStatuses: List<FeedSourceStatusDto> = emptyList(),
)

@Serializable
data class MarkSeenRequest(
    val itemIds: List<String>,
)
