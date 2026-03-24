package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AddSourceState(
    private val configuredSourceRepository: ConfiguredSourceRepository,
) {
    private val _uiState = MutableStateFlow(AddSourceUiState())

    val uiState: StateFlow<AddSourceUiState> = _uiState.asStateFlow()

    fun selectType(type: SourceType) {
        _uiState.update { it.copy(selectedType = type, addError = null, didAddSource = false) }
    }

    suspend fun addRssSource(url: String) {
        addSource(
            type = SourceType.Rss,
            source = ConfiguredSource.RssFeed(url = url),
        )
    }

    suspend fun addBlueskySource(handle: String) {
        addSource(
            type = SourceType.Bluesky,
            source = ConfiguredSource.SocialUser(platformId = PlatformId.Bluesky, user = handle),
        )
    }

    private suspend fun addSource(type: SourceType, source: ConfiguredSource) {
        _uiState.update { it.copy(selectedType = type, isAdding = true, addError = null, didAddSource = false) }
        try {
            configuredSourceRepository.addSource(source)
            _uiState.update { it.copy(selectedType = null, isAdding = false, didAddSource = true) }
        } catch (cancellation: CancellationException) {
            _uiState.update { it.copy(isAdding = false) }
            throw cancellation
        } catch (failure: Throwable) {
            _uiState.update { it.copy(isAdding = false, addError = failure.message ?: "Unable to add source", didAddSource = false) }
        }
    }
}

data class AddSourceUiState(
    val selectedType: SourceType? = null,
    val isAdding: Boolean = false,
    val addError: String? = null,
    val didAddSource: Boolean = false,
)

enum class SourceType {
    Rss,
    Bluesky,
    Twitter,
}
