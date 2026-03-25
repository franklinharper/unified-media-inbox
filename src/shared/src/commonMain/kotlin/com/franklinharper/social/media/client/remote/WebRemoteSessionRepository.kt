package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.SessionRepository
import kotlinx.serialization.encodeToString

class WebRemoteSessionRepository(
    private val http: WebApiHttp,
) : SessionRepository {
    suspend fun signIn(email: String, password: String): SessionState {
        val response = http.post(
            path = "/api/auth/sign-in",
            body = webApiJson.encodeToString(
                SignInRequestDto.serializer(),
                SignInRequestDto(email = email, password = password),
            ),
        )
        val session = response.decodeSuccess(AuthSessionResponseDto.serializer()).toDomain()
        http.bearerToken = session.session.accessToken
        return session
    }

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
}

private fun AuthSessionResponseDto.toDomain(): SessionState.SignedIn =
    SessionState.SignedIn(
        session = AccountSession(
            accountId = user.userId,
            accessToken = token,
        ),
    )
