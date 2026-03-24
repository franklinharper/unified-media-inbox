package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FeedShellState(
    private val configuredSourceRepository: ConfiguredSourceRepository,
    private val feedRepository: FeedRepository,
) {
    private val _uiState = MutableStateFlow(FeedShellUiState())

    val uiState: StateFlow<FeedShellUiState> = _uiState.asStateFlow()

    suspend fun start() {
        refresh()
    }

    suspend fun refresh() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        try {
            val configuredSources = configuredSourceRepository.listSources()
            val sources = configuredSources.map { it.toFeedSource() }
            _uiState.update { current ->
                val resolvedSource = current.selectedSourceKey?.takeIf(sources::contains)
                    ?: current.selectedSourceId?.let { sourceId ->
                        sources.firstOrNull { it.sourceId == sourceId }
                    }
                current.copy(
                    sources = sources,
                    selectedSourceId = resolvedSource?.sourceId,
                    selectedSourceKey = resolvedSource,
                )
            }
            val result = feedRepository.loadFeedItems(
                FeedRequest(
                    sources = configuredSources,
                ),
            )
            _uiState.update { current ->
                current.copy(
                    items = result.items,
                    isLoading = false,
                )
            }
        } catch (cancellation: CancellationException) {
            _uiState.update { it.copy(isLoading = false) }
            throw cancellation
        } catch (failure: Throwable) {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    loadError = failure.toClientError(),
                )
            }
        }
    }

    fun selectSource(source: FeedSource?) {
        _uiState.update { current ->
            val resolvedSource = source?.takeIf(current.sources::contains)
            current.copy(
                selectedSourceId = resolvedSource?.sourceId,
                selectedSourceKey = resolvedSource,
            )
        }
    }

    fun selectSource(sourceId: String?) {
        _uiState.update { current ->
            val resolvedSource = sourceId?.let { id ->
                current.selectedSourceKey?.takeIf { it.sourceId == id && current.sources.contains(it) }
                    ?: current.sources.firstOrNull { it.sourceId == id }
            }
            current.copy(
                selectedSourceId = resolvedSource?.sourceId,
                selectedSourceKey = resolvedSource,
            )
        }
    }

    val sources: List<FeedSource>
        get() = uiState.value.sources

    val visibleItems: List<FeedItem>
        get() = uiState.value.visibleItems

    val emptyState: VisibleFeedEmptyState?
        get() = uiState.value.emptyState

    val isLoading: Boolean
        get() = uiState.value.isLoading

    val loadError: ClientError?
        get() = uiState.value.loadError
}

data class FeedShellUiState(
    val sources: List<FeedSource> = emptyList(),
    val selectedSourceId: String? = null,
    internal val selectedSourceKey: FeedSource? = null,
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: ClientError? = null,
) {
    val visibleItems: List<FeedItem>
        get() = when (val source = selectedSourceKey) {
            null -> items
            else -> items.filter { it.source == source }
        }

    val emptyState: VisibleFeedEmptyState?
        get() = when {
            loadError != null -> null
            sources.isEmpty() -> VisibleFeedEmptyState.NoConfiguredSources
            visibleItems.isEmpty() && selectedSourceId != null -> VisibleFeedEmptyState.NoItemsForSelectedSource(selectedSourceId)
            else -> null
        }
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
