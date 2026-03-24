package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedRepository

class FeedShellState(
    private val configuredSourceRepository: ConfiguredSourceRepository,
    private val feedRepository: FeedRepository,
) {
    private var configuredSources: List<FeedSource> = emptyList()
    private var loadedItems: List<FeedItem> = emptyList()
    private var selectedSourceId: String? = null
    private var loading = false
    private var error: ClientError? = null

    suspend fun start() {
        refresh()
    }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val configuredSources = configuredSourceRepository.listSources()
            val sources = configuredSources.map { it.toFeedSource() }
            this.configuredSources = sources
            val result = feedRepository.loadFeedItems(
                FeedRequest(
                    sources = configuredSources,
                ),
            )
            loadedItems = result.items
        } catch (failure: Throwable) {
            error = failure.toClientError()
        } finally {
            loading = false
        }
    }

    fun selectSource(sourceId: String?) {
        selectedSourceId = sourceId
    }

    val sources: List<FeedSource>
        get() = configuredSources

    val visibleItems: List<FeedItem>
        get() = loadedItems.filter { selectedSourceId == null || it.source.sourceId == selectedSourceId }

    val emptyState: VisibleFeedEmptyState?
        get() = when {
            error != null -> null
            sources.isEmpty() -> VisibleFeedEmptyState.NoConfiguredSources
            visibleItems.isEmpty() && selectedSourceId != null -> VisibleFeedEmptyState.NoItemsForSelectedSource(selectedSourceId!!)
            else -> null
        }

    val isLoading: Boolean
        get() = loading

    val loadError: ClientError?
        get() = error
}

sealed interface VisibleFeedEmptyState {
    data object NoConfiguredSources : VisibleFeedEmptyState
    data class NoItemsForSelectedSource(val sourceId: String) : VisibleFeedEmptyState
}

private fun ConfiguredSource.toFeedSource(): FeedSource = when (this) {
    is ConfiguredSource.RssFeed -> FeedSource(
        platformId = platformId,
        sourceId = url,
        displayName = url,
    )

    is ConfiguredSource.SocialUser -> FeedSource(
        platformId = platformId,
        sourceId = user,
        displayName = user,
    )
}

private fun Throwable.toClientError(): ClientError = when (this) {
    is ClientFailure -> clientError
    is ClientError -> this
    else -> ClientError.TemporaryFailure(message)
}
