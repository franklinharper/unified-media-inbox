package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.SessionRepository
import kotlinx.serialization.encodeToString

interface WebAuthenticationSessionRepository {
    suspend fun restoreSession(): SessionState
    suspend fun signIn(email: String, password: String): SessionState
    suspend fun signUp(email: String, password: String): SessionState
    suspend fun clearSession()
}

class WebRemoteSessionRepository(
    private val http: WebApiHttp,
) : SessionRepository, WebAuthenticationSessionRepository {
    override suspend fun signIn(email: String, password: String): SessionState {
        return authenticate("/api/auth/sign-in", email, password)
    }

    override suspend fun signUp(email: String, password: String): SessionState {
        return authenticate("/api/auth/sign-up", email, password)
    }

    private suspend fun authenticate(path: String, email: String, password: String): SessionState {
        val response = http.post(
            path = path,
            body = webApiJson.encodeToString(
                SignInRequestDto.serializer(),
                SignInRequestDto(email = email, password = password),
            ),
        )
        val session = response.decodeSuccess(AuthSessionResponseDto.serializer()).toDomain()
        http.bearerToken = session.session.accessToken
        return session
    }

    override suspend fun restoreSession(): SessionState =
        getSessionState(PlatformId.Bluesky)

    override suspend fun getSessionState(platformId: PlatformId): SessionState {
        if (http.bearerToken.isNullOrBlank()) return SessionState.SignedOut
        val response = http.get("/api/auth/session")
        if (response.statusCode == 401) {
            http.bearerToken = null
            return SessionState.SignedOut
        }
        val session = response.decodeSuccess(AuthSessionResponseDto.serializer()).toDomain()
        http.bearerToken = session.session.accessToken
        return session
    }

    override suspend fun upsertSession(platformId: PlatformId, session: AccountSession) {
        http.bearerToken = session.accessToken
    }

    override suspend fun signOut(platformId: PlatformId) {
        val token = http.bearerToken ?: return
        val response = http.post("/api/auth/sign-out")
        if (response.statusCode == 401 || token == http.bearerToken) {
            http.bearerToken = null
        }
        if (response.statusCode !in 200..299 && response.statusCode != 401) {
            response.requireNoContentSuccess()
        }
    }

    override suspend fun clearAll() {
        http.bearerToken = null
    }

    override suspend fun clearSession() {
        clearAll()
    }
}

private fun AuthSessionResponseDto.toDomain(): SessionState.SignedIn =
    SessionState.SignedIn(
        session = AccountSession(
            accountId = user.userId,
            accessToken = token,
        ),
    )
