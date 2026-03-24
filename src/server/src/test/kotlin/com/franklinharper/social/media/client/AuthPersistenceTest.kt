package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AuthPersistenceTest {
    @Test
    fun `server can create user and restore password-authenticated session`() = runBlocking {
        val database = ServerDatabaseFactory.inMemory()
        val sessions = ServerSessionService(database)

        val user = sessions.createUser("alice@example.com", "secret")
        val session = sessions.signIn("alice@example.com", "secret")

        assertEquals(user.userId, session.userId)
        assertEquals(user, sessions.requireUser(session.token))
    }

    @Test
    fun `server scopes shared persistence by user id`() = runBlocking {
        val database = ServerDatabaseFactory.inMemory()
        val sessions = ServerSessionService(database)
        val alice = sessions.createUser("alice@example.com", "secret")
        val bob = sessions.createUser("bob@example.com", "secret")

        val aliceSources = SqlDelightConfiguredSourceRepository(database, ownerUserId = alice.userId)
        val bobSources = SqlDelightConfiguredSourceRepository(database, ownerUserId = bob.userId)
        val aliceSeen = SqlDelightSeenItemRepository(database, ownerUserId = alice.userId)
        val bobSeen = SqlDelightSeenItemRepository(database, ownerUserId = bob.userId)
        val aliceCache = SqlDelightFeedCacheRepository(database, ownerUserId = alice.userId)
        val bobCache = SqlDelightFeedCacheRepository(database, ownerUserId = bob.userId)

        val aliceSource = ConfiguredSource.RssFeed(url = "https://example.com/alice.xml")
        val bobSource = ConfiguredSource.SocialUser(PlatformId.Bluesky, "bob")
        val aliceFeedSource = FeedSource(
            platformId = PlatformId.Rss,
            sourceId = "https://example.com/alice.xml",
            displayName = "Alice Feed",
        )
        val bobFeedSource = FeedSource(
            platformId = PlatformId.Bluesky,
            sourceId = "bob",
            displayName = "Bob Feed",
        )

        aliceSources.addSource(aliceSource)
        bobSources.addSource(bobSource)
        aliceSeen.markSeen("alice:item")
        bobSeen.markSeen("bob:item")
        aliceCache.replaceItems(
            source = aliceFeedSource,
            items = emptyList(),
            nextCursor = "alice-cursor",
            refreshedAtEpochMillis = 1000L,
        )
        bobCache.replaceItems(
            source = bobFeedSource,
            items = emptyList(),
            nextCursor = "bob-cursor",
            refreshedAtEpochMillis = 2000L,
        )

        assertEquals(listOf(aliceSource), aliceSources.listSources())
        assertEquals(listOf(bobSource), bobSources.listSources())
        assertTrue(aliceSeen.isSeen("alice:item"))
        assertTrue(!aliceSeen.isSeen("bob:item"))
        assertTrue(bobSeen.isSeen("bob:item"))
        assertTrue(!bobSeen.isSeen("alice:item"))
        assertEquals("alice-cursor", aliceCache.getSyncState(aliceFeedSource)?.nextCursor?.value)
        assertEquals("bob-cursor", bobCache.getSyncState(bobFeedSource)?.nextCursor?.value)
    }
}
