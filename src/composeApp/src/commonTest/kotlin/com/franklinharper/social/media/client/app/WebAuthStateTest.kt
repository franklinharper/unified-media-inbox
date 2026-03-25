package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.remote.WebAuthenticationSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebAuthStateTest {

    @Test
    fun `successful session restore enters authenticated state`() = runTest {
        val state = WebAuthState(
            sessionRepository = FakeWebAuthSessionRepository(
                restoreState = SessionState.SignedIn(
                    AccountSession(accountId = "user-1", accessToken = "token-1"),
                ),
            ),
        )

        state.start()

        assertEquals(WebAuthStatus.Authenticated, state.uiState.value.status)
    }

    @Test
    fun `missing session restore enters unauthenticated state`() = runTest {
        val state = WebAuthState(
            sessionRepository = FakeWebAuthSessionRepository(
                restoreState = SessionState.SignedOut,
            ),
        )

        state.start()

        assertEquals(WebAuthStatus.Unauthenticated, state.uiState.value.status)
    }

    @Test
    fun `successful sign in enters authenticated state`() = runTest {
        val state = WebAuthState(
            sessionRepository = FakeWebAuthSessionRepository(
                restoreState = SessionState.SignedOut,
                signInState = SessionState.SignedIn(
                    AccountSession(accountId = "user-1", accessToken = "token-1"),
                ),
            ),
        )

        state.signIn("alice@example.com", "secret")

        assertEquals(WebAuthStatus.Authenticated, state.uiState.value.status)
        assertEquals("alice@example.com" to "secret", state.uiState.value.lastSubmittedCredentials)
    }

    @Test
    fun `authentication failure surfaces error state`() = runTest {
        val state = WebAuthState(
            sessionRepository = FakeWebAuthSessionRepository(
                restoreState = SessionState.SignedOut,
                signInFailure = FakeClientFailure(ClientError.AuthenticationError("Bad credentials")),
            ),
        )

        state.signIn("alice@example.com", "bad-password")

        assertEquals(WebAuthStatus.Error, state.uiState.value.status)
        assertEquals("Bad credentials", state.uiState.value.message)
    }

    @Test
    fun `successful sign up enters authenticated state`() = runTest {
        val state = WebAuthState(
            sessionRepository = FakeWebAuthSessionRepository(
                restoreState = SessionState.SignedOut,
                signUpState = SessionState.SignedIn(
                    AccountSession(accountId = "user-2", accessToken = "token-2"),
                ),
            ),
        )

        state.signUp("new@example.com", "secret")

        assertEquals(WebAuthStatus.Authenticated, state.uiState.value.status)
        assertEquals("new@example.com" to "secret", state.uiState.value.lastSubmittedCredentials)
    }

    @Test
    fun `unauthorized server response resets to session expired state`() = runTest {
        val repository = FakeWebAuthSessionRepository(
            restoreState = SessionState.SignedIn(
                AccountSession(accountId = "user-1", accessToken = "token-1"),
            ),
        )
        val state = WebAuthState(sessionRepository = repository)

        state.start()
        state.onUnauthorized()

        assertEquals(WebAuthStatus.SessionExpired, state.uiState.value.status)
        assertEquals(1, repository.clearCalls)
    }
}

private class FakeWebAuthSessionRepository(
    private val restoreState: SessionState,
    private val signInState: SessionState = restoreState,
    private val signUpState: SessionState = restoreState,
    private val signInFailure: Throwable? = null,
) : WebAuthenticationSessionRepository {
    var clearCalls = 0

    override suspend fun restoreSession(): SessionState = restoreState

    override suspend fun signIn(email: String, password: String): SessionState {
        signInFailure?.let { throw it }
        return signInState
    }

    override suspend fun signUp(email: String, password: String): SessionState =
        signUpState

    override suspend fun clearSession() {
        clearCalls += 1
    }
}

private class FakeClientFailure(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure
