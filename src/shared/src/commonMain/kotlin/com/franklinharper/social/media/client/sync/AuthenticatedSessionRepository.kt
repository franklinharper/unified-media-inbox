package com.franklinharper.social.media.client.sync

import com.franklinharper.social.media.client.domain.SessionState

interface AuthenticatedSessionRepository {
    suspend fun restoreSession(): SessionState
    suspend fun signIn(email: String, password: String): SessionState
    suspend fun signUp(email: String, password: String): SessionState
    suspend fun signOut()
    suspend fun clearSession()
}
