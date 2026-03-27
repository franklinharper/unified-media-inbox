package com.franklinharper.social.media.client.sync

import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.SessionState

class SqlDelightServerSyncSessionStore(
    private val database: SocialMediaDatabase,
) : ServerSyncSessionStore {
    private val queries = database.socialMediaDatabaseQueries

    override suspend fun restoreSession(): SessionState =
        queries.selectServerSyncSession(
            session_key = SERVER_SYNC_SESSION_KEY,
            mapper = ::toAccountSession,
        ).executeAsOneOrNull()?.let(SessionState::SignedIn) ?: SessionState.SignedOut

    override suspend fun upsertSession(session: AccountSession) {
        val accessToken = requireNotNull(session.accessToken) {
            "Server sync sessions require an access token"
        }
        queries.upsertServerSyncSession(
            session_key = SERVER_SYNC_SESSION_KEY,
            account_id = session.accountId,
            access_token = accessToken,
            refresh_token = session.refreshToken,
            expires_at_epoch_millis = session.expiresAtEpochMillis,
        )
    }

    override suspend fun clearSession() {
        queries.removeServerSyncSession(session_key = SERVER_SYNC_SESSION_KEY)
    }
}

private fun toAccountSession(
    account_id: String,
    access_token: String,
    refresh_token: String?,
    expires_at_epoch_millis: Long?,
): AccountSession = AccountSession(
    accountId = account_id,
    accessToken = access_token,
    refreshToken = refresh_token,
    expiresAtEpochMillis = expires_at_epoch_millis,
)

private const val SERVER_SYNC_SESSION_KEY = "server_sync"
