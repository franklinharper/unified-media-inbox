package com.franklinharper.social.media.client.sync

import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.remote.WebApiHttp
import com.franklinharper.social.media.client.remote.WebRemoteSessionRepository
import com.franklinharper.social.media.client.repository.SessionRepository

interface ServerSyncSessionStore {
    suspend fun restoreSession(): SessionState
    suspend fun upsertSession(session: AccountSession)
    suspend fun clearSession()
}

class ServerSyncAuthenticatedSessionRepository(
    private val platformSessionRepository: SessionRepository,
    private val http: WebApiHttp,
    private val serverSyncSessionStore: ServerSyncSessionStore,
) : SessionRepository, AuthenticatedSessionRepository {
    private val remoteSessionRepository = WebRemoteSessionRepository(http)

    override suspend fun getSessionState(platformId: PlatformId): SessionState =
        platformSessionRepository.getSessionState(platformId)

    override suspend fun upsertSession(platformId: PlatformId, session: AccountSession) {
        platformSessionRepository.upsertSession(platformId, session)
    }

    override suspend fun signOut(platformId: PlatformId) {
        platformSessionRepository.signOut(platformId)
    }

    override suspend fun clearAll() {
        platformSessionRepository.clearAll()
    }

    override suspend fun restoreSession(): SessionState {
        val storedSession = serverSyncSessionStore.restoreSession()
        if (storedSession !is SessionState.SignedIn) return SessionState.SignedOut

        val token = storedSession.session.accessToken
        if (token.isNullOrBlank()) {
            serverSyncSessionStore.clearSession()
            return SessionState.SignedOut
        }

        http.bearerToken = token
        return try {
            when (val remoteState = remoteSessionRepository.restoreSession()) {
                is SessionState.SignedIn -> {
                    serverSyncSessionStore.upsertSession(remoteState.session)
                    remoteState
                }
                SessionState.SignedOut -> {
                    serverSyncSessionStore.clearSession()
                    SessionState.Expired("Your session expired. Sign in again.")
                }
                is SessionState.Expired -> {
                    serverSyncSessionStore.clearSession()
                    remoteState
                }
                SessionState.NotRequired -> remoteState
            }
        } catch (_: Throwable) {
            storedSession
        }
    }

    override suspend fun signIn(email: String, password: String): SessionState =
        authenticate {
            remoteSessionRepository.signIn(email, password)
        }

    override suspend fun signUp(email: String, password: String): SessionState =
        authenticate {
            remoteSessionRepository.signUp(email, password)
        }

    override suspend fun signOut() {
        try {
            remoteSessionRepository.signOut()
        } finally {
            remoteSessionRepository.clearSession()
            serverSyncSessionStore.clearSession()
        }
    }

    override suspend fun clearSession() {
        remoteSessionRepository.clearSession()
        serverSyncSessionStore.clearSession()
    }

    private suspend fun authenticate(request: suspend () -> SessionState): SessionState {
        val state = request()
        if (state is SessionState.SignedIn) {
            serverSyncSessionStore.upsertSession(state.session)
        }
        return state
    }
}
