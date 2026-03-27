package com.franklinharper.social.media.client.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.SessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerSyncSessionStoreTest {

    @Test
    fun `server sync session store persists and restores the signed in account`() = withDatabase { database ->
        runBlocking {
            val store = SqlDelightServerSyncSessionStore(database)
            val session = AccountSession(
                accountId = "server-user-1",
                accessToken = "server-token-1",
                refreshToken = "refresh-token-1",
                expiresAtEpochMillis = 1234L,
            )

            store.upsertSession(session)

            val restored = store.restoreSession()
            val signedIn = assertIs<SessionState.SignedIn>(restored)
            assertEquals(session, signedIn.session)
        }
    }

    @Test
    fun `server sync session store clears the signed in account`() = withDatabase { database ->
        runBlocking {
            val store = SqlDelightServerSyncSessionStore(database)
            store.upsertSession(
                AccountSession(
                    accountId = "server-user-1",
                    accessToken = "server-token-1",
                ),
            )

            store.clearSession()

            assertEquals(SessionState.SignedOut, store.restoreSession())
        }
    }

    private fun withDatabase(block: (SocialMediaDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SocialMediaDatabase.Schema.create(driver)
        try {
            block(SocialMediaDatabase(driver))
        } finally {
            driver.close()
        }
    }
}
