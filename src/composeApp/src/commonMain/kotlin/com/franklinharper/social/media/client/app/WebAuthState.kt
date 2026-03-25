package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.remote.WebAuthenticationSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WebAuthState(
    private val sessionRepository: WebAuthenticationSessionRepository,
) {
    private val _uiState = MutableStateFlow(WebAuthUiState())

    val uiState: StateFlow<WebAuthUiState> = _uiState.asStateFlow()

    suspend fun start() {
        _uiState.update { it.copy(status = WebAuthStatus.Authenticating, message = null) }
        try {
            _uiState.value = sessionRepository.restoreSession().toUiState(
                fallbackStatus = WebAuthStatus.Unauthenticated,
                previousCredentials = _uiState.value.lastSubmittedCredentials,
            )
        } catch (cancellation: CancellationException) {
            _uiState.update { it.copy(status = WebAuthStatus.Unauthenticated) }
            throw cancellation
        } catch (failure: Throwable) {
            _uiState.value = WebAuthUiState(
                status = WebAuthStatus.Error,
                message = failure.toMessage(defaultMessage = "Unable to restore session."),
                lastSubmittedCredentials = _uiState.value.lastSubmittedCredentials,
            )
        }
    }

    suspend fun signIn(email: String, password: String) {
        _uiState.value = WebAuthUiState(
            status = WebAuthStatus.Authenticating,
            message = null,
            lastSubmittedCredentials = email to password,
        )
        try {
            _uiState.value = sessionRepository.signIn(email, password).toUiState(
                fallbackStatus = WebAuthStatus.Error,
                previousCredentials = email to password,
            )
        } catch (cancellation: CancellationException) {
            _uiState.update { it.copy(status = WebAuthStatus.Unauthenticated) }
            throw cancellation
        } catch (failure: Throwable) {
            _uiState.value = WebAuthUiState(
                status = WebAuthStatus.Error,
                message = failure.toMessage(defaultMessage = "Unable to sign in."),
                lastSubmittedCredentials = email to password,
            )
        }
    }

    suspend fun onUnauthorized() {
        sessionRepository.clearSession()
        _uiState.value = WebAuthUiState(
            status = WebAuthStatus.SessionExpired,
            message = "Your session expired. Sign in again.",
            lastSubmittedCredentials = _uiState.value.lastSubmittedCredentials,
        )
    }
}

data class WebAuthUiState(
    val status: WebAuthStatus = WebAuthStatus.Unauthenticated,
    val message: String? = null,
    val lastSubmittedCredentials: Pair<String, String>? = null,
) {
    fun toLoginUiState(): LoginUiState =
        LoginUiState(
            isSubmitting = status == WebAuthStatus.Authenticating,
            message = message,
        )
}

data class LoginUiState(
    val isSubmitting: Boolean = false,
    val message: String? = null,
)

enum class WebAuthStatus {
    Unauthenticated,
    Authenticating,
    Authenticated,
    SessionExpired,
    Error,
}

private fun SessionState.toUiState(
    fallbackStatus: WebAuthStatus,
    previousCredentials: Pair<String, String>?,
): WebAuthUiState = when (this) {
    is SessionState.SignedIn -> WebAuthUiState(
        status = WebAuthStatus.Authenticated,
        lastSubmittedCredentials = previousCredentials,
    )
    SessionState.SignedOut,
    SessionState.NotRequired -> WebAuthUiState(
        status = if (this == SessionState.NotRequired) WebAuthStatus.Authenticated else fallbackStatus,
        lastSubmittedCredentials = previousCredentials,
    )
    is SessionState.Expired -> WebAuthUiState(
        status = WebAuthStatus.SessionExpired,
        message = reason ?: "Your session expired. Sign in again.",
        lastSubmittedCredentials = previousCredentials,
    )
}

private fun Throwable.toMessage(defaultMessage: String): String = when (this) {
    is ClientFailure -> clientError.toUserMessage(defaultMessage)
    is ClientError -> toUserMessage(defaultMessage)
    else -> message ?: defaultMessage
}

private fun ClientError.toUserMessage(defaultMessage: String): String = when (this) {
    is ClientError.AuthenticationError -> message ?: defaultMessage
    is ClientError.NetworkError -> message ?: defaultMessage
    is ClientError.RateLimitError -> retryAfterMillis?.let { "Try again in ${it}ms." } ?: defaultMessage
    is ClientError.ParsingError -> message ?: defaultMessage
    is ClientError.TemporaryFailure -> message ?: defaultMessage
    is ClientError.PermanentFailure -> message ?: defaultMessage
}
