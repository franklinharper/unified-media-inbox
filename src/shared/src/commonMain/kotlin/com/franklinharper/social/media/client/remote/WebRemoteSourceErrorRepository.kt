package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SourceErrorLogEntry
import com.franklinharper.social.media.client.repository.SourceErrorRepository

class WebRemoteSourceErrorRepository(
    private val http: WebApiHttp,
) : SourceErrorRepository {
    override suspend fun logError(
        source: FeedSource,
        contentOrigin: SourceContentOrigin,
        errorKind: String,
        errorMessage: String?,
        occurredAtEpochMillis: Long,
    ) {
    }

    override suspend fun listErrors(source: FeedSource?, limit: Long): List<SourceErrorLogEntry> = emptyList()

    override suspend fun clearAll() {
    }
}
