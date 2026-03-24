package com.franklinharper.social.media.client.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
import com.franklinharper.social.media.client.domain.SessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightRepositoriesJvmTest {

    @Test
    fun `configured source repository persists and removes sources`() = withDatabase { database ->
        runBlocking {
            val repository = SqlDelightConfiguredSourceRepository(database)
            val rssFeed = ConfiguredSource.RssFeed(url = "https://example.com/feed.xml")
            val socialUser = ConfiguredSource.SocialUser(PlatformId.Bluesky, "frank")

            repository.addSource(rssFeed)
            repository.addSource(socialUser)

            assertEquals(listOf(socialUser, rssFeed), repository.listSources())

            repository.removeSource(socialUser)

            assertEquals(listOf(rssFeed), repository.listSources())
        }
    }

    @Test
    fun `seen item repository tracks seen state`() = withDatabase { database ->
        runBlocking {
            val repository = SqlDelightSeenItemRepository(database) { 123L }

            assertEquals(false, repository.isSeen("rss:item-1"))

            repository.markSeen(listOf("rss:item-1", "rss:item-2"))

            assertTrue(repository.isSeen("rss:item-1"))
            assertTrue(repository.isSeen("rss:item-2"))

            repository.clearAll()

            assertEquals(false, repository.isSeen("rss:item-1"))
        }
    }

    @Test
    fun `session repository stores signed in state and sign out clears it`() = withDatabase { database ->
        runBlocking {
            val repository = SqlDelightSessionRepository(database)
            val session = AccountSession(
                accountId = "frank",
                accessToken = "access",
                refreshToken = "refresh",
                expiresAtEpochMillis = 999L,
            )

            repository.upsertSession(PlatformId.Bluesky, session)

            val state = repository.getSessionState(PlatformId.Bluesky)
            assertIs<SessionState.SignedIn>(state)
            assertEquals(session, state.session)

            repository.signOut(PlatformId.Bluesky)

            assertEquals(SessionState.SignedOut, repository.getSessionState(PlatformId.Bluesky))
        }
    }

    @Test
    fun `feed cache repository stores items and sync state while filtering seen items`() = withDatabase { database ->
        runBlocking {
            val repository = SqlDelightFeedCacheRepository(database) { 500L }
            val source = FeedSource(
                platformId = PlatformId.Rss,
                sourceId = "https://example.com/feed.xml",
                displayName = "Example Feed",
            )
            val unseenItem = FeedItem(
                itemId = "item-1",
                platformId = PlatformId.Rss,
                source = source,
                authorName = "Author",
                title = "First",
                body = "Body one",
                permalink = "https://example.com/1",
                commentsPermalink = "https://example.com/1/comments",
                publishedAtEpochMillis = 100L,
                seenState = SeenState.Unseen,
            )
            val seenItem = FeedItem(
                itemId = "item-2",
                platformId = PlatformId.Rss,
                source = source,
                authorName = "Author",
                title = "Second",
                body = "Body two",
                permalink = "https://example.com/2",
                commentsPermalink = null,
                publishedAtEpochMillis = 200L,
                seenState = SeenState.Seen,
            )

            repository.replaceItems(
                source = source,
                items = listOf(unseenItem, seenItem),
                nextCursor = "cursor-1",
                refreshedAtEpochMillis = 700L,
            )

            assertEquals(listOf(seenItem, unseenItem), repository.readItems(source, includeSeen = true))
            assertEquals(listOf(unseenItem), repository.readItems(source, includeSeen = false))

            val syncState = repository.getSyncState(source)
            assertEquals("cursor-1", syncState?.nextCursor?.value)
            assertEquals(700L, syncState?.lastRefreshedAtEpochMillis)

            repository.clearAll()

            assertEquals(emptyList(), repository.readItems(source, includeSeen = true))
            assertNull(repository.getSyncState(source))
        }
    }

    @Test
    fun `source error repository stores and clears errors`() = withDatabase { database ->
        runBlocking {
            val repository = SqlDelightSourceErrorRepository(database)
            val source = FeedSource(
                platformId = PlatformId.Rss,
                sourceId = "https://example.com/feed.xml",
                displayName = "Example Feed",
            )

            repository.logError(
                source = source,
                contentOrigin = SourceContentOrigin.None,
                errorKind = "parsing",
                errorMessage = "invalid xml",
                occurredAtEpochMillis = 123L,
            )

            val errors = repository.listErrors(source)
            assertEquals(1, errors.size)
            assertEquals(source, errors.single().source)
            assertEquals(SourceContentOrigin.None, errors.single().contentOrigin)
            assertEquals("parsing", errors.single().errorKind)
            assertEquals("invalid xml", errors.single().errorMessage)
            assertEquals(123L, errors.single().occurredAtEpochMillis)

            repository.clearAll()

            assertEquals(emptyList(), repository.listErrors())
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
