package com.franklinharper.social.media.client.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WebAutomationUiState(
    val email: String = "",
    val password: String = "",
    val rssUrl: String = "",
    val authVisible: Boolean = false,
    val feedVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val isAddingSource: Boolean = false,
    val feedItemCount: Int = 0,
    val feedSourceNames: List<String> = emptyList(),
    val feedItemTitles: List<String> = emptyList(),
) {
    val canSubmitAuth: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()

    val canSubmitRss: Boolean
        get() = !isAddingSource && rssUrl.isNotBlank()
}

class WebAutomationState {
    private val _uiState = MutableStateFlow(WebAutomationUiState())
    val uiState: StateFlow<WebAutomationUiState> = _uiState.asStateFlow()

    private var onSignIn: ((String, String) -> Unit)? = null
    private var onSignUp: ((String, String) -> Unit)? = null
    private var onRefreshFeed: (() -> Unit)? = null
    private var onSignOut: (() -> Unit)? = null
    private var onOpenAddSourceScreen: (() -> Unit)? = null
    private var onSelectRssSourceType: (() -> Unit)? = null
    private var onAddRssSource: ((String) -> Unit)? = null

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updateRssUrl(rssUrl: String) {
        _uiState.update { it.copy(rssUrl = rssUrl) }
    }

    fun updateEnvironment(
        authVisible: Boolean,
        feedVisible: Boolean,
        isSubmitting: Boolean,
        isAddingSource: Boolean,
        feedItemCount: Int,
        feedSourceNames: List<String>,
        feedItemTitles: List<String>,
    ) {
        _uiState.update {
            it.copy(
                authVisible = authVisible,
                feedVisible = feedVisible,
                isSubmitting = isSubmitting,
                isAddingSource = isAddingSource,
                feedItemCount = feedItemCount,
                feedSourceNames = feedSourceNames,
                feedItemTitles = feedItemTitles,
            )
        }
    }

    fun bindActions(
        onSignIn: (String, String) -> Unit,
        onSignUp: (String, String) -> Unit,
        onRefreshFeed: () -> Unit,
        onSignOut: () -> Unit,
        onOpenAddSourceScreen: () -> Unit,
        onSelectRssSourceType: () -> Unit,
        onAddRssSource: (String) -> Unit,
    ) {
        this.onSignIn = onSignIn
        this.onSignUp = onSignUp
        this.onRefreshFeed = onRefreshFeed
        this.onSignOut = onSignOut
        this.onOpenAddSourceScreen = onOpenAddSourceScreen
        this.onSelectRssSourceType = onSelectRssSourceType
        this.onAddRssSource = onAddRssSource
    }

    fun clearActions() {
        onSignIn = null
        onSignUp = null
        onRefreshFeed = null
        onSignOut = null
        onOpenAddSourceScreen = null
        onSelectRssSourceType = null
        onAddRssSource = null
    }

    fun submitSignIn() {
        val state = _uiState.value
        if (!state.canSubmitAuth) return
        onSignIn?.invoke(state.email.trim(), state.password)
    }

    fun submitSignUp() {
        val state = _uiState.value
        if (!state.canSubmitAuth) return
        onSignUp?.invoke(state.email.trim(), state.password)
    }

    fun refreshFeed() {
        onRefreshFeed?.invoke()
    }

    fun signOut() {
        onSignOut?.invoke()
    }

    fun submitRssSource() {
        val state = _uiState.value
        if (!state.canSubmitRss) return
        onOpenAddSourceScreen?.invoke()
        onSelectRssSourceType?.invoke()
        onAddRssSource?.invoke(state.rssUrl.trim())
    }
}
