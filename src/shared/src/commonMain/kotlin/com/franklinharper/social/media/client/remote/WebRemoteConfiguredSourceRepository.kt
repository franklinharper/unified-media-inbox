package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import kotlinx.serialization.encodeToString

class WebRemoteConfiguredSourceRepository(
    private val http: WebApiHttp,
) : ConfiguredSourceRepository {
    override suspend fun listSources(): List<ConfiguredSource> =
        http.get("/api/sources")
            .decodeSuccess(ListSourcesResponseDto.serializer())
            .sources
            .map(SourceDto::toDomainSource)

    override suspend fun addSource(source: ConfiguredSource) {
        http.post(
            path = "/api/sources",
            body = webApiJson.encodeToString(AddSourceRequestDto.serializer(), source.toAddSourceRequest()),
        ).requireNoContentSuccess()
    }

    override suspend fun removeSource(source: ConfiguredSource) {
        http.delete(
            path = "/api/sources",
            body = webApiJson.encodeToString(SourceDto.serializer(), source.toSourceDto()),
        ).requireNoContentSuccess()
    }

    override suspend fun clearAll() {
        listSources().forEach { source -> removeSource(source) }
    }
}

private fun ConfiguredSource.toAddSourceRequest(): AddSourceRequestDto = when (this) {
    is ConfiguredSource.RssFeed -> AddSourceRequestDto(
        platformId = "rss",
        kind = "rss_feed",
        value = url,
    )
    is ConfiguredSource.SocialUser -> AddSourceRequestDto(
        platformId = platformId.serializedName,
        kind = "social_user",
        value = user,
    )
}

private fun ConfiguredSource.toSourceDto(): SourceDto = when (this) {
    is ConfiguredSource.RssFeed -> SourceDto(
        platformId = "rss",
        kind = "rss_feed",
        value = url,
    )
    is ConfiguredSource.SocialUser -> SourceDto(
        platformId = platformId.serializedName,
        kind = "social_user",
        value = user,
    )
}

private fun SourceDto.toDomainSource(): ConfiguredSource = when (kind) {
    "rss_feed" -> ConfiguredSource.RssFeed(url = value)
    "social_user" -> ConfiguredSource.SocialUser(platformId = platformId.toPlatformId(), user = value)
    else -> error("Unsupported source kind: $kind")
}

private fun String.toPlatformId(): PlatformId = when (lowercase()) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> error("Unsupported platform id: $this")
}

private val PlatformId.serializedName: String
    get() = name.lowercase()
