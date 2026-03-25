package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.repository.SeenItemRepository
import kotlinx.serialization.encodeToString

class WebRemoteSeenItemRepository(
    private val http: WebApiHttp,
) : SeenItemRepository {
    override suspend fun markSeen(itemId: String) {
        markSeen(listOf(itemId))
    }

    override suspend fun markSeen(itemIds: List<String>) {
        http.post(
            path = "/api/feed/seen",
            body = webApiJson.encodeToString(MarkSeenRequestDto.serializer(), MarkSeenRequestDto(itemIds)),
        ).requireNoContentSuccess()
    }

    override suspend fun isSeen(itemId: String): Boolean =
        http.get("/api/feed?includeSeen=true")
            .decodeSuccess(FeedResponseDto.serializer())
            .items
            .firstOrNull { item -> item.itemId == itemId }
            ?.seen
            ?: false

    override suspend fun clearAll() {
    }
}
